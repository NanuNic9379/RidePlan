package io.github.nanunic9379.rideplan.route

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nanunic9379.rideplan.personal.RowIcon
import io.github.nanunic9379.rideplan.personal.SwitchRow
import io.github.nanunic9379.rideplan.profile.Bike
import io.github.nanunic9379.rideplan.profile.forBike
import io.github.nanunic9379.rideplan.resources.Res
import io.github.nanunic9379.rideplan.resources.close
import io.github.nanunic9379.rideplan.resources.menu_delete
import io.github.nanunic9379.rideplan.resources.route_always
import io.github.nanunic9379.rideplan.resources.route_apply
import io.github.nanunic9379.rideplan.resources.route_avoid
import io.github.nanunic9379.rideplan.resources.route_avoid_ferries
import io.github.nanunic9379.rideplan.resources.route_avoid_motorways
import io.github.nanunic9379.rideplan.resources.route_avoid_tolls
import io.github.nanunic9379.rideplan.resources.route_avoid_towns
import io.github.nanunic9379.rideplan.resources.route_avoid_towns_sub
import io.github.nanunic9379.rideplan.resources.route_avoid_unpaved
import io.github.nanunic9379.rideplan.resources.route_avoid_unpaved_sub
import io.github.nanunic9379.rideplan.resources.route_bike
import io.github.nanunic9379.rideplan.resources.route_bike_none
import io.github.nanunic9379.rideplan.resources.route_bike_small
import io.github.nanunic9379.rideplan.resources.route_cancel
import io.github.nanunic9379.rideplan.resources.route_custom
import io.github.nanunic9379.rideplan.resources.route_fine_title
import io.github.nanunic9379.rideplan.resources.route_mode_balanced
import io.github.nanunic9379.rideplan.resources.route_mode_balanced_desc
import io.github.nanunic9379.rideplan.resources.route_mode_balanced_does
import io.github.nanunic9379.rideplan.resources.route_mode_balanced_line
import io.github.nanunic9379.rideplan.resources.route_mode_change
import io.github.nanunic9379.rideplan.resources.route_mode_curvy
import io.github.nanunic9379.rideplan.resources.route_mode_curvy_desc
import io.github.nanunic9379.rideplan.resources.route_mode_curvy_does
import io.github.nanunic9379.rideplan.resources.route_mode_curvy_line
import io.github.nanunic9379.rideplan.resources.route_mode_dirt
import io.github.nanunic9379.rideplan.resources.route_mode_dirt_line
import io.github.nanunic9379.rideplan.resources.route_mode_fast
import io.github.nanunic9379.rideplan.resources.route_mode_fast_desc
import io.github.nanunic9379.rideplan.resources.route_mode_fast_does
import io.github.nanunic9379.rideplan.resources.route_mode_fast_line
import io.github.nanunic9379.rideplan.resources.route_mode_new_roads
import io.github.nanunic9379.rideplan.resources.route_mode_new_roads_line
import io.github.nanunic9379.rideplan.resources.route_mode_scenic
import io.github.nanunic9379.rideplan.resources.route_mode_scenic_line
import io.github.nanunic9379.rideplan.resources.route_mode_section
import io.github.nanunic9379.rideplan.resources.route_mode_soon
import io.github.nanunic9379.rideplan.resources.route_mode_title
import io.github.nanunic9379.rideplan.resources.route_mode_touring
import io.github.nanunic9379.rideplan.resources.route_mode_touring_line
import io.github.nanunic9379.rideplan.resources.route_opt_curves
import io.github.nanunic9379.rideplan.resources.route_preset_delete_title
import io.github.nanunic9379.rideplan.resources.route_preset_hint
import io.github.nanunic9379.rideplan.resources.route_preset_name
import io.github.nanunic9379.rideplan.resources.route_preset_save
import io.github.nanunic9379.rideplan.resources.route_preset_title
import io.github.nanunic9379.rideplan.resources.route_presets
import io.github.nanunic9379.rideplan.resources.route_preview_fastest
import io.github.nanunic9379.rideplan.resources.route_preview_none
import io.github.nanunic9379.rideplan.resources.route_preview_pending
import io.github.nanunic9379.rideplan.resources.route_preview_vs_fast
import io.github.nanunic9379.rideplan.resources.route_reset
import io.github.nanunic9379.rideplan.resources.save
import io.github.nanunic9379.rideplan.ride.Ride
import io.github.nanunic9379.rideplan.resources.route_mode_more
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.horizontalScroll
import io.github.nanunic9379.rideplan.ui.AppIcons
import io.github.nanunic9379.rideplan.ui.Radius
import io.github.nanunic9379.rideplan.ui.TabularFigures
import io.github.nanunic9379.rideplan.ui.Touch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** Name, one line, icon of a mode: the tile in the grid. */
private class ModeLook(val name: StringResource, val line: StringResource, val icon: ImageVector)

