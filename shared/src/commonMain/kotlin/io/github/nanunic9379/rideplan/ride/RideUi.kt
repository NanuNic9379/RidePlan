package io.github.nanunic9379.rideplan.ride

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.semantics.Role
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
import io.github.nanunic9379.rideplan.resources.err_link_broken
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
import io.github.nanunic9379.rideplan.resources.route_cancel
import io.github.nanunic9379.rideplan.resources.share_confirm
import io.github.nanunic9379.rideplan.resources.share_qr
import io.github.nanunic9379.rideplan.resources.qr_hint
import io.github.nanunic9379.rideplan.resources.qr_send_link
import io.github.nanunic9379.rideplan.resources.qr_title
import io.github.nanunic9379.rideplan.resources.share_gpx
import io.github.nanunic9379.rideplan.resources.share_privacy_note
import io.github.nanunic9379.rideplan.resources.share_title
import io.github.nanunic9379.rideplan.resources.share_trim_ends
import io.github.nanunic9379.rideplan.resources.ok
import io.github.nanunic9379.rideplan.resources.ride_default_name
import io.github.nanunic9379.rideplan.resources.ride_km
import io.github.nanunic9379.rideplan.resources.ride_km_straight
import io.github.nanunic9379.rideplan.resources.ride_source_google
import io.github.nanunic9379.rideplan.resources.ride_source_link
import io.github.nanunic9379.rideplan.resources.ride_source_gpx
import io.github.nanunic9379.rideplan.resources.ride_source_kml
import io.github.nanunic9379.rideplan.resources.ride_stops
import io.github.nanunic9379.rideplan.resources.ride_straight_lines_note
import io.github.nanunic9379.rideplan.route.DetourNote
import io.github.nanunic9379.rideplan.route.RouteActions
import io.github.nanunic9379.rideplan.route.RouteOptions
import io.github.nanunic9379.rideplan.route.RouteOptionsRow
import io.github.nanunic9379.rideplan.route.StopKindsRow
import io.github.nanunic9379.rideplan.route.routable
import io.github.nanunic9379.rideplan.route.RouteSection
import io.github.nanunic9379.rideplan.route.RouteState
import io.github.nanunic9379.rideplan.route.durationText
import io.github.nanunic9379.rideplan.route.routeDoneNote
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.map.MapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.spatialk.geojson.BoundingBox
import kotlin.math.roundToInt

/** Dove si trova chi usa l'app (#41). La mappa si vede anche quando c'è un giro aperto. */
enum class Page { HOME, MAP, PROFILE }

/** Stato del giro sullo schermo. Lo cambia la piattaforma (file, link) e lo legge [App]. */
@Stable
class RideScreenState {
    var ride by mutableStateOf<Ride?>(null)
    var busy by mutableStateOf(false)
    /** Ultimo errore; il testo tradotto lo sceglie [RidePanel]. */
    var error by mutableStateOf<Throwable?>(null)
    /** Calcolo del percorso del giro mostrato (#3). */
    var route by mutableStateOf<RouteState>(RouteState.Idle)
    /** Preferenze del percorso (#4); valgono per tutti i giri. */
    var options by mutableStateOf(RouteOptions())
    var page by mutableStateOf(Page.HOME)
    /** Il giro mostrato è in "I tuoi giri" con questo id: ogni modifica lo aggiorna. */
    var savedId by mutableStateOf<String?>(null)

    val onMap: Boolean get() = ride != null || page == Page.MAP

    /** Mostra il giro o l'errore. Un giro nuovo non è ancora tra quelli salvati. */
    fun show(result: Result<Ride>) {
        busy = false
        result.onSuccess { ride = it; error = null; route = RouteState.Idle; savedId = null }.onFailure { error = it }
    }

    /** Torna alla schermata iniziale; il giro è già salvato. */
    fun goHome() {
        ride = null
        route = RouteState.Idle
        savedId = null
        page = Page.HOME
    }
}

/**
 * Modi di importare offerti dalla piattaforma.
 * [readClipboard]: testo negli appunti, per precompilare il campo del link.
 * [importText]: importa un link (o testo che lo contiene) di un percorso di Google Maps.
 * [openRide]: apre un giro già pronto (da "I tuoi giri") e ne calcola il percorso se serve.
 */
class ImportActions(
    val openFile: () -> Unit,
    val readClipboard: () -> String?,
    val importText: (String) -> Unit,
    val openRide: (Ride) -> Unit = {},
    /** Manda il giro come file GPX (#5). Il nome serve quando il giro non ne ha uno. */
    val shareGpx: ((ride: Ride, fallbackName: String) -> Unit)? = null,
    /** Manda un testo (il link del giro, #6) con la condivisione di sistema. */
    val shareText: ((String) -> Unit)? = null,
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
fun BoxScope.RidePanel(
    state: RideScreenState,
    actions: ImportActions?,
    routeActions: RouteActions? = null,
    saved: @Composable () -> Unit = {},
) {
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
        ImportStatus(state)
        if (ride != null) {
            RideCard(
                ride,
                route = state.route,
                options = state.options,
                routeActions = routeActions,
                onClose = { routeActions?.cancel?.invoke(); state.goHome() },
                actions = actions,
                onLink = { linkDialog = true },
                saved = saved,
            )
        } else if (actions != null && !state.busy) {
            ImportMenu(actions, onLink = { linkDialog = true }) { open ->
                ExtendedFloatingActionButton(onClick = open) { Text(stringResource(Res.string.import_button)) }
            }
        }
    }
}

