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
import io.github.nanunic9379.rideplan.resources.card_saved
import io.github.nanunic9379.rideplan.resources.date_format
import io.github.nanunic9379.rideplan.resources.done_title
import io.github.nanunic9379.rideplan.resources.home_back
import io.github.nanunic9379.rideplan.resources.menu_done
import io.github.nanunic9379.rideplan.resources.menu_rename
import io.github.nanunic9379.rideplan.resources.preview_received
import io.github.nanunic9379.rideplan.resources.preview_save
import io.github.nanunic9379.rideplan.resources.rename_title
import io.github.nanunic9379.rideplan.resources.ride_default_name
import io.github.nanunic9379.rideplan.resources.ride_done_many
import io.github.nanunic9379.rideplan.resources.ride_done_once
import io.github.nanunic9379.rideplan.resources.ride_todo
import io.github.nanunic9379.rideplan.resources.route_cancel
import io.github.nanunic9379.rideplan.resources.save
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
import io.github.nanunic9379.rideplan.ui.Radius
import io.github.nanunic9379.rideplan.ui.Touch
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = modifier.semantics { heading() })
}

@Composable
internal fun TonalAction(icon: ImageVector, text: String, onClick: () -> Unit, modifier: Modifier) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = Touch.Glove),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        shape = Radius.Control,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun MenuItem(icon: ImageVector?, text: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        onClick = onClick,
        leadingIcon = icon?.let { { Icon(it, contentDescription = null) } },
        modifier = Modifier.heightIn(min = Touch.Glove),
    )
}

@Composable
internal fun title(saved: SavedRide): String {
    saved.name?.let { return it }
    val first = saved.stops.firstOrNull()?.name
    val last = saved.stops.lastOrNull()?.name
    return if (first != null && last != null && saved.stops.size >= 2) "$first → $last" else stringResource(Res.string.ride_default_name)
}

@Composable
internal fun doneText(saved: SavedRide): String {
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
    if (onSave != null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(Res.string.preview_received),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth().heightIn(min = Touch.Glove), shape = Radius.Control) {
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

/** First launch: a single screen, name and bike optional. */
@Composable
fun WelcomeScreen(store: ProfileStore) {
    var name by remember { mutableStateOf(store.data.profile.name) }
    var bike by remember { mutableStateOf(store.data.profile.bike) }
    val finish = {
        store.update { d ->
            val named = d.copy(profile = d.profile.copy(name = name.trim(), welcomed = true))
            // The bike typed here is the first one of the garage (#21).
            if (bike.isNotBlank() && d.bikes.isEmpty()) named.saveBike(Bike(id = ProfileData.newId(), name = bike.trim()), default = true) else named
        }
    }
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
        Button(onClick = finish, modifier = Modifier.fillMaxWidth().heightIn(min = Touch.Glove), shape = Radius.Control) {
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
internal fun RenameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(AppIcons.Edit, contentDescription = null) },
        title = { Text(stringResource(Res.string.rename_title)) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it.take(80) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { Button(onClick = { onSave(text) }, modifier = Modifier.heightIn(min = Touch.Min), shape = Radius.Control) { Text(stringResource(Res.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Touch.Min)) { Text(stringResource(Res.string.route_cancel)) } },
    )
}

/** The day of the outing, today preselected. Returns midnight UTC of that day. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
internal fun DayPicker(onDismiss: () -> Unit, onPick: (Long) -> Unit) {
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
            Button(enabled = state.selectedDateMillis != null, onClick = { state.selectedDateMillis?.let(onPick) }, shape = Radius.Control) {
                Text(stringResource(Res.string.menu_done))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.route_cancel)) } },
    ) {
        DatePicker(state, title = { Text(stringResource(Res.string.done_title), Modifier.padding(start = 24.dp, top = 16.dp)) })
    }
}
