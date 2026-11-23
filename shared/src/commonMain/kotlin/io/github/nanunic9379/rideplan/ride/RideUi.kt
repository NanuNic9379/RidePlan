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
    var message by mutableStateOf<String?>(null)

    /** Esegue l'importazione e mostra il giro o l'errore. */
    fun show(result: Result<Ride>) {
        busy = false
        result.onSuccess { ride = it; message = null }
            .onFailure { message = (it as? ImportException)?.message ?: "Non riesco ad aprire il giro: ${it.message ?: it::class.simpleName}" }
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
        state.message?.let { msg ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(msg)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { state.message = null }) { Text("OK") }
                    }
                }
            }
        }
        if (state.busy) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Apro il giro...")
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
        if (ride != null) {
            RideCard(ride, onClose = { state.ride = null }, actions = actions, onLink = { linkDialog = true })
        } else if (actions != null && !state.busy) {
            ImportButton(actions, onLink = { linkDialog = true })
        }
    }
}

@Composable
private fun ImportButton(actions: ImportActions, onLink: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ExtendedFloatingActionButton(onClick = { open = true }) { Text("Importa un giro") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Apri file GPX o KML") }, onClick = { open = false; actions.openFile() })
            DropdownMenuItem(text = { Text("Incolla link di Google Maps") }, onClick = { open = false; onLink() })
        }
    }
}

@Composable
private fun RideCard(ride: Ride, onClose: () -> Unit, actions: ImportActions?, onLink: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(ride.name ?: defaultName(ride), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(summary(ride), style = MaterialTheme.typography.bodyMedium)
            val names = ride.stops.mapNotNull { it.name }
            if (names.isNotEmpty()) {
                Text(names.joinToString(" → "), style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (ride.straightLines) {
                Text(
                    "Da Google arrivano solo le tappe, non la strada: per ora le unisco in linea retta. " +
                        "La strada vera arriva con il calcolo del percorso.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClose) { Text("Chiudi") }
                if (actions != null) {
                    var open by remember { mutableStateOf(false) }
                    Box {
                        Button(onClick = { open = true }) { Text("Importa un altro") }
                        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            DropdownMenuItem(text = { Text("Apri file GPX o KML") }, onClick = { open = false; actions.openFile() })
                            DropdownMenuItem(text = { Text("Incolla link di Google Maps") }, onClick = { open = false; onLink() })
                        }
                    }
                }
            }
        }
    }
}

/** Campo per incollare il link di un percorso creato con Google Maps. */
@Composable
private fun LinkDialog(actions: ImportActions, onDismiss: () -> Unit) {
    var text by remember {
        mutableStateOf(actions.readClipboard()?.takeIf { RideImport.findUrl(it) != null }?.let { RideImport.findUrl(it) } ?: "")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link di Google Maps") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "In Google Maps crea il percorso con le tappe, tocca Condividi e copia il link. Poi incollalo qui.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Link del percorso") },
                    placeholder = { Text("https://maps.app.goo.gl/...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(enabled = text.isNotBlank(), onClick = { onDismiss(); actions.importText(text) }) { Text("Importa") }
        },
        dismissButton = {
            TextButton(onClick = {
                actions.readClipboard()?.let { text = RideImport.findUrl(it) ?: it }
            }) { Text("Incolla") }
        },
    )
}

private fun defaultName(ride: Ride): String {
    val first = ride.stops.firstOrNull()?.name
    val last = ride.stops.lastOrNull()?.name
    return if (first != null && last != null && ride.stops.size >= 2) "$first → $last" else "Giro importato"
}

internal fun summary(ride: Ride): String {
    val km = ride.lengthKm
    val kmText = if (km < 10) "${(km * 10).roundToInt() / 10.0} km" else "${km.roundToInt()} km"
    val parts = buildList {
        add(if (ride.straightLines) "$kmText in linea d'aria" else kmText)
        if (ride.stops.isNotEmpty()) add("${ride.stops.size} tappe")
        add(
            when (ride.source) {
                Ride.Source.GPX -> "da GPX"
                Ride.Source.KML -> "da KML"
                Ride.Source.GOOGLE_LINK -> "da Google Maps"
            },
        )
    }
    return parts.joinToString(" · ")
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
