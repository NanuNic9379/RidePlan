package io.github.nanunic9379.rideplan.ride

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import io.github.nanunic9379.rideplan.personal.dateText
import io.github.nanunic9379.rideplan.personal.distanceText
import io.github.nanunic9379.rideplan.personal.monthLong
import io.github.nanunic9379.rideplan.profile.civilDate
import io.github.nanunic9379.rideplan.resources.Res
import io.github.nanunic9379.rideplan.resources.cameras_on_ride
import io.github.nanunic9379.rideplan.resources.closure_allow
import io.github.nanunic9379.rideplan.resources.closure_avoid
import io.github.nanunic9379.rideplan.resources.closure_avoiding
import io.github.nanunic9379.rideplan.resources.closure_closed
import io.github.nanunic9379.rideplan.resources.closure_date
import io.github.nanunic9379.rideplan.resources.closure_date_change
import io.github.nanunic9379.rideplan.resources.closure_day
import io.github.nanunic9379.rideplan.resources.closure_on_closed
import io.github.nanunic9379.rideplan.resources.closure_on_outside
import io.github.nanunic9379.rideplan.resources.closure_on_partly
import io.github.nanunic9379.rideplan.resources.closure_osm
import io.github.nanunic9379.rideplan.resources.closure_partly_period
import io.github.nanunic9379.rideplan.resources.closure_period
import io.github.nanunic9379.rideplan.resources.closure_title
import io.github.nanunic9379.rideplan.resources.closure_unknown
import io.github.nanunic9379.rideplan.resources.closure_unnamed
import io.github.nanunic9379.rideplan.resources.closure_where
import io.github.nanunic9379.rideplan.resources.menu_done
import io.github.nanunic9379.rideplan.resources.speed_camera_marker_title
import io.github.nanunic9379.rideplan.ui.AppIcons
import io.github.nanunic9379.rideplan.resources.speed_camera_marker_details
import io.github.nanunic9379.rideplan.resources.speed_camera_limit
import io.github.nanunic9379.rideplan.resources.speed_camera_direction
import io.github.nanunic9379.rideplan.resources.route_cancel
import io.github.nanunic9379.rideplan.route.AvoidedRoad
import io.github.nanunic9379.rideplan.route.CameraOnRide
import io.github.nanunic9379.rideplan.route.ClosedOn
import io.github.nanunic9379.rideplan.route.ClosureOnRide
import io.github.nanunic9379.rideplan.route.ClosureRule
import io.github.nanunic9379.rideplan.route.DayRange
import io.github.nanunic9379.rideplan.route.MonthDay
import io.github.nanunic9379.rideplan.route.RoadNotes
import io.github.nanunic9379.rideplan.route.RoadNotesAlong
import io.github.nanunic9379.rideplan.route.RouteActions
import io.github.nanunic9379.rideplan.route.RouteState
import io.github.nanunic9379.rideplan.route.SpeedCamera
import io.github.nanunic9379.rideplan.route.routable
import io.github.nanunic9379.rideplan.route.unrouted
import io.github.nanunic9379.rideplan.ui.Touch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import kotlin.time.Clock

/** Closures and cameras found along a line (#13). */
internal data class NotesOnRide(val closures: List<ClosureOnRide>, val cameras: List<CameraOnRide>) {
    val isEmpty: Boolean get() = closures.isEmpty() && cameras.isEmpty()
}

/**
 * The road notes along [line], computed off the main thread; null while loading and when the
 * phone has no road-notes file (then nothing is said, never "no closures").
 */
