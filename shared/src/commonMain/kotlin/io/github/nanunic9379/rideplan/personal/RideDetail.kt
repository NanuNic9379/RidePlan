package io.github.nanunic9379.rideplan.personal

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.nanunic9379.rideplan.profile.MenuItem
import io.github.nanunic9379.rideplan.profile.ProfileStore
import io.github.nanunic9379.rideplan.profile.RenameDialog
import io.github.nanunic9379.rideplan.profile.DayPicker
import io.github.nanunic9379.rideplan.profile.SavedRide
import io.github.nanunic9379.rideplan.profile.StopDistances
import io.github.nanunic9379.rideplan.profile.TonalAction
import io.github.nanunic9379.rideplan.profile.title
import io.github.nanunic9379.rideplan.resources.Res
import io.github.nanunic9379.rideplan.resources.detail_discard
import io.github.nanunic9379.rideplan.resources.detail_done_times
import io.github.nanunic9379.rideplan.resources.detail_footer
import io.github.nanunic9379.rideplan.resources.detail_not_done
import io.github.nanunic9379.rideplan.resources.detail_notes
import io.github.nanunic9379.rideplan.resources.detail_notes_help
import io.github.nanunic9379.rideplan.resources.detail_notes_hint
import io.github.nanunic9379.rideplan.resources.detail_open_map
import io.github.nanunic9379.rideplan.resources.detail_received
import io.github.nanunic9379.rideplan.resources.detail_stop_finish
import io.github.nanunic9379.rideplan.resources.detail_stop_from_start
import io.github.nanunic9379.rideplan.resources.detail_stop_n
import io.github.nanunic9379.rideplan.resources.detail_stop_start
import io.github.nanunic9379.rideplan.resources.detail_view_map
import io.github.nanunic9379.rideplan.resources.menu_delete
import io.github.nanunic9379.rideplan.resources.menu_done
import io.github.nanunic9379.rideplan.resources.menu_more
import io.github.nanunic9379.rideplan.resources.menu_rename
import io.github.nanunic9379.rideplan.resources.menu_undo_done
import io.github.nanunic9379.rideplan.resources.preview_save
import io.github.nanunic9379.rideplan.resources.ride_stops
import io.github.nanunic9379.rideplan.resources.route_stops_title
import io.github.nanunic9379.rideplan.resources.share_gpx
import io.github.nanunic9379.rideplan.resources.snack_deleted
import io.github.nanunic9379.rideplan.resources.snack_done
import io.github.nanunic9379.rideplan.resources.snack_undo
import io.github.nanunic9379.rideplan.ride.ImportActions
import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.QrDialog
import io.github.nanunic9379.rideplan.ride.RideLink
import io.github.nanunic9379.rideplan.ride.RideScreenState
import io.github.nanunic9379.rideplan.ride.ShareDialog
import io.github.nanunic9379.rideplan.ride.sourceText
import io.github.nanunic9379.rideplan.route.durationText
import io.github.nanunic9379.rideplan.ui.AppIcons
import io.github.nanunic9379.rideplan.ui.Figure
import io.github.nanunic9379.rideplan.ui.Motion
import io.github.nanunic9379.rideplan.ui.Radius
import io.github.nanunic9379.rideplan.ui.TabularFigures
import io.github.nanunic9379.rideplan.ui.Touch
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Opens a saved ride on the map and remembers when (for "last opened"). */
fun openOnMap(store: ProfileStore, rides: RideScreenState, actions: ImportActions?, saved: SavedRide) {
    actions?.openRide?.invoke(saved.toRide())
    rides.savedId = saved.id
    store.update { it.opened(saved.id, store.time()) }
}

