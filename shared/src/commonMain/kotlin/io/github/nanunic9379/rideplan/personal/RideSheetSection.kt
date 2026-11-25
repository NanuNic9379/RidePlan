package io.github.nanunic9379.rideplan.personal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.nanunic9379.rideplan.profile.Measures
import io.github.nanunic9379.rideplan.profile.ProfileStore
import io.github.nanunic9379.rideplan.profile.SavedRide
import io.github.nanunic9379.rideplan.profile.of
import io.github.nanunic9379.rideplan.profile.title
import io.github.nanunic9379.rideplan.resources.Res
import io.github.nanunic9379.rideplan.resources.detail_stop_finish
import io.github.nanunic9379.rideplan.resources.detail_stop_n
import io.github.nanunic9379.rideplan.resources.detail_stop_start
import io.github.nanunic9379.rideplan.resources.plan_clear
import io.github.nanunic9379.rideplan.resources.plan_none
import io.github.nanunic9379.rideplan.resources.plan_note
import io.github.nanunic9379.rideplan.resources.plan_pause
import io.github.nanunic9379.rideplan.resources.plan_save
import io.github.nanunic9379.rideplan.resources.plan_time
import io.github.nanunic9379.rideplan.resources.plan_time_invalid
import io.github.nanunic9379.rideplan.resources.purpose_coffee
import io.github.nanunic9379.rideplan.resources.purpose_fuel
import io.github.nanunic9379.rideplan.resources.purpose_lunch
import io.github.nanunic9379.rideplan.resources.purpose_meeting
import io.github.nanunic9379.rideplan.resources.purpose_photo
import io.github.nanunic9379.rideplan.resources.route_cancel
import io.github.nanunic9379.rideplan.resources.sheet_finish
import io.github.nanunic9379.rideplan.resources.sheet_hint
import io.github.nanunic9379.rideplan.resources.sheet_late
import io.github.nanunic9379.rideplan.resources.sheet_pause
import io.github.nanunic9379.rideplan.resources.sheet_private
import io.github.nanunic9379.rideplan.resources.sheet_rough
import io.github.nanunic9379.rideplan.resources.sheet_share
import io.github.nanunic9379.rideplan.resources.sheet_title
import io.github.nanunic9379.rideplan.resources.sheet_totals
import io.github.nanunic9379.rideplan.resources.sheet_updated_note
import io.github.nanunic9379.rideplan.resources.sheet_version
import io.github.nanunic9379.rideplan.resources.update_added
import io.github.nanunic9379.rideplan.resources.update_ok
import io.github.nanunic9379.rideplan.resources.update_removed
import io.github.nanunic9379.rideplan.resources.update_snack
import io.github.nanunic9379.rideplan.resources.update_replanned
import io.github.nanunic9379.rideplan.resources.update_title
import io.github.nanunic9379.rideplan.resources.update_unnamed
import io.github.nanunic9379.rideplan.resources.version_note_add
import io.github.nanunic9379.rideplan.resources.version_note_hint
import io.github.nanunic9379.rideplan.resources.version_note_title
import io.github.nanunic9379.rideplan.ride.RideSheet
import io.github.nanunic9379.rideplan.ride.RideUpdate
import io.github.nanunic9379.rideplan.ride.Stop
import io.github.nanunic9379.rideplan.ride.StopPlan
import io.github.nanunic9379.rideplan.ride.Timetable
import io.github.nanunic9379.rideplan.ride.formatKm
import io.github.nanunic9379.rideplan.route.durationText
import io.github.nanunic9379.rideplan.ui.AppIcons
import io.github.nanunic9379.rideplan.ui.Radius
import io.github.nanunic9379.rideplan.ui.Touch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * "Updated from v2 to v3" (#11): what changed when a newer version of the ride replaced
 * the one you had. Shown until you tap "Got it".
 */
@Composable
fun RideUpdateCard(store: ProfileStore, saved: SavedRide) {
    val update = saved.update ?: return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        NoteCard(
            AppIcons.Refresh,
            updateDetails(update),
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer,
            title = stringResource(Res.string.update_title, update.from, update.to),
        )
        TextButton(onClick = { store.update { it.dismissUpdate(saved.id) } }, modifier = Modifier.heightIn(min = Touch.Min)) {
            Text(stringResource(Res.string.update_ok))
        }
    }
}

/** The differences of an update, one per line: the leader's note first. */
@Composable
fun updateDetails(update: RideUpdate): String {
    val unnamed = stringResource(Res.string.update_unnamed)
    fun names(list: List<String>) = list.joinToString(", ") { it.substringBefore(',').ifBlank { unnamed } }
    return listOfNotNull(
        update.note.ifBlank { null },
        update.added.takeIf { it.isNotEmpty() }?.let { stringResource(Res.string.update_added, names(it)) },
        update.removed.takeIf { it.isNotEmpty() }?.let { stringResource(Res.string.update_removed, names(it)) },
        update.replanned.takeIf { it > 0 }?.let { stringResource(Res.string.update_replanned, it) },
    ).joinToString("\n")
}

