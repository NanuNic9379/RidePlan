package io.github.nanunic9379.rideplan.ride

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.nanunic9379.rideplan.resources.Res
import io.github.nanunic9379.rideplan.resources.card_collapse
import io.github.nanunic9379.rideplan.resources.card_expand
import io.github.nanunic9379.rideplan.resources.close
import io.github.nanunic9379.rideplan.resources.err_file_too_big
import io.github.nanunic9379.rideplan.resources.err_file_unreadable
import io.github.nanunic9379.rideplan.resources.err_gpx_empty
import io.github.nanunic9379.rideplan.resources.err_invalid_coords
import io.github.nanunic9379.rideplan.resources.err_kml_empty
import io.github.nanunic9379.rideplan.resources.err_link_broken
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
import io.github.nanunic9379.rideplan.resources.qr_hint
import io.github.nanunic9379.rideplan.resources.qr_send_link
import io.github.nanunic9379.rideplan.resources.qr_title
import io.github.nanunic9379.rideplan.resources.ride_default_name
import io.github.nanunic9379.rideplan.resources.ride_km
import io.github.nanunic9379.rideplan.resources.ride_km_straight
import io.github.nanunic9379.rideplan.resources.ride_sheet_label
import io.github.nanunic9379.rideplan.resources.ride_source_google
import io.github.nanunic9379.rideplan.resources.ride_source_gpx
import io.github.nanunic9379.rideplan.resources.ride_source_kml
import io.github.nanunic9379.rideplan.resources.ride_source_link
import io.github.nanunic9379.rideplan.resources.ride_source_planned
import io.github.nanunic9379.rideplan.resources.ride_stops
import io.github.nanunic9379.rideplan.resources.ride_straight_lines_note
import io.github.nanunic9379.rideplan.resources.route_cancel
import io.github.nanunic9379.rideplan.resources.share_confirm
import io.github.nanunic9379.rideplan.resources.share_gpx
import io.github.nanunic9379.rideplan.resources.share_privacy_note
import io.github.nanunic9379.rideplan.resources.share_qr
import io.github.nanunic9379.rideplan.resources.share_title
import io.github.nanunic9379.rideplan.resources.share_trim_ends
import io.github.nanunic9379.rideplan.resources.stop_edit_hint
import io.github.nanunic9379.rideplan.nav.NavigateButton
import io.github.nanunic9379.rideplan.route.DetourNote
import io.github.nanunic9379.rideplan.route.RoughLayers
import io.github.nanunic9379.rideplan.route.SurfaceNote
import io.github.nanunic9379.rideplan.route.RouteActions
import io.github.nanunic9379.rideplan.route.RidingModeRow
import io.github.nanunic9379.rideplan.route.RidingMode
import io.github.nanunic9379.rideplan.route.RoutePreset
import io.github.nanunic9379.rideplan.route.RouteSettingsSheet
import io.github.nanunic9379.rideplan.profile.Bike
import io.github.nanunic9379.rideplan.route.RouteSection
import io.github.nanunic9379.rideplan.route.RouteState
import io.github.nanunic9379.rideplan.route.StopKindsRow
import io.github.nanunic9379.rideplan.route.durationText
import io.github.nanunic9379.rideplan.route.routable
import io.github.nanunic9379.rideplan.route.routeDoneNote
import io.github.nanunic9379.rideplan.ui.AppIcons
import io.github.nanunic9379.rideplan.ui.Brand
import io.github.nanunic9379.rideplan.ui.Radius
import io.github.nanunic9379.rideplan.ui.Touch
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.maplibre.compose.camera.CameraFit
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.map.MapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.toDpPadding
import org.maplibre.spatialk.geojson.BoundingBox
import kotlin.math.roundToInt

/** Where the user is in the app (#41, #21): the three tabs. The map is also shown when a ride is open. */
enum class Page { HOME, MAP, YOU }

/** A page opened on top of a tab (#21); back closes it. */
sealed interface Dest {
    data class RideDetail(val id: String) : Dest
    data object EditProfile : Dest
    data object Stats : Dest
    data object Garage : Dest
    /** A bike of the garage; null id = a new one. */
    data class BikePage(val id: String?) : Dest
    data object Log : Dest
    data object Zones : Dest
    data object Privacy : Dest
    data object DeleteAll : Dest
    data object Settings : Dest
    data object Language : Dest
    data object MapData : Dest
    data object Info : Dest
}

