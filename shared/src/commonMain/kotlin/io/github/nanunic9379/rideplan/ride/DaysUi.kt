package io.github.nanunic9379.rideplan.ride

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nanunic9379.rideplan.personal.LocalUnits
import io.github.nanunic9379.rideplan.personal.distanceText
import io.github.nanunic9379.rideplan.profile.of
import io.github.nanunic9379.rideplan.resources.Res
import io.github.nanunic9379.rideplan.resources.day_on_road
import io.github.nanunic9379.rideplan.resources.day_share
import io.github.nanunic9379.rideplan.resources.day_time_estimate
import io.github.nanunic9379.rideplan.resources.day_title
import io.github.nanunic9379.rideplan.resources.days_cap_hours
import io.github.nanunic9379.rideplan.resources.days_cap_km
import io.github.nanunic9379.rideplan.resources.days_cap_less
import io.github.nanunic9379.rideplan.resources.days_cap_more
import io.github.nanunic9379.rideplan.resources.days_cap_switch
import io.github.nanunic9379.rideplan.resources.days_cap_value
import io.github.nanunic9379.rideplan.resources.days_hint
import io.github.nanunic9379.rideplan.resources.days_overnights
import io.github.nanunic9379.rideplan.resources.days_overnights_hint
import io.github.nanunic9379.rideplan.resources.days_share_all
import io.github.nanunic9379.rideplan.resources.days_title
import io.github.nanunic9379.rideplan.route.durationText
import io.github.nanunic9379.rideplan.ui.AppIcons
import io.github.nanunic9379.rideplan.ui.Radius
import io.github.nanunic9379.rideplan.ui.TabularFigures
import io.github.nanunic9379.rideplan.ui.Touch
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/** Step of the daily cap: 25 km (or mi), half an hour. */
private const val DISTANCE_STEP = 25.0
private const val HOURS_STEP = 0.5
private val DistanceRange = 50.0..1500.0
private val HoursRange = 1.0..16.0

