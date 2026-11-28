package io.github.nanunic9379.rideplan.personal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.nanunic9379.rideplan.profile.PhotoSpot
import io.github.nanunic9379.rideplan.profile.ProfileData
import io.github.nanunic9379.rideplan.profile.ProfileStore
import io.github.nanunic9379.rideplan.resources.Res
import io.github.nanunic9379.rideplan.resources.photo_spots_title
import io.github.nanunic9379.rideplan.resources.photo_spots_help
import io.github.nanunic9379.rideplan.resources.photo_spots_add
import io.github.nanunic9379.rideplan.resources.photo_spots_empty
import io.github.nanunic9379.rideplan.resources.photo_spots_edit
import io.github.nanunic9379.rideplan.resources.photo_spots_open_link
import io.github.nanunic9379.rideplan.resources.photo_spots_publish
import io.github.nanunic9379.rideplan.resources.photo_spots_publish_again
import io.github.nanunic9379.rideplan.resources.photo_spots_publish_help
import io.github.nanunic9379.rideplan.resources.photo_spots_share_address
import io.github.nanunic9379.rideplan.resources.photo_spots_published
import io.github.nanunic9379.rideplan.resources.photo_spots_publish_failed
import io.github.nanunic9379.rideplan.resources.photo_spots_delete
import io.github.nanunic9379.rideplan.resources.photo_spots_delete_confirm
import io.github.nanunic9379.rideplan.resources.route_cancel
import io.github.nanunic9379.rideplan.resources.photo_spots_public_warning
import io.github.nanunic9379.rideplan.resources.photo_spots_public_location
import io.github.nanunic9379.rideplan.resources.photo_spots_name
import io.github.nanunic9379.rideplan.resources.photo_spots_details
import io.github.nanunic9379.rideplan.resources.photo_spots_url
import io.github.nanunic9379.rideplan.resources.photo_spots_publishing
import io.github.nanunic9379.rideplan.resources.photo_spots_follow_title
import io.github.nanunic9379.rideplan.resources.photo_spots_address
import io.github.nanunic9379.rideplan.resources.photo_spots_import
import io.github.nanunic9379.rideplan.resources.photo_spots_imported
import io.github.nanunic9379.rideplan.resources.nostr_no_answer
import io.github.nanunic9379.rideplan.resources.nostr_not_found
import io.github.nanunic9379.rideplan.resources.nostr_bad_address
import io.github.nanunic9379.rideplan.resources.photo_spots_nostr_disabled
import io.github.nanunic9379.rideplan.resources.photo_spots_editor_title
import io.github.nanunic9379.rideplan.resources.photo_spots_url_help
import io.github.nanunic9379.rideplan.resources.stop_edit_done
import io.github.nanunic9379.rideplan.ride.Dest
import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.RideScreenState
import io.github.nanunic9379.rideplan.social.NostrService
import io.github.nanunic9379.rideplan.social.PublishOutcome
import io.github.nanunic9379.rideplan.social.FollowPhotoSpotOutcome
import io.github.nanunic9379.rideplan.ui.AppIcons
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.launch