private fun RidingMode.look(): ModeLook = when (this) {
    RidingMode.FAST -> ModeLook(Res.string.route_mode_fast, Res.string.route_mode_fast_line, AppIcons.Bolt)
    RidingMode.BALANCED -> ModeLook(Res.string.route_mode_balanced, Res.string.route_mode_balanced_line, AppIcons.Tune)
    RidingMode.CURVY -> ModeLook(Res.string.route_mode_curvy, Res.string.route_mode_curvy_line, AppIcons.Route)
}

private fun ComingMode.look(): ModeLook = when (this) {
    ComingMode.SCENIC -> ModeLook(Res.string.route_mode_scenic, Res.string.route_mode_scenic_line, AppIcons.Landscape)
    ComingMode.TOURING -> ModeLook(Res.string.route_mode_touring, Res.string.route_mode_touring_line, AppIcons.Luggage)
    ComingMode.NEW_ROADS -> ModeLook(Res.string.route_mode_new_roads, Res.string.route_mode_new_roads_line, AppIcons.Explore)
    ComingMode.DIRT -> ModeLook(Res.string.route_mode_dirt, Res.string.route_mode_dirt_line, AppIcons.Moto)
}

/**
 * The riding mode in the ride sheet (#30): one 56 dp row that opens [RouteSettingsSheet].
 * "Custom" when the switches were changed by hand.
 */