@Composable
internal fun rememberNotesOnRide(line: List<LatLon>, totalKm: Double?, load: (suspend () -> RoadNotes?)?): NotesOnRide? {
    val notes by produceState<NotesOnRide?>(null, line, totalKm, load) {
        value = null
        if (load == null || line.size < 2) return@produceState
        value = try {
            val file = load() ?: return@produceState
            withContext(Dispatchers.Default) {
                NotesOnRide(
                    RoadNotesAlong.closures(line, file.closures, totalKm),
                    RoadNotesAlong.allowed(RoadNotesAlong.cameras(line, file.cameras, totalKm)),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
    return notes
}

/**
 * Seasonal closures and speed cameras in the ride sheet (#13): "According to OpenStreetMap,
 * Passo X is closed from November to May", checked against the ride date (today unless the
 * rider picks another), with a route that avoids the closed road; then the number of fixed
 * cameras (only where warnings are allowed).
 */
@Composable
internal fun RoadNotesSection(state: RideScreenState, actions: RouteActions?) {
    val ride = state.ride ?: return
    val load = actions?.roadNotes ?: return
    if (state.plan != null) return
    val notes = if (ride.straightLines) null else rememberNotesOnRide(ride.line, ride.lengthKm, load)
    val closures = notes?.closures.orEmpty()
    val cameras = notes?.cameras.orEmpty()
    if (closures.isEmpty() && cameras.isEmpty() && ride.avoid.isEmpty()) return
    val canRoute = ride.routable && state.route == RouteState.Idle
    val language = Locale.current.language
    val reroute = { fresh: Ride ->
        state.stopsEdited = true
        actions.editStops(fresh.unrouted())
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (closures.isNotEmpty() || ride.avoid.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(AppIcons.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                Text(stringResource(Res.string.closure_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            }
        }
        if (closures.isNotEmpty()) {
            // Rides have no date: today, unless the rider says when they leave.
            var dayMillis by remember(ride.stops) { mutableStateOf(today()) }
            var picking by remember { mutableStateOf(false) }
            if (picking) RideDayPicker(dayMillis, onDismiss = { picking = false }) { dayMillis = it; picking = false }
            val (d, m, _) = civilDate(dayMillis)
            val day = MonthDay(m, d)
            val date = dateText(dayMillis)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.closure_date, date), style = MaterialTheme.typography.bodyMedium, color = muted, modifier = Modifier.weight(1f))
                TextButton(onClick = { picking = true }, modifier = Modifier.heightIn(min = Touch.Min)) {
                    Text(stringResource(Res.string.closure_date_change))
                }
            }
            closures.forEach { c ->
                ClosureRow(c, language, day, date, onAvoid = if (canRoute) ({ reroute(ride.copy(avoid = ride.avoid + c.avoided(language))) }) else null)
            }
        }
        ride.avoid.forEach { a ->
            AvoidedRow(a, onAllow = if (canRoute || ride.straightLines) ({ reroute(ride.copy(avoid = ride.avoid - a)) }) else null)
        }
        if (cameras.isNotEmpty()) {
            Text(pluralStringResource(Res.plurals.cameras_on_ride, cameras.size, cameras.size), style = MaterialTheme.typography.bodyMedium, color = muted)
        }
    }
}

@Composable
private fun ClosureRow(c: ClosureOnRide, language: String, day: MonthDay, date: String, onAvoid: (() -> Unit)?) {
    val name = c.closure.name(language) ?: stringResource(Res.string.closure_unnamed)
    val rule = c.rule
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val warn = MaterialTheme.colorScheme.error
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val text = when {
            rule is ClosureRule.Seasonal && rule.closed.isNotEmpty() -> stringResource(Res.string.closure_closed, name, periodsText(rule.closed))
            rule is ClosureRule.Seasonal && rule.partly.isNotEmpty() -> stringResource(Res.string.closure_partly_period, name, periodsText(rule.partly))
            else -> stringResource(Res.string.closure_unknown, name)
        }
        Text(text, style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(Res.string.closure_where, distanceText(c.fromKm), distanceText(c.toKm)), style = MaterialTheme.typography.bodyMedium, color = muted)
        when (rule.on(day)) {
            ClosedOn.CLOSED -> Text(stringResource(Res.string.closure_on_closed, date), style = MaterialTheme.typography.bodyMedium, color = warn)
            ClosedOn.PART_OF_DAY -> Text(stringResource(Res.string.closure_on_partly, date), style = MaterialTheme.typography.bodyMedium, color = warn)
            ClosedOn.OUTSIDE -> Text(stringResource(Res.string.closure_on_outside, date), style = MaterialTheme.typography.bodyMedium, color = muted)
            ClosedOn.UNKNOWN -> Unit
        }
        val raw = when (rule) {
            is ClosureRule.Seasonal -> rule.raw
            is ClosureRule.Unreadable -> rule.raw
            ClosureRule.Never -> ""
        }
        if (raw.isNotEmpty()) Text(stringResource(Res.string.closure_osm, raw), style = MaterialTheme.typography.bodySmall, color = muted)
        if (onAvoid != null) {
            OutlinedButton(onClick = onAvoid, modifier = Modifier.heightIn(min = Touch.Min)) {
                Text(stringResource(Res.string.closure_avoid))
            }
        }
    }
}

@Composable
private fun AvoidedRow(a: AvoidedRoad, onAllow: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().heightIn(min = Touch.Min), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(Res.string.closure_avoiding, a.name ?: stringResource(Res.string.closure_unnamed)),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (onAllow != null) {
            TextButton(onClick = onAllow, modifier = Modifier.heightIn(min = Touch.Min)) { Text(stringResource(Res.string.closure_allow)) }
        }
    }
}

/** "from November to May", "from 15 November to 31 May"; several periods joined by commas. */
@Composable
private fun periodsText(ranges: List<DayRange>): String = ranges.map { r ->
    if (r.wholeMonths) stringResource(Res.string.closure_period, monthLong(r.from.month), monthLong(r.to.month))
    else stringResource(Res.string.closure_period, dayText(r.from), dayText(r.to))
}.joinToString(", ")

@Composable
private fun dayText(d: MonthDay): String = stringResource(Res.string.closure_day, d.day, monthLong(d.month))

/** Today at midnight UTC, as the date picker counts days. */
private fun today(): Long = Clock.System.now().toEpochMilliseconds().floorDiv(86_400_000L) * 86_400_000L

/** The day of the ride: today or later. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RideDayPicker(selected: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val first = remember { today() }
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = selected,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= first
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(enabled = picker.selectedDateMillis != null, onClick = { picker.selectedDateMillis?.let(onPick) }, modifier = Modifier.heightIn(min = Touch.Min)) {
                Text(stringResource(Res.string.menu_done))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Touch.Min)) { Text(stringResource(Res.string.route_cancel)) } },
    ) {
        DatePicker(state = picker)
    }
}

/** Closed stretches drawn over the ride's line and the fixed cameras as dots (#13). */
@Composable
internal fun RoadNotesLayers(ride: Ride, notes: NotesOnRide?) {
    val closed = remember(notes) {
        notes?.closures.orEmpty().filter { it.stretch.size >= 2 }.joinToString(",", """{"type":"FeatureCollection","features":[""", "]}") { lineJson(it.stretch) }
    }
    val cams = remember(notes) { pointsJson(notes?.cameras.orEmpty().map { it.camera.point }) }
    val scheme = MaterialTheme.colorScheme
    val closedSource = rememberGeoJsonSource(GeoJsonData.JsonString(closed))
    val camSource = rememberGeoJsonSource(GeoJsonData.JsonString(cams))
    LineLayer(id = "ride-closed", source = closedSource, color = const(scheme.error), width = const(5.dp))
    SymbolLayer(
        id = "ride-cameras",
        source = camSource,
        iconImage = image(rememberVectorPainter(AppIcons.SpeedCameraMarker), size = DpSize(36.dp, 44.dp)),
        iconAnchor = const(SymbolAnchor.Bottom),
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true),
    )
}

/** Details for a fixed speed camera selected on the ride map (#252). */
@Composable
internal fun SpeedCameraInfoDialog(camera: SpeedCamera, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.speed_camera_marker_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.speed_camera_marker_details))
                camera.maxspeedKmh?.let { Text(stringResource(Res.string.speed_camera_limit, it)) }
                camera.direction?.let { Text(stringResource(Res.string.speed_camera_direction, it)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.menu_done)) } },
    )
}
