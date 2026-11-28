package io.github.nanunic9379.rideplan.personal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.nanunic9379.rideplan.profile.BackupActions
import io.github.nanunic9379.rideplan.profile.BackupNotice
import io.github.nanunic9379.rideplan.profile.ProfileStore
import io.github.nanunic9379.rideplan.profile.HairpinWarningMode
import io.github.nanunic9379.rideplan.profile.ThemeMode
import io.github.nanunic9379.rideplan.profile.TonalAction
import io.github.nanunic9379.rideplan.profile.Units
import io.github.nanunic9379.rideplan.profile.symbol
import io.github.nanunic9379.rideplan.profile.WipeSummary
import io.github.nanunic9379.rideplan.resources.Res
import io.github.nanunic9379.rideplan.ride.Dest
import io.github.nanunic9379.rideplan.ride.RideScreenState
import io.github.nanunic9379.rideplan.ui.AppIcons
import io.github.nanunic9379.rideplan.ui.Radius
import io.github.nanunic9379.rideplan.ui.Touch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import io.github.nanunic9379.rideplan.resources.backup_failed
import io.github.nanunic9379.rideplan.resources.backup_last
import io.github.nanunic9379.rideplan.resources.backup_restore
import io.github.nanunic9379.rideplan.resources.backup_restored
import io.github.nanunic9379.rideplan.resources.backup_save
import io.github.nanunic9379.rideplan.resources.backup_saved
import io.github.nanunic9379.rideplan.resources.backup_text
import io.github.nanunic9379.rideplan.resources.backup_title
import io.github.nanunic9379.rideplan.resources.break_reminder_title
import io.github.nanunic9379.rideplan.resources.break_reminder_off
import io.github.nanunic9379.rideplan.resources.break_reminder_90
import io.github.nanunic9379.rideplan.resources.break_reminder_120
import io.github.nanunic9379.rideplan.resources.break_reminder_180
import io.github.nanunic9379.rideplan.resources.break_reminder_note
import io.github.nanunic9379.rideplan.resources.hairpin_warnings_title
import io.github.nanunic9379.rideplan.resources.hairpin_warnings_note
import io.github.nanunic9379.rideplan.resources.hairpin_warnings_off
import io.github.nanunic9379.rideplan.resources.hairpin_warnings_sound
import io.github.nanunic9379.rideplan.resources.hairpin_warnings_voice
import io.github.nanunic9379.rideplan.resources.danger_title
import io.github.nanunic9379.rideplan.resources.export_all
import io.github.nanunic9379.rideplan.resources.export_done
import io.github.nanunic9379.rideplan.resources.export_failed
import io.github.nanunic9379.rideplan.resources.export_sub
import io.github.nanunic9379.rideplan.resources.export_title
import io.github.nanunic9379.rideplan.resources.info_coffee
import io.github.nanunic9379.rideplan.resources.info_coffee_text
import io.github.nanunic9379.rideplan.resources.info_licences
import io.github.nanunic9379.rideplan.resources.info_licences_text
import io.github.nanunic9379.rideplan.resources.info_privacy
import io.github.nanunic9379.rideplan.resources.info_source
import io.github.nanunic9379.rideplan.resources.info_source_text
import io.github.nanunic9379.rideplan.resources.info_title
import io.github.nanunic9379.rideplan.resources.info_version
import io.github.nanunic9379.rideplan.resources.language_help
import io.github.nanunic9379.rideplan.resources.language_phone
import io.github.nanunic9379.rideplan.resources.language_title
import io.github.nanunic9379.rideplan.resources.mapdata_cells
import io.github.nanunic9379.rideplan.resources.mapdata_ride_map
import io.github.nanunic9379.rideplan.resources.ride_default_name
import io.github.nanunic9379.rideplan.resources.mapdata_delete_title
import io.github.nanunic9379.rideplan.resources.mapdata_free
import io.github.nanunic9379.rideplan.resources.mapdata_list
import io.github.nanunic9379.rideplan.resources.mapdata_none
import io.github.nanunic9379.rideplan.resources.mapdata_none_text
import io.github.nanunic9379.rideplan.resources.mapdata_note
import io.github.nanunic9379.rideplan.resources.mapdata_sub
import io.github.nanunic9379.rideplan.resources.mapdata_title
import io.github.nanunic9379.rideplan.resources.mapdata_used
import io.github.nanunic9379.rideplan.resources.privacy_asked
import io.github.nanunic9379.rideplan.resources.privacy_asked_text
import io.github.nanunic9379.rideplan.resources.privacy_leaves
import io.github.nanunic9379.rideplan.resources.privacy_needed
import io.github.nanunic9379.rideplan.resources.privacy_needed_text
import io.github.nanunic9379.rideplan.resources.privacy_never
import io.github.nanunic9379.rideplan.resources.privacy_never_text
import io.github.nanunic9379.rideplan.resources.privacy_promise
import io.github.nanunic9379.rideplan.resources.privacy_title
import io.github.nanunic9379.rideplan.resources.route_cancel
import io.github.nanunic9379.rideplan.resources.settings_app
import io.github.nanunic9379.rideplan.resources.settings_maps
import io.github.nanunic9379.rideplan.resources.settings_title
import io.github.nanunic9379.rideplan.resources.theme_dark
import io.github.nanunic9379.rideplan.resources.theme_light
import io.github.nanunic9379.rideplan.resources.theme_note
import io.github.nanunic9379.rideplan.resources.theme_system
import io.github.nanunic9379.rideplan.resources.theme_title
import io.github.nanunic9379.rideplan.resources.units_auto
import io.github.nanunic9379.rideplan.resources.units_note
import io.github.nanunic9379.rideplan.resources.units_title
import io.github.nanunic9379.rideplan.resources.update_check_start
import io.github.nanunic9379.rideplan.resources.update_check_text
import io.github.nanunic9379.rideplan.resources.update_background_title
import io.github.nanunic9379.rideplan.resources.update_background_text
import io.github.nanunic9379.rideplan.resources.speed_cameras
import io.github.nanunic9379.rideplan.resources.speed_cameras_text
import io.github.nanunic9379.rideplan.resources.wifi_only
import io.github.nanunic9379.rideplan.resources.wifi_only_text
import io.github.nanunic9379.rideplan.resources.wipe_backup_first
import io.github.nanunic9379.rideplan.resources.wipe_bikes
import io.github.nanunic9379.rideplan.resources.wipe_confirm
import io.github.nanunic9379.rideplan.resources.wipe_no_undo
import io.github.nanunic9379.rideplan.resources.wipe_open
import io.github.nanunic9379.rideplan.resources.wipe_outings
import io.github.nanunic9379.rideplan.resources.wipe_rides
import io.github.nanunic9379.rideplan.resources.wipe_title
import io.github.nanunic9379.rideplan.resources.wipe_understood
import io.github.nanunic9379.rideplan.resources.wipe_zones
import io.github.nanunic9379.rideplan.resources.now_playing_title
import io.github.nanunic9379.rideplan.resources.now_playing_text
import io.github.nanunic9379.rideplan.resources.now_playing_access_hint
import io.github.nanunic9379.rideplan.resources.now_playing_privacy_title
import io.github.nanunic9379.rideplan.resources.now_playing_privacy_text
import io.github.nanunic9379.rideplan.resources.now_playing_privacy_continue
import io.github.nanunic9379.rideplan.resources.zones_delete
import io.github.nanunic9379.rideplan.resources.update_available_text
import io.github.nanunic9379.rideplan.resources.update_available_title
import io.github.nanunic9379.rideplan.resources.update_check_now
import io.github.nanunic9379.rideplan.resources.update_checking
import io.github.nanunic9379.rideplan.resources.update_err_network
import io.github.nanunic9379.rideplan.resources.update_latest
import io.github.nanunic9379.rideplan.resources.update_retry
import io.github.nanunic9379.rideplan.resources.photo_spots_title
import io.github.nanunic9379.rideplan.resources.photo_spots_map_toggle
import io.github.nanunic9379.rideplan.resources.photo_spots_map_help
import io.github.nanunic9379.rideplan.resources.photo_spots_manage
import io.github.nanunic9379.rideplan.resources.photo_spots_count

