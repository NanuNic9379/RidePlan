package io.github.nanunic9379.rideplan.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.nanunic9379.rideplan.personal.GroupDivider
import io.github.nanunic9379.rideplan.personal.NavRow
import io.github.nanunic9379.rideplan.personal.NoteCard
import io.github.nanunic9379.rideplan.personal.RowGroup
import io.github.nanunic9379.rideplan.personal.SectionLabel
import io.github.nanunic9379.rideplan.personal.SubPage
import io.github.nanunic9379.rideplan.personal.SwitchRow
import io.github.nanunic9379.rideplan.personal.dateText
import io.github.nanunic9379.rideplan.profile.ProfileStore
import io.github.nanunic9379.rideplan.profile.SavedRide
import io.github.nanunic9379.rideplan.resources.Res
import io.github.nanunic9379.rideplan.resources.close
import io.github.nanunic9379.rideplan.resources.nostr_add_relay
import io.github.nanunic9379.rideplan.resources.nostr_check
import io.github.nanunic9379.rideplan.resources.nostr_checking
import io.github.nanunic9379.rideplan.resources.nostr_connect
import io.github.nanunic9379.rideplan.resources.nostr_disconnect
import io.github.nanunic9379.rideplan.resources.nostr_enable
import io.github.nanunic9379.rideplan.resources.nostr_enable_text
import io.github.nanunic9379.rideplan.resources.nostr_err_bad_signature
import io.github.nanunic9379.rideplan.resources.nostr_err_cancelled
import io.github.nanunic9379.rideplan.resources.nostr_err_no_relays
import io.github.nanunic9379.rideplan.resources.nostr_err_no_signer
import io.github.nanunic9379.rideplan.resources.nostr_err_nobody
import io.github.nanunic9379.rideplan.resources.nostr_err_nothing
import io.github.nanunic9379.rideplan.resources.nostr_err_too_big
import io.github.nanunic9379.rideplan.resources.nostr_follow
import io.github.nanunic9379.rideplan.resources.nostr_follow_hint
import io.github.nanunic9379.rideplan.resources.nostr_follow_title
import io.github.nanunic9379.rideplan.resources.nostr_followed_from
import io.github.nanunic9379.rideplan.resources.nostr_get_signer
import io.github.nanunic9379.rideplan.resources.nostr_intro
import io.github.nanunic9379.rideplan.resources.nostr_no_answer
import io.github.nanunic9379.rideplan.resources.nostr_not_found
import io.github.nanunic9379.rideplan.resources.nostr_bad_address
import io.github.nanunic9379.rideplan.resources.nostr_open_ride
import io.github.nanunic9379.rideplan.resources.nostr_public_title
import io.github.nanunic9379.rideplan.resources.nostr_public_warning
import io.github.nanunic9379.rideplan.resources.nostr_publish
import io.github.nanunic9379.rideplan.resources.nostr_publish_again
import io.github.nanunic9379.rideplan.resources.nostr_published
import io.github.nanunic9379.rideplan.resources.nostr_published_on
import io.github.nanunic9379.rideplan.resources.nostr_publishing
import io.github.nanunic9379.rideplan.resources.nostr_relay_invalid
import io.github.nanunic9379.rideplan.resources.nostr_relay_remove
import io.github.nanunic9379.rideplan.resources.nostr_relays
import io.github.nanunic9379.rideplan.resources.nostr_relays_note
import io.github.nanunic9379.rideplan.resources.nostr_relays_reset
import io.github.nanunic9379.rideplan.resources.nostr_section
import io.github.nanunic9379.rideplan.resources.nostr_settings_on
import io.github.nanunic9379.rideplan.resources.nostr_settings_sub
import io.github.nanunic9379.rideplan.resources.nostr_share_address
import io.github.nanunic9379.rideplan.resources.nostr_signer
import io.github.nanunic9379.rideplan.resources.nostr_signer_none
import io.github.nanunic9379.rideplan.resources.nostr_signer_text
import io.github.nanunic9379.rideplan.resources.nostr_summary
import io.github.nanunic9379.rideplan.resources.nostr_title
import io.github.nanunic9379.rideplan.resources.nostr_up_to_date
import io.github.nanunic9379.rideplan.resources.nostr_updated
import io.github.nanunic9379.rideplan.resources.route_cancel
import io.github.nanunic9379.rideplan.resources.share_trim_ends
import io.github.nanunic9379.rideplan.ride.Dest
import io.github.nanunic9379.rideplan.ride.HideZonesOption
import io.github.nanunic9379.rideplan.ride.RideScreenState
import io.github.nanunic9379.rideplan.ride.ShareOption
import io.github.nanunic9379.rideplan.ride.SharePreview
import io.github.nanunic9379.rideplan.ride.SharePreviewText
import io.github.nanunic9379.rideplan.route.RouteOptions
import io.github.nanunic9379.rideplan.ui.AppIcons
import io.github.nanunic9379.rideplan.ui.Radius
import io.github.nanunic9379.rideplan.ui.Touch
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Where to get a signer app: Amber, open source, also on F-Droid (zap.store / Obtainium). */
const val SIGNER_URL = "https://github.com/greenart7c3/Amber"

