package io.github.nanunic9379.rideplan.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.nanunic9379.rideplan.resources.Res
import io.github.nanunic9379.rideplan.resources.update_action
import io.github.nanunic9379.rideplan.resources.update_available_text
import io.github.nanunic9379.rideplan.resources.update_available_title
import io.github.nanunic9379.rideplan.resources.update_downloading
import io.github.nanunic9379.rideplan.resources.update_err_aborted
import io.github.nanunic9379.rideplan.resources.update_err_checksum
import io.github.nanunic9379.rideplan.resources.update_err_download
import io.github.nanunic9379.rideplan.resources.update_err_network
import io.github.nanunic9379.rideplan.resources.update_err_other
import io.github.nanunic9379.rideplan.resources.update_err_signature
import io.github.nanunic9379.rideplan.resources.update_failed_title
import io.github.nanunic9379.rideplan.resources.update_installing_text
import io.github.nanunic9379.rideplan.resources.update_installing_title
import io.github.nanunic9379.rideplan.resources.update_later
import io.github.nanunic9379.rideplan.resources.update_open_settings
import io.github.nanunic9379.rideplan.resources.update_permission_text
import io.github.nanunic9379.rideplan.resources.update_permission_title
import io.github.nanunic9379.rideplan.resources.update_retry
import io.github.nanunic9379.rideplan.ui.Radius
import org.jetbrains.compose.resources.stringResource

/** Banner at the top over the map. Non-blocking: "Later" closes it until the next launch. */
@Composable
fun UpdateBanner(
    state: UpdateUi,
    onUpdate: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier.safeDrawingPadding().padding(12.dp).fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (state) {
                is UpdateUi.Available -> {
                    Text(stringResource(Res.string.update_available_title, state.versionName), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(Res.string.update_available_text))
                    Actions(primary = stringResource(Res.string.update_action), onPrimary = onUpdate, onDismiss = onDismiss)
                }
                is UpdateUi.Downloading -> {
                    Text(stringResource(Res.string.update_downloading), style = MaterialTheme.typography.titleMedium)
                    val p = state.percent
                    if (p == null) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(progress = { p / 100f }, modifier = Modifier.fillMaxWidth())
                    }
                }
                UpdateUi.NeedsPermission -> {
                    Text(stringResource(Res.string.update_permission_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(Res.string.update_permission_text))
                    Actions(primary = stringResource(Res.string.update_open_settings), onPrimary = onOpenSettings, onDismiss = onDismiss)
                }
                UpdateUi.Installing -> {
                    Text(stringResource(Res.string.update_installing_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(Res.string.update_installing_text))
                }
                is UpdateUi.Failed -> {
                    Text(stringResource(Res.string.update_failed_title), style = MaterialTheme.typography.titleMedium)
                    Text(failureText(state))
                    Actions(primary = stringResource(Res.string.update_retry), onPrimary = onUpdate, onDismiss = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun Actions(primary: String, onPrimary: () -> Unit, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onDismiss) { Text(stringResource(Res.string.update_later)) }
        Button(onClick = onPrimary, shape = Radius.Control) { Text(primary) }
    }
}

@Composable
private fun failureText(state: UpdateUi.Failed): String {
    val text = stringResource(
        when (state.reason) {
            UpdateError.NETWORK -> Res.string.update_err_network
            UpdateError.DOWNLOAD_HTTP -> Res.string.update_err_download
            UpdateError.CHECKSUM -> Res.string.update_err_checksum
            UpdateError.ABORTED -> Res.string.update_err_aborted
            UpdateError.SIGNATURE_CONFLICT -> Res.string.update_err_signature
            UpdateError.OTHER -> Res.string.update_err_other
        },
    )
    return if (state.detail != null && state.reason != UpdateError.CHECKSUM) "$text (${state.detail})" else text
}
