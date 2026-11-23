package io.github.nanunic9379.rideplan.ride

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nanunic9379.rideplan.resources.card_collapse
import io.github.nanunic9379.rideplan.resources.card_expand
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
import io.github.nanunic9379.rideplan.resources.stop_edit_hint
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

/** Where the user is in the app (#41). The map is also shown when a ride is open. */
enum class Page { HOME, MAP, PROFILE }

/** State of the ride on screen. The platform (file, link) changes it and [App] reads it. */
@Stable
class RideScreenState {
    var ride by mutableStateOf<Ride?>(null)
    var busy by mutableStateOf(false)
    /** Last error; [RidePanel] picks the translated text. */
    var error by mutableStateOf<Throwable?>(null)
    /** Route calculation of the shown ride (#3). */
    var route by mutableStateOf<RouteState>(RouteState.Idle)
    /** Route preferences (#4); they apply to all rides. */
    var options by mutableStateOf(RouteOptions())
    var page by mutableStateOf(Page.HOME)
    /** The shown ride is in "Your rides" with this id: every change updates it. */
    var savedId by mutableStateOf<String?>(null)
    /** Ride card open (settings) or collapsed to name and km, to see the route. */
    var panelOpen by mutableStateOf(true)
    /** The stops were edited on the map (#2): the camera stops re-framing the ride at every change. */
    var stopsEdited by mutableStateOf(false)

    val onMap: Boolean get() = ride != null || page == Page.MAP

    /** Ride received by link and not saved yet (#48): no automatic saving. */
    val isPreview: Boolean get() = ride?.source == Ride.Source.RIDEPLAN_LINK && savedId == null

    /** Shows the ride or the error. A new ride is not among the saved ones yet. */
    fun show(result: Result<Ride>) {
        busy = false
        result.onSuccess { ride = it; error = null; route = RouteState.Idle; savedId = null; panelOpen = true; stopsEdited = false }.onFailure { error = it }
    }

    /** Back to the home screen; the ride is already saved. */
    fun goHome() {
        ride = null
        route = RouteState.Idle
        savedId = null
        page = Page.HOME
        stopsEdited = false
    }
}

/**
 * Ways of importing offered by the platform.
 * [readClipboard]: clipboard text, to prefill the link field.
 * [importText]: imports a Google Maps route link (or text containing it).
 * [openRide]: opens a ready ride (from "Your rides") and calculates its route if needed.
 */
class ImportActions(
    val openFile: () -> Unit,
    val readClipboard: () -> String?,
    val importText: (String) -> Unit,
    val openRide: (Ride) -> Unit = {},
    /** Sends the ride as a GPX file (#5). The name is used when the ride has none. */
    val shareGpx: ((ride: Ride, fallbackName: String) -> Unit)? = null,
    /** Sends a text (the ride link, #6) with the system share sheet. */
    val shareText: ((String) -> Unit)? = null,
)

private val RouteColor = Color(0xFFFF8A1F)
private val RouteCasing = Color(0xFF7A3300)
private val StopFill = Color(0xFFFFFFFF)
private val StopStroke = Color(0xFF1C1B26)

/**
 * Track and stops on the map. Only a real road or recorded track is drawn: while the route is
 * being calculated (or cannot be) the stops are shown alone, never joined by straight lines.
 */
@Composable
fun RideLayers(ride: Ride) {
    val line = rememberGeoJsonSource(GeoJsonData.JsonString(roadJson(ride)))
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
 * Frames the whole ride, with a margin on the sides and room at the bottom for the card.
 * The margin is added to the bounds instead of in pixels: it works the same on every screen.
 */
suspend fun MapState.fitRide(ride: Ride, panelOpen: Boolean = true) {
    val (sw, ne) = ride.bounds()
    val dLon = (ne.lon - sw.lon).coerceAtLeast(0.01)
    val dLat = (ne.lat - sw.lat).coerceAtLeast(0.01)
    animateCameraToBounds(
        BoundingBox(
            sw.lon - dLon * 0.15,
            sw.lat - dLat * (if (panelOpen) 0.9 else 0.3),
            ne.lon + dLon * 0.15,
            ne.lat + dLat * 0.25,
        ),
    )
}

/** Import button, ride card, progress and errors. */
@Composable
fun BoxScope.RidePanel(
    state: RideScreenState,
    actions: ImportActions?,
    routeActions: RouteActions? = null,
    saved: @Composable () -> Unit = {},
    zones: List<PrivateZone> = emptyList(),
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
                open = state.panelOpen,
                onToggle = { state.panelOpen = !state.panelOpen },
                zones = zones,
            )
        } else if (actions != null && !state.busy) {
            ImportMenu(actions, onLink = { linkDialog = true }) { open ->
                ExtendedFloatingActionButton(onClick = open) { Text(stringResource(Res.string.import_button)) }
            }
        }
    }
}