/** The row of Settings that opens [NostrSettingsPage]; it says whether sharing is on. */
@Composable
fun NostrSettingsRow(nostr: NostrService, rides: RideScreenState) {
    RowGroup {
        NavRow(
            AppIcons.Explore,
            stringResource(Res.string.nostr_title),
            stringResource(if (nostr.data.enabled) Res.string.nostr_settings_on else Res.string.nostr_settings_sub),
            onClick = { rides.open(Dest.Nostr) },
        )
    }
}

/**
 * Settings of public sharing on Nostr (#3): the switch (off by default), the signer app that
 * holds the key, the relays, and following a ride from its `naddr` address.
 */
@Composable
fun NostrSettingsPage(nostr: NostrService, rides: RideScreenState, openUrl: (String) -> Unit) {
    val data = nostr.data
    val scope = rememberCoroutineScope()
    SubPage(stringResource(Res.string.nostr_title), onBack = { rides.back() }) {
        Text(stringResource(Res.string.nostr_intro), style = MaterialTheme.typography.bodyMedium)
        RowGroup {
            SwitchRow(AppIcons.Explore, stringResource(Res.string.nostr_enable), stringResource(Res.string.nostr_enable_text), data.enabled) { nostr.setEnabled(it) }
        }
        NoteCard(
            AppIcons.Warning,
            stringResource(Res.string.nostr_public_warning),
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            title = stringResource(Res.string.nostr_public_title),
        )

        SectionLabel(stringResource(Res.string.nostr_signer))
        val signer = data.signer
        val installed = remember { nostr.signer?.installed() == true }
        RowGroup {
            when {
                signer != null -> NavRow(
                    AppIcons.Lock,
                    shortKey(signer.npub),
                    signer.packageName,
                    onClick = null,
                    end = { TextButton(onClick = { nostr.disconnectSigner() }, modifier = Modifier.heightIn(min = Touch.Min)) { Text(stringResource(Res.string.nostr_disconnect)) } },
                )
                installed -> NavRow(AppIcons.Lock, stringResource(Res.string.nostr_connect), stringResource(Res.string.nostr_signer_text), onClick = { scope.launch { nostr.connectSigner() } })
                else -> NavRow(AppIcons.Download, stringResource(Res.string.nostr_get_signer), stringResource(Res.string.nostr_signer_none), onClick = { openUrl(SIGNER_URL) })
            }
        }

        SectionLabel(stringResource(Res.string.nostr_relays))
        Text(stringResource(Res.string.nostr_relays_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        RowGroup {
            data.relays.forEachIndexed { i, url ->
                if (i > 0) GroupDivider()
                NavRow(
                    AppIcons.Storage,
                    url.removePrefix("wss://"),
                    onClick = null,
                    end = {
                        IconButton(onClick = { nostr.removeRelay(url) }, modifier = Modifier.size(Touch.Min)) {
                            Icon(AppIcons.Close, contentDescription = stringResource(Res.string.nostr_relay_remove, url))
                        }
                    },
                )
            }
        }
        var relayText by remember { mutableStateOf("") }
        var relayError by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = relayText,
                onValueChange = { relayText = it; relayError = false },
                label = { Text(stringResource(Res.string.nostr_add_relay)) },
                placeholder = { Text("wss://") },
                singleLine = true,
                isError = relayError,
                supportingText = if (relayError) ({ Text(stringResource(Res.string.nostr_relay_invalid)) }) else null,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { if (nostr.addRelay(relayText)) relayText = "" else relayError = true },
                enabled = relayText.isNotBlank(),
                modifier = Modifier.size(Touch.Glove),
            ) { Icon(AppIcons.Add, contentDescription = stringResource(Res.string.nostr_add_relay)) }
        }
        TextButton(onClick = { nostr.resetRelays() }, modifier = Modifier.heightIn(min = Touch.Min)) { Text(stringResource(Res.string.nostr_relays_reset)) }

        SectionLabel(stringResource(Res.string.nostr_follow_title))
        FollowBox(nostr, rides, enabled = data.enabled)
    }
}

