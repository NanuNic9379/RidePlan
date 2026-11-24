package io.github.nanunic9379.rideplan.route

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.github.nanunic9379.rideplan.resources.Res
import io.github.nanunic9379.rideplan.resources.route_needs_space
import io.github.nanunic9379.rideplan.resources.route_not_covered
import io.github.nanunic9379.rideplan.resources.route_not_covered_stops
import io.github.nanunic9379.rideplan.resources.route_outside_stop_n
import io.github.nanunic9379.rideplan.ride.Stop
import org.jetbrains.compose.resources.stringResource

/** A stop outside the published road data (#14). [number] counts from 1, like on the map. */
data class OutsideStop(val number: Int, val name: String?)

/**
 * Coverage check (#14): which stops of the ride fall outside the published grid cells.
 * Empty when every stop is covered or when the published cells are unknown (no network,
 * old whole-region data), so the generic message is shown.
 */
fun outsideStops(stops: List<Stop>, published: RoutingCells?): List<OutsideStop> {
    if (published == null || published.packs.none { it.level == 1 }) return emptyList()
    return published.outside(stops.map { it.point }).map { OutsideStop(it + 1, stops[it].name?.trim()?.ifEmpty { null }) }
}

/**
 * The rider's text for [RouteState.NotCovered]: names the stops that have no road data, so it is
 * clear what to move (a stop across the border, a typo in an imported link), not just "no data".
 */
@Composable
internal fun NotCoveredText(state: RouteState.NotCovered) {
    val text = if (state.outside.isEmpty()) {
        stringResource(Res.string.route_not_covered)
    } else {
        val names = state.outside.map { it.name ?: stringResource(Res.string.route_outside_stop_n, it.number) }
        stringResource(Res.string.route_not_covered_stops, names.joinToString(", "))
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Space used on the phone after a download: about 2.5 times the download itself. */
@Composable
internal fun spaceNote(download: DataDownload): String = stringResource(Res.string.route_needs_space, formatBytes(download.tileBytes))
