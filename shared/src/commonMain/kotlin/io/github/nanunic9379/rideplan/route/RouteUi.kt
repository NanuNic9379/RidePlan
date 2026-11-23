package io.github.nanunic9379.rideplan.route

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.nanunic9379.rideplan.resources.Res
import io.github.nanunic9379.rideplan.resources.region_around_ride
import io.github.nanunic9379.rideplan.resources.region_it_calabria_sicilia
import io.github.nanunic9379.rideplan.resources.region_it_centro
import io.github.nanunic9379.rideplan.resources.region_it_nord_est
import io.github.nanunic9379.rideplan.resources.region_it_nord_ovest
import io.github.nanunic9379.rideplan.resources.region_it_sardegna
import io.github.nanunic9379.rideplan.resources.region_it_sud
import io.github.nanunic9379.rideplan.resources.ride_duration
import io.github.nanunic9379.rideplan.resources.ride_duration_min
import io.github.nanunic9379.rideplan.resources.route_cancel
import io.github.nanunic9379.rideplan.resources.route_checking
import io.github.nanunic9379.rideplan.resources.route_computing
import io.github.nanunic9379.rideplan.resources.route_done_note
import io.github.nanunic9379.rideplan.resources.route_detour
import io.github.nanunic9379.rideplan.resources.route_detour_long
import io.github.nanunic9379.rideplan.resources.route_download
import io.github.nanunic9379.rideplan.resources.route_downloading
import io.github.nanunic9379.rideplan.resources.route_err_checksum
import io.github.nanunic9379.rideplan.resources.route_err_engine
import io.github.nanunic9379.rideplan.resources.route_err_network
import io.github.nanunic9379.rideplan.resources.route_err_no_route
import io.github.nanunic9379.rideplan.resources.route_err_no_space
import io.github.nanunic9379.rideplan.resources.route_needs_data
import io.github.nanunic9379.rideplan.resources.route_not_covered
import io.github.nanunic9379.rideplan.resources.route_curves_max
import io.github.nanunic9379.rideplan.resources.route_curves_more
import io.github.nanunic9379.rideplan.resources.route_curves_normal
import io.github.nanunic9379.rideplan.resources.route_curves_some
import io.github.nanunic9379.rideplan.resources.route_opt_curves
import io.github.nanunic9379.rideplan.resources.route_opt_no_highways
import io.github.nanunic9379.rideplan.resources.route_opt_no_tolls
import io.github.nanunic9379.rideplan.resources.route_retry
import io.github.nanunic9379.rideplan.ride.Ride
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Where the routing of the ride on screen stands. */
sealed interface RouteState {
    data object Idle : RouteState
    data object Checking : RouteState
    data class NeedsData(val download: DataDownload) : RouteState
    data class Downloading(val download: DataDownload, val done: Long) : RouteState
    data object Computing : RouteState
    data object NotCovered : RouteState
    data class Failed(val error: RouteError, val detail: String? = null) : RouteState
}

enum class RouteError { NETWORK, CHECKSUM, NO_SPACE, NO_ROUTE, ENGINE }

class RouteException(val error: RouteError, val detail: String? = null) :
    Exception(error.name + (detail?.let { ": $it" } ?: ""))

/** What the user can do; the platform carries it out. */
class RouteActions(
    val download: (DataDownload) -> Unit,
    val cancel: () -> Unit,
    val retry: () -> Unit,
    /** Preferences changed: the platform remembers them and routes the ride again (#4). */
    val changeOptions: (RouteOptions) -> Unit = {},
    /** A stop switches between "stop" and "pass near" or back: route again (#29). */
    val toggleStop: (index: Int) -> Unit = {},
    /** The stops were edited on the map (#2): show the new ride and route it again. */
    val editStops: (Ride) -> Unit = {},
)

/**
 * Route character: curve level and roads to avoid.
 * Everything is 48 dp tall: usable with gloves on.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RouteOptionsRow(options: RouteOptions, enabled: Boolean, onChange: (RouteOptions) -> Unit) = Column(
    verticalArrangement = Arrangement.spacedBy(4.dp),
) {
    Text(stringResource(Res.string.route_opt_curves), style = MaterialTheme.typography.labelLarge)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        Curves.entries.forEachIndexed { i, level ->
            SegmentedButton(
                selected = options.curves == level,
                onClick = { if (options.curves != level) onChange(options.copy(curves = level)) },
                shape = SegmentedButtonDefaults.itemShape(i, Curves.entries.size),
                enabled = enabled,
                icon = {},
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(curvesLabel(level), maxLines = 1) }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OptionChip(stringResource(Res.string.route_opt_no_highways), options.avoidHighways, enabled) { onChange(options.copy(avoidHighways = it)) }
        OptionChip(stringResource(Res.string.route_opt_no_tolls), options.avoidTolls, enabled) { onChange(options.copy(avoidTolls = it)) }
    }
}

/**
 * What the chosen curves cost, measured on the real ride against the normal route.
 * Below 5% nothing is said; from 20% up the warning is highlighted.
 */
