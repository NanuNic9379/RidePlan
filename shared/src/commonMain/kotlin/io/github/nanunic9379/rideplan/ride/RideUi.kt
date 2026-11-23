package io.github.nanunic9379.rideplan.ride

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nanunic9379.rideplan.resources.Res
import io.github.nanunic9379.rideplan.resources.close
import io.github.nanunic9379.rideplan.resources.err_file_too_big
import io.github.nanunic9379.rideplan.resources.err_file_unreadable
import io.github.nanunic9379.rideplan.resources.err_gpx_empty
import io.github.nanunic9379.rideplan.resources.err_invalid_coords
import io.github.nanunic9379.rideplan.resources.err_kml_empty
import io.github.nanunic9379.rideplan.resources.err_link_names_without_coords
import io.github.nanunic9379.rideplan.resources.err_link_no_route
import io.github.nanunic9379.rideplan.resources.err_link_too_many_redirects
import io.github.nanunic9379.rideplan.resources.err_network
import io.github.nanunic9379.rideplan.resources.err_no_stops
import io.github.nanunic9379.rideplan.resources.err_not_google_maps
import io.github.nanunic9379.rideplan.resources.err_single_place
import io.github.nanunic9379.rideplan.resources.err_stop_without_coords
import io.github.nanunic9379.rideplan.resources.err_unexpected
import io.github.nanunic9379.rideplan.resources.err_unknown_format
import io.github.nanunic9379.rideplan.resources.err_zip_without_ride
import io.github.nanunic9379.rideplan.resources.import_another
import io.github.nanunic9379.rideplan.resources.import_button
import io.github.nanunic9379.rideplan.resources.import_loading
import io.github.nanunic9379.rideplan.resources.import_open_file
import io.github.nanunic9379.rideplan.resources.import_paste_link
import io.github.nanunic9379.rideplan.resources.link_dialog_help
import io.github.nanunic9379.rideplan.resources.link_dialog_import
import io.github.nanunic9379.rideplan.resources.link_dialog_label
import io.github.nanunic9379.rideplan.resources.link_dialog_paste
import io.github.nanunic9379.rideplan.resources.link_dialog_title
import io.github.nanunic9379.rideplan.resources.ok
import io.github.nanunic9379.rideplan.resources.ride_default_name
import io.github.nanunic9379.rideplan.resources.ride_km
import io.github.nanunic9379.rideplan.resources.ride_km_straight
import io.github.nanunic9379.rideplan.resources.ride_source_google
import io.github.nanunic9379.rideplan.resources.ride_source_gpx
import io.github.nanunic9379.rideplan.resources.ride_source_kml
import io.github.nanunic9379.rideplan.resources.ride_stops
import io.github.nanunic9379.rideplan.resources.ride_straight_lines_note
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.map.MapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.spatialk.geojson.BoundingBox
import kotlin.math.roundToInt

/** Stato del giro sullo schermo. Lo cambia la piattaforma (file, link) e lo legge [App]. */
@Stable
class RideScreenState {
    var ride by mutableStateOf<Ride?>(null)
    var busy by mutableStateOf(false)
    /** Ultimo errore; il testo tradotto lo sceglie [RidePanel]. */
    var error by mutableStateOf<Throwable?>(null)

    /** Mostra il giro o l'errore. */
    fun show(result: Result<Ride>) {
        busy = false
        result.onSuccess { ride = it; error = null }.onFailure { error = it }
    }
}

/**
 * Modi di importare offerti dalla piattaforma.
 * [readClipboard]: testo negli appunti, per precompilare il campo del link.
 * [importText]: importa un link (o testo che lo contiene) di un percorso di Google Maps.
 */
class ImportActions(
    val openFile: () -> Unit,
    val readClipboard: () -> String?,
    val importText: (String) -> Unit,
)

private val RouteColor = Color(0xFFFF8A1F)
private val RouteCasing = Color(0xFF7A3300)
private val StopFill = Color(0xFFFFFFFF)
private val StopStroke = Color(0xFF1C1B26)

