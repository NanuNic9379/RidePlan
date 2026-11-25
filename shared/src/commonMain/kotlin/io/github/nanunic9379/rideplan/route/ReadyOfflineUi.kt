package io.github.nanunic9379.rideplan.route

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.nanunic9379.rideplan.resources.Res
import io.github.nanunic9379.rideplan.resources.nav_offline_anyway
import io.github.nanunic9379.rideplan.resources.nav_offline_download
import io.github.nanunic9379.rideplan.resources.nav_offline_download_size
import io.github.nanunic9379.rideplan.resources.nav_offline_text
import io.github.nanunic9379.rideplan.resources.nav_offline_title
import io.github.nanunic9379.rideplan.resources.ready_checking
import io.github.nanunic9379.rideplan.resources.ready_downloading_map
import io.github.nanunic9379.rideplan.resources.ready_downloading_road
import io.github.nanunic9379.rideplan.resources.ready_err_network
import io.github.nanunic9379.rideplan.resources.ready_err_other
import io.github.nanunic9379.rideplan.resources.ready_err_space
import io.github.nanunic9379.rideplan.resources.ready_get_all
import io.github.nanunic9379.rideplan.resources.ready_looking_up
import io.github.nanunic9379.rideplan.resources.ready_map_too_long
import io.github.nanunic9379.rideplan.resources.ready_missing
import io.github.nanunic9379.rideplan.resources.ready_mobile_ask
import io.github.nanunic9379.rideplan.resources.ready_not_covered
import io.github.nanunic9379.rideplan.resources.ready_ok_text
import io.github.nanunic9379.rideplan.resources.ready_part_map
import io.github.nanunic9379.rideplan.resources.ready_part_map_update
import io.github.nanunic9379.rideplan.resources.ready_part_road
import io.github.nanunic9379.rideplan.resources.ready_title_missing
import io.github.nanunic9379.rideplan.resources.ready_title_ok
import io.github.nanunic9379.rideplan.resources.route_cancel
import io.github.nanunic9379.rideplan.resources.route_mobile_confirm
import io.github.nanunic9379.rideplan.resources.route_mobile_wait
import io.github.nanunic9379.rideplan.resources.route_retry
import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.Ride
import io.github.nanunic9379.rideplan.ui.AppIcons
import io.github.nanunic9379.rideplan.ui.Radius
import io.github.nanunic9379.rideplan.ui.Touch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

enum class ReadyError { NETWORK, NO_SPACE, OTHER }

/** Where the one-tap "Get everything" of a ride stands (#67); [ride] is its [RideMap.rideKey]. */
sealed interface ReadyStatus {
    data object Idle : ReadyStatus
    /** Asking online which road data the ride needs. */
    data class LookingUp(val ride: String) : ReadyStatus
    /** On mobile data with "Download only on Wi-Fi" on: one question for everything. */
    data class AskMobile(val ride: String, val get: GetEverything) : ReadyStatus
    data class Downloading(val ride: String, val done: Long, val total: Long) : ReadyStatus
    data class Failed(val ride: String, val error: ReadyError, val detail: String? = null) : ReadyStatus
    /** No road data is published where the ride goes: the map may still have been downloaded. */
    data class NotCovered(val ride: String) : ReadyStatus
}

private val ReadyStatus.ride: String?
    get() = when (this) {
        ReadyStatus.Idle -> null
        is ReadyStatus.LookingUp -> ride
        is ReadyStatus.AskMobile -> ride
        is ReadyStatus.Downloading -> ride
        is ReadyStatus.Failed -> ride
        is ReadyStatus.NotCovered -> ride
    }

/**
 * "Ready without signal" (#67): the platform says what is on the phone and downloads; this
 * chains the road data and the ride map behind one button, with one question about mobile data.
 */
