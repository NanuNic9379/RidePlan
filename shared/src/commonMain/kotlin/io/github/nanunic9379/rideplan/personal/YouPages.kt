package io.github.nanunic9379.rideplan.personal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nanunic9379.rideplan.profile.Bike
import io.github.nanunic9379.rideplan.profile.BikeType
import io.github.nanunic9379.rideplan.profile.Engine
import io.github.nanunic9379.rideplan.profile.Period
import io.github.nanunic9379.rideplan.profile.PrivateZonesSection
import io.github.nanunic9379.rideplan.profile.ProfileData
import io.github.nanunic9379.rideplan.profile.ProfileStore
import io.github.nanunic9379.rideplan.profile.RideStats
import io.github.nanunic9379.rideplan.profile.Units
import io.github.nanunic9379.rideplan.profile.civilDate
import io.github.nanunic9379.rideplan.profile.parseDecimal
import io.github.nanunic9379.rideplan.profile.title
import io.github.nanunic9379.rideplan.resources.Res
import io.github.nanunic9379.rideplan.ride.Dest
import io.github.nanunic9379.rideplan.ride.RideScreenState
import io.github.nanunic9379.rideplan.ui.AppIcons
import io.github.nanunic9379.rideplan.ui.Touch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import io.github.nanunic9379.rideplan.resources.bike_adventure
import io.github.nanunic9379.rideplan.resources.bike_custom
import io.github.nanunic9379.rideplan.resources.bike_default
import io.github.nanunic9379.rideplan.resources.bike_default_tag
import io.github.nanunic9379.rideplan.resources.bike_default_text
import io.github.nanunic9379.rideplan.resources.bike_delete
import io.github.nanunic9379.rideplan.resources.bike_delete_text
import io.github.nanunic9379.rideplan.resources.bike_delete_title
import io.github.nanunic9379.rideplan.resources.bike_electric_range
import io.github.nanunic9379.rideplan.resources.bike_engine
import io.github.nanunic9379.rideplan.resources.bike_naked
import io.github.nanunic9379.rideplan.resources.bike_name
import io.github.nanunic9379.rideplan.resources.bike_new
import io.github.nanunic9379.rideplan.resources.bike_no_limits
import io.github.nanunic9379.rideplan.resources.bike_no_motorways
import io.github.nanunic9379.rideplan.resources.bike_range
import io.github.nanunic9379.rideplan.resources.bike_range_short
import io.github.nanunic9379.rideplan.resources.bike_range_text
import io.github.nanunic9379.rideplan.resources.bike_scooter
import io.github.nanunic9379.rideplan.resources.bike_small_rule
import io.github.nanunic9379.rideplan.resources.bike_sport
import io.github.nanunic9379.rideplan.resources.bike_tank
import io.github.nanunic9379.rideplan.resources.bike_touring
import io.github.nanunic9379.rideplan.resources.bike_type
import io.github.nanunic9379.rideplan.resources.bike_use
import io.github.nanunic9379.rideplan.resources.engine_big
import io.github.nanunic9379.rideplan.resources.engine_electric
import io.github.nanunic9379.rideplan.resources.engine_small
import io.github.nanunic9379.rideplan.resources.garage_add
import io.github.nanunic9379.rideplan.resources.garage_empty
import io.github.nanunic9379.rideplan.resources.garage_empty_title
import io.github.nanunic9379.rideplan.resources.garage_note
import io.github.nanunic9379.rideplan.resources.garage_title
import io.github.nanunic9379.rideplan.resources.log_empty
import io.github.nanunic9379.rideplan.resources.log_empty_title
import io.github.nanunic9379.rideplan.resources.log_title
import io.github.nanunic9379.rideplan.resources.log_total
import io.github.nanunic9379.rideplan.resources.route_cancel
import io.github.nanunic9379.rideplan.resources.save
import io.github.nanunic9379.rideplan.resources.stat_average
import io.github.nanunic9379.rideplan.resources.stat_done
import io.github.nanunic9379.rideplan.resources.stat_longest_ride
import io.github.nanunic9379.rideplan.resources.stat_outings_label
import io.github.nanunic9379.rideplan.resources.stats_always
import io.github.nanunic9379.rideplan.resources.stats_best
import io.github.nanunic9379.rideplan.resources.stats_empty
import io.github.nanunic9379.rideplan.resources.stats_none_title
import io.github.nanunic9379.rideplan.resources.stats_per_month
import io.github.nanunic9379.rideplan.resources.stats_per_year
import io.github.nanunic9379.rideplan.resources.stats_repeated
import io.github.nanunic9379.rideplan.resources.stats_repeated_times
import io.github.nanunic9379.rideplan.resources.stats_title
import io.github.nanunic9379.rideplan.resources.stats_where
import io.github.nanunic9379.rideplan.resources.stats_where_note
import io.github.nanunic9379.rideplan.resources.zones_delete
import io.github.nanunic9379.rideplan.resources.zones_title