@Composable
fun PhotoSpotsPage(store: ProfileStore, rides: RideScreenState, openUrl: ((String) -> Unit)?, nostr: NostrService?, shareText: ((String) -> Unit)?) {
    var editing by remember { mutableStateOf<PhotoSpot?>(null) }
    var confirmDelete by remember { mutableStateOf<PhotoSpot?>(null) }
    var publishingSpot by remember { mutableStateOf<PhotoSpot?>(null) }
    var publishing by remember { mutableStateOf(false) }
    var publishOutcome by remember { mutableStateOf<PublishOutcome?>(null) }
    val scope = rememberCoroutineScope()
    SubPage(stringResource(Res.string.photo_spots_title), onBack = { rides.back() }) {
        Text(stringResource(Res.string.photo_spots_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(
            onClick = { rides.stack = emptyList(); rides.page = io.github.nanunic9379.rideplan.ride.Page.MAP; rides.choosingPhotoSpot = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(stringResource(Res.string.photo_spots_add)) }
        if (store.data.photoSpots.isEmpty()) {
            Text(stringResource(Res.string.photo_spots_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        store.data.photoSpots.forEach { spot ->
            RowGroup {
                SwitchRow(AppIcons.PhotoSpotMarker, spot.name, spot.details.ifBlank { spot.photoUrl.orEmpty() }, spot.enabled, preserveIconColors = true) { enabled ->
                    store.update { it.savePhotoSpot(spot.copy(enabled = enabled)) }
                }
                GroupDivider()
                NavRow(null, stringResource(Res.string.photo_spots_edit), "${spot.lat}, ${spot.lon}", onClick = { editing = spot })
                if (spot.photoUrl != null) {
                    GroupDivider()
                    NavRow(AppIcons.Info, stringResource(Res.string.photo_spots_open_link), spot.photoUrl, onClick = { openUrl?.invoke(spot.photoUrl) })
                }
                if (spot.sourceAddress == null && nostr != null && nostr.data.enabled) {
                    GroupDivider()
                    val published = nostr.data.publishedPhotoSpotFor(spot.id)
                    NavRow(AppIcons.Share, stringResource(if (published == null) Res.string.photo_spots_publish else Res.string.photo_spots_publish_again),
                        stringResource(Res.string.photo_spots_publish_help), onClick = { publishingSpot = spot })
                    if (published != null && shareText != null) {
                        GroupDivider()
                        val naddr = remember(published) { io.github.nanunic9379.rideplan.social.Nip19.naddr(published.address.copy(relays = published.relays.take(2))) }
                        NavRow(AppIcons.Share, stringResource(Res.string.photo_spots_share_address), naddr, onClick = { shareText("nostr:$naddr") })
                    }
                } else if (spot.sourceAddress != null && shareText != null) {
                    GroupDivider()
                    NavRow(AppIcons.Share, stringResource(Res.string.photo_spots_share_address), onClick = { shareText("nostr:${spot.sourceAddress}") })
                }
                GroupDivider()
                NavRow(AppIcons.Delete, stringResource(Res.string.photo_spots_delete), onClick = { confirmDelete = spot })
            }
        }
        publishOutcome?.let { result ->
            Text(
                stringResource(if (result is PublishOutcome.Done) Res.string.photo_spots_published else Res.string.photo_spots_publish_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (nostr != null && nostr.data.enabled) FollowPhotoSpotBox(nostr)
    }
    editing?.let { spot -> PhotoSpotEditorDialog(
        initialName = spot.name,
        initialDetails = spot.details,
        initialUrl = spot.photoUrl.orEmpty(),
        onDismiss = { editing = null },
        onSave = { name, details, url ->
            store.update { it.savePhotoSpot(spot.copy(name = name, details = details, photoUrl = url.trim().ifEmpty { null })) }
            editing = null
        },
    ) }
    confirmDelete?.let { spot ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(Res.string.photo_spots_delete)) },
            text = { Text(stringResource(Res.string.photo_spots_delete_confirm, spot.name)) },
            confirmButton = { Button(onClick = { store.update { it.deletePhotoSpot(spot.id) }; confirmDelete = null }) { Text(stringResource(Res.string.photo_spots_delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(Res.string.route_cancel)) } },
        )
    }
    publishingSpot?.let { spot ->
        PhotoSpotPublishDialog(
            spot = spot,
            busy = publishing,
            onDismiss = { if (!publishing) publishingSpot = null },
            onPublish = { selected ->
                store.update { it.savePhotoSpot(selected) }
                publishing = true
                scope.launch {
                    val result = nostr?.publishPhotoSpot(selected)
                    publishOutcome = result
                    if (result is PublishOutcome.Done && shareText != null) shareText("nostr:${result.naddr}")
                    publishing = false
                    publishingSpot = null
                }
            },
        )
    }
}

@Composable
private fun PhotoSpotPublishDialog(spot: PhotoSpot, busy: Boolean, onDismiss: () -> Unit, onPublish: (PhotoSpot) -> Unit) {
    var name by remember(spot.id) { mutableStateOf(spot.shareName) }
    var details by remember(spot.id) { mutableStateOf(spot.shareDetails) }
    var link by remember(spot.id) { mutableStateOf(spot.sharePhotoUrl) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(Res.string.photo_spots_publish)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(Res.string.photo_spots_public_warning), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(Res.string.photo_spots_public_location, spot.lat, spot.lon), style = MaterialTheme.typography.bodySmall)
                PhotoShareChoice(stringResource(Res.string.photo_spots_name), name, spot.name.isNotBlank()) { name = it }
                PhotoShareChoice(stringResource(Res.string.photo_spots_details), details, spot.details.isNotBlank()) { details = it }
                PhotoShareChoice(stringResource(Res.string.photo_spots_url), link, spot.photoUrl != null) { link = it }
            }
        },
        confirmButton = { Button(onClick = { onPublish(spot.copy(shareName = name, shareDetails = details, sharePhotoUrl = link)) }, enabled = !busy && (name || details || (link && spot.photoUrl != null))) { Text(stringResource(if (busy) Res.string.photo_spots_publishing else Res.string.photo_spots_publish)) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(Res.string.route_cancel)) } },
    )
}

@Composable
private fun PhotoShareChoice(label: String, checked: Boolean, enabled: Boolean, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked && enabled, onCheckedChange = onChecked, enabled = enabled)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FollowPhotoSpotBox(nostr: NostrService) {
    var address by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<FollowPhotoSpotOutcome?>(null) }
    val scope = rememberCoroutineScope()
    Text(stringResource(Res.string.photo_spots_follow_title), style = MaterialTheme.typography.titleSmall)
    OutlinedTextField(address, { address = it; outcome = null }, label = { Text(stringResource(Res.string.photo_spots_address)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Button(onClick = {
        busy = true
        scope.launch {
            val result = nostr.followPhotoSpot(address)
            outcome = result
            if (result is FollowPhotoSpotOutcome.Imported) address = ""
            busy = false
        }
    }, enabled = !busy && address.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.photo_spots_import)) }
    outcome?.let { result ->
        val message = when (result) {
            is FollowPhotoSpotOutcome.Imported -> stringResource(Res.string.photo_spots_imported, result.spot.name)
            FollowPhotoSpotOutcome.NoAnswer -> stringResource(Res.string.nostr_no_answer)
            FollowPhotoSpotOutcome.NotFound -> stringResource(Res.string.nostr_not_found)
            FollowPhotoSpotOutcome.BadAddress -> stringResource(Res.string.nostr_bad_address)
            FollowPhotoSpotOutcome.Disabled -> stringResource(Res.string.photo_spots_nostr_disabled)
        }
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun PhotoSpotEditorDialog(
    initialName: String,
    initialDetails: String,
    initialUrl: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var details by remember(initialDetails) { mutableStateOf(initialDetails) }
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    val validUrl = url.isBlank() || (url.startsWith("https://") && url.length in 9..2048 && url.none { it <= ' ' || it == '\u007F' })
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.photo_spots_editor_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(Res.string.photo_spots_name)) }, singleLine = true)
                OutlinedTextField(details, { details = it }, label = { Text(stringResource(Res.string.photo_spots_details)) }, minLines = 2)
                OutlinedTextField(url, { url = it }, label = { Text(stringResource(Res.string.photo_spots_url)) }, singleLine = true, isError = !validUrl)
                Text(stringResource(Res.string.photo_spots_url_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = { onSave(name.trim(), details.trim(), url.trim()) }, enabled = name.isNotBlank() && validUrl) { Text(stringResource(Res.string.stop_edit_done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.route_cancel)) } },
    )
}

/** Shared map-point editor. New points get their coordinates from a deliberate map tap. */
@Composable
fun NewPhotoSpotDialog(point: LatLon, onDismiss: () -> Unit, onSave: (PhotoSpot) -> Unit) {
    PhotoSpotEditorDialog(
        initialName = "", initialDetails = "", initialUrl = "", onDismiss = onDismiss,
        onSave = { name, details, url ->
            onSave(PhotoSpot(ProfileData.newId(), name, details, point.lat, point.lon, url.trim().ifEmpty { null }))
        },
    )
}