/**
 * "Days" in the ride sheet (#12): a daily cap, the stops where you sleep, one row per day with
 * km and time, and GPX files day by day.
 * [onChange]: the ride with a new cap or overnights; the road does not change.
 * [share]: sends GPX files (name, content); null = not available on this platform.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DaysSection(
    ride: Ride,
    enabled: Boolean,
    fallbackName: String,
    onChange: (Ride) -> Unit,
    share: ((List<Pair<String, String>>) -> Unit)?,
) {
    if (ride.line.size < 2) return
    val cap = ride.dayCap?.takeIf { it.valid }
    val days = remember(ride) { Stages.split(ride) }
    val scope = rememberCoroutineScope()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(Res.string.days_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(Res.string.days_hint), style = MaterialTheme.typography.bodySmall, color = muted)

        // Daily cap on or off: the whole row is the switch, easier with gloves.
        Row(
            Modifier.fillMaxWidth().heightIn(min = Touch.Min).toggleable(
                value = cap != null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = { on -> onChange(ride.copy(dayCap = if (on) DayCap.DEFAULT_KM else null)) },
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(Res.string.days_cap_switch), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = cap != null, onCheckedChange = null, enabled = enabled)
        }
        if (cap != null) CapPicker(cap, enabled) { onChange(ride.copy(dayCap = it)) }

        OvernightChips(ride, enabled, onChange)

        if (Stages.active(ride) && days.isNotEmpty()) {
            days.forEach { day -> DayRow(ride, day, fallbackName, share) }
            if (share != null && days.size > 1) {
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            val names = days.associate { it.day to getString(Res.string.day_title, it.day) }
                            share(StageExport.files(ride, days, fallbackName) { names.getValue(it) })
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = Touch.Glove),
                    shape = Radius.Control,
                ) {
                    Icon(AppIcons.Share, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(Res.string.days_share_all))
                }
            }
        }
    }
}

/** Km (or mi) or riding hours a day, with big minus and plus buttons. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CapPicker(cap: DayCap, enabled: Boolean, onChange: (DayCap) -> Unit) {
    val units = LocalUnits.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(DayCap.Unit.KM to Res.string.days_cap_km, DayCap.Unit.HOURS to Res.string.days_cap_hours).forEach { (unit, label) ->
            FilterChip(
                selected = cap.unit == unit,
                onClick = { if (cap.unit != unit) onChange(if (unit == DayCap.Unit.KM) DayCap.DEFAULT_KM else DayCap.DEFAULT_HOURS) },
                enabled = enabled,
                label = { Text(stringResource(label)) },
                modifier = Modifier.heightIn(min = Touch.Min),
            )
        }
    }
    val shown = when (cap.unit) {
        DayCap.Unit.KM -> distanceText(cap.value)
        DayCap.Unit.HOURS -> durationText(cap.value * 3600)
    }
    fun stepped(delta: Int): DayCap = when (cap.unit) {
        DayCap.Unit.KM -> {
            // Steps in the unit the rider reads (km or mi); stored in km.
            val perKm = units.of(1.0)
            val v = ((cap.value * perKm / DISTANCE_STEP).roundToInt() + delta) * DISTANCE_STEP
            cap.copy(value = v.coerceIn(DistanceRange) / perKm)
        }
        DayCap.Unit.HOURS -> cap.copy(value = ((cap.value / HOURS_STEP).roundToInt() + delta).times(HOURS_STEP).coerceIn(HoursRange))
    }
    val less = stringResource(Res.string.days_cap_less)
    val more = stringResource(Res.string.days_cap_more)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(
            onClick = { onChange(stepped(-1)) },
            enabled = enabled && stepped(-1) != cap,
            modifier = Modifier.size(Touch.Glove).semantics { contentDescription = less },
        ) { Text("−", style = MaterialTheme.typography.headlineSmall) }
        Text(
            stringResource(Res.string.days_cap_value, shown),
            style = MaterialTheme.typography.titleMedium.merge(TabularFigures),
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        FilledTonalIconButton(
            onClick = { onChange(stepped(1)) },
            enabled = enabled && stepped(1) != cap,
            modifier = Modifier.size(Touch.Glove),
        ) { Icon(AppIcons.Add, contentDescription = more) }
    }
}

/** The intermediate stops: a tap marks (or unmarks) where you sleep. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OvernightChips(ride: Ride, enabled: Boolean, onChange: (Ride) -> Unit) {
    val items = ride.stops.withIndex().filter { (i, s) -> i != 0 && i != ride.stops.lastIndex && s.name != null }
    if (items.isEmpty()) return
    Text(stringResource(Res.string.days_overnights), style = MaterialTheme.typography.labelLarge)
    Text(stringResource(Res.string.days_overnights_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (i, s) ->
            FilterChip(
                selected = s.overnight,
                onClick = { onChange(ride.withOvernight(i, !s.overnight)) },
                enabled = enabled,
                label = { Text(s.name.orEmpty().substringBefore(',').take(28), maxLines = 1) },
                leadingIcon = if (s.overnight) {
                    { Icon(AppIcons.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                } else null,
                modifier = Modifier.heightIn(min = Touch.Min),
            )
        }
    }
}

/** "Day 2 · 207 km · ~3 h 50 min", from where to where, and its own GPX. */
@Composable
private fun DayRow(ride: Ride, day: Stage, fallbackName: String, share: ((List<Pair<String, String>>) -> Unit)?) {
    val dayName = stringResource(Res.string.day_title, day.day)
    val from = day.stops.first().name?.substringBefore(',') ?: stringResource(Res.string.day_on_road, distanceText(day.startKm))
    val to = day.stops.last().name?.substringBefore(',') ?: stringResource(Res.string.day_on_road, distanceText(day.endKm))
    val figures = listOf(dayName, distanceText(day.km), stringResource(Res.string.day_time_estimate, durationText(day.seconds)))
    Row(Modifier.fillMaxWidth().heightIn(min = Touch.Min), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(figures.joinToString(" · "), style = MaterialTheme.typography.titleSmall.merge(TabularFigures))
            Text(
                "$from → $to",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (share != null) {
            IconButton(
                onClick = { share(StageExport.files(ride, listOf(day), fallbackName) { dayName }) },
                modifier = Modifier.size(Touch.Min),
            ) { Icon(AppIcons.Share, contentDescription = stringResource(Res.string.day_share, day.day)) }
        }
    }
}