/** State of the ride on screen. The platform (file, link) changes it and [App] reads it. */
@Stable
class RideScreenState {
    var ride by mutableStateOf<Ride?>(null)
    var busy by mutableStateOf(false)
    /** Last error; [ImportStatus] picks the translated text. */
    var error by mutableStateOf<Throwable?>(null)
    /** Route calculation of the shown ride (#3). */
    var route by mutableStateOf<RouteState>(RouteState.Idle)
    /** Route preferences (#4); they apply to all rides. A new rider starts on the default mode (#30). */
    var options by mutableStateOf(RidingMode.DEFAULT.options)
    /** Route settings the rider saved with a name (#30). */
    var presets by mutableStateOf<List<RoutePreset>>(emptyList())
    var page by mutableStateOf(Page.HOME)
    /** The shown ride is in "Your rides" with this id: every change updates it. */
    var savedId by mutableStateOf<String?>(null)
    /** Ride sheet expanded (settings) or collapsed to name and km, to see the route. */
    var panelOpen by mutableStateOf(true)
    /** The stops were edited on the map (#2): the camera stops re-framing the ride at every change. */
    var stopsEdited by mutableStateOf(false)
    /** Turn-by-turn navigation of the ride on screen (#5). */
    var navigating by mutableStateOf(false)
    /**
     * A new ride being planned on the map (#33). [ride] is then the draft's ride, routed after
     * every change, and is not saved until "Done".
     */
    var plan by mutableStateOf<PlanDraft?>(null)
    /** Leaving the plan was asked (back) while it has points: the map asks whether to discard them. */
    var planLeaving by mutableStateOf(false)

    /** Search, chip, order and filters of the ride list (#21): they stay while you look at a ride. */
    var query by mutableStateOf(io.github.nanunic9379.rideplan.profile.RideQuery())

    /** Pages opened on top of the current tab, last = on screen (#21). */
    var stack by mutableStateOf<List<Dest>>(emptyList())

    fun open(dest: Dest) { stack = stack + dest }

    /** Closes the page on top; false when there is none. */
    fun back(): Boolean {
        if (stack.isEmpty()) return false
        stack = stack.dropLast(1)
        return true
    }

    /** A tab of the bottom bar: it opens on its first page. */
    fun tab(to: Page) {
        stack = emptyList()
        page = to
    }

    val onMap: Boolean get() = ride != null || page == Page.MAP

    /** Ride received by link and not saved yet (#48): no automatic saving. */
    val isPreview: Boolean get() = ride?.source == Ride.Source.RIDEPLAN_LINK && savedId == null

    /** Shows the ride or the error. A new ride is not among the saved ones yet. */
    fun show(result: Result<Ride>) {
        busy = false
        result.onSuccess { ride = it; error = null; route = RouteState.Idle; savedId = null; panelOpen = true; stopsEdited = false; navigating = false; plan = null; planLeaving = false }.onFailure { error = it }
    }

    /**
     * "Plan a ride" (#33): an empty map ready for taps. Leaving it goes back to where it was
     * opened from (Rides or the map).
     */
    fun startPlan() {
        val draft = PlanDraft()
        plan = draft
        planLeaving = false
        ride = draft.ride()
        error = null
        route = RouteState.Idle
        savedId = null
        panelOpen = false
        // The camera stays where the rider taps: no re-framing after every stop.
        stopsEdited = true
        navigating = false
    }

    /**
     * "Done" (#33): the planned ride becomes a ride like the others. Clearing [plan] lets [App]
     * save it to "Your rides"; the sheet opens on it and the camera frames it.
     */
    fun finishPlan(): Ride? {
        val draft = plan ?: return null
        val current = ride ?: return null
        if (!draft.canFinish) return null
        val done = draft.finished(current)
        plan = null
        planLeaving = false
        ride = done
        savedId = null
        panelOpen = true
        stopsEdited = false
        return done
    }

    /**
     * Back while planning: with nothing tapped it just leaves, otherwise it asks first. True when
     * the question was raised: the caller leaves only on false.
     */
    fun askBeforeLeavingPlan(): Boolean {
        val draft = plan ?: return false
        if (draft.points.isEmpty() || planLeaving) return false
        planLeaving = true
        return true
    }