/**
 * The page of a ride (#21): its shape, numbers, the map button, stops, your private notes and
 * when you rode it. A ride received by link first asks whether to keep it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RideDetailPage(store: ProfileStore, rides: RideScreenState, actions: ImportActions?, id: String, snackbar: SnackbarHostState, platform: PlatformActions? = null) {
    val saved = store.data.rides.firstOrNull { it.id == id }
    if (saved == null) {
        LaunchedEffect(Unit) { rides.back() }
        return
    }
    val scope = rememberCoroutineScope()
    val name = title(saved)
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var pickDay by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var qr by remember { mutableStateOf<String?>(null) }
    if (rename) RenameDialog(saved.name.orEmpty(), onDismiss = { rename = false }, onSave = { n -> rename = false; store.update { it.rename(id, n) } })
    if (pickDay) DayPicker(onDismiss = { pickDay = false }, onPick = { day ->
        pickDay = false
        store.update { it.markRidden(id, day) }
        scope.launch { snackbar.showSnackbar(getString(Res.string.snack_done)) }
    })
    val share = actions?.shareGpx
    if (sharing && share != null) {
        val ride = saved.toRide()
        ShareDialog(ride, store.data.privateZones, onDismiss = { sharing = false }) { out, asLink ->
            sharing = false
            if (asLink) qr = RideLink.encode(out, rides.options) else share(out, name)
        }
    }
    qr?.let { QrDialog(it, actions?.shareText, onDismiss = { qr = null }) }

    fun delete() {
        val index = store.data.rides.indexOf(saved)
        store.update { it.delete(id) }
        rides.back()
        scope.launch {
            val r = snackbar.showSnackbar(getString(Res.string.snack_deleted), getString(Res.string.snack_undo), withDismissAction = false)
            if (r == SnackbarResult.ActionPerformed) {
                store.update { d -> d.copy(rides = d.rides.toMutableList().apply { add(index.coerceIn(0, size), saved) }) }
            }
        }
    }

    SubPage(
        title = name,
        onBack = { rides.back() },
        actions = {
            StarButton(saved.favourite) { store.update { it.setFavourite(id, !saved.favourite) } }
            if (share != null) {
                IconButton(onClick = { sharing = true }, modifier = Modifier.size(Touch.Min)) {
                    Icon(AppIcons.Share, contentDescription = stringResource(Res.string.share_gpx))
                }
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(Touch.Min)) {
                    Icon(AppIcons.More, contentDescription = stringResource(Res.string.menu_more))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    MenuItem(AppIcons.Delete, stringResource(Res.string.menu_delete)) { menu = false; delete() }
                }
            }
        },
    ) {
        val received = saved.received
        if (received != null) {
            NoteCard(
                AppIcons.Inbox,
                stringResource(Res.string.detail_received, dateText(received.at)),
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
        RideUpdateCard(store, saved)
        // The route draws itself once when the page opens (the system "remove animations" setting skips it).
        val drawn = remember(saved.id) { Animatable(0f) }
        LaunchedEffect(saved.id) { drawn.animateTo(1f, tween(Motion.Draw, easing = Motion.Emphasized)) }
        RideSketch(
            listOf(saved.shape),
            Modifier.fillMaxWidth().aspectRatio(16f / 10f),
            background = MaterialTheme.colorScheme.surfaceContainer,
            grid = 24.dp,
            progress = { drawn.value },
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.Bottom,
        ) {
            RideFigure(distanceNumber(saved.km), distanceUnit())
            saved.seconds?.let { RideFigure(durationText(it.toDouble()), null) }
            if (saved.stops.isNotEmpty()) {
                Text(
                    pluralStringResource(Res.plurals.ride_stops, saved.stops.size, saved.stops.size),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
        if (received != null) {
            Button(
                onClick = { store.update { it.keep(id) } },
                modifier = Modifier.fillMaxWidth().heightIn(min = Touch.Glove),
                shape = Radius.Control,
            ) {
                Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(Res.string.preview_save), style = MaterialTheme.typography.titleMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TonalAction(AppIcons.Map, stringResource(Res.string.detail_view_map), { openOnMap(store, rides, actions, saved) }, Modifier.weight(1f))
                OutlinedButton(
                    onClick = { store.update { it.delete(id) }; rides.back() },
                    modifier = Modifier.weight(1f).heightIn(min = Touch.Glove),
                    shape = Radius.Control,
                ) { Text(stringResource(Res.string.detail_discard)) }
            }
        } else {
            Button(
                onClick = { openOnMap(store, rides, actions, saved) },
                modifier = Modifier.fillMaxWidth().heightIn(min = Touch.Glove),
                shape = Radius.Control,
            ) {
                Icon(AppIcons.Map, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(Res.string.detail_open_map), style = MaterialTheme.typography.titleMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TonalAction(AppIcons.Check, stringResource(Res.string.menu_done), { pickDay = true }, Modifier.weight(1f))
                TonalAction(AppIcons.Edit, stringResource(Res.string.menu_rename), { rename = true }, Modifier.weight(1f))
            }
        }
        if (saved.stops.isNotEmpty()) {
            SectionLabel(stringResource(Res.string.route_stops_title))
            // Distance from the start only along a real track: a routed ride keeps no track.
            val alongKm = remember(saved.id, saved.track, saved.stops) {
                if (saved.track.size >= 4) StopDistances.along(saved.shape, saved.stops.map { LatLon(it.lat, it.lon) })
                else saved.stops.map { null }
            }
            Column {
                saved.stops.forEachIndexed { i, stop -> StopRow(i, saved.stops.size, stop.name, stop.lat, stop.lon, alongKm[i]) }
            }
        }
        if (saved.stops.size >= 2) RideSheetSection(store, saved, actions?.shareText)
        platform?.nostr?.let { io.github.nanunic9379.rideplan.social.NostrRideSection(it, store, saved, rides.options, actions?.shareText) }
        if (received == null) {
            Notes(store, saved)
            History(saved, onUndo = { store.update { it.undoRidden(id) } })
        }
        Text(
            stringResource(Res.string.detail_footer, sourceText(saved.toRide()), dateText(saved.savedAt)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
    }
}

private fun fmt(v: Double): String = ((v * 10_000).toLong() / 10_000.0).toString()

/** A ride figure set like a dashboard: big tabular number, small unit. */
@Composable
private fun RideFigure(value: String, unit: String?) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Figure(value, style = MaterialTheme.typography.headlineMedium.merge(TabularFigures), color = MaterialTheme.colorScheme.primary)
        if (unit != null) {
            Text(unit, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}

/**
 * One stop: what it is in the ride (start, stop n, finish), its name when the ride has one,
 * then how far from the start and the coordinates, small. No place name is made up.
 */
@Composable
private fun StopRow(index: Int, count: Int, name: String?, lat: Double, lon: Double, alongKm: Double?) {
    val role = when {
        index == 0 -> stringResource(Res.string.detail_stop_start)
        index == count - 1 -> stringResource(Res.string.detail_stop_finish)
        else -> stringResource(Res.string.detail_stop_n, index + 1)
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().heightIn(min = Touch.Min).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // As on the map: white with a dark ring, the finish all dark.
        val ink = MaterialTheme.colorScheme.onSurface
        val finish = index == count - 1 && count > 1
        Box(Modifier.padding(top = 4.dp).size(24.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(12.dp)
                    .background(if (finish) ink else MaterialTheme.colorScheme.surface, CircleShape)
                    .border(2.5.dp, ink, CircleShape),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(role, style = MaterialTheme.typography.titleMedium)
            if (name != null) Text(name, style = MaterialTheme.typography.bodyMedium)
            // The start is where the distances count from: no "0 km" there.
            val distance = alongKm?.takeIf { index > 0 }?.let { stringResource(Res.string.detail_stop_from_start, distanceText(it)) }
            Text(
                listOfNotNull(distance, "${fmt(lat)}, ${fmt(lon)}").joinToString(" · "),
                style = MaterialTheme.typography.bodySmall.merge(TabularFigures),
                color = muted,
            )
        }
    }
}

/** Private notes: saved as you type, never in the GPX or the link. */
@Composable
private fun Notes(store: ProfileStore, saved: SavedRide) {
    var text by remember(saved.id) { mutableStateOf(saved.notes) }
    LaunchedEffect(text) {
        if (text == saved.notes) return@LaunchedEffect
        delay(400)
        store.update { it.setNotes(saved.id, text) }
    }
    SectionLabel(stringResource(Res.string.detail_notes))
    OutlinedTextField(
        value = text,
        onValueChange = { text = it.take(2000) },
        placeholder = { Text(stringResource(Res.string.detail_notes_hint)) },
        leadingIcon = { Icon(AppIcons.Notes, contentDescription = null) },
        supportingText = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(AppIcons.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                Text(stringResource(Res.string.detail_notes_help))
            }
        },
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun History(saved: SavedRide, onUndo: () -> Unit) {
    val n = saved.ridden.size
    SectionLabel(if (n == 0) stringResource(Res.string.detail_not_done) else pluralStringResource(Res.plurals.detail_done_times, n, n))
    for (day in saved.ridden.sortedDescending()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.heightIn(min = 40.dp)) {
            Icon(AppIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(
                listOfNotNull(dateText(day), saved.riddenWith[day]).joinToString(" · "),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
    if (n > 0) {
        TextButton(onClick = onUndo, modifier = Modifier.heightIn(min = Touch.Min)) { Text(stringResource(Res.string.menu_undo_done)) }
    }
}
