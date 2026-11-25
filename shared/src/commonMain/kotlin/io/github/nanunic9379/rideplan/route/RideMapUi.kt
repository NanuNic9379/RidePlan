package io.github.nanunic9379.rideplan.route

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.nanunic9379.rideplan.personal.LocalUnits
import io.github.nanunic9379.rideplan.profile.Measures
import io.github.nanunic9379.rideplan.resources.Res
import io.github.nanunic9379.rideplan.resources.ridemap_attribution
import io.github.nanunic9379.rideplan.resources.ridemap_busy
import io.github.nanunic9379.rideplan.resources.ridemap_delete
import io.github.nanunic9379.rideplan.resources.ridemap_download
import io.github.nanunic9379.rideplan.resources.ridemap_download_less
import io.github.nanunic9379.rideplan.resources.ridemap_downloading
import io.github.nanunic9379.rideplan.resources.ridemap_err_limit
import io.github.nanunic9379.rideplan.resources.ridemap_err_network
import io.github.nanunic9379.rideplan.resources.ridemap_err_other
import io.github.nanunic9379.rideplan.resources.ridemap_interrupted
import io.github.nanunic9379.rideplan.resources.ridemap_mobile_ask
import io.github.nanunic9379.rideplan.resources.ridemap_reduced
import io.github.nanunic9379.rideplan.resources.ridemap_resume
import io.github.nanunic9379.rideplan.resources.ridemap_saved
import io.github.nanunic9379.rideplan.resources.ridemap_saved_less
import io.github.nanunic9379.rideplan.resources.ridemap_size
import io.github.nanunic9379.rideplan.resources.ridemap_stale
import io.github.nanunic9379.rideplan.resources.ridemap_title
import io.github.nanunic9379.rideplan.resources.ridemap_too_long
import io.github.nanunic9379.rideplan.resources.ridemap_update
import io.github.nanunic9379.rideplan.resources.route_cancel
import io.github.nanunic9379.rideplan.resources.route_mobile_confirm
import io.github.nanunic9379.rideplan.resources.route_mobile_wait
import io.github.nanunic9379.rideplan.ride.Ride
import io.github.nanunic9379.rideplan.ui.AppIcons
import io.github.nanunic9379.rideplan.ui.Radius
import io.github.nanunic9379.rideplan.ui.Touch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/** A map saved for one ride (#1), as the platform found it on the phone. */
data class SavedRideMap(
    val info: RideMapInfo,
    /** Space it takes on the phone, as MapLibre counts it. */
    val bytes: Long,
    /** OpenFreeMap has published newer tiles since: with no signal the map may not show. */
    val stale: Boolean = false,
)

/** What to download: the road of a ride and the planned area. */
class RideMapRequest(val ride: String, val name: String?, val plan: RideMap.Plan)

enum class RideMapError { NETWORK, LIMIT, OTHER }

/** The one map download at a time, if any. */
sealed interface RideMapStatus {
    data object Idle : RideMapStatus
    /** On mobile data with "Download only on Wi-Fi" on (#44): the rider confirms first. */
    data class AskMobile(val request: RideMapRequest) : RideMapStatus
    /** [share]: of everything to download (tiles, style, fonts), 0 to 1. */
    data class Downloading(val ride: String, val tiles: Long, val totalTiles: Long, val bytes: Long, val share: Float = 0f) : RideMapStatus
    data class Failed(val request: RideMapRequest, val error: RideMapError, val detail: String? = null) : RideMapStatus
}

/** The map for one ride, for use without signal (#1): the platform downloads, the sheet shows. */
@Stable
class RideMapActions(
    /** Starts (or resumes) the download; [mobileConfirmed]: the rider accepted mobile data. */
    val download: (request: RideMapRequest, mobileConfirmed: Boolean) -> Unit,
    /** Stops the running download and removes what it saved. */
    val cancel: () -> Unit,
    /** Deletes the saved map of the ride with this [RideMap.rideKey]. */
    val delete: (ride: String) -> Unit,
) {
    var saved by mutableStateOf<List<SavedRideMap>>(emptyList())
    var status by mutableStateOf<RideMapStatus>(RideMapStatus.Idle)

    /** "Ready without signal" (#67, #68): the map with the road data; null = not offered. */
    var ready: ReadyOfflineActions? = null
}

/**
 * "Map without signal" in the ride sheet: the size first, then the download; while it runs,
 * the progress with Cancel; once saved, the space it takes with Delete.
 */
