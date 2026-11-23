package io.github.nanunic9379.rideplan.route

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.nanunic9379.rideplan.resources.Res
import io.github.nanunic9379.rideplan.resources.route_stop_pass
import io.github.nanunic9379.rideplan.resources.route_stop_stop
import io.github.nanunic9379.rideplan.resources.route_stops_hint
import io.github.nanunic9379.rideplan.resources.route_stops_title
import io.github.nanunic9379.rideplan.ride.Ride
import io.github.nanunic9379.rideplan.ride.Stop
import org.jetbrains.compose.resources.stringResource

/**
 * The named intermediate stops: stop there (you reach it) or pass nearby without entering the town (#29).
 * A tap switches; points dragged on the map are not listed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StopKindsRow(stops: List<Stop>, enabled: Boolean, onToggle: (Int) -> Unit) {
    val items = stops.withIndex().filter { (i, s) -> i != 0 && i != stops.lastIndex && s.name != null && s.kind != Stop.Kind.VIA }
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.route_stops_title), style = MaterialTheme.typography.labelLarge)
        Text(
            stringResource(Res.string.route_stops_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { (i, s) ->
                val pass = s.kind == Stop.Kind.PASS
                val name = s.name.orEmpty().substringBefore(',').take(28)
                FilterChip(
                    selected = pass,
                    onClick = { onToggle(i) },
                    enabled = enabled,
                    label = {
                        Text(stringResource(if (pass) Res.string.route_stop_pass else Res.string.route_stop_stop, name), maxLines = 1)
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}

/** Stop [index] switches from "stop there" to "pass nearby" or back (#29); the route must be recalculated. */
fun Ride.toggledStop(index: Int): Ride {
    val stop = stops.getOrNull(index) ?: return this
    val kind = when (stop.kind) {
        Stop.Kind.STOP -> Stop.Kind.PASS
        Stop.Kind.PASS -> Stop.Kind.STOP
        Stop.Kind.VIA -> return this
    }
    return copy(stops = stops.toMutableList().also { it[index] = stop.copy(kind = kind) }).unrouted()
}