@Stable
class ReadyOfflineActions(
    val rideMap: RideMapActions,
    private val scope: CoroutineScope,
    /** The road data on the phone covers these points, without network. */
    private val hasRoadData: suspend (List<LatLon>) -> Boolean,
    /** What the published road data offers for these points (online); null = none published here. */
    private val planRoadData: suspend (List<LatLon>) -> DataDownload?,
    private val downloadRoadData: suspend (DataDownload, progress: (Long) -> Unit) -> Unit,
    /** "Download only on Wi-Fi" is on and the network is metered. */
    private val mustAskForMobile: () -> Boolean,
) {
    var status by mutableStateOf<ReadyStatus>(ReadyStatus.Idle)

    /** Goes up after a road-data download, so the status is checked again. */
    var checks by mutableIntStateOf(0)
        private set

    private var job: Job? = null
    private var offerKey: String? = null
    private var offer: RideMap.Offer? = null

    internal suspend fun roadData(points: List<LatLon>): Boolean =
        points.isEmpty() || runCatching { hasRoadData(points) }.getOrDefault(false)

    /** The map offer of the road: one walk of the track per road. */
    internal suspend fun offer(key: String, track: List<LatLon>): RideMap.Offer? {
        if (key == offerKey) return offer
        val fresh = withContext(Dispatchers.Default) { RideMap.offer(track) }
        offerKey = key
        offer = fresh
        return fresh
    }

    /** A download for this ride is running (road data or map). */
    fun busy(key: String): Boolean {
        val s = status
        val map = rideMap.status
        return job?.isActive == true && s.ride == key || map is RideMapStatus.Downloading && map.ride == key
    }

    /** Everything [readiness] says is missing, road data first, then the map. */
    fun getEverything(ride: Ride, readiness: OfflineReadiness, mobileConfirmed: Boolean = false) {
        if (job?.isActive == true || !readiness.canDownload) return
        val key = RideMap.rideKey(ride.track)
        job = scope.launch {
            var notCovered = false
            val roadPlan = if (OfflineNeed.ROAD_DATA in readiness.missing) {
                status = ReadyStatus.LookingUp(key)
                val plan = attempt(key) { planRoadData(ReadyOffline.points(ride)) } ?: return@launch
                plan.value.also { notCovered = it == null }
            } else null
            val get = ReadyOffline.getEverything(readiness, key, ride.name, roadPlan)
            if (!get.empty && !mobileConfirmed && mustAskForMobile()) {
                status = ReadyStatus.AskMobile(key, get)
                return@launch
            }
            run(key, get, notCovered)
        }
    }

    /** The rider accepted mobile data for everything. */
    fun confirmMobile(ask: ReadyStatus.AskMobile) {
        if (job?.isActive == true) return
        job = scope.launch { run(ask.ride, ask.get, notCovered = false) }
    }

    fun cancel() {
        job?.cancel()
        status = ReadyStatus.Idle
    }

    /** Back to the status line (after an error, or "Wait for Wi-Fi"). */
    fun dismiss() {
        if (job?.isActive != true) status = ReadyStatus.Idle
    }

    private suspend fun run(key: String, get: GetEverything, notCovered: Boolean) {
        val road = get.road
        if (road != null) {
            status = ReadyStatus.Downloading(key, 0, road.bytes)
            attempt(key) { downloadRoadData(road) { done -> status = ReadyStatus.Downloading(key, done, road.bytes) } } ?: return
            checks++
        }
        status = if (notCovered) ReadyStatus.NotCovered(key) else ReadyStatus.Idle
        // Already asked (or not on mobile data): the map does not ask again.
        get.map?.let { rideMap.download(it, true) }
    }

    private class Done<T>(val value: T)

    /** Runs [block]; on failure shows it and gives null. */
    private suspend fun <T> attempt(key: String, block: suspend () -> T): Done<T>? = try {
        Done(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: RouteException) {
        status = ReadyStatus.Failed(
            key,
            when (e.error) {
                RouteError.NETWORK, RouteError.WIFI_ONLY -> ReadyError.NETWORK
                RouteError.NO_SPACE -> ReadyError.NO_SPACE
                else -> ReadyError.OTHER
            },
            e.error.name,
        )
        checks++
        null
    } catch (e: Exception) {
        status = ReadyStatus.Failed(key, ReadyError.OTHER, e.message ?: e::class.simpleName)
        checks++
        null
    }
}

/**
 * What [ride] still needs for a day without signal; null while it is being checked, or when
 * the question does not apply (a ride of straight lines has no road to keep).
 */
@Composable
fun rememberOfflineReadiness(ride: Ride?, ready: ReadyOfflineActions?): OfflineReadiness? {
    if (ride == null || ready == null || ride.straightLines) return null
    val key = remember(ride.track) { RideMap.rideKey(ride.track) }
    val points = remember(ride.stops, ride.track) { ReadyOffline.points(ride) }
    val offer by produceState<RideMap.Offer?>(null, key) { value = ready.offer(key, ride.track) }
    val road by produceState<Boolean?>(null, points, ready.checks) { value = ready.roadData(points) }
    val roadData = road ?: return null
    val map = ReadyOffline.mapState(key, ready.rideMap.saved)
    if (map != MapState.SAVED && offer == null) return null
    return ReadyOffline.check(map, offer, roadData)
}

/** "Road data, map": what is missing, in the rider's words. */
@Composable
private fun partsText(missing: List<OfflineNeed>): String = missing.map {
    when (it) {
        OfflineNeed.ROAD_DATA -> stringResource(Res.string.ready_part_road)
        OfflineNeed.MAP -> stringResource(Res.string.ready_part_map)
        OfflineNeed.MAP_UPDATE -> stringResource(Res.string.ready_part_map_update)
    }
}.joinToString(", ")

/**
 * One status line in the ride sheet (#67): "Ready without signal", or what is missing with one
 * button that gets it all. The detail of the map stays in [RideMapSection] below.
 */
@Composable
fun ReadyOfflineSection(ride: Ride, ready: ReadyOfflineActions) {
    if (ride.straightLines) return
    val key = remember(ride.track) { RideMap.rideKey(ride.track) }
    val readiness = rememberOfflineReadiness(ride, ready)
    val status = ready.status.takeIf { it.ride == key } ?: ReadyStatus.Idle
    val map = ready.rideMap.status
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            status is ReadyStatus.LookingUp -> Waiting(stringResource(Res.string.ready_looking_up), null)
            status is ReadyStatus.Downloading -> {
                Waiting(
                    stringResource(Res.string.ready_downloading_road, formatBytes(status.done), formatBytes(status.total)),
                    if (status.total > 0) (status.done.toFloat() / status.total).coerceIn(0f, 1f) else null,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = ready::cancel, modifier = Modifier.heightIn(min = Touch.Min)) { Text(stringResource(Res.string.route_cancel)) }
                }
            }
            status is ReadyStatus.AskMobile -> {
                Line(AppIcons.Wifi, stringResource(Res.string.ready_mobile_ask, formatBytes(status.get.bytes)), error = false)
                MainButton(stringResource(Res.string.route_mobile_confirm)) { ready.confirmMobile(status) }
                TextButton(onClick = ready::dismiss, modifier = Modifier.fillMaxWidth().heightIn(min = Touch.Min)) {
                    Text(stringResource(Res.string.route_mobile_wait))
                }
            }
            map is RideMapStatus.Downloading && map.ride == key ->
                Waiting(stringResource(Res.string.ready_downloading_map), map.share)
            readiness == null -> Waiting(stringResource(Res.string.ready_checking), null)
            readiness.ready -> {
                Line(AppIcons.Check, stringResource(Res.string.ready_title_ok), error = false, title = true)
                Text(stringResource(Res.string.ready_ok_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> {
                Line(AppIcons.Warning, stringResource(Res.string.ready_title_missing), error = true, title = true)
                Text(stringResource(Res.string.ready_missing, partsText(readiness.missing)), style = MaterialTheme.typography.bodyMedium)
                if (readiness.mapMissing && readiness.mapPlan == null) {
                    Text(stringResource(Res.string.ready_map_too_long), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when (status) {
                    is ReadyStatus.Failed -> Text(
                        when (status.error) {
                            ReadyError.NETWORK -> stringResource(Res.string.ready_err_network)
                            ReadyError.NO_SPACE -> stringResource(Res.string.ready_err_space)
                            ReadyError.OTHER -> stringResource(Res.string.ready_err_other, status.detail ?: "?")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    is ReadyStatus.NotCovered -> Text(stringResource(Res.string.ready_not_covered), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> {}
                }
                val otherMap = map is RideMapStatus.Downloading
                // After "no road data here" only the map can still help: the button stays for it.
                val worth = if (status is ReadyStatus.NotCovered) readiness.mapMissing && readiness.mapPlan != null else readiness.canDownload
                if (worth) {
                    MainButton(
                        stringResource(if (status is ReadyStatus.Failed) Res.string.route_retry else Res.string.ready_get_all),
                        enabled = !otherMap,
                    ) { ready.getEverything(ride, readiness) }
                }
            }
        }
    }
}

@Composable
private fun Line(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, error: Boolean, title: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        Text(text, style = if (title) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Waiting(text: String, share: Float?) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (share == null) LinearProgressIndicator(Modifier.fillMaxWidth())
    else LinearProgressIndicator(progress = { share }, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun MainButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = Touch.Glove), shape = Radius.Control) {
        Icon(AppIcons.Download, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(text)
    }
}

/**
 * "Navigate" with a check first (#68): when the ride is not ready without signal, a warning
 * with "Download now" and "Navigate anyway"; otherwise navigation starts at once. While the
 * check is still running it does not hold the rider back. [onDownload]: the download started,
 * e.g. open the ride sheet so its progress shows.
 */
@Composable
fun rememberNavigateCheck(ride: Ride?, ready: ReadyOfflineActions?, onDownload: () -> Unit, navigate: () -> Unit): () -> Unit {
    val readiness = rememberOfflineReadiness(ride, ready)
    var asking by remember { mutableStateOf(false) }
    if (asking && ride != null && ready != null && readiness != null && !readiness.ready) {
        val key = remember(ride.track) { RideMap.rideKey(ride.track) }
        val busy = ready.busy(key) || ready.rideMap.status is RideMapStatus.Downloading
        val canGet = readiness.canDownload && !busy
        // Only the map size is known offline; the road data size comes once online.
        val mapOnly = OfflineNeed.ROAD_DATA !in readiness.missing
        val size = readiness.mapPlan?.estimate?.bytes?.takeIf { mapOnly }
        AlertDialog(
            onDismissRequest = { asking = false },
            icon = { Icon(AppIcons.Warning, contentDescription = null) },
            title = { Text(stringResource(Res.string.nav_offline_title)) },
            text = { Text(stringResource(Res.string.nav_offline_text, partsText(readiness.missing))) },
            confirmButton = {
                if (canGet) {
                    Button(
                        onClick = { asking = false; ready.getEverything(ride, readiness); onDownload() },
                        modifier = Modifier.heightIn(min = Touch.Min),
                        shape = Radius.Control,
                    ) {
                        Text(if (size != null) stringResource(Res.string.nav_offline_download_size, formatBytes(size)) else stringResource(Res.string.nav_offline_download))
                    }
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { asking = false; navigate() }, modifier = Modifier.heightIn(min = Touch.Min), shape = Radius.Control) {
                    Text(stringResource(Res.string.nav_offline_anyway))
                }
            },
        )
    }
    return { if (readiness != null && !readiness.ready) asking = true else navigate() }
}