/** Data & privacy (#21): what leaves the phone, the backup, export, and deleting everything. */
@Composable
fun PrivacyPage(store: ProfileStore, rides: RideScreenState, backup: BackupActions?, platform: PlatformActions?) {
    val data = store.data
    SubPage(stringResource(Res.string.privacy_title), onBack = { rides.back() }) {
        SectionLabel(stringResource(Res.string.privacy_leaves))
        RowGroup {
            NavRow(AppIcons.Lock, stringResource(Res.string.privacy_never), stringResource(Res.string.privacy_never_text), onClick = null)
            GroupDivider()
            NavRow(AppIcons.Share, stringResource(Res.string.privacy_asked), stringResource(Res.string.privacy_asked_text), onClick = null)
            GroupDivider()
            NavRow(AppIcons.Map, stringResource(Res.string.privacy_needed), stringResource(Res.string.privacy_needed_text), onClick = null)
        }
        Text(stringResource(Res.string.privacy_promise), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (backup != null) {
            SectionLabel(stringResource(Res.string.backup_title))
            Text(stringResource(Res.string.backup_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TonalAction(AppIcons.Upload, stringResource(Res.string.backup_save), backup.save, Modifier.weight(1f))
                TonalAction(AppIcons.Download, stringResource(Res.string.backup_restore), backup.restore, Modifier.weight(1f))
            }
            when (val n = store.backupNotice) {
                null -> data.settings.lastBackupAt?.let {
                    Text(stringResource(Res.string.backup_last, dateText(it)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BackupNotice.Saved -> Text(stringResource(Res.string.backup_saved), style = MaterialTheme.typography.bodyMedium)
                is BackupNotice.Restored -> Text(stringResource(Res.string.backup_restored, n.added), style = MaterialTheme.typography.bodyMedium)
                BackupNotice.Failed -> Text(stringResource(Res.string.backup_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        }

        val export = platform?.exportAll
        if (export != null) {
            SectionLabel(stringResource(Res.string.export_title))
            RowGroup {
                NavRow(
                    AppIcons.Document,
                    stringResource(Res.string.export_all),
                    pluralStringResource(Res.plurals.export_sub, data.rides.size, data.rides.size),
                    enabled = data.rides.isNotEmpty(),
                    onClick = export,
                )
            }
            when (platform.exportNotice) {
                true -> Text(stringResource(Res.string.export_done), style = MaterialTheme.typography.bodyMedium)
                false -> Text(stringResource(Res.string.export_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                null -> {}
            }
        }

        SectionLabel(stringResource(Res.string.danger_title))
        OutlinedButton(
            onClick = { rides.open(Dest.DeleteAll) },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().heightIn(min = Touch.Glove),
            shape = Radius.Control,
        ) {
            Icon(AppIcons.Delete, contentDescription = null)
            Text(stringResource(Res.string.wipe_open), Modifier.padding(start = 8.dp))
        }
    }
}

/** Deleting everything (#21): what goes, with numbers; a backup first; two gestures. */
@Composable
fun DeleteAllPage(store: ProfileStore, rides: RideScreenState, backup: BackupActions?, platform: PlatformActions?) {
    val w = WipeSummary.of(store.data)
    var understood by remember { mutableStateOf(false) }
    SubPage(stringResource(Res.string.wipe_open), onBack = { rides.back() }) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Icon(AppIcons.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
        }
        Text(
            stringResource(Res.string.wipe_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(Res.string.wipe_no_undo),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        RowGroup {
            NavRow(AppIcons.Route, pluralStringResource(Res.plurals.wipe_rides, w.rides, w.rides, w.notes), onClick = null)
            GroupDivider()
            NavRow(AppIcons.History, pluralStringResource(Res.plurals.wipe_outings, w.outings, w.outings), onClick = null)
            GroupDivider()
            NavRow(AppIcons.Moto, pluralStringResource(Res.plurals.wipe_bikes, w.bikes, w.bikes), onClick = null)
            GroupDivider()
            NavRow(AppIcons.Lock, pluralStringResource(Res.plurals.wipe_zones, w.zones, w.zones), onClick = null)
        }
        if (backup != null) {
            OutlinedButton(onClick = backup.save, modifier = Modifier.fillMaxWidth().heightIn(min = Touch.Glove), shape = Radius.Control) {
                Icon(AppIcons.Upload, contentDescription = null)
                Text(stringResource(Res.string.wipe_backup_first), Modifier.padding(start = 8.dp))
            }
            if (store.backupNotice == BackupNotice.Saved) Text(stringResource(Res.string.backup_saved), style = MaterialTheme.typography.bodyMedium)
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = Touch.Min)
                .toggleable(value = understood, role = Role.Checkbox, onValueChange = { understood = it }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = understood, onCheckedChange = null, modifier = Modifier.padding(end = 12.dp))
            Text(stringResource(Res.string.wipe_understood), style = MaterialTheme.typography.bodyLarge)
        }
        Button(
            onClick = {
                platform?.wipeDevice?.invoke()
                store.wipe()
                rides.tab(io.github.nanunic9379.rideplan.ride.Page.HOME)
            },
            enabled = understood,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
            modifier = Modifier.fillMaxWidth().heightIn(min = Touch.Glove),
            shape = Radius.Control,
        ) { Text(stringResource(Res.string.wipe_confirm)) }
        TextButton(onClick = { rides.back() }, modifier = Modifier.fillMaxWidth().heightIn(min = Touch.Min)) {
            Text(stringResource(Res.string.route_cancel))
        }
    }
}

/** The languages the app has, each written in its own language. */
private val Languages = listOf("it" to "Italiano", "en" to "English", "de" to "Deutsch", "es" to "Español", "fr" to "Français")

private fun languageName(tag: String?): String? = Languages.firstOrNull { tag != null && tag.startsWith(it.first) }?.second

/** Settings (#21): language, units, theme, road data and Info. */
@Composable
fun SettingsPage(store: ProfileStore, rides: RideScreenState, platform: PlatformActions?) {
    val s = store.data.settings
    var showMediaDisclosure by remember { mutableStateOf(false) }
    SubPage(stringResource(Res.string.settings_title), onBack = { rides.back() }) {
        platform?.nostr?.let { io.github.nanunic9379.rideplan.social.NostrSettingsRow(it, rides) }
        platform?.language?.let { lang ->
            RowGroup {
                NavRow(
                    AppIcons.Language,
                    stringResource(Res.string.language_title),
                    languageName(lang.current()) ?: stringResource(Res.string.language_phone),
                    onClick = { rides.open(Dest.Language) },
                )
            }
        }
        SectionLabel(stringResource(Res.string.units_title))
        // "Automatic" (null) follows the country of the phone (#15).
        val unitChoices = listOf(null) + Units.entries
        Segmented(
            listOf(stringResource(Res.string.units_auto), Units.KM.symbol, Units.MILES.symbol),
            unitChoices.indexOf(s.units),
        ) { i -> store.update { it.copy(settings = it.settings.copy(units = unitChoices[i])) } }
        Text(stringResource(Res.string.units_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionLabel(stringResource(Res.string.break_reminder_title))
        val breakChoices = listOf(0, 90, 120, 180)
        Segmented(
            listOf(
                stringResource(Res.string.break_reminder_off),
                stringResource(Res.string.break_reminder_90),
                stringResource(Res.string.break_reminder_120),
                stringResource(Res.string.break_reminder_180),
            ),
            breakChoices.indexOf(s.breakReminderMinutes).takeIf { it >= 0 } ?: 2,
        ) { i -> store.update { it.copy(settings = it.settings.copy(breakReminderMinutes = breakChoices[i])) } }
        Text(stringResource(Res.string.break_reminder_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionLabel(stringResource(Res.string.hairpin_warnings_title))
        val hairpinChoices = HairpinWarningMode.entries
        Segmented(
            listOf(
                stringResource(Res.string.hairpin_warnings_off),
                stringResource(Res.string.hairpin_warnings_sound),
                stringResource(Res.string.hairpin_warnings_voice),
            ),
            hairpinChoices.indexOf(s.hairpinWarnings).coerceAtLeast(0),
        ) { i -> store.update { it.copy(settings = it.settings.copy(hairpinWarnings = hairpinChoices[i])) } }
        Text(stringResource(Res.string.hairpin_warnings_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionLabel(stringResource(Res.string.theme_title))
        Segmented(
            listOf(stringResource(Res.string.theme_system), stringResource(Res.string.theme_light), stringResource(Res.string.theme_dark)),
            ThemeMode.entries.indexOf(s.theme),
        ) { i -> store.update { it.copy(settings = it.settings.copy(theme = ThemeMode.entries[i])) } }
        Text(stringResource(Res.string.theme_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionLabel(stringResource(Res.string.settings_maps))
        RowGroup {
            platform?.mapData?.let { md ->
                val items = remember { md.list() }
                NavRow(
                    AppIcons.Storage,
                    stringResource(Res.string.mapdata_title),
                    if (items.isEmpty()) stringResource(Res.string.mapdata_none) else pluralStringResource(Res.plurals.mapdata_sub, items.size, items.size, sizeText(items.sumOf { it.bytes })),
                    onClick = { rides.open(Dest.MapData) },
                )
                GroupDivider()
            }
            SwitchRow(AppIcons.Wifi, stringResource(Res.string.wifi_only), stringResource(Res.string.wifi_only_text), s.wifiOnly) { v ->
                store.update { it.copy(settings = it.settings.copy(wifiOnly = v)) }
            }
            GroupDivider()
            SwitchRow(AppIcons.Warning, stringResource(Res.string.speed_cameras), stringResource(Res.string.speed_cameras_text), s.speedCameras) { v ->
                store.update { it.copy(settings = it.settings.copy(speedCameras = v)) }
            }
            platform?.mediaAccess?.let { media ->
                GroupDivider()
                SwitchRow(AppIcons.Notes, stringResource(Res.string.now_playing_title), stringResource(Res.string.now_playing_text), s.nowPlayingBubble) { enabled ->
                    if (enabled) showMediaDisclosure = true
                    else store.update { it.copy(settings = it.settings.copy(nowPlayingBubble = false)) }
                }
                if (s.nowPlayingBubble && !media.granted.value) {
                    Text(
                        stringResource(Res.string.now_playing_access_hint),
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        SectionLabel(stringResource(Res.string.photo_spots_title))
        RowGroup {
            SwitchRow(null, stringResource(Res.string.photo_spots_map_toggle), stringResource(Res.string.photo_spots_map_help), s.photoSpots) { enabled ->
                store.update { it.copy(settings = it.settings.copy(photoSpots = enabled)) }
            }
            GroupDivider()
            NavRow(AppIcons.Map, stringResource(Res.string.photo_spots_manage), stringResource(Res.string.photo_spots_count, store.data.photoSpots.size), onClick = { rides.open(Dest.PhotoSpots) })
        }
        SectionLabel(stringResource(Res.string.settings_app))
        RowGroup {
            if (platform?.backgroundUpdateChecksAvailable == true) {
                SwitchRow(
                    AppIcons.Refresh,
                    stringResource(Res.string.update_background_title),
                    stringResource(Res.string.update_background_text),
                    s.backgroundUpdateChecks,
                ) { enabled -> store.update { it.copy(settings = it.settings.copy(backgroundUpdateChecks = enabled)) } }
                GroupDivider()
            }
            NavRow(
                AppIcons.Info,
                stringResource(Res.string.info_title),
                platform?.let { stringResource(Res.string.info_version, it.versionName) },
                onClick = { rides.open(Dest.Info) },
            )
        }
    }
    if (showMediaDisclosure) {
        val media = platform?.mediaAccess
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showMediaDisclosure = false },
            title = { Text(stringResource(Res.string.now_playing_privacy_title)) },
            text = { Text(stringResource(Res.string.now_playing_privacy_text)) },
            confirmButton = {
                Button(
                    onClick = {
                        showMediaDisclosure = false
                        store.update { it.copy(settings = it.settings.copy(nowPlayingBubble = true)) }
                        if (media?.granted?.value != true) media?.openSettings?.invoke()
                    },
                    modifier = Modifier.heightIn(min = Touch.Min),
                    shape = Radius.Control,
                ) { Text(stringResource(Res.string.now_playing_privacy_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { showMediaDisclosure = false }, modifier = Modifier.heightIn(min = Touch.Min)) {
                    Text(stringResource(Res.string.route_cancel))
                }
            },
        )
    }
}

/** Language of the app: the phone's, or one of the five. */
@Composable
fun LanguagePage(rides: RideScreenState, platform: PlatformActions?) {
    val lang = platform?.language
    var current by remember { mutableStateOf(lang?.current()) }
    SubPage(stringResource(Res.string.language_title), onBack = { rides.back() }) {
        RowGroup {
            val options = listOf<Pair<String?, String>>(null to stringResource(Res.string.language_phone)) + Languages
            options.forEachIndexed { i, (tag, label) ->
                if (i > 0) GroupDivider()
                val selected = if (tag == null) current == null else current?.startsWith(tag) == true
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .selectable(selected = selected, role = Role.RadioButton) { current = tag; lang?.set(tag) }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(end = 16.dp))
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        Text(stringResource(Res.string.language_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 167 MB, 21.4 GB: one decimal only above 10 GB is noise. */
internal fun sizeText(bytes: Long): String {
    val mb = bytes / 1_048_576.0
    return when {
        mb < 1 -> "< 1 MB"
        mb < 1024 -> "${mb.roundToInt()} MB"
        else -> "${(mb / 1024 * 10).roundToInt() / 10.0} GB"
    }
}

/** Road data on the phone (#21): how much, and delete what you do not need. */
@Composable
fun MapDataPage(rides: RideScreenState, platform: PlatformActions?) {
    val md = platform?.mapData
    var version by remember { mutableIntStateOf(0) }
    val items = remember(version) { md?.list().orEmpty() }
    val free = remember(version) { md?.freeBytes() ?: 0L }
    var confirm by remember { mutableStateOf<MapDataItem?>(null) }
    SubPage(stringResource(Res.string.mapdata_title), onBack = { rides.back() }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(sizeText(items.sumOf { it.bytes }), stringResource(Res.string.mapdata_used), Modifier.weight(1f))
            StatTile(sizeText(free), stringResource(Res.string.mapdata_free), Modifier.weight(1f))
        }
        if (items.isEmpty()) {
            EmptyState(stringResource(Res.string.mapdata_none), stringResource(Res.string.mapdata_none_text))
        } else {
            SectionLabel(stringResource(Res.string.mapdata_list))
            RowGroup {
                items.forEachIndexed { i, item ->
                    if (i > 0) GroupDivider()
                    NavRow(
                        AppIcons.Map,
                        item.rideMap?.let { rideMapLabel(it) } ?: if (item.isCells) io.github.nanunic9379.rideplan.route.cellsLabel(item.area) else regionLabel(item.id),
                        sizeText(item.bytes),
                        onClick = null,
                        end = {
                            IconButton(onClick = { confirm = item }, modifier = Modifier.size(Touch.Min)) {
                                Icon(AppIcons.Delete, contentDescription = stringResource(Res.string.zones_delete))
                            }
                        },
                    )
                }
            }
        }
        Text(stringResource(Res.string.mapdata_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    confirm?.let { item ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirm = null },
            icon = { Icon(AppIcons.Delete, contentDescription = null) },
            title = { Text(stringResource(Res.string.mapdata_delete_title)) },
            text = { Text(stringResource(Res.string.mapdata_note)) },
            confirmButton = {
                Button(onClick = { md?.delete(item); confirm = null; version++ }, modifier = Modifier.heightIn(min = Touch.Min), shape = Radius.Control) {
                    Text(stringResource(Res.string.zones_delete))
                }
            },
            dismissButton = { TextButton(onClick = { confirm = null }, modifier = Modifier.heightIn(min = Touch.Min)) { Text(stringResource(Res.string.route_cancel)) } },
        )
    }
}

/** A region id ("italy-nord-ovest") read as words. */
@Composable
private fun regionLabel(id: String): String = io.github.nanunic9379.rideplan.route.regionName(id)

@Composable
private fun rideMapLabel(name: String): String =
    stringResource(Res.string.mapdata_ride_map, name.ifEmpty { stringResource(Res.string.ride_default_name) })

/** Info & updates (#21, #30): version, the update card even after closing the banner, links. */
@Composable
fun InfoPage(store: ProfileStore, rides: RideScreenState, platform: PlatformActions?) {
    val s = store.data.settings
    SubPage(stringResource(Res.string.info_title), onBack = { rides.back() }) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(72.dp).padding(4.dp),
                contentAlignment = Alignment.Center,
            ) {
                RowIcon(AppIcons.Route)
            }
            Text("RidePlan", style = MaterialTheme.typography.headlineSmall)
            platform?.let {
                Text(stringResource(Res.string.info_version, it.versionName), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        platform?.updates?.let { up ->
            RowGroup {
                val (title, sub) = when (up.status) {
                    UpdateCheck.IDLE -> stringResource(Res.string.update_check_now) to null
                    UpdateCheck.CHECKING -> stringResource(Res.string.update_checking) to null
                    UpdateCheck.LATEST -> stringResource(Res.string.update_latest) to stringResource(Res.string.update_check_now)
                    UpdateCheck.AVAILABLE -> stringResource(Res.string.update_available_title, up.available.orEmpty()) to stringResource(Res.string.update_available_text)
                    UpdateCheck.FAILED -> stringResource(Res.string.update_err_network) to stringResource(Res.string.update_retry)
                }
                NavRow(AppIcons.Refresh, title, sub, onClick = { up.check() }.takeIf { up.status != UpdateCheck.CHECKING })
            }
            RowGroup {
                SwitchRow(AppIcons.Refresh, stringResource(Res.string.update_check_start), stringResource(Res.string.update_check_text), s.checkUpdates) { v ->
                    store.update { it.copy(settings = it.settings.copy(checkUpdates = v)) }
                }
            }
        }
        val open = platform?.openUrl
        RowGroup {
            NavRow(AppIcons.Code, stringResource(Res.string.info_source), stringResource(Res.string.info_source_text), onClick = open?.let { { it(Links.SOURCE) } })
            GroupDivider()
            NavRow(AppIcons.Document, stringResource(Res.string.info_licences), stringResource(Res.string.info_licences_text), onClick = { rides.open(Dest.Licences) })
            GroupDivider()
            NavRow(AppIcons.Shield, stringResource(Res.string.info_privacy), onClick = open?.let { { it(Links.PRIVACY) } })
            GroupDivider()
            NavRow(AppIcons.Coffee, stringResource(Res.string.info_coffee), stringResource(Res.string.info_coffee_text), onClick = open?.let { { it(Links.COFFEE) } })
        }
    }
}