/** Paste an `naddr`, fetch the ride, open it. */
@Composable
private fun FollowBox(nostr: NostrService, rides: RideScreenState, enabled: Boolean) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<FollowOutcome?>(null) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; outcome = null },
        label = { Text("naddr1…") },
        supportingText = { Text(stringResource(Res.string.nostr_follow_hint)) },
        singleLine = true,
        enabled = enabled && !busy,
        modifier = Modifier.fillMaxWidth(),
    )
    WideButton(
        if (busy) stringResource(Res.string.nostr_checking) else stringResource(Res.string.nostr_follow),
        AppIcons.Download,
        enabled = enabled && !busy && text.isNotBlank(),
        busy = busy,
    ) {
        busy = true
        scope.launch {
            outcome = nostr.follow(text)
            busy = false
        }
    }
    outcome?.let { o ->
        Text(outcomeText(o), style = MaterialTheme.typography.bodyMedium)
        val id = (o as? FollowOutcome.Updated)?.savedId ?: (o as? FollowOutcome.UpToDate)?.savedId
        if (id != null) {
            OutlinedButton(onClick = { rides.open(Dest.RideDetail(id)) }, modifier = Modifier.fillMaxWidth().heightIn(min = Touch.Glove), shape = Radius.Control) {
                Text(stringResource(Res.string.nostr_open_ride))
            }
        }
    }
}

@Composable
private fun outcomeText(o: FollowOutcome): String = when (o) {
    is FollowOutcome.Updated -> stringResource(Res.string.nostr_updated, o.version)
    is FollowOutcome.UpToDate -> stringResource(Res.string.nostr_up_to_date, o.version)
    FollowOutcome.NoAnswer -> stringResource(Res.string.nostr_no_answer)
    FollowOutcome.NotFound -> stringResource(Res.string.nostr_not_found)
    FollowOutcome.BadAddress -> stringResource(Res.string.nostr_bad_address)
}

/**
 * Nostr on the page of a ride: publish it (or a new version of it), share its address, and,
 * for a ride followed from Nostr, look for a newer version. Shown only when Nostr is on.
 */