@Composable
fun RideMapSection(ride: Ride, actions: RideMapActions) {
    if (ride.straightLines) return
    val key = remember(ride.track) { RideMap.rideKey(ride.track) }
    val saved = actions.saved.firstOrNull { it.info.ride == key }
    val status = actions.status
    val mine = when (status) {
        RideMapStatus.Idle -> false
        is RideMapStatus.AskMobile -> status.request.ride == key
        is RideMapStatus.Downloading -> status.ride == key
        is RideMapStatus.Failed -> status.request.ride == key
    }
    // The tile count walks the whole road: off the main thread, once per road.
    val offer by produceState<RideMap.Offer?>(null, key) { value = withContext(Dispatchers.Default) { RideMap.offer(ride.track) } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Title()
        when {
            mine && status is RideMapStatus.Downloading -> Progress(status, actions)
            mine && status is RideMapStatus.AskMobile -> AskMobile(status.request, actions)
            mine && status is RideMapStatus.Failed -> Failure(status, actions)
            saved != null -> Saved(saved, offer, ride, key, actions)
            else -> Offer(offer, ride, key, actions, busy = status is RideMapStatus.Downloading)
        }
        Text(stringResource(Res.string.ridemap_attribution), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** How wide the saved map is on each side of the road: "5 km" or "3 mi" (#15). */
@Composable
private fun marginText(): String = Measures.roundDistance(RideMap.MARGIN_KM, LocalUnits.current)

@Composable
private fun Title() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(AppIcons.Map, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(Res.string.ridemap_title), style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun Offer(offer: RideMap.Offer?, ride: Ride, key: String, actions: RideMapActions, busy: Boolean) {
    when (offer) {
        null -> LinearProgressIndicator(Modifier.fillMaxWidth())
        is RideMap.Offer.Full -> {
            Body(stringResource(Res.string.ridemap_size, "${offer.plan.estimate.tiles}", formatBytes(offer.plan.estimate.bytes), marginText()))
            DownloadButton(stringResource(Res.string.ridemap_download), AppIcons.Download, enabled = !busy) {
                actions.download(RideMapRequest(key, ride.name, offer.plan), false)
            }
        }
        is RideMap.Offer.Reduced -> {
            Body(stringResource(Res.string.ridemap_reduced, formatBytes(offer.full.bytes), "${offer.plan.estimate.tiles}", formatBytes(offer.plan.estimate.bytes)))
            DownloadButton(stringResource(Res.string.ridemap_download_less), AppIcons.Download, enabled = !busy) {
                actions.download(RideMapRequest(key, ride.name, offer.plan), false)
            }
        }
        is RideMap.Offer.TooLong ->
            Body(stringResource(Res.string.ridemap_too_long, formatBytes(offer.lowest.bytes), formatBytes(MapTiles.OPENFREEMAP_MAX_BYTES)), error = true)
    }
    if (busy && offer !is RideMap.Offer.TooLong) Body(stringResource(Res.string.ridemap_busy))
}

@Composable
private fun Progress(status: RideMapStatus.Downloading, actions: RideMapActions) {
    Body(stringResource(Res.string.ridemap_downloading, "${status.tiles}", "${status.totalTiles}", formatBytes(status.bytes)))
    LinearProgressIndicator(
        progress = { status.share },
        modifier = Modifier.fillMaxWidth(),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = actions.cancel, modifier = Modifier.heightIn(min = Touch.Min)) { Text(stringResource(Res.string.route_cancel)) }
    }
}

@Composable
private fun AskMobile(request: RideMapRequest, actions: RideMapActions) {
    Body(stringResource(Res.string.ridemap_mobile_ask, formatBytes(request.plan.estimate.bytes)))
    DownloadButton(stringResource(Res.string.route_mobile_confirm), AppIcons.Download) { actions.download(request, true) }
    TextButton(onClick = { actions.status = RideMapStatus.Idle }, modifier = Modifier.fillMaxWidth().heightIn(min = Touch.Min)) {
        Text(stringResource(Res.string.route_mobile_wait))
    }
}

@Composable
private fun Failure(status: RideMapStatus.Failed, actions: RideMapActions) {
    val text = when (status.error) {
        RideMapError.NETWORK -> stringResource(Res.string.ridemap_err_network)
        RideMapError.LIMIT -> stringResource(Res.string.ridemap_err_limit)
        RideMapError.OTHER -> stringResource(Res.string.ridemap_err_other, status.detail ?: "?")
    }
    Body(text, error = true)
    if (status.error != RideMapError.LIMIT) {
        DownloadButton(stringResource(Res.string.ridemap_resume), AppIcons.Refresh) { actions.download(status.request, false) }
    }
    TextButton(onClick = { actions.status = RideMapStatus.Idle }, modifier = Modifier.fillMaxWidth().heightIn(min = Touch.Min)) {
        Text(stringResource(Res.string.route_cancel))
    }
}

@Composable
private fun Saved(saved: SavedRideMap, offer: RideMap.Offer?, ride: Ride, key: String, actions: RideMapActions) {
    val info = saved.info
    val size = formatBytes(saved.bytes)
    when {
        !info.complete -> {
            Body(stringResource(Res.string.ridemap_interrupted), error = true)
            val plan = plan(offer)
            if (plan != null) {
                DownloadButton(stringResource(Res.string.ridemap_resume), AppIcons.Download) { actions.download(RideMapRequest(key, ride.name, plan), false) }
            }
        }
        saved.stale -> {
            Body(stringResource(Res.string.ridemap_stale), error = true)
            val plan = plan(offer)
            if (plan != null) {
                DownloadButton(stringResource(Res.string.ridemap_update), AppIcons.Refresh) { actions.download(RideMapRequest(key, ride.name, plan), false) }
            }
        }
        else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(AppIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                stringResource(if (info.maxZoom >= RideMap.FULL_ZOOM) Res.string.ridemap_saved else Res.string.ridemap_saved_less, size),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = { actions.delete(key) }, modifier = Modifier.heightIn(min = Touch.Min)) {
            Icon(AppIcons.Delete, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(Res.string.ridemap_delete))
        }
    }
}

/** The plan to download again; the platform resumes the saved map if it is the same. */
private fun plan(offer: RideMap.Offer?): RideMap.Plan? = when (offer) {
    is RideMap.Offer.Full -> offer.plan
    is RideMap.Offer.Reduced -> offer.plan
    else -> null
}

@Composable
private fun Body(text: String, error: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DownloadButton(text: String, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = Touch.Glove), shape = Radius.Control) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(text)
    }
}