@Composable
fun DetourNote(info: RouteInfo) {
    val share = info.extraShare ?: return
    val base = info.baseline ?: return
    if (share < 0.05) return
    val long = share >= RouteInfo.LONG_DETOUR
    val text = stringResource(
        if (long) Res.string.route_detour_long else Res.string.route_detour,
        "${(share * 100).roundToInt()}",
        "${(info.km - base.km).roundToInt()}",
        durationText((info.seconds - base.seconds).coerceAtLeast(0.0)),
    )
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (long) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun curvesLabel(level: Curves): String = when (level) {
    Curves.NORMAL -> stringResource(Res.string.route_curves_normal)
    Curves.SOME -> stringResource(Res.string.route_curves_some)
    Curves.MORE -> stringResource(Res.string.route_curves_more)
    Curves.MAX -> stringResource(Res.string.route_curves_max)
}

@Composable
private fun OptionChip(label: String, selected: Boolean, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    FilterChip(
        selected = selected,
        onClick = { onToggle(!selected) },
        enabled = enabled,
        label = { Text(label) },
        leadingIcon = if (selected) ({ Text("✓") }) else null,
        modifier = Modifier.heightIn(min = 48.dp),
    )
}

/** Row of the ride card dedicated to the route. */
@Composable
fun RouteSection(state: RouteState, actions: RouteActions?) {
    val small = MaterialTheme.typography.bodySmall
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when (state) {
        RouteState.Idle -> Unit
        RouteState.Checking -> Waiting(stringResource(Res.string.route_checking))
        RouteState.Computing -> Waiting(stringResource(Res.string.route_computing))
        RouteState.NotCovered -> Text(stringResource(Res.string.route_not_covered), style = small, color = muted)
        is RouteState.NeedsData -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(Res.string.route_needs_data, dataName(state.download), formatBytes(state.download.bytes)),
                style = small,
            )
            if (actions != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = { actions.download(state.download) }) { Text(stringResource(Res.string.route_download)) }
                }
            }
        }
        is RouteState.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(
                    Res.string.route_downloading,
                    dataName(state.download),
                    formatBytes(state.done),
                    formatBytes(state.download.bytes),
                ),
                style = small,
            )
            LinearProgressIndicator(
                progress = { (state.done.toFloat() / state.download.bytes.coerceAtLeast(1)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (actions != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = actions.cancel) { Text(stringResource(Res.string.route_cancel)) }
                }
            }
        }
        is RouteState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(failureText(state), style = small, color = MaterialTheme.colorScheme.error)
            if (actions != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = actions.retry) { Text(stringResource(Res.string.route_retry)) }
                }
            }
        }
    }
}

@Composable
private fun Waiting(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text, style = MaterialTheme.typography.bodySmall)
        LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

/** Note under a routed ride: which data was used. */
@Composable
fun routeDoneNote(info: RouteInfo): String = stringResource(Res.string.route_done_note, info.osmDate.take(10))

@Composable
fun durationText(seconds: Double): String {
    val total = (seconds / 60).roundToInt()
    return if (total < 60) stringResource(Res.string.ride_duration_min, "$total")
    else stringResource(Res.string.ride_duration, "${total / 60}", (total % 60).toString().padStart(2, '0'))
}

@Composable
private fun failureText(state: RouteState.Failed): String = when (state.error) {
    RouteError.NETWORK -> stringResource(Res.string.route_err_network)
    RouteError.CHECKSUM -> stringResource(Res.string.route_err_checksum)
    RouteError.NO_SPACE -> stringResource(Res.string.route_err_no_space)
    RouteError.NO_ROUTE -> stringResource(Res.string.route_err_no_route)
    RouteError.ENGINE -> stringResource(Res.string.route_err_engine, state.detail ?: "?")
}

@Composable
private fun dataName(download: DataDownload): String = when (download) {
    is RegionDownload -> regionName(download.region.id)
    is CellsDownload -> stringResource(Res.string.region_around_ride)
}

@Composable
private fun regionName(id: String): String = when (id) {
    "it-nord-ovest" -> stringResource(Res.string.region_it_nord_ovest)
    "it-nord-est" -> stringResource(Res.string.region_it_nord_est)
    "it-centro" -> stringResource(Res.string.region_it_centro)
    "it-sud" -> stringResource(Res.string.region_it_sud)
    "it-calabria-sicilia" -> stringResource(Res.string.region_it_calabria_sicilia)
    "it-sardegna" -> stringResource(Res.string.region_it_sardegna)
    else -> id
}

/** "410 MB", "1.2 GB": at a glance, not to the byte. */

internal fun formatBytes(bytes: Long): String {
    val mb = bytes / 1_000_000.0
    return if (mb < 1000) "${mb.roundToLong()} MB" else "${(mb / 100).roundToLong() / 10.0} GB"
}