@Composable
fun NostrRideSection(nostr: NostrService, store: ProfileStore, saved: SavedRide, options: RouteOptions?, shareText: ((String) -> Unit)?) {
    val data = nostr.data
    if (!data.enabled) return
    val scope = rememberCoroutineScope()
    val followed = data.followedFor(saved.id)
    val published = data.publishedFor(saved.id)
    var publishing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var publishOutcome by remember(saved.id) { mutableStateOf<PublishOutcome?>(null) }
    var checkOutcome by remember(saved.id) { mutableStateOf<FollowOutcome?>(null) }

    SectionLabel(stringResource(Res.string.nostr_section))
    if (followed != null) {
        // Opening a followed ride looks for a newer version once; with no answer the last one stays.
        LaunchedEffect(saved.id) {
            busy = true
            checkOutcome = nostr.checkUpdate(saved.id)
            busy = false
        }
        val checked = followed.checkedAt.takeIf { it > 0 }?.let { dateText(it) }
        Text(stringResource(Res.string.nostr_followed_from, shortKey(Nip19.npub(followed.pubkey)), followed.lastVersion, checked ?: "–"), style = MaterialTheme.typography.bodyMedium)
        checkOutcome?.let { Text(outcomeText(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        WideButton(
            if (busy) stringResource(Res.string.nostr_checking) else stringResource(Res.string.nostr_check),
            AppIcons.Refresh,
            enabled = !busy,
            busy = busy,
            outlined = true,
        ) {
            busy = true
            scope.launch {
                checkOutcome = nostr.checkUpdate(saved.id)
                busy = false
            }
        }
    }
    if (followed == null || followed.pubkey == data.signer?.pubkey) {
        if (published != null) {
            Text(stringResource(Res.string.nostr_published_on, published.versionNumber, dateText(published.createdAt * 1000), published.relays.size), style = MaterialTheme.typography.bodyMedium)
        }
        WideButton(
            stringResource(if (published != null) Res.string.nostr_publish_again else Res.string.nostr_publish),
            AppIcons.Upload,
            enabled = !busy,
        ) { publishing = true; publishOutcome = null }
        if (published != null && shareText != null) {
            val naddr = remember(published) { Nip19.naddr(published.address.copy(relays = published.relays.take(2))) }
            WideButton(stringResource(Res.string.nostr_share_address), AppIcons.Share, outlined = true) { shareText("nostr:$naddr") }
        }
    }
    when (val o = publishOutcome) {
        is PublishOutcome.Done -> Text(stringResource(Res.string.nostr_published, o.accepted, o.total), style = MaterialTheme.typography.bodyMedium)
        is PublishOutcome.Failed -> Text(stringResource(errorText(o.error)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        null -> {}
    }

    if (publishing) {
        PublishDialog(
            saved = saved,
            store = store,
            nostr = nostr,
            busy = busy,
            onDismiss = { if (!busy) publishing = false },
        ) { preview, summary ->
            busy = true
            scope.launch {
                publishOutcome = nostr.publish(saved.id, preview, options, summary)
                busy = false
                publishing = false
            }
        }
    }
}

/**
 * Before publishing: what becomes public, the same privacy options as every other way of
 * sharing (#6, #12), an optional line for strangers, the relays it goes to.
 */
@Composable
private fun PublishDialog(
    saved: SavedRide,
    store: ProfileStore,
    nostr: NostrService,
    busy: Boolean,
    onDismiss: () -> Unit,
    onPublish: (SharePreview, String) -> Unit,
) {
    val zones = store.data.privateZones
    val ride = remember(saved) { saved.toRide() }
    var trim by remember { mutableStateOf(true) }
    var hideZones by remember { mutableStateOf(true) }
    var summary by remember { mutableStateOf("") }
    val preview = remember(ride, zones, trim, hideZones) { SharePreview.of(ride, zones, trim, hideZones) }
    val signer = nostr.data.signer
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(AppIcons.Upload, contentDescription = null) },
        title = { Text(stringResource(Res.string.nostr_publish)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.nostr_public_warning), style = MaterialTheme.typography.bodyMedium)
                HideZonesOption(zones, hideZones) { hideZones = it }
                ShareOption(stringResource(Res.string.share_trim_ends), trim) { trim = it }
                SharePreviewText(preview)
                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it.take(RideEvent.MAX_SUMMARY) },
                    label = { Text(stringResource(Res.string.nostr_summary)) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(nostr.data.relays.joinToString(", ") { it.removePrefix("wss://") }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (signer == null) Text(stringResource(Res.string.nostr_err_no_signer), color = MaterialTheme.colorScheme.error)
                WideButton(
                    if (busy) stringResource(Res.string.nostr_publishing) else stringResource(Res.string.nostr_publish),
                    AppIcons.Upload,
                    enabled = !busy && !preview.empty && signer != null && nostr.data.relays.isNotEmpty(),
                    busy = busy,
                ) { onPublish(preview, summary) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(if (busy) Res.string.close else Res.string.route_cancel)) } },
    )
}

private fun errorText(e: PublishError): StringResource = when (e) {
    PublishError.DISABLED -> Res.string.photo_spots_nostr_disabled
    PublishError.NO_SIGNER -> Res.string.nostr_err_no_signer
    PublishError.CANCELLED -> Res.string.nostr_err_cancelled
    PublishError.BAD_SIGNATURE -> Res.string.nostr_err_bad_signature
    PublishError.NOTHING_TO_SHARE -> Res.string.nostr_err_nothing
    PublishError.TOO_BIG -> Res.string.nostr_err_too_big
    PublishError.NO_RELAYS -> Res.string.nostr_err_no_relays
    PublishError.NOBODY_ACCEPTED -> Res.string.nostr_err_nobody
}

/** `npub1abcd…wxyz`: enough to recognize a key. */
internal fun shortKey(npub: String): String = if (npub.length <= 20) npub else npub.take(12) + "…" + npub.takeLast(6)

/** Main action: 56 dp for gloves. */
@Composable
private fun WideButton(text: String, icon: ImageVector, enabled: Boolean = true, busy: Boolean = false, outlined: Boolean = false, onClick: () -> Unit) {
    val modifier = Modifier.fillMaxWidth().heightIn(min = Touch.Glove)
    val content: @Composable () -> Unit = {
        if (busy) CircularProgressIndicator(Modifier.size(ButtonDefaults.IconSize), strokeWidth = 2.dp)
        else Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(text)
    }
    if (outlined) OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier, shape = Radius.Control) { content() }
    else Button(onClick = onClick, enabled = enabled, modifier = modifier, shape = Radius.Control) { content() }
}