/** Traccia e tappe sulla mappa. */
@Composable
fun RideLayers(ride: Ride) {
    val line = rememberGeoJsonSource(GeoJsonData.JsonString(lineJson(ride.line)))
    val stops = rememberGeoJsonSource(GeoJsonData.JsonString(pointsJson(ride.stops.map { it.point })))
    LineLayer(id = "ride-casing", source = line, color = const(RouteCasing), width = const(7.dp))
    LineLayer(id = "ride-line", source = line, color = const(RouteColor), width = const(4.dp))
    CircleLayer(
        id = "ride-stops",
        source = stops,
        color = const(StopFill),
        radius = const(6.dp),
        strokeColor = const(StopStroke),
        strokeWidth = const(2.5.dp),
    )
}

/**
 * Inquadra tutto il giro, con margine ai lati e spazio in basso per la scheda.
 * Il margine si aggiunge ai limiti invece che in pixel: funziona uguale su ogni schermo.
 */
suspend fun MapState.fitRide(ride: Ride) {
    val (sw, ne) = ride.bounds()
    val dLon = (ne.lon - sw.lon).coerceAtLeast(0.01)
    val dLat = (ne.lat - sw.lat).coerceAtLeast(0.01)
    animateCameraToBounds(
        BoundingBox(
            sw.lon - dLon * 0.15,
            sw.lat - dLat * 0.9,
            ne.lon + dLon * 0.15,
            ne.lat + dLat * 0.25,
        ),
    )
}

/** Pulsante di importazione, scheda del giro, attesa ed errori. */
@Composable
fun BoxScope.RidePanel(state: RideScreenState, actions: ImportActions?) {
    val ride = state.ride
    var linkDialog by remember { mutableStateOf(false) }
    if (linkDialog && actions != null) {
        LinkDialog(actions, onDismiss = { linkDialog = false })
    }
    Column(
        Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(12.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.End,
    ) {
        state.error?.let { e ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(errorText(e))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { state.error = null }) { Text(stringResource(Res.string.ok)) }
                    }
                }
            }
        }
        if (state.busy) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.import_loading))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
        if (ride != null) {
            RideCard(ride, onClose = { state.ride = null }, actions = actions, onLink = { linkDialog = true })
        } else if (actions != null && !state.busy) {
            ImportMenu(actions, onLink = { linkDialog = true }) { open ->
                ExtendedFloatingActionButton(onClick = open) { Text(stringResource(Res.string.import_button)) }
            }
        }
    }
}

/** Menu "file o link" attaccato al pulsante che lo apre. */
@Composable
private fun ImportMenu(actions: ImportActions, onLink: () -> Unit, anchor: @Composable (open: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        anchor { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(Res.string.import_open_file)) }, onClick = { open = false; actions.openFile() })
            DropdownMenuItem(text = { Text(stringResource(Res.string.import_paste_link)) }, onClick = { open = false; onLink() })
        }
    }
}

@Composable
private fun RideCard(ride: Ride, onClose: () -> Unit, actions: ImportActions?, onLink: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                ride.name ?: defaultName(ride) ?: stringResource(Res.string.ride_default_name),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(summary(ride), style = MaterialTheme.typography.bodyMedium)
            val names = ride.stops.mapNotNull { it.name }
            if (names.isNotEmpty()) {
                Text(names.joinToString(" → "), style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (ride.straightLines) {
                Text(
                    stringResource(Res.string.ride_straight_lines_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClose) { Text(stringResource(Res.string.close)) }
                if (actions != null) {
                    ImportMenu(actions, onLink) { open ->
                        Button(onClick = open) { Text(stringResource(Res.string.import_another)) }
                    }
                }
            }
        }
    }
}

/** Campo per incollare il link di un percorso creato con Google Maps. */
@Composable
private fun LinkDialog(actions: ImportActions, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(actions.readClipboard()?.let(RideImport::findUrl) ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.link_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.link_dialog_help), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(Res.string.link_dialog_label)) },
                    placeholder = { Text("https://maps.app.goo.gl/...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(enabled = text.isNotBlank(), onClick = { onDismiss(); actions.importText(text) }) {
                Text(stringResource(Res.string.link_dialog_import))
            }
        },
        dismissButton = {
            TextButton(onClick = { actions.readClipboard()?.let { text = RideImport.findUrl(it) ?: it } }) {
                Text(stringResource(Res.string.link_dialog_paste))
            }
        },
    )
}