    /**
     * Off the map, back to the page it was opened from (the ride is already saved);
     * from Explore back to the rides.
     */
    fun goHome() {
        // A plan started from the map goes back to the map, any other ride back to the rides.
        val fromPlan = plan != null
        plan = null
        planLeaving = false
        ride = null
        route = RouteState.Idle
        savedId = null
        if (page == Page.MAP && !fromPlan) page = Page.HOME
        stopsEdited = false
        navigating = false
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
    /** Sends several GPX files at once (name, content): the days of a trip, #12. */
    val shareFiles: ((List<Pair<String, String>>) -> Unit)? = null,
)

private val RouteColor = Brand.Orange
private val RouteCasing = Color(0xFF7A3300)
private val StopFill = Color(0xFFFFFFFF)
private val StopStroke = Brand.Ink

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
    RoughLayers(ride)
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
 * Frames the whole ride in the part of the map not covered by the controls:
 * [padding] is the room taken by the back button (top) and by the sheet (bottom).
 */
suspend fun MapState.fitRide(ride: Ride, padding: PaddingValues) {
    val (sw, ne) = ride.bounds()
    animateCameraToBounds(
        BoundingBox(sw.lon, sw.lat, ne.lon, ne.lat),
        CameraFit(fitPadding = padding.toDpPadding(LayoutDirection.Ltr)),
    )
}

/** On the empty map: the button that imports a ride (file or link). */
@Composable
fun ImportFab(actions: ImportActions, modifier: Modifier = Modifier) {
    var linkDialog by remember { mutableStateOf(false) }
    if (linkDialog) LinkDialog(actions, onDismiss = { linkDialog = false })
    Box(modifier) {
        ImportMenu(actions, onLink = { linkDialog = true }) { open ->
            ExtendedFloatingActionButton(
                onClick = open,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(AppIcons.Add, contentDescription = null) },
                text = { Text(stringResource(Res.string.import_button)) },
            )
        }
    }
}

/** Import progress and errors: on the map and on the home screen. */
@Composable
internal fun ImportStatus(state: RideScreenState) {
    state.error?.let { e ->
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) {
            Row(Modifier.padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(AppIcons.Warning, contentDescription = null)
                Column(Modifier.weight(1f)) {
                    Text(errorText(e), style = MaterialTheme.typography.bodyLarge)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { state.error = null }, modifier = Modifier.heightIn(min = Touch.Min)) {
                            Text(stringResource(Res.string.ok))
                        }
                    }
                }
            }
        }
    }
    if (state.busy) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.import_loading), style = MaterialTheme.typography.bodyLarge)
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
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.import_paste_link)) },
                leadingIcon = { Icon(AppIcons.Link, contentDescription = null) },
                onClick = { open = false; onLink() },
                modifier = Modifier.heightIn(min = Touch.Glove),
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.import_open_file)) },
                leadingIcon = { Icon(AppIcons.File, contentDescription = null) },
                onClick = { open = false; actions.openFile() },
                modifier = Modifier.heightIn(min = Touch.Glove),
            )
        }
    }
}

/**
 * Content of the bottom sheet over the map.
 * Header (always visible, also collapsed): name, km/time/stops, Share, route progress.
 * Body (sheet dragged up): route state, notes, preferences, saved state, import another.
 * [onHeaderHeight] reports the header height: the collapsed sheet shows exactly the header.
 */