/** Import progress and errors: on the map and on the home screen. */
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

/** "File or link" menu attached to the button that opens it. */
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
    open: Boolean = true,
    onToggle: () -> Unit = {},
    zones: List<PrivateZone> = emptyList(),
) {
    val title = ride.name ?: defaultName(ride) ?: stringResource(Res.string.ride_default_name)
    // Never taller than about half the screen: the rest scrolls and the map stays visible.
    Card(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Collapsed it shows only name, km and time: the whole route is visible on the map.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable(onClick = onToggle)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(summary(ride), style = MaterialTheme.typography.bodyMedium)
                }
                TextButton(onClick = onToggle) {
                    Text(if (open) "▾ " + stringResource(Res.string.card_collapse) else "▴ " + stringResource(Res.string.card_expand))
                }
            }
            if (!open) return@Column
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
                if (ride.stopsEditable) {
                    Text(stringResource(Res.string.stop_edit_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            RouteSection(route, routeActions)
            saved()
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalArrangement = Arrangement.Center) {
                actions?.shareGpx?.let { share ->
                    var asking by remember { mutableStateOf(false) }
                    TextButton(onClick = { asking = true }) { Text(stringResource(Res.string.share_gpx)) }
                    var qr by remember { mutableStateOf<String?>(null) }
                    if (asking) ShareDialog(ride, zones, onDismiss = { asking = false }) { out, asLink ->
                        asking = false
                        if (asLink) qr = RideLink.encode(out, options) else share(out, title)
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

/**
 * Before sharing: start and finish are often home or the garage (#12), and the private zones
 * (#6) are cut out. [onShare] receives only what may leave the phone ([SharePreview]).
 */
@Composable
private fun ShareDialog(ride: Ride, zones: List<PrivateZone>, onDismiss: () -> Unit, onShare: (out: Ride, asLink: Boolean) -> Unit) {
    var trim by remember { mutableStateOf(true) }
    var hideZones by remember { mutableStateOf(true) }
    val preview = remember(ride, zones, trim, hideZones) { SharePreview.of(ride, zones, trim, hideZones) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.share_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.share_privacy_note), style = MaterialTheme.typography.bodyMedium)
                HideZonesOption(zones, hideZones) { hideZones = it }
                ShareOption(stringResource(Res.string.share_trim_ends), trim) { trim = it }
                SharePreviewText(preview)
            }
        },
        // The GPX file for navigators and other apps; QR and link for those who have RidePlan (#6).
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onShare(preview.ride, false) }, enabled = !preview.empty) { Text(stringResource(Res.string.share_confirm)) }
                Button(onClick = { onShare(preview.ride, true) }, enabled = !preview.empty) { Text(stringResource(Res.string.share_qr)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.route_cancel)) } },
    )
}

/** The ride in a QR to scan at the bar, plus the link to send in a chat (#6). */
@Composable
private fun QrDialog(link: String, send: ((String) -> Unit)?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.qr_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Always black on white, even with the dark theme: cameras read it better.
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

/** Field to paste the link of a route created with Google Maps. */
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

/** "Start → Finish" if the stops have names. */
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

/** One decimal below 10 km, whole number above. */
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

/** Hand-made GeoJSON: numbers only, no text to escape. */
/** The ride's road as GeoJSON; an empty collection while only straight lines between stops are known. */
internal fun roadJson(ride: Ride): String =
    if (ride.straightLines) EMPTY_FEATURES else lineJson(ride.track)

internal const val EMPTY_FEATURES = """{"type":"FeatureCollection","features":[]}"""

internal fun lineJson(points: List<LatLon>): String =
    points.joinToString(",", """{"type":"Feature","properties":{},"geometry":{"type":"LineString","coordinates":[""", "]}}") {
        "[${it.lon},${it.lat}]"
    }

internal fun pointsJson(points: List<LatLon>): String =
    points.joinToString(",", """{"type":"FeatureCollection","features":[""", "]}") {
        """{"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":[${it.lon},${it.lat}]}}"""
    }