/** "Partenza → Arrivo" se le tappe hanno un nome. */
private fun defaultName(ride: Ride): String? {
    val first = ride.stops.firstOrNull()?.name
    val last = ride.stops.lastOrNull()?.name
    return if (first != null && last != null && ride.stops.size >= 2) "$first → $last" else null
}

@Composable
private fun summary(ride: Ride): String {
    val km = formatKm(ride.lengthKm)
    val parts = buildList {
        add(stringResource(if (ride.straightLines) Res.string.ride_km_straight else Res.string.ride_km, km))
        if (ride.stops.isNotEmpty()) add(pluralStringResource(Res.plurals.ride_stops, ride.stops.size, ride.stops.size))
        add(
            stringResource(
                when (ride.source) {
                    Ride.Source.GPX -> Res.string.ride_source_gpx
                    Ride.Source.KML -> Res.string.ride_source_kml
                    Ride.Source.GOOGLE_LINK -> Res.string.ride_source_google
                },
            ),
        )
    }
    return parts.joinToString(" · ")
}

/** Un decimale sotto i 10 km, intero sopra. */
internal fun formatKm(km: Double): String =
    if (km < 10) "${(km * 10).roundToInt() / 10.0}" else "${km.roundToInt()}"

@Composable
private fun errorText(e: Throwable): String {
    if (e !is ImportException) return stringResource(Res.string.err_unexpected, e.message ?: e::class.simpleName ?: "?")
    val text = stringResource(importErrorText(e.error))
    return if (e.detail != null && e.error == ImportError.LINK_NO_ROUTE) "$text (${e.detail})" else text
}

internal fun importErrorText(error: ImportError): StringResource = when (error) {
    ImportError.UNKNOWN_FORMAT -> Res.string.err_unknown_format
    ImportError.GPX_EMPTY -> Res.string.err_gpx_empty
    ImportError.KML_EMPTY -> Res.string.err_kml_empty
    ImportError.NOT_GOOGLE_MAPS -> Res.string.err_not_google_maps
    ImportError.NO_STOPS -> Res.string.err_no_stops
    ImportError.SINGLE_PLACE -> Res.string.err_single_place
    ImportError.STOP_WITHOUT_COORDS -> Res.string.err_stop_without_coords
    ImportError.LINK_NAMES_WITHOUT_COORDS -> Res.string.err_link_names_without_coords
    ImportError.INVALID_COORDS -> Res.string.err_invalid_coords
    ImportError.FILE_TOO_BIG -> Res.string.err_file_too_big
    ImportError.FILE_UNREADABLE -> Res.string.err_file_unreadable
    ImportError.ZIP_WITHOUT_RIDE -> Res.string.err_zip_without_ride
    ImportError.LINK_NO_ROUTE -> Res.string.err_link_no_route
    ImportError.LINK_TOO_MANY_REDIRECTS -> Res.string.err_link_too_many_redirects
    ImportError.NETWORK -> Res.string.err_network
}

/** GeoJSON a mano: solo numeri, niente testo da proteggere. */
internal fun lineJson(points: List<LatLon>): String =
    points.joinToString(",", """{"type":"Feature","properties":{},"geometry":{"type":"LineString","coordinates":[""", "]}}") {
        "[${it.lon},${it.lat}]"
    }

internal fun pointsJson(points: List<LatLon>): String =
    points.joinToString(",", """{"type":"FeatureCollection","features":[""", "]}") {
        """{"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":[${it.lon},${it.lat}]}}"""
    }