@Composable
fun RideSheet(
    state: RideScreenState,
    actions: ImportActions?,
    routeActions: RouteActions?,
    maxBodyHeight: Dp,
    onHeaderHeight: (Dp) -> Unit,
    saved: @Composable () -> Unit = {},
    zones: List<PrivateZone> = emptyList(),
    /** Starts turn-by-turn navigation (#5); null = not available on this platform. */
    onNavigate: (() -> Unit)? = null,
    /** The default bike: under 150 cc it locks "avoid motorways" in the route settings (#8). */
    bike: Bike? = null,
    /** The map of this ride for use without signal (#1). */
    offlineMap: @Composable (Ride) -> Unit = {},
) {
    val ride = state.ride ?: return
    val route = state.route
    val title = ride.name ?: defaultName(ride) ?: stringResource(Res.string.ride_default_name)
    val density = LocalDensity.current
    val busy = route == RouteState.Checking || route == RouteState.Computing || route is RouteState.Downloading
    var linkDialog by remember { mutableStateOf(false) }
    if (linkDialog && actions != null) LinkDialog(actions, onDismiss = { linkDialog = false })
    var settingsOpen by remember { mutableStateOf(false) }
    if (settingsOpen && routeActions != null) {
        RouteSettingsSheet(
            ride = ride,
            title = title,
            applied = state.options,
            presets = state.presets,
            bike = bike,
            onApply = { settingsOpen = false; routeActions.changeOptions(it) },
            onSavePreset = routeActions.savePreset,
            onDeletePreset = routeActions.deletePreset,
            onDismiss = { settingsOpen = false },
        )
    }
    val sheetLabel = stringResource(Res.string.ride_sheet_label)

    Column(Modifier.fillMaxWidth().navigationBarsPadding().semantics { paneTitle = sheetLabel }) {
        Column(Modifier.onSizeChanged { onHeaderHeight(with(density) { it.height.toDp() }) }) {
            Row(
                Modifier.padding(start = 24.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    Modifier.weight(1f).clickable(
                        onClickLabel = stringResource(if (state.panelOpen) Res.string.card_collapse else Res.string.card_expand),
                        role = Role.Button,
                    ) { state.panelOpen = !state.panelOpen }.semantics(mergeDescendants = true) { heading() },
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(stats(ride), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                actions?.shareGpx?.let { share ->
                    var asking by remember { mutableStateOf(false) }
                    var qr by remember { mutableStateOf<String?>(null) }
                    FilledTonalIconButton(onClick = { asking = true }, modifier = Modifier.size(Touch.Glove)) {
                        Icon(AppIcons.Share, contentDescription = stringResource(Res.string.share_gpx))
                    }
                    if (asking) ShareDialog(ride, zones, onDismiss = { asking = false }) { out, asLink ->
                        asking = false
                        if (asLink) qr = RideLink.encode(out, state.options) else share(out, title)
                    }
                    qr?.let { QrDialog(it, actions.shareText, onDismiss = { qr = null }) }
                }
            }
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 12.dp))
            }
            // Navigate (#5) is the main action of a ride with a road: in the header, so it is there
            // even with the sheet collapsed.
            if (onNavigate != null && !ride.straightLines && route == RouteState.Idle) {
                NavigateButton(onNavigate, Modifier.padding(horizontal = 24.dp).padding(top = 12.dp))
            }
            Spacer(Modifier.height(16.dp))
        }
        Column(
            Modifier.heightIn(max = maxBodyHeight).verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HorizontalDivider()
            RouteSection(route, routeActions)
            RideNotes(ride, route)
            FuelSection(state, routeActions, bike)
            CountryWarningsSection(ride)
            if (route == RouteState.Idle) offlineMap(ride)
            if (routeActions != null && ride.routable && route !is RouteState.NotCovered) {
                RidingModeRow(state.options, enabled = !busy, onOpen = { settingsOpen = true })
                StopKindsRow(ride.stops, enabled = !busy, onToggle = routeActions.toggleStop)
                if (ride.stopsEditable) {
                    Text(stringResource(Res.string.stop_edit_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            DaysSection(ride, enabled = !busy, fallbackName = title, onChange = { state.ride = it }, share = actions?.shareFiles)
            HorizontalDivider()
            saved()
            if (actions != null) {
                ImportMenu(actions, onLink = { linkDialog = true }) { open ->
                    OutlinedButton(onClick = open, modifier = Modifier.fillMaxWidth().heightIn(min = Touch.Glove), shape = Radius.Control) {
                        Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(stringResource(Res.string.import_another))
                    }
                }
            }
        }
    }
}

/** Stops in order, where the ride comes from, and what the line on the map means. */
@Composable
private fun RideNotes(ride: Ride, route: RouteState) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val names = ride.stops.mapNotNull { it.name }
        if (names.isNotEmpty()) {
            Text(names.joinToString(" → "), style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Text(sourceText(ride), style = MaterialTheme.typography.labelLarge, color = muted)
        val note = when {
            ride.route != null -> routeDoneNote(ride.route)
            ride.straightLines && route == RouteState.Idle -> stringResource(Res.string.ride_straight_lines_note)
            else -> null
        }
        if (note != null) Text(note, style = MaterialTheme.typography.bodyMedium, color = muted)
        ride.route?.let { DetourNote(it) }
        SurfaceNote(ride.surfaces)
    }
}

/**
 * Before sharing: start and finish are often home or the garage (#12), and the private zones
 * (#6) are cut out. [onShare] receives only what may leave the phone ([SharePreview]).
 */
@Composable
internal fun ShareDialog(ride: Ride, zones: List<PrivateZone>, onDismiss: () -> Unit, onShare: (out: Ride, asLink: Boolean) -> Unit) {
    var trim by remember { mutableStateOf(true) }
    var hideZones by remember { mutableStateOf(true) }
    val preview = remember(ride, zones, trim, hideZones) { SharePreview.of(ride, zones, trim, hideZones) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(AppIcons.Share, contentDescription = null) },
        title = { Text(stringResource(Res.string.share_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.share_privacy_note), style = MaterialTheme.typography.bodyMedium)
                HideZonesOption(zones, hideZones) { hideZones = it }
                ShareOption(stringResource(Res.string.share_trim_ends), trim) { trim = it }
                SharePreviewText(preview)
                // The QR and link for those who have RidePlan (#6); the GPX file for navigators and other apps.
                Button(
                    onClick = { onShare(preview.ride, true) },
                    enabled = !preview.empty,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Touch.Glove),
                    shape = Radius.Control,
                ) {
                    Icon(AppIcons.Qr, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(Res.string.share_qr))
                }
                OutlinedButton(
                    onClick = { onShare(preview.ride, false) },
                    enabled = !preview.empty,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Touch.Glove),
                    shape = Radius.Control,
                ) {
                    Icon(AppIcons.File, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(Res.string.share_confirm))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.route_cancel)) } },
    )
}

