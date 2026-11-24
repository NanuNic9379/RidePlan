package io.github.nanunic9379.rideplan.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nanunic9379.rideplan.resources.Res
import io.github.nanunic9379.rideplan.resources.route_cancel
import io.github.nanunic9379.rideplan.resources.zones_add
import io.github.nanunic9379.rideplan.resources.zones_candidate
import io.github.nanunic9379.rideplan.resources.zones_candidate_unnamed
import io.github.nanunic9379.rideplan.resources.zones_coords
import io.github.nanunic9379.rideplan.resources.zones_coords_help
import io.github.nanunic9379.rideplan.resources.zones_coords_invalid
import io.github.nanunic9379.rideplan.resources.zones_delete
import io.github.nanunic9379.rideplan.resources.zones_empty
import io.github.nanunic9379.rideplan.resources.zones_name
import io.github.nanunic9379.rideplan.resources.zones_name_default
import io.github.nanunic9379.rideplan.resources.zones_other_place
import io.github.nanunic9379.rideplan.resources.zones_radius
import io.github.nanunic9379.rideplan.resources.zones_save
import io.github.nanunic9379.rideplan.resources.zones_suggested
import io.github.nanunic9379.rideplan.resources.zones_text
import io.github.nanunic9379.rideplan.resources.zones_title
import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.PrivateZone
import io.github.nanunic9379.rideplan.ride.PrivateZones
import io.github.nanunic9379.rideplan.ui.Radius
import org.jetbrains.compose.resources.stringResource

/**
 * Private zones in the profile (#6): the list, delete, and adding one from where your rides
 * start and finish or from coordinates copied from a map app.
 */
@Composable
fun PrivateZonesSection(store: ProfileStore) {
    val zones = store.data.privateZones
    var adding by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.zones_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(Res.string.zones_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (zones.isEmpty()) {
            Text(stringResource(Res.string.zones_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        zones.forEach { z ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    z.name + " · " + stringResource(Res.string.zones_radius, z.radiusM),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = { store.update { it.deleteZone(z.id) } }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(Res.string.zones_delete))
                }
            }
        }
        FilledTonalButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = Radius.Control) {
            Text(stringResource(Res.string.zones_add))
        }
    }
    if (adding) {
        val candidates = remember(store.data) { PrivateZones.candidates(store.data.rides, zones).take(5) }
        AddZoneDialog(candidates, onDismiss = { adding = false }) { place, name, radius ->
            adding = false
            store.update { it.addZone(PrivateZone.around(place, name, radius, ProfileData.newId())) }
        }
    }
}

@Composable
private fun AddZoneDialog(
    candidates: List<PrivateZones.Candidate>,
    onDismiss: () -> Unit,
    onSave: (place: LatLon, name: String, radiusM: Int) -> Unit,
) {
    // Index in candidates, or -1 for coordinates typed by hand.
    var picked by remember { mutableStateOf(if (candidates.isEmpty()) -1 else 0) }
    var coords by remember { mutableStateOf("") }
    val defaultName = stringResource(Res.string.zones_name_default)
    var name by remember { mutableStateOf(defaultName) }
    var radius by remember { mutableStateOf(PrivateZone.DEFAULT_RADIUS_M) }
    val place = if (picked >= 0) candidates.getOrNull(picked)?.point else PrivateZones.parseLatLon(coords)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.zones_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (candidates.isNotEmpty()) {
                    Text(stringResource(Res.string.zones_suggested), style = MaterialTheme.typography.bodyMedium)
                    candidates.forEachIndexed { i, c ->
                        val label = c.rideName?.let { stringResource(Res.string.zones_candidate, it, c.rides) }
                            ?: stringResource(Res.string.zones_candidate_unnamed, c.rides)
                        Choice(label, picked == i) { picked = i }
                    }
                    Choice(stringResource(Res.string.zones_other_place), picked == -1) { picked = -1 }
                }
                if (picked == -1) {
                    OutlinedTextField(
                        value = coords,
                        onValueChange = { coords = it },
                        label = { Text(stringResource(Res.string.zones_coords)) },
                        placeholder = { Text("40.6667, 16.6043") },
                        isError = coords.isNotBlank() && place == null,
                        supportingText = {
                            Text(stringResource(if (coords.isNotBlank() && place == null) Res.string.zones_coords_invalid else Res.string.zones_coords_help))
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30) },
                    label = { Text(stringResource(Res.string.zones_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrivateZone.RADII_M.forEach { r ->
                        FilterChip(selected = radius == r, onClick = { radius = r }, label = { Text(stringResource(Res.string.zones_radius, r)) })
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { place?.let { onSave(it, name.ifBlank { defaultName }, radius) } }, enabled = place != null, shape = Radius.Control) {
                Text(stringResource(Res.string.zones_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.route_cancel)) } },
    )
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, Modifier.padding(start = 8.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
