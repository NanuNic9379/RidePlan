package io.github.nanunic9379.rideplan.nav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.nanunic9379.rideplan.resources.Res
import io.github.nanunic9379.rideplan.resources.nav_end_confirm
import io.github.nanunic9379.rideplan.resources.nav_end_title
import io.github.nanunic9379.rideplan.resources.nav_keep_going
import io.github.nanunic9379.rideplan.ui.Radius
import org.jetbrains.compose.resources.stringResource

/**
 * "End navigation?" (#66): a wet glove on Exit or an edge swipe must not end guidance in the
 * middle of a pass. Two full-width 56 dp buttons, "Keep navigating" on top, far from the finger
 * that just touched Exit at the bottom; tapping outside or Back keeps navigating.
 */
@Composable
internal fun EndNavigationDialog(onEnd: () -> Unit, onKeep: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeep,
        title = { Text(stringResource(Res.string.nav_end_title)) },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onKeep, Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = Radius.Control) {
                    Text(stringResource(Res.string.nav_keep_going), style = MaterialTheme.typography.titleMedium)
                }
                OutlinedButton(
                    onClick = onEnd,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    shape = Radius.Control,
                ) {
                    Text(stringResource(Res.string.nav_end_confirm), style = MaterialTheme.typography.titleMedium)
                }
            }
        },
    )
}
