package io.github.nanunic9379.rideplan.ride

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.nanunic9379.rideplan.ui.Touch
import io.github.nanunic9379.rideplan.resources.Res
import io.github.nanunic9379.rideplan.resources.share_hide_zones
import io.github.nanunic9379.rideplan.resources.share_no_zones
import io.github.nanunic9379.rideplan.resources.share_preview_empty
import io.github.nanunic9379.rideplan.resources.share_preview_hidden
import io.github.nanunic9379.rideplan.resources.share_preview_moved
import io.github.nanunic9379.rideplan.resources.share_preview_summary
import org.jetbrains.compose.resources.stringResource

/** A checkbox row of the share dialog. */
@Composable
internal fun ShareOption(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Touch.Glove).toggleable(checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, Modifier.padding(start = 8.dp))
    }
}

/** The private zones switch (#6), or a hint to create one in the profile. */
@Composable
internal fun HideZonesOption(zones: List<PrivateZone>, checked: Boolean, onChange: (Boolean) -> Unit) {
    if (zones.isEmpty()) {
        Text(stringResource(Res.string.share_no_zones), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        ShareOption(stringResource(Res.string.share_hide_zones, zones.size), checked, onChange)
    }
}

/** What leaves the phone (#6): length, stops, and how far start and finish moved. */
@Composable
internal fun SharePreviewText(preview: SharePreview) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (preview.empty) {
            Text(stringResource(Res.string.share_preview_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            return@Column
        }
        val style = MaterialTheme.typography.bodySmall
        Text(stringResource(Res.string.share_preview_summary, formatKm(preview.km), preview.ride.stops.size), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(Res.string.share_preview_moved, formatKm(preview.startMovedKm), formatKm(preview.finishMovedKm)), style = style)
        if (preview.hiddenStops > 0) Text(stringResource(Res.string.share_preview_hidden, preview.hiddenStops), style = style)
    }
}