/** The ride in a QR to scan at the bar, plus the link to send in a chat (#6). */
@Composable
internal fun QrDialog(link: String, send: ((String) -> Unit)?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.qr_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Always black on white, even with the dark theme: cameras read it better.
                Box(Modifier.background(Color.White, MaterialTheme.shapes.medium).padding(16.dp)) {
                    Image(rememberQrCodePainter(link), contentDescription = stringResource(Res.string.qr_title), modifier = Modifier.size(240.dp))
                }
                Text(stringResource(Res.string.qr_hint), style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            if (send != null) Button(onClick = { send(link) }, shape = Radius.Control) { Text(stringResource(Res.string.qr_send_link)) }
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
        icon = { Icon(AppIcons.Link, contentDescription = null) },
        title = { Text(stringResource(Res.string.link_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.link_dialog_help), style = MaterialTheme.typography.bodyMedium)
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
            Button(enabled = text.isNotBlank(), onClick = { onDismiss(); actions.importText(text) }, shape = Radius.Control) {
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
internal fun defaultName(ride: Ride): String? {
    val first = ride.stops.firstOrNull()?.name
    val last = ride.stops.lastOrNull()?.name
    return if (first != null && last != null && ride.stops.size >= 2) "$first → $last" else null
}

/** The numbers you look for at a glance: km, time, stops. */
@Composable
internal fun stats(ride: Ride): String {
    val km = io.github.nanunic9379.rideplan.personal.distanceText(ride.lengthKm)
    val parts = buildList {
        add(if (ride.straightLines) stringResource(Res.string.ride_km_straight, km) else km)
        ride.route?.let { add(durationText(it.seconds)) }
        if (ride.stops.isNotEmpty()) add(pluralStringResource(Res.plurals.ride_stops, ride.stops.size, ride.stops.size))
    }
    return parts.joinToString(" · ")
}

@Composable
internal fun sourceText(ride: Ride): String = stringResource(
    when (ride.source) {
        Ride.Source.GPX -> Res.string.ride_source_gpx
        Ride.Source.KML -> Res.string.ride_source_kml
        Ride.Source.GOOGLE_LINK -> Res.string.ride_source_google
        Ride.Source.RIDEPLAN_LINK -> Res.string.ride_source_link
        Ride.Source.PLANNED -> Res.string.ride_source_planned
    },
)

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