/** Big choices already open, no drop-down menu: usable with gloves. */
@Composable
internal fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, label ->
            SegmentedButton(
                selected = i == selected,
                onClick = { onSelect(i) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                modifier = Modifier.heightIn(min = Touch.Min),
            ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** Statistics (#21): per year, per month and where you rode. Computed on the phone. */
@Composable
fun StatsPage(store: ProfileStore, rides: RideScreenState) {
    var period by remember { mutableStateOf(Period.THIS_YEAR) }
    val now = store.time()
    val year = RideStats.yearOf(now)
    val s = remember(store.data, period) { RideStats.of(store.data, period, now) }
    SubPage(stringResource(Res.string.stats_title), onBack = { rides.back() }) {
        Segmented(
            listOf("$year", "${year - 1}", stringResource(Res.string.stats_always)),
            Period.entries.indexOf(period),
        ) { period = Period.entries[it] }
        if (s.outings == 0) {
            EmptyState(stringResource(Res.string.stats_none_title), stringResource(Res.string.stats_empty))
            return@SubPage
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("${s.outings}", pluralStringResource(Res.plurals.stat_outings_label, s.outings), Modifier.weight(1f))
            StatTile(distanceText(s.km), stringResource(Res.string.stat_done), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(s.longestKm?.let { distanceText(it) } ?: "–", stringResource(Res.string.stat_longest_ride), Modifier.weight(1f))
            StatTile(s.averageKm?.let { distanceText(it) } ?: "–", stringResource(Res.string.stat_average), Modifier.weight(1f))
        }
        SectionLabel(stringResource(if (s.barsAreYears) Res.string.stats_per_year else Res.string.stats_per_month))
        Bars(s.bars.map { (if (s.barsAreYears) "${it.label}".takeLast(2) else monthShort(it.label).take(1)) to it.km })
        s.best?.let { b ->
            val label = if (s.barsAreYears) "${b.label}" else monthLong(b.label)
            Text(
                stringResource(Res.string.stats_best, label, distanceText(b.km)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SectionLabel(stringResource(Res.string.stats_where))
        RideSketch(s.ridden.map { it.shape }, Modifier.fillMaxWidth().height(180.dp), strokeWidth = 3.dp)
        Text(stringResource(Res.string.stats_where_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (s.repeated.isNotEmpty()) {
            SectionLabel(stringResource(Res.string.stats_repeated))
            RowGroup {
                s.repeated.take(5).forEachIndexed { i, r ->
                    if (i > 0) GroupDivider()
                    NavRow(
                        AppIcons.Route,
                        title(r.ride),
                        pluralStringResource(Res.plurals.stats_repeated_times, r.times, r.times, distanceText(r.km)),
                        onClick = { rides.open(Dest.RideDetail(r.ride.id)) },
                    )
                }
            }
        }
    }
}

/** A bar per month (or year): the riding season at a glance. */
@Composable
private fun Bars(bars: List<Pair<String, Double>>) {
    val max = bars.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1.0
    val color = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        bars.forEach { (label, km) ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                val f = (km / max).toFloat().coerceIn(0.02f, 1f)
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.fillMaxWidth().fillMaxHeight(f)
                            .background(if (km > 0) color else empty, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .semantics { contentDescription = "$label ${km.roundToInt()}" },
                    )
                }
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/** The ride log (#21): every "I rode it", newest first, grouped by month. */
@Composable
fun LogPage(store: ProfileStore, rides: RideScreenState) {
    val groups = remember(store.data) { RideStats.log(store.data) }
    val all = remember(store.data) { RideStats.outings(store.data) }
    SubPage(stringResource(Res.string.log_title), onBack = { rides.back() }) {
        if (all.isEmpty()) {
            EmptyState(stringResource(Res.string.log_empty_title), stringResource(Res.string.log_empty))
            return@SubPage
        }
        Text(
            pluralStringResource(Res.plurals.log_total, all.size, all.size, distanceText(all.sumOf { it.km })),
            style = MaterialTheme.typography.titleMedium,
        )
        groups.forEach { (key, outings) ->
            SectionLabel("${monthLong(key % 100).replaceFirstChar { it.uppercase() }} ${key / 100}")
            RowGroup {
                outings.forEachIndexed { i, o ->
                    if (i > 0) GroupDivider()
                    val (d, m, _) = civilDate(o.day)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { rides.open(Dest.RideDetail(o.ride.id)) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Column(Modifier.width(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$d", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                            Text(monthShort(m), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(title(o.ride), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(distanceText(o.km), o.bike).joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(AppIcons.Chevron, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Private zones (#6) on a page of their own. */
@Composable
fun ZonesPage(store: ProfileStore, rides: RideScreenState) {
    SubPage(stringResource(Res.string.zones_title), onBack = { rides.back() }) {
        PrivateZonesSection(store)
    }
}

@Composable
internal fun bikeTypeText(t: BikeType): String = stringResource(
    when (t) {
        BikeType.NAKED -> Res.string.bike_naked
        BikeType.TOURING -> Res.string.bike_touring
        BikeType.ADVENTURE -> Res.string.bike_adventure
        BikeType.SPORT -> Res.string.bike_sport
        BikeType.CUSTOM -> Res.string.bike_custom
        BikeType.SCOOTER -> Res.string.bike_scooter
    },
)

@Composable
internal fun engineText(e: Engine): String = stringResource(
    when (e) {
        Engine.UP_TO_150 -> Res.string.engine_small
        Engine.OVER_150 -> Res.string.engine_big
        Engine.ELECTRIC -> Res.string.engine_electric
    },
)

/** The garage (#21): each bike with its type and what a full tank gives. */
@Composable
fun GaragePage(store: ProfileStore, rides: RideScreenState) {
    val data = store.data
    SubPage(stringResource(Res.string.garage_title), onBack = { rides.back() }) {
        if (data.bikes.isEmpty()) EmptyState(stringResource(Res.string.garage_empty_title), stringResource(Res.string.garage_empty))
        data.bikes.forEach { b -> BikeCard(b, isDefault = b.id == data.defaultBike?.id) { rides.open(Dest.BikePage(b.id)) } }
        Button(
            onClick = { rides.open(Dest.BikePage(null)) },
            modifier = Modifier.fillMaxWidth().heightIn(min = Touch.Glove),
        ) {
            Icon(AppIcons.Add, contentDescription = null)
            Text(stringResource(Res.string.garage_add), Modifier.padding(start = 8.dp))
        }
        Text(stringResource(Res.string.garage_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BikeCard(bike: Bike, isDefault: Boolean, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isDefault) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RowIcon(AppIcons.Moto)
                Column(Modifier.weight(1f)) {
                    Text(bike.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${bikeTypeText(bike.type)} · ${engineText(bike.engine)}", style = MaterialTheme.typography.bodyMedium)
                }
                if (isDefault) Text(stringResource(Res.string.bike_default_tag), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                else Icon(AppIcons.Chevron, contentDescription = null)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                bike.rangeKm?.let { r ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(AppIcons.Fuel, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(Res.string.bike_range_short, distanceText(r.toDouble())), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(if (bike.avoidsMotorways) AppIcons.Block else AppIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(if (bike.avoidsMotorways) Res.string.bike_no_motorways else Res.string.bike_no_limits),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

/** One bike (#21): a few big choices, the 150 cc rule said clearly, range from tank × use. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BikePage(store: ProfileStore, rides: RideScreenState, id: String?) {
    val data = store.data
    val old = data.bikes.firstOrNull { it.id == id }
    var name by remember(id) { mutableStateOf(old?.name.orEmpty()) }
    var type by remember(id) { mutableStateOf(old?.type ?: BikeType.NAKED) }
    var engine by remember(id) { mutableStateOf(old?.engine ?: Engine.OVER_150) }
    var tank by remember(id) { mutableStateOf(old?.tankLitres?.let(::decimalText).orEmpty()) }
    var use by remember(id) { mutableStateOf(old?.kmPerLitre?.let(::decimalText).orEmpty()) }
    var electric by remember(id) { mutableStateOf(old?.electricRangeKm?.let(::decimalText).orEmpty()) }
    var isDefault by remember(id) { mutableStateOf(old == null && data.bikes.isEmpty() || old != null && data.defaultBike?.id == old.id) }
    var confirmDelete by remember { mutableStateOf(false) }
    val draft = Bike(
        id = old?.id ?: "",
        name = name.trim(),
        type = type,
        engine = engine,
        tankLitres = parseDecimal(tank),
        kmPerLitre = parseDecimal(use),
        electricRangeKm = parseDecimal(electric),
    )
    SubPage(old?.name ?: stringResource(Res.string.bike_new), onBack = { rides.back() }) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(60) },
            label = { Text(stringResource(Res.string.bike_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        SectionLabel(stringResource(Res.string.bike_type))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BikeType.entries.forEach { t ->
                FilterChip(
                    selected = type == t,
                    onClick = { type = t },
                    label = { Text(bikeTypeText(t)) },
                    leadingIcon = if (type == t) ({ Icon(AppIcons.Check, contentDescription = null, Modifier.size(18.dp)) }) else null,
                    modifier = Modifier.heightIn(min = Touch.Min),
                )
            }
        }
        SectionLabel(stringResource(Res.string.bike_engine))
        Segmented(Engine.entries.map { engineText(it) }, Engine.entries.indexOf(engine)) { engine = Engine.entries[it] }
        if (engine == Engine.UP_TO_150) {
            NoteCard(
                AppIcons.Block,
                stringResource(Res.string.bike_small_rule),
                MaterialTheme.colorScheme.tertiaryContainer,
                MaterialTheme.colorScheme.onTertiaryContainer,
                title = stringResource(Res.string.bike_no_motorways),
            )
        }
        SectionLabel(stringResource(Res.string.bike_range))
        if (engine == Engine.ELECTRIC) {
            OutlinedTextField(
                value = electric,
                onValueChange = { electric = it.take(8) },
                label = { Text(stringResource(Res.string.bike_electric_range)) },
                suffix = { Text("km") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = tank,
                    onValueChange = { tank = it.take(6) },
                    label = { Text(stringResource(Res.string.bike_tank)) },
                    suffix = { Text("l") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = use,
                    onValueChange = { use = it.take(6) },
                    label = { Text(stringResource(Res.string.bike_use)) },
                    suffix = { Text("km/l") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        draft.rangeKm?.let { r ->
            NoteCard(
                AppIcons.Fuel,
                stringResource(Res.string.bike_range_text, distanceText(r.toDouble())),
                MaterialTheme.colorScheme.secondaryContainer,
                MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        RowGroup {
            SwitchRow(null, stringResource(Res.string.bike_default), stringResource(Res.string.bike_default_text), isDefault) { isDefault = it }
        }
        Button(
            onClick = {
                val bike = draft.copy(id = old?.id ?: ProfileData.newId())
                store.update { it.saveBike(bike, isDefault) }
                rides.back()
            },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = Touch.Glove),
        ) { Text(stringResource(Res.string.save)) }
        if (old != null) {
            OutlinedButton(
                onClick = { confirmDelete = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp).heightIn(min = Touch.Glove),
            ) {
                Icon(AppIcons.Delete, contentDescription = null)
                Text(stringResource(Res.string.bike_delete), Modifier.padding(start = 8.dp))
            }
        }
    }
    if (confirmDelete && old != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(AppIcons.Delete, contentDescription = null) },
            title = { Text(stringResource(Res.string.bike_delete_title, old.name)) },
            text = { Text(stringResource(Res.string.bike_delete_text), textAlign = TextAlign.Start) },
            confirmButton = {
                Button(
                    onClick = { confirmDelete = false; store.update { it.deleteBike(old.id) }; rides.back() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                    modifier = Modifier.heightIn(min = Touch.Min),
                ) { Text(stringResource(Res.string.zones_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }, modifier = Modifier.heightIn(min = Touch.Min)) { Text(stringResource(Res.string.route_cancel)) }
            },
        )
    }
}

/** 7.5 → "7.5", 35.0 → "35": what was typed, read back. */
internal fun decimalText(v: Double): String = if (v == v.roundToInt().toDouble()) "${v.roundToInt()}" else "${(v * 100).roundToInt() / 100.0}"

