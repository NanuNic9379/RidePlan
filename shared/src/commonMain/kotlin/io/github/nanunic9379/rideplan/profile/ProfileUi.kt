package io.github.nanunic9379.rideplan.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nanunic9379.rideplan.resources.Res
import io.github.nanunic9379.rideplan.resources.backup_failed
import io.github.nanunic9379.rideplan.resources.backup_restore
import io.github.nanunic9379.rideplan.resources.backup_restored
import io.github.nanunic9379.rideplan.resources.backup_save
import io.github.nanunic9379.rideplan.resources.backup_saved
import io.github.nanunic9379.rideplan.resources.backup_text
import io.github.nanunic9379.rideplan.resources.backup_title
import io.github.nanunic9379.rideplan.resources.card_saved
import io.github.nanunic9379.rideplan.resources.date_format
import io.github.nanunic9379.rideplan.resources.done_title
import io.github.nanunic9379.rideplan.resources.home_add_bike
import io.github.nanunic9379.rideplan.resources.home_add_profile
import io.github.nanunic9379.rideplan.resources.home_back
import io.github.nanunic9379.rideplan.resources.home_empty_text
import io.github.nanunic9379.rideplan.resources.home_empty_title
import io.github.nanunic9379.rideplan.resources.home_explore
import io.github.nanunic9379.rideplan.resources.home_hello
import io.github.nanunic9379.rideplan.resources.home_hello_anon
import io.github.nanunic9379.rideplan.resources.home_km_ridden
import io.github.nanunic9379.rideplan.resources.home_new_ride
import io.github.nanunic9379.rideplan.resources.home_open_file
import io.github.nanunic9379.rideplan.resources.home_paste_link
import io.github.nanunic9379.rideplan.resources.home_rides
import io.github.nanunic9379.rideplan.resources.menu_delete
import io.github.nanunic9379.rideplan.resources.menu_done
import io.github.nanunic9379.rideplan.resources.menu_more
import io.github.nanunic9379.rideplan.resources.menu_rename
import io.github.nanunic9379.rideplan.resources.menu_undo_done
import io.github.nanunic9379.rideplan.resources.nav_back
import io.github.nanunic9379.rideplan.resources.preview_received
import io.github.nanunic9379.rideplan.resources.preview_save
import io.github.nanunic9379.rideplan.resources.profile_bike
import io.github.nanunic9379.rideplan.resources.profile_local
import io.github.nanunic9379.rideplan.resources.profile_name
import io.github.nanunic9379.rideplan.resources.profile_stats
import io.github.nanunic9379.rideplan.resources.profile_title
import io.github.nanunic9379.rideplan.resources.rename_title
import io.github.nanunic9379.rideplan.resources.ride_default_name
import io.github.nanunic9379.rideplan.resources.ride_done_many
import io.github.nanunic9379.rideplan.resources.ride_done_once
import io.github.nanunic9379.rideplan.resources.ride_km
import io.github.nanunic9379.rideplan.resources.ride_stops
import io.github.nanunic9379.rideplan.resources.ride_todo
import io.github.nanunic9379.rideplan.resources.route_cancel
import io.github.nanunic9379.rideplan.resources.save
import io.github.nanunic9379.rideplan.resources.snack_deleted
import io.github.nanunic9379.rideplan.resources.snack_done
import io.github.nanunic9379.rideplan.resources.snack_undo
import io.github.nanunic9379.rideplan.resources.stat_km
import io.github.nanunic9379.rideplan.resources.stat_longest
import io.github.nanunic9379.rideplan.resources.stat_ridden
import io.github.nanunic9379.rideplan.resources.stats_empty
import io.github.nanunic9379.rideplan.resources.welcome_bike
import io.github.nanunic9379.rideplan.resources.welcome_name
import io.github.nanunic9379.rideplan.resources.welcome_skip
import io.github.nanunic9379.rideplan.resources.welcome_start
import io.github.nanunic9379.rideplan.resources.welcome_text
import io.github.nanunic9379.rideplan.resources.welcome_title
import io.github.nanunic9379.rideplan.ride.ImportActions
import io.github.nanunic9379.rideplan.ride.ImportStatus
import io.github.nanunic9379.rideplan.ride.LinkDialog
import io.github.nanunic9379.rideplan.ride.Page
import io.github.nanunic9379.rideplan.ride.RideScreenState
import io.github.nanunic9379.rideplan.ride.formatKm
import io.github.nanunic9379.rideplan.route.durationText
import io.github.nanunic9379.rideplan.ui.AppIcons
import io.github.nanunic9379.rideplan.ui.Touch
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Home screen (#41): who you are, the actions for a new ride and your rides.
 * The map opens only when needed. One primary action (paste a link), two tonal ones,
 * then the list: every row is a 56 dp+ target and the overflow menu has a label.
 */
@Composable
fun HomeScreen(store: ProfileStore, rides: RideScreenState, actions: ImportActions?, snackbar: SnackbarHostState) {
    val data = store.data
    val scope = rememberCoroutineScope()
    var linkDialog by remember { mutableStateOf(false) }
    if (linkDialog && actions != null) LinkDialog(actions, onDismiss = { linkDialog = false })

    LazyColumn(
        Modifier.fillMaxSize().safeDrawingPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Greeting(data, onClick = { rides.page = Page.PROFILE }) }
        item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { ImportStatus(rides) } }
        if (actions != null) {
            item { SectionTitle(stringResource(Res.string.home_new_ride), Modifier.padding(top = 4.dp)) }
            item {
                Button(onClick = { linkDialog = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                    Icon(AppIcons.Link, contentDescription = null)
                    Spacer(Modifier.size(12.dp))
                    Text(stringResource(Res.string.home_paste_link), style = MaterialTheme.typography.titleMedium)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TonalAction(AppIcons.File, stringResource(Res.string.home_open_file), actions.openFile, Modifier.weight(1f))
                    TonalAction(AppIcons.Map, stringResource(Res.string.home_explore), { rides.page = Page.MAP }, Modifier.weight(1f))
                }
            }
        }
        item { SectionTitle(stringResource(Res.string.home_rides), Modifier.padding(top = 12.dp)) }
        if (data.rides.isEmpty()) item { EmptyRides() }
        items(data.rides, key = { it.id }) { saved ->
            SavedRideCard(
                saved,
                onOpen = {
                    actions?.openRide?.invoke(saved.toRide())
                    rides.savedId = saved.id
                },
                onDone = { day ->
                    store.update { it.markRidden(saved.id, day) }
                    scope.launch { snackbar.showSnackbar(getString(Res.string.snack_done)) }
                },
                onUndoDone = { store.update { it.undoRidden(saved.id) } },
                onRename = { name -> store.update { it.rename(saved.id, name) } },
                onDelete = {
                    val index = store.data.rides.indexOf(saved)
                    store.update { it.delete(saved.id) }
                    scope.launch {
                        val r = snackbar.showSnackbar(getString(Res.string.snack_deleted), getString(Res.string.snack_undo), withDismissAction = false)
                        if (r == SnackbarResult.ActionPerformed) {
                            store.update { d ->
                                val list = d.rides.toMutableList().apply { add(index.coerceIn(0, size), saved) }
                                d.copy(rides = list)
                            }
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = modifier.semantics { heading() })
}

@Composable
private fun TonalAction(icon: ImageVector, text: String, onClick: () -> Unit, modifier: Modifier) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = Touch.Glove),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EmptyRides() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(72.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Route, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(36.dp))
        }
        Text(stringResource(Res.string.home_empty_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(Res.string.home_empty_text),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Greeting(data: ProfileData, onClick: () -> Unit) {
    val p = data.profile
    val stats = data.stats
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClickLabel = stringResource(Res.string.profile_title), onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier.size(56.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            val initial = p.name.trim().firstOrNull()?.uppercase()
            if (initial != null) {
                Text(initial, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            } else {
                Icon(AppIcons.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                if (p.name.isBlank()) stringResource(Res.string.home_hello_anon) else stringResource(Res.string.home_hello, p.name.trim()),
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = listOfNotNull(
                p.bike.trim().ifEmpty { null },
                if (stats.ridden > 0) stringResource(Res.string.home_km_ridden, formatKm(stats.kmRidden)) else null,
            ).joinToString(" · ")
            // Ask only for what is missing: with a name set, "add your name" was misleading.
            val hint = when {
                p.name.isBlank() -> stringResource(Res.string.home_add_profile)
                p.bike.isBlank() -> stringResource(Res.string.home_add_bike)
                else -> null
            }
            Text(
                sub.ifEmpty { hint.orEmpty() },
                style = MaterialTheme.typography.bodyLarge,
                color = if (sub.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SavedRideCard(
    saved: SavedRide,
    onOpen: () -> Unit,
    onDone: (Long) -> Unit,
    onUndoDone: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var pickDay by remember { mutableStateOf(false) }
    if (rename) RenameDialog(saved.name.orEmpty(), onDismiss = { rename = false }, onSave = { rename = false; onRename(it) })
    if (pickDay) DayPicker(onDismiss = { pickDay = false }, onPick = { pickDay = false; onDone(it) })
    val ridden = saved.ridden.isNotEmpty()

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 80.dp).clickable(onClick = onOpen).padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .background(
                        if (ridden) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (ridden) AppIcons.Check else AppIcons.Route,
                    contentDescription = null,
                    tint = if (ridden) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title(saved), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val parts = buildList {
                    add(stringResource(Res.string.ride_km, formatKm(saved.km)))
                    saved.seconds?.let { add(durationText(it.toDouble())) }
                    if (saved.stops.isNotEmpty()) add(pluralStringResource(Res.plurals.ride_stops, saved.stops.size, saved.stops.size))
                }
                Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    doneText(saved),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (ridden) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(Touch.Glove)) {
                    Icon(AppIcons.More, contentDescription = stringResource(Res.string.menu_more))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    MenuItem(AppIcons.Check, stringResource(Res.string.menu_done)) { menu = false; pickDay = true }
                    if (ridden) MenuItem(null, stringResource(Res.string.menu_undo_done)) { menu = false; onUndoDone() }
                    MenuItem(AppIcons.Edit, stringResource(Res.string.menu_rename)) { menu = false; rename = true }
                    MenuItem(AppIcons.Delete, stringResource(Res.string.menu_delete)) { menu = false; onDelete() }
                }
            }
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector?, text: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        onClick = onClick,
        leadingIcon = icon?.let { { Icon(it, contentDescription = null) } },
        modifier = Modifier.heightIn(min = Touch.Glove),
    )
}

@Composable
private fun title(saved: SavedRide): String {
    saved.name?.let { return it }
    val first = saved.stops.firstOrNull()?.name
    val last = saved.stops.lastOrNull()?.name
    return if (first != null && last != null && saved.stops.size >= 2) "$first → $last" else stringResource(Res.string.ride_default_name)
}

@Composable
private fun doneText(saved: SavedRide): String {
    val last = saved.ridden.maxOrNull() ?: return stringResource(Res.string.ride_todo)
    val (d, m, y) = civilDate(last)
    val date = stringResource(Res.string.date_format, d, m, y)
    return if (saved.ridden.size == 1) stringResource(Res.string.ride_done_once, date)
    else stringResource(Res.string.ride_done_many, date, saved.ridden.size)
}

/**
 * Below the open ride: already saved (Rename, I rode it) or, for a ride received
 * by link (#48), the button to keep it. [onSave]: null if there is nothing to save.
 * [onRename] changes the name of the shown ride: automatic saving does the rest.
 */
@Composable
fun SavedRow(store: ProfileStore, savedId: String?, currentName: String?, onSave: (() -> Unit)? = null, onRename: (String) -> Unit) {
    if (savedId == null && onSave != null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(Res.string.preview_received),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth().heightIn(min = Touch.Glove)) {
                Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(Res.string.preview_save))
            }
        }
        return
    }
    if (savedId == null) return
    val saved = store.data.rides.firstOrNull { it.id == savedId } ?: return
    var rename by remember { mutableStateOf(false) }
    var pickDay by remember { mutableStateOf(false) }
    if (rename) RenameDialog(currentName.orEmpty(), onDismiss = { rename = false }, onSave = { rename = false; onRename(it) })
    if (pickDay) DayPicker(onDismiss = { pickDay = false }, onPick = { day -> pickDay = false; store.update { it.markRidden(savedId, day) } })
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(AppIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(
                stringResource(Res.string.card_saved) + if (saved.ridden.isEmpty()) "" else " · " + doneText(saved),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TonalAction(AppIcons.Edit, stringResource(Res.string.menu_rename), { rename = true }, Modifier.weight(1f))
            TonalAction(AppIcons.Check, stringResource(Res.string.menu_done), { pickDay = true }, Modifier.weight(1f))
        }
    }
}

/** Profile page: name, bike and statistics. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(store: ProfileStore, onBack: () -> Unit, backup: BackupActions? = null) {
    val data = store.data
    val stats = data.stats
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(Res.string.profile_title)) },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.size(Touch.Glove)) {
                    Icon(AppIcons.Back, contentDescription = stringResource(Res.string.nav_back))
                }
            },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = data.profile.name,
                onValueChange = { v -> store.update { it.copy(profile = it.profile.copy(name = v.take(40))) } },
                label = { Text(stringResource(Res.string.profile_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = data.profile.bike,
                onValueChange = { v -> store.update { it.copy(profile = it.profile.copy(bike = v.take(60))) } },
                label = { Text(stringResource(Res.string.profile_bike)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SectionTitle(stringResource(Res.string.profile_stats), Modifier.padding(top = 8.dp))
            if (stats.ridden == 0) {
                Text(stringResource(Res.string.stats_empty), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat(stringResource(Res.string.stat_ridden), stats.ridden.toString(), Modifier.weight(1f))
                    Stat(stringResource(Res.string.stat_km), formatKm(stats.kmRidden), Modifier.weight(1f))
                    Stat(stringResource(Res.string.stat_longest), stats.longestKm?.let { formatKm(it) } ?: "–", Modifier.weight(1f))
                }
            }
            PrivateZonesSection(store)
            if (backup != null) BackupSection(store, backup)
            Row(
                Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Text(stringResource(Res.string.profile_local), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Profile backup (#43): a file to keep or carry to another phone. */
@Composable
private fun BackupSection(store: ProfileStore, backup: BackupActions) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(Res.string.backup_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(stringResource(Res.string.backup_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TonalAction(AppIcons.Upload, stringResource(Res.string.backup_save), backup.save, Modifier.weight(1f))
                TonalAction(AppIcons.Download, stringResource(Res.string.backup_restore), backup.restore, Modifier.weight(1f))
            }
            when (val n = store.backupNotice) {
                null -> {}
                BackupNotice.Saved -> Text(stringResource(Res.string.backup_saved), style = MaterialTheme.typography.bodyMedium)
                is BackupNotice.Restored -> Text(stringResource(Res.string.backup_restored, n.added), style = MaterialTheme.typography.bodyMedium)
                BackupNotice.Failed -> Text(stringResource(Res.string.backup_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    ElevatedCard(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** First launch: a single screen, name and bike optional. */
@Composable
fun WelcomeScreen(store: ProfileStore) {
    var name by remember { mutableStateOf(store.data.profile.name) }
    var bike by remember { mutableStateOf(store.data.profile.bike) }
    val finish = { store.update { it.copy(profile = it.profile.copy(name = name.trim(), bike = bike.trim(), welcomed = true)) } }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Box(
            Modifier.size(72.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Route, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(40.dp))
        }
        Text(stringResource(Res.string.welcome_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Text(stringResource(Res.string.welcome_text), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(40) },
            label = { Text(stringResource(Res.string.welcome_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = bike,
            onValueChange = { bike = it.take(60) },
            label = { Text(stringResource(Res.string.welcome_bike)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = finish, modifier = Modifier.fillMaxWidth().heightIn(min = Touch.Glove)) {
            Text(stringResource(Res.string.welcome_start), style = MaterialTheme.typography.titleMedium)
        }
        // "Skip" also keeps what you already typed: with the keyboard open the buttons move.
        TextButton(onClick = finish, modifier = Modifier.fillMaxWidth().heightIn(min = Touch.Glove)) {
            Text(stringResource(Res.string.welcome_skip))
        }
    }
}

/**
 * Button at the top of the map to go back to the home screen: a surface-coloured
 * extended FAB, so it reads on any map tile and is big enough for a gloved thumb.
 */
@Composable
fun HomeButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = Touch.Glove),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        icon = { Icon(AppIcons.Back, contentDescription = null) },
        text = { Text(stringResource(Res.string.home_back), style = MaterialTheme.typography.labelLarge) },
    )
}

@Composable
private fun RenameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(AppIcons.Edit, contentDescription = null) },
        title = { Text(stringResource(Res.string.rename_title)) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it.take(80) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { Button(onClick = { onSave(text) }, modifier = Modifier.heightIn(min = Touch.Min)) { Text(stringResource(Res.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Touch.Min)) { Text(stringResource(Res.string.route_cancel)) } },
    )
}

/** The day of the outing, today preselected. Returns midnight UTC of that day. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
private fun DayPicker(onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    // Usually you rode it today: it starts preselected. Future days cannot be chosen.
    val today = remember { Clock.System.now().toEpochMilliseconds().floorDiv(86_400_000L) * 86_400_000L }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = today,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= today
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(enabled = state.selectedDateMillis != null, onClick = { state.selectedDateMillis?.let(onPick) }) {
                Text(stringResource(Res.string.menu_done))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.route_cancel)) } },
    ) {
        DatePicker(state, title = { Text(stringResource(Res.string.done_title), Modifier.padding(start = 24.dp, top = 16.dp)) })
    }
}