/** Attesa dell'importazione ed errori: sulla mappa e nella schermata iniziale. */
@Composable
internal fun ImportStatus(state: RideScreenState) {
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RideCard(
    ride: Ride,
    route: RouteState,
    options: RouteOptions,
    routeActions: RouteActions?,
    onClose: () -> Unit,
    actions: ImportActions?,
    onLink: () -> Unit,
    saved: @Composable () -> Unit,
) {
    val title = ride.name ?: defaultName(ride) ?: stringResource(Res.string.ride_default_name)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(summary(ride), style = MaterialTheme.typography.bodyMedium)
            val names = ride.stops.mapNotNull { it.name }
            if (names.isNotEmpty()) {
                Text(names.joinToString(" → "), style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            val note = when {
                ride.route != null -> routeDoneNote(ride.route)
                ride.straightLines && route == RouteState.Idle -> stringResource(Res.string.ride_straight_lines_note)
                else -> null
            }
            if (note != null) {
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ride.route?.let { DetourNote(it) }
            if (routeActions != null && ride.routable && route != RouteState.NotCovered) {
                val busy = route == RouteState.Checking || route == RouteState.Computing || route is RouteState.Downloading
                RouteOptionsRow(options, enabled = !busy, onChange = routeActions.changeOptions)
                StopKindsRow(ride.stops, enabled = !busy, onToggle = routeActions.toggleStop)
            }
            RouteSection(route, routeActions)
            saved()
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalArrangement = Arrangement.Center) {
                actions?.shareGpx?.let { share ->
                    var asking by remember { mutableStateOf(false) }
                    TextButton(onClick = { asking = true }) { Text(stringResource(Res.string.share_gpx)) }
                    var qr by remember { mutableStateOf<String?>(null) }
                    if (asking) ShareDialog(onDismiss = { asking = false }) { trim, asLink ->
                        asking = false
                        val out = if (trim) PrivateEnds.trim(ride) else ride
                        if (asLink) qr = RideLink.encode(out) else share(out, title)
                    }
                    qr?.let { QrDialog(it, actions?.shareText, onDismiss = { qr = null }) }
                }
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

/** Prima di condividere: partenza e arrivo spesso sono casa o garage (#12). */
@Composable
private fun ShareDialog(onDismiss: () -> Unit, onShare: (trimEnds: Boolean, asLink: Boolean) -> Unit) {
    var trim by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.share_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.share_privacy_note), style = MaterialTheme.typography.bodyMedium)
                Row(
                    Modifier.fillMaxWidth().toggleable(trim, role = Role.Checkbox) { trim = it },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = trim, onCheckedChange = null)
                    Text(stringResource(Res.string.share_trim_ends), Modifier.padding(start = 8.dp))
                }
            }
        },
        // Il file GPX per navigatori e altre app; QR e link per chi ha RidePlan (#6).
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onShare(trim, false) }) { Text(stringResource(Res.string.share_confirm)) }
                Button(onClick = { onShare(trim, true) }) { Text(stringResource(Res.string.share_qr)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.route_cancel)) } },
    )
}

/** Il giro in un QR da inquadrare al bar, più il link da mandare in chat (#6). */
@Composable
private fun QrDialog(link: String, send: ((String) -> Unit)?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.qr_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Sempre nero su bianco, anche col tema scuro: le fotocamere lo leggono meglio.
                Box(Modifier.background(Color.White).padding(12.dp)) {
                    Image(rememberQrCodePainter(link), contentDescription = null, modifier = Modifier.size(240.dp))
                }
                Text(stringResource(Res.string.qr_hint), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            if (send != null) Button(onClick = { send(link) }) { Text(stringResource(Res.string.qr_send_link)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.close)) } },
    )
}

/** Campo per incollare il link di un percorso creato con Google Maps. */
@Composable
internal fun LinkDialog(actions: ImportActions, onDismiss: () -> Unit) {
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
        ride.route?.let { add(durationText(it.seconds)) }
        if (ride.stops.isNotEmpty()) add(pluralStringResource(Res.plurals.ride_stops, ride.stops.size, ride.stops.size))
        add(
            stringResource(
                when (ride.source) {
                    Ride.Source.GPX -> Res.string.ride_source_gpx
                    Ride.Source.KML -> Res.string.ride_source_kml
                    Ride.Source.GOOGLE_LINK -> Res.string.ride_source_google
                    Ride.Source.RIDEPLAN_LINK -> Res.string.ride_source_link
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
    ImportError.LINK_BROKEN -> Res.string.err_link_broken
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
