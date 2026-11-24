package io.github.nanunic9379.rideplan.ride

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nanunic9379.rideplan.personal.distanceText
import io.github.nanunic9379.rideplan.profile.Bike
import io.github.nanunic9379.rideplan.resources.Res
import io.github.nanunic9379.rideplan.resources.fuel_hide
import io.github.nanunic9379.rideplan.resources.fuel_longest
import io.github.nanunic9379.rideplan.resources.fuel_no_range
import io.github.nanunic9379.rideplan.resources.fuel_none
import io.github.nanunic9379.rideplan.resources.fuel_over_range
import io.github.nanunic9379.rideplan.resources.fuel_refuel_here
import io.github.nanunic9379.rideplan.resources.fuel_show
import io.github.nanunic9379.rideplan.resources.fuel_station
import io.github.nanunic9379.rideplan.resources.fuel_stretch
import io.github.nanunic9379.rideplan.resources.fuel_title
import io.github.nanunic9379.rideplan.resources.fuel_unknown
import io.github.nanunic9379.rideplan.resources.fuel_within_range
import io.github.nanunic9379.rideplan.route.FuelAlong
import io.github.nanunic9379.rideplan.route.FuelReport
import io.github.nanunic9379.rideplan.route.RouteActions
import io.github.nanunic9379.rideplan.route.RouteState
import io.github.nanunic9379.rideplan.route.StationOnRide
import io.github.nanunic9379.rideplan.route.withRefuelStop
import io.github.nanunic9379.rideplan.ui.AppIcons
import io.github.nanunic9379.rideplan.ui.Touch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** What the ride sheet knows about fuel along the ride. */
private sealed interface FuelView {
    data object Loading : FuelView

    /** No station list on the phone for part of the ride: nothing is said about fuel. */
    data object Unknown : FuelView

    data class Ready(val report: FuelReport) : FuelView
}

/**
 * Fuel along the ride (#7), in the ride sheet: the longest stretch without a station, a warning
 * when it is longer than the bike's range (only if the rider entered tank and consumption), and
 * the stations near the road with "Refuel here". Only rides with a real road.
 */
@Composable
internal fun FuelSection(state: RideScreenState, actions: RouteActions?, bike: Bike?) {
    val ride = state.ride ?: return
    val load = actions?.fuelStations ?: return
    if (ride.straightLines || state.plan != null) return
    val line = ride.line
    val totalKm = ride.lengthKm
    val view by produceState<FuelView>(FuelView.Loading, line, totalKm) {
        value = try {
            val stations = load(line)
            if (stations == null) FuelView.Unknown
            else FuelView.Ready(withContext(Dispatchers.Default) { FuelAlong.report(line, stations, totalKm) })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FuelView.Unknown
        }
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when (val v = view) {
        FuelView.Loading -> Unit
        FuelView.Unknown -> Text(stringResource(Res.string.fuel_unknown), style = MaterialTheme.typography.bodyMedium, color = muted)
        is FuelView.Ready -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val report = v.report
            val range = bike?.rangeKm?.toDouble()
            val over = range?.let(report::overRange).orEmpty()
            val warn = MaterialTheme.colorScheme.error
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(AppIcons.Fuel, contentDescription = null, tint = if (over.isNotEmpty()) warn else muted, modifier = Modifier.size(20.dp))
                Text(stringResource(Res.string.fuel_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            }
            if (report.stations.isEmpty()) {
                Text(stringResource(Res.string.fuel_none), style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                stringResource(Res.string.fuel_longest, distanceText(report.longest.km)),
                style = MaterialTheme.typography.bodyLarge,
                color = if (over.isNotEmpty()) warn else MaterialTheme.colorScheme.onSurface,
            )
            when {
                range == null -> Text(stringResource(Res.string.fuel_no_range), style = MaterialTheme.typography.bodyMedium, color = muted)
                over.isEmpty() -> Text(stringResource(Res.string.fuel_within_range, distanceText(range)), style = MaterialTheme.typography.bodyMedium, color = muted)
                else -> {
                    Text(stringResource(Res.string.fuel_over_range, distanceText(range)), style = MaterialTheme.typography.bodyMedium, color = warn)
                    over.forEach { s ->
                        Text(stringResource(Res.string.fuel_stretch, distanceText(s.fromKm), distanceText(s.toKm)), style = MaterialTheme.typography.bodyMedium, color = warn)
                    }
                }
            }
            if (report.stations.isNotEmpty()) {
                var open by remember(line) { mutableStateOf(false) }
                TextButton(onClick = { open = !open }, modifier = Modifier.heightIn(min = Touch.Min)) {
                    Text(if (open) stringResource(Res.string.fuel_hide) else pluralStringResource(Res.plurals.fuel_show, report.stations.size, report.stations.size))
                }
                if (open) {
                    val canEdit = ride.stopsEditable && state.route == RouteState.Idle
                    report.stations.forEach { s ->
                        StationRow(s, isStop = ride.stops.any { distanceKm(it.point, s.station.point) < 0.03 }, onRefuel = if (canEdit) ({
                            val fresh = ride.withRefuelStop(s)
                            if (fresh !== ride) {
                                state.stopsEdited = true
                                actions.editStops(fresh)
                            }
                        }) else null)
                    }
                }
            }
        }
    }
}

@Composable
private fun StationRow(s: StationOnRide, isStop: Boolean, onRefuel: (() -> Unit)?) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().heightIn(min = Touch.Min),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(distanceText(s.km), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.widthIn(min = 64.dp))
        Column(Modifier.weight(1f)) {
            val name = s.station.name
            val brand = s.station.brand
            Text(name ?: brand ?: stringResource(Res.string.fuel_station), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (name != null && brand != null && !name.contains(brand, ignoreCase = true)) {
                Text(brand, style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (onRefuel != null && !isStop) {
            TextButton(onClick = onRefuel, modifier = Modifier.heightIn(min = Touch.Glove)) {
                Text(stringResource(Res.string.fuel_refuel_here))
            }
        }
    }
}