/**
 * The ride sheet (#10) and the version (#11) on the ride page: the stops where the group
 * stops, with what, when and how long; tap one to plan it. "Share ride sheet" sends it as text.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RideSheetSection(store: ProfileStore, saved: SavedRide, shareText: ((String) -> Unit)?) {
    val ride = remember(saved) { saved.toRide() }
    val table = remember(saved) { Timetable.of(ride, saved.km, saved.seconds?.toDouble()) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var noting by remember { mutableStateOf(false) }
    val now = store.time()

    editing?.let { i ->
        val stop = ride.stops.getOrNull(i)
        if (stop == null) editing = null else StopPlanDialog(
            title = stopRole(i, ride.stops.size) + (stop.name?.let { " · " + it.substringBefore(',') } ?: ""),
            initial = stop.plan,
            onDismiss = { editing = null },
            onSave = { plan -> editing = null; store.update { it.setStopPlan(saved.id, i, plan, now) } },
        )
    }
    if (noting) VersionNoteDialog(saved.version?.note.orEmpty(), onDismiss = { noting = false }) { note ->
        noting = false
        store.update { it.setVersionNote(saved.id, note, now) }
    }

    SectionLabel(stringResource(Res.string.sheet_title))
    saved.version?.let { v ->
        Text(
            stringResource(Res.string.sheet_version, v.number, dateText(v.updatedAt.takeIf { it > 0 } ?: saved.savedAt)),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (v.note.isNotBlank()) Text(v.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    TextButton(onClick = { noting = true }, modifier = Modifier.heightIn(min = Touch.Min)) {
        Icon(AppIcons.Edit, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(stringResource(Res.string.version_note_add))
    }
    Text(stringResource(Res.string.sheet_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Column {
        ride.stops.forEachIndexed { i, stop ->
            if (i != 0 && i != ride.stops.lastIndex && stop.plan == null && stop.kind != Stop.Kind.STOP) return@forEachIndexed
            SheetRow(i, ride.stops.size, stop, table.rows[i]) { editing = i }
        }
    }
    val totals = stringResource(Res.string.sheet_totals, durationText(table.ridingSeconds), durationText(table.totalSeconds))
    Text(
        listOfNotNull(totals, table.finish?.let { stringResource(Res.string.sheet_finish, "~" + StopPlan.timeText(it)) }).joinToString(" · "),
        style = MaterialTheme.typography.bodyMedium,
    )
    if (table.roughSpeed && table.hasTimes) {
        Text(stringResource(Res.string.sheet_rough, Measures.speedText(Timetable.ROUGH_KMH, LocalUnits.current)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (shareText != null) {
        val labels = sheetLabels()
        val name = title(saved)
        val zones = store.data.privateZones
        OutlinedButton(
            onClick = { shareText(RideSheet.text(ride, name, labels, saved.km, saved.seconds?.toDouble(), zones)) },
            modifier = Modifier.fillMaxWidth().heightIn(min = Touch.Glove),
            shape = Radius.Control,
        ) {
            Icon(AppIcons.Share, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(Res.string.sheet_share))
        }
    }
}

@Composable
private fun SheetRow(index: Int, count: Int, stop: Stop, row: Timetable.Row, onClick: () -> Unit) {
    val plan = stop.plan
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().heightIn(min = Touch.Min).clickable(role = Role.Button, onClick = onClick).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            row.arrival?.let { (if (row.estimated) "~" else "") + StopPlan.timeText(it) } ?: "--:--",
            style = MaterialTheme.typography.titleMedium,
            color = if (row.estimated) muted else MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(64.dp),
        )
        Column(Modifier.weight(1f)) {
            val head = listOfNotNull(plan?.purpose?.let { purposeText(it) }, stopRole(index, count)).joinToString(" · ")
            Text(head, style = MaterialTheme.typography.titleMedium)
            stop.name?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            val extra = listOfNotNull(
                plan?.pauseMinutes?.takeIf { it > 0 && index != count - 1 }?.let { stringResource(Res.string.sheet_pause, it) },
                if (row.late) stringResource(Res.string.sheet_late) else null,
                plan?.note?.ifBlank { null },
            )
            if (extra.isNotEmpty()) Text(extra.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = muted)
        }
        Icon(AppIcons.Edit, contentDescription = null, tint = muted, modifier = Modifier.padding(top = 4.dp).size(20.dp))
    }
}

@Composable
private fun stopRole(index: Int, count: Int): String = when {
    index == 0 -> stringResource(Res.string.detail_stop_start)
    index == count - 1 -> stringResource(Res.string.detail_stop_finish)
    else -> stringResource(Res.string.detail_stop_n, index + 1)
}

@Composable
fun purposeText(p: StopPlan.Purpose): String = stringResource(
    when (p) {
        StopPlan.Purpose.MEETING -> Res.string.purpose_meeting
        StopPlan.Purpose.FUEL -> Res.string.purpose_fuel
        StopPlan.Purpose.COFFEE -> Res.string.purpose_coffee
        StopPlan.Purpose.LUNCH -> Res.string.purpose_lunch
        StopPlan.Purpose.PHOTO -> Res.string.purpose_photo
    },
)

/** The translated words of the shared sheet. */
@Composable
private fun sheetLabels(): RideSheet.Labels {
    val purposes = StopPlan.Purpose.entries.associateWith { purposeText(it) }
    val pause = stringResource(Res.string.sheet_pause, 999)
    val totals = stringResource(Res.string.sheet_totals, "\u0001", "\u0002")
    val updated = stringResource(Res.string.sheet_updated_note, "\u0001")
    val stopN = stringResource(Res.string.detail_stop_n, 999)
    val distanceUnit = distanceUnit()
    val units = LocalUnits.current
    val km = { k: Double -> formatKm(units.of(k)) }
    return RideSheet.Labels(
        title = { v -> v?.let { "v$it" }.orEmpty() },
        distance = { k -> km(k) + " " + distanceUnit },
        duration = { s -> durationTextPlain(s) },
        totals = { r, t -> totals.replace("\u0001", r).replace("\u0002", t) },
        purpose = { purposes.getValue(it) },
        pause = { m -> pause.replace("999", m.toString()) },
        stop = { n -> stopN.replace("999", n.toString()) },
        privatePlace = stringResource(Res.string.sheet_private),
        start = stringResource(Res.string.detail_stop_start),
        finish = stringResource(Res.string.detail_stop_finish),
        approx = "~",
        late = "(" + stringResource(Res.string.sheet_late) + ")",
        updated = { n -> updated.replace("\u0001", n) },
    )
}