@Composable
fun RidingModeRow(options: RouteOptions, enabled: Boolean, onOpen: () -> Unit) {
    val look = options.mode?.look()
    Surface(
        onClick = onOpen,
        enabled = enabled,
        shape = Radius.Card,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().heightIn(min = Touch.Glove),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RowIcon(look?.icon ?: AppIcons.Tune)
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.route_mode_section), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (look != null) Text(stringResource(look.name), style = MaterialTheme.typography.titleMedium)
                    if (options.custom) CustomBadge()
                }
            }
            Text(stringResource(Res.string.route_mode_change), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * The riding modes as one row of chips (#33): planning a new ride, the mode is chosen before the
 * first tap. The last chip opens the full route settings. No chip is on when the switches were
 * changed by hand ("Custom").
 */
@Composable
fun RidingModeChips(options: RouteOptions, onPick: (RidingMode) -> Unit, onMore: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RidingMode.entries.forEach { mode ->
            val look = mode.look()
            val on = options.mode == mode && !options.custom
            FilterChip(
                selected = on,
                onClick = { onPick(mode) },
                label = { Text(stringResource(look.name), style = MaterialTheme.typography.labelLarge) },
                leadingIcon = { Icon(look.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.heightIn(min = Touch.Glove),
            )
        }
        FilterChip(
            selected = options.custom,
            onClick = onMore,
            label = { Text(stringResource(if (options.custom) Res.string.route_custom else Res.string.route_mode_more), style = MaterialTheme.typography.labelLarge) },
            leadingIcon = { Icon(AppIcons.Settings, contentDescription = null, modifier = Modifier.size(18.dp)) },
            colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier.heightIn(min = Touch.Glove),
        )
    }
}

@Composable
private fun CustomBadge() {
    Surface(shape = Radius.Tag, color = MaterialTheme.colorScheme.tertiaryContainer) {
        Text(
            stringResource(Res.string.route_custom),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/**
 * Route settings (#30): a near full-height sheet. The rider picks a mode or tunes the switches
 * on a draft; nothing changes until "Apply and recalculate". The preview on top shows the ride
 * as it is now (km, time, difference against the fastest route).
 * [bike]: the default bike; under 150 cc it locks "avoid motorways" on (#8).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RouteSettingsSheet(
    ride: Ride,
    title: String,
    applied: RouteOptions,
    presets: List<RoutePreset>,
    bike: Bike?,
    onApply: (RouteOptions) -> Unit,
    onSavePreset: (String, RouteOptions) -> Unit,
    onDeletePreset: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(applied) { mutableStateOf(applied.withInferredMode()) }
    var saving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<RoutePreset?>(null) }
    val locked = bike?.avoidsMotorways == true

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight().navigationBarsPadding()) {
            Row(Modifier.padding(start = 24.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
                    Text(stringResource(Res.string.route_mode_title), style = MaterialTheme.typography.titleLarge)
                    Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(Touch.Min)) {
                    Icon(AppIcons.Close, contentDescription = stringResource(Res.string.close))
                }
            }
            Preview(ride, applied, bike, pending = draft != applied, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Section(stringResource(Res.string.route_mode_section))
                ModeGrid(draft.mode, onPick = { draft = it.options })
                draft.mode?.let { ModeDescription(it) }

                Section(stringResource(Res.string.route_presets))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { p ->
                        PresetChip(p.name, selected = p.options == draft, onClick = { draft = p.options.withInferredMode() }, onLongClick = { deleting = p })
                    }
                    PresetChip(stringResource(Res.string.route_preset_save), selected = false, icon = AppIcons.Add, onClick = { saving = true }, onLongClick = null)
                }
                if (presets.isNotEmpty()) {
                    Text(stringResource(Res.string.route_preset_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Section(stringResource(Res.string.route_fine_title))
                    if (draft.custom) Box(Modifier.padding(top = 16.dp)) { CustomBadge() }
                }
                BikeRow(bike)
                Label(stringResource(Res.string.route_opt_curves))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Curves.entries.forEachIndexed { i, level ->
                        SegmentedButton(
                            selected = draft.curves == level,
                            onClick = { draft = draft.copy(curves = level) },
                            shape = SegmentedButtonDefaults.itemShape(i, Curves.entries.size),
                            icon = {},
                            modifier = Modifier.heightIn(min = Touch.Min),
                        ) { Text(curvesLabel(level), maxLines = 1) }
                    }
                }
                Label(stringResource(Res.string.route_avoid))
                Column {
                    SwitchRow(
                        null,
                        stringResource(Res.string.route_avoid_motorways),
                        null,
                        checked = draft.avoidHighways || locked,
                        enabled = !locked,
                    ) { draft = draft.copy(avoidHighways = it) }
                    SwitchRow(null, stringResource(Res.string.route_avoid_tolls), null, draft.avoidTolls) { draft = draft.copy(avoidTolls = it) }
                    SwitchRow(null, stringResource(Res.string.route_avoid_ferries), null, draft.avoidFerries) { draft = draft.copy(avoidFerries = it) }
                    SwitchRow(null, stringResource(Res.string.route_avoid_towns), stringResource(Res.string.route_avoid_towns_sub), draft.avoidTowns) {
                        draft = draft.copy(avoidTowns = it)
                    }
                    UnpavedRow()
                }
                Spacer(Modifier.size(8.dp))
            }
            HorizontalDivider()
            // Sticky: the main action never needs scrolling.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = { draft = (draft.mode ?: RidingMode.DEFAULT).options },
                    enabled = draft.custom,
                    modifier = Modifier.heightIn(min = Touch.Glove),
                ) { Text(stringResource(Res.string.route_reset)) }
                Button(onClick = { onApply(draft) }, modifier = Modifier.weight(1f).heightIn(min = Touch.Glove), shape = Radius.Control) {
                    Text(stringResource(Res.string.route_apply))
                }
            }
        }
    }

    if (saving) SavePresetDialog(onDismiss = { saving = false }) { name ->
        saving = false
        onSavePreset(name, draft)
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(Res.string.route_preset_delete_title, p.name)) },
            confirmButton = {
                TextButton(onClick = { deleting = null; onDeletePreset(p.id) }, modifier = Modifier.heightIn(min = Touch.Min)) {
                    Text(stringResource(Res.string.menu_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }, modifier = Modifier.heightIn(min = Touch.Min)) { Text(stringResource(Res.string.route_cancel)) }
            },
        )
    }
}

@Composable
private fun Section(text: String) = Text(
    text,
    style = MaterialTheme.typography.titleMedium,
    modifier = Modifier.padding(top = 16.dp).semantics { heading() },
)

@Composable
private fun Label(text: String) = Text(
    text,
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(top = 8.dp),
)

/** The ride as it is now: what Apply will change is measured against this. */
@Composable
private fun Preview(ride: Ride, applied: RouteOptions, bike: Bike?, pending: Boolean, modifier: Modifier) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val info = ride.route
            if (info == null) {
                Text(stringResource(Res.string.route_preview_none), style = MaterialTheme.typography.titleMedium)
            } else {
                Text(
                    "${info.km.roundToInt()} km · ${durationText(info.seconds)}",
                    style = MaterialTheme.typography.headlineSmall.merge(TabularFigures),
                )
                // Less than a kilometre and a minute apart: the same road, said in words.
                val base = info.baseline?.takeIf { kotlin.math.abs(info.km - it.km) >= 1 || info.seconds - it.seconds >= 60 }
                val fastest = applied.forBike(bike) == RidingMode.FAST.options.forBike(bike) || info.baseline != null
                when {
                    base != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(shape = Radius.Tag, color = MaterialTheme.colorScheme.primaryContainer) {
                            Text(
                                deltaText(info.km - base.km, info.seconds - base.seconds),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                        Text(stringResource(Res.string.route_preview_vs_fast), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    fastest -> Text(stringResource(Res.string.route_preview_fastest), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (pending) {
                Text(stringResource(Res.string.route_preview_pending), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun deltaText(km: Double, seconds: Double): String {
    val k = km.roundToInt()
    val sign = if (k < 0) "−" else "+"
    return "$sign${kotlin.math.abs(k)} km · +${durationText(seconds.coerceAtLeast(0.0))}"
}

/** Two tiles per row: the three modes, then the ones coming soon, greyed out. */
@Composable
private fun ModeGrid(selected: RidingMode?, onPick: (RidingMode) -> Unit) {
    val tiles: List<Pair<ModeLook, RidingMode?>> =
        RidingMode.entries.map { it.look() to it } + ComingMode.entries.map { it.look() to null }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (look, mode) ->
                    ModeTile(look, mode, selected = mode != null && mode == selected, onPick = onPick, modifier = Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ModeTile(look: ModeLook, mode: RidingMode?, selected: Boolean, onPick: (RidingMode) -> Unit, modifier: Modifier) {
    val soon = mode == null
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = Radius.Card,
        color = when {
            selected -> colors.primaryContainer
            soon -> colors.surface
            else -> colors.surfaceContainerHigh
        },
        border = when {
            selected -> BorderStroke(2.dp, colors.primary)
            soon -> BorderStroke(1.dp, colors.outlineVariant)
            else -> null
        },
        modifier = modifier
            .heightIn(min = 104.dp)
            .selectable(selected = selected, enabled = !soon, role = Role.RadioButton) { mode?.let(onPick) },
    ) {
        val alpha = if (soon) 0.6f else 1f
        val content = if (selected) colors.onPrimaryContainer else colors.onSurface
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(look.icon, contentDescription = null, tint = (if (selected) colors.primary else colors.onSurfaceVariant).copy(alpha = alpha))
                Spacer(Modifier.weight(1f))
                when {
                    selected -> Icon(AppIcons.Check, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                    soon -> Surface(shape = Radius.Tag, color = colors.surfaceContainerHighest) {
                        Text(
                            stringResource(Res.string.route_mode_soon),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Text(stringResource(look.name), style = MaterialTheme.typography.titleMedium, color = content.copy(alpha = alpha), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(look.line), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant.copy(alpha = alpha), maxLines = 2)
        }
    }
}

/** In plain words, what the chosen mode does. */
@Composable
private fun ModeDescription(mode: RidingMode) {
    val (desc, does) = when (mode) {
        RidingMode.FAST -> Res.string.route_mode_fast_desc to Res.string.route_mode_fast_does
        RidingMode.BALANCED -> Res.string.route_mode_balanced_desc to Res.string.route_mode_balanced_does
        RidingMode.CURVY -> Res.string.route_mode_curvy_desc to Res.string.route_mode_curvy_does
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(desc), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            stringResource(does).split(" · ").forEach { line ->
                Text("• $line", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetChip(label: String, selected: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)?, icon: ImageVector = AppIcons.Bookmark) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) colors.secondaryContainer else colors.surface,
        border = BorderStroke(1.dp, if (selected) colors.secondary else colors.outline),
        modifier = Modifier.heightIn(min = Touch.Min).combinedClickable(onClick = onClick, onLongClick = onLongClick, role = Role.Button),
    ) {
        Row(Modifier.padding(horizontal = 12.dp).heightIn(min = Touch.Min), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** The default bike; under 150 cc a warning explains why motorways are locked. */
@Composable
private fun BikeRow(bike: Bike?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label(stringResource(Res.string.route_bike))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RowIcon(AppIcons.Moto)
            Text(
                bike?.name ?: stringResource(Res.string.route_bike_none),
                style = if (bike != null) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                color = if (bike != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (bike?.avoidsMotorways == true) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(AppIcons.Block, contentDescription = null)
                    Text(stringResource(Res.string.route_bike_small), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** Unpaved roads are always avoided today: a row with a lock, not a switch. */
@Composable
private fun UnpavedRow() {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(Res.string.route_avoid_unpaved), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(Res.string.route_avoid_unpaved_sub), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(AppIcons.Lock, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(Res.string.route_always), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SavePresetDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(AppIcons.Bookmark, contentDescription = null) },
        title = { Text(stringResource(Res.string.route_preset_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(RouteSettings.MAX_NAME) },
                label = { Text(stringResource(Res.string.route_preset_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank(), modifier = Modifier.heightIn(min = Touch.Min)) {
                Text(stringResource(Res.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Touch.Min)) { Text(stringResource(Res.string.route_cancel)) }
        },
    )
}