/** "3 h 10 min" outside composition: hours and minutes read the same in every language of the app. */
private fun durationTextPlain(seconds: Double): String {
    val total = (seconds / 60).toLong()
    val h = total / 60
    val m = total % 60
    return if (h > 0) "$h h ${m.toString().padStart(2, '0')} min" else "$m min"
}

/** What the group does at a stop (#10): purpose, time, pause and a short note. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StopPlanDialog(title: String, initial: StopPlan?, onDismiss: () -> Unit, onSave: (StopPlan?) -> Unit) {
    var purpose by remember { mutableStateOf(initial?.purpose) }
    var time by remember { mutableStateOf(initial?.time?.let(StopPlan::timeText).orEmpty()) }
    var pause by remember { mutableStateOf(initial?.pauseMinutes ?: 0) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }
    val timeOk = time.isBlank() || StopPlan.parseTime(time) != null
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(AppIcons.Pin, contentDescription = null) },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = purpose == null,
                        onClick = { purpose = null },
                        label = { Text(stringResource(Res.string.plan_none)) },
                        modifier = Modifier.heightIn(min = Touch.Min),
                    )
                    StopPlan.Purpose.entries.forEach { p ->
                        FilterChip(
                            selected = purpose == p,
                            onClick = { purpose = p },
                            label = { Text(purposeText(p)) },
                            modifier = Modifier.heightIn(min = Touch.Min),
                        )
                    }
                }
                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it.take(5) },
                    label = { Text(stringResource(Res.string.plan_time)) },
                    isError = !timeOk,
                    supportingText = if (timeOk) null else ({ Text(stringResource(Res.string.plan_time_invalid)) }),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(Res.string.plan_pause), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StopPlan.PAUSES.forEach { m ->
                        FilterChip(
                            selected = pause == m,
                            onClick = { pause = m },
                            label = { Text(if (m == 0) "0" else "$m min") },
                            modifier = Modifier.heightIn(min = Touch.Min),
                        )
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(StopPlan.MAX_NOTE) },
                    label = { Text(stringResource(Res.string.plan_note)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (initial != null) {
                    TextButton(onClick = { onSave(null) }, modifier = Modifier.heightIn(min = Touch.Min)) {
                        Text(stringResource(Res.string.plan_clear))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(StopPlan(purpose, StopPlan.parseTime(time), pause, note).normalized()) },
                enabled = timeOk,
                modifier = Modifier.heightIn(min = Touch.Min),
            ) { Text(stringResource(Res.string.plan_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Touch.Min)) { Text(stringResource(Res.string.route_cancel)) }
        },
    )
}

/** The leader's line about the current version (#11). */
@Composable
private fun VersionNoteDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(AppIcons.Edit, contentDescription = null) },
        title = { Text(stringResource(Res.string.version_note_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(io.github.nanunic9379.rideplan.ride.RideVersion.MAX_NOTE) },
                placeholder = { Text(stringResource(Res.string.version_note_hint)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }, modifier = Modifier.heightIn(min = Touch.Min)) { Text(stringResource(Res.string.plan_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Touch.Min)) { Text(stringResource(Res.string.route_cancel)) }
        },
    )
}

/** The snackbar text when [saved] was just replaced by a newer version at [now] (#11); null otherwise. */
suspend fun updateNotice(saved: SavedRide?, now: Long): String? {
    val update = saved?.update?.takeIf { it.at == now } ?: return null
    return listOfNotNull(getString(Res.string.update_snack, update.to), update.note.ifBlank { null }).joinToString(": ")
}
