package io.github.nanunic9379.rideplan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.nanunic9379.rideplan.profile.BackupActions
import io.github.nanunic9379.rideplan.profile.HomeButton
import io.github.nanunic9379.rideplan.profile.HomeScreen
import io.github.nanunic9379.rideplan.profile.ProfileScreen
import io.github.nanunic9379.rideplan.profile.ProfileStore
import io.github.nanunic9379.rideplan.profile.SavedRow
import io.github.nanunic9379.rideplan.profile.WelcomeScreen
import io.github.nanunic9379.rideplan.ride.ImportActions
import io.github.nanunic9379.rideplan.ride.ImportFab
import io.github.nanunic9379.rideplan.ride.ImportStatus
import io.github.nanunic9379.rideplan.ride.Page
import io.github.nanunic9379.rideplan.ride.RideLayers
import io.github.nanunic9379.rideplan.ride.Ride
import io.github.nanunic9379.rideplan.ride.RideScreenState
import io.github.nanunic9379.rideplan.ride.RideSheet
import io.github.nanunic9379.rideplan.ride.StopHandles
import io.github.nanunic9379.rideplan.ride.StopToolbar
import io.github.nanunic9379.rideplan.ride.rememberStopInteractions
import io.github.nanunic9379.rideplan.ride.stopsEditable
import io.github.nanunic9379.rideplan.ride.withStopAdded
import io.github.nanunic9379.rideplan.ride.withStopMoved
import io.github.nanunic9379.rideplan.ride.withStopRemoved
import io.github.nanunic9379.rideplan.ride.withStopReordered
import io.github.nanunic9379.rideplan.ride.fitRide
import io.github.nanunic9379.rideplan.route.RouteActions
import io.github.nanunic9379.rideplan.route.RouteState
import io.github.nanunic9379.rideplan.nav.NavigationPlatform
import io.github.nanunic9379.rideplan.nav.NavigationScreen
import io.github.nanunic9379.rideplan.ui.RidePlanTheme
import kotlinx.coroutines.flow.first
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.overlay.MapOverlay
import org.maplibre.compose.overlay.include
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position

/**
 * The app (#41): it opens on the home screen with your rides; the map comes
 * when you open a ride or choose "Explore". A ride opened from a link goes straight to the map.
 *
 * [rides]: ride imported from a file or link (#27, #5), handled by the platform.
 * [store]: profile and rides saved on the phone; null = map only, as before.
 * [importActions]: how to import on this platform; null = buttons hidden.
 * [routeActions]: data download and route calculation (#3); null = not available.
 * [overlay]: platform-specific elements on top of everything (e.g. the update banner, #30).
 */
@Composable
fun App(
    rides: RideScreenState = remember { RideScreenState() },
    store: ProfileStore? = null,
    importActions: ImportActions? = null,
    routeActions: RouteActions? = null,
    backupActions: BackupActions? = null,
    /** Turn-by-turn navigation on this platform (#5); null = no "Navigate" button. */
    navigation: NavigationPlatform? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    RidePlanTheme {
        val snackbar = remember { SnackbarHostState() }
        val ride = rides.ride
        // Every opened ride ends up in "Your rides" and stays up to date (route, preferences, name).
        // A ride received by link stays in preview until you save it (#48).
        LaunchedEffect(ride) {
            if (ride != null && store != null && !rides.isPreview) {
                store.update { d ->
                    val (fresh, id) = d.save(ride, rides.savedId, store.time())
                    rides.savedId = id
                    fresh
                }
            }
        }
        // The map draws edge to edge: each screen handles the system bars itself.
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }, contentWindowInsets = WindowInsets(0)) { _ ->
            Box(Modifier.fillMaxSize()) {
                when {
                    rides.navigating && navigation != null && ride != null && !ride.straightLines -> NavigationScreen(
                        ride,
                        navigation,
                        onExit = { rides.navigating = false },
                        onMarkDone = if (store != null) rides.savedId?.let { id -> { store.update { it.markRidden(id, store.time()) } } } else null,
                    )
                    store == null || rides.onMap -> MapScreen(rides, store, importActions, routeActions, navigation)
                    !store.data.profile.welcomed -> Surface(Modifier.fillMaxSize()) { WelcomeScreen(store) }
                    rides.page == Page.PROFILE -> Surface(Modifier.fillMaxSize()) { ProfileScreen(store, onBack = { rides.page = Page.HOME }, backup = backupActions) }
                    else -> Surface(Modifier.fillMaxSize()) { HomeScreen(store, rides, importActions, snackbar) }
                }
                overlay()
            }
        }
    }
}

/**
 * Map first: the map fills the screen and the ride lives in a bottom sheet over it.
 * Collapsed ("peek") the sheet shows name, km, time and Share; dragged up it shows
 * route state, preferences and the rest. Its state is [RideScreenState.panelOpen].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapScreen(
    rides: RideScreenState,
    store: ProfileStore?,
    importActions: ImportActions?,
    routeActions: RouteActions?,
    navigation: NavigationPlatform? = null,
) {
    val ride = rides.ride
    val state = rememberMapState(
        baseStyle = BaseStyle.Uri(MapDefaults.STYLE),
        initialCameraPosition = CameraPosition(
            center = Position(latitude = MapDefaults.START_LAT, longitude = MapDefaults.START_LON),
            zoom = MapDefaults.START_ZOOM,
        ),
    ) {
        if (ride != null) RideLayers(ride)
    }
    // Editing the stops on the map (#2): only rides made of stops, and not while data is downloading.
    val editable = ride != null && routeActions != null && ride.stopsEditable && rides.route !is RouteState.Downloading
    var selected by remember { mutableStateOf<Int?>(null) }
    val selectedStop = selected?.takeIf { editable && ride != null && it in ride.stops.indices }
    fun edit(change: (Ride) -> Ride) {
        val current = rides.ride ?: return
        val fresh = change(current)
        if (fresh === current) return
        rides.stopsEdited = true
        routeActions?.editStops?.invoke(fresh)
    }
    val interactions = rememberStopInteractions(
        onAdd = if (editable) ({ point -> selected = null; edit { it.withStopAdded(point) } }) else null,
        onTapMap = { if (selected != null) { selected = null; true } else false },
    )

    val sheet = rememberStandardBottomSheetState(
        initialValue = if (rides.panelOpen) SheetValue.Expanded else SheetValue.PartiallyExpanded,
    )
    val scaffold = rememberBottomSheetScaffoldState(sheet)
    val density = LocalDensity.current
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // Height of the sheet header, measured: the collapsed sheet shows exactly that.
    var header by remember { mutableStateOf(0.dp) }
    val peek = if (ride == null) 0.dp else DragHandleHeight + header + navBar

    // Sheet <-> state, both ways: the platform opens a new ride expanded, the user drags it.
    LaunchedEffect(rides.panelOpen, ride == null) {
        if (ride == null) return@LaunchedEffect
        if (rides.panelOpen) sheet.expand() else sheet.partialExpand()
    }
    LaunchedEffect(sheet) {
        snapshotFlow { sheet.currentValue }.collect { if (rides.ride != null) rides.panelOpen = it == SheetValue.Expanded }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screen = maxHeight
        // Frame the ride in the part of the map left visible by the sheet, once the sheet has settled.
        // After an edit on the map the camera stays where the rider is working.
        LaunchedEffect(ride, rides.panelOpen, peek) {
            if (ride == null || rides.stopsEdited) return@LaunchedEffect
            val wanted = if (rides.panelOpen) SheetValue.Expanded else SheetValue.PartiallyExpanded
            snapshotFlow { sheet.currentValue == wanted && !sheet.isAnimationRunning }.first { it }
            val sheetTop = runCatching { with(density) { sheet.requireOffset().toDp() } }.getOrDefault(screen - peek)
            val covered = (screen - sheetTop).coerceAtLeast(peek)
            // The map viewport already excludes the top bar and the peek (viewportInsets below):
            // the fit padding only adds what the expanded sheet covers on top of that.
            state.fitRide(ride, PaddingValues(start = 32.dp, end = 32.dp, top = 16.dp, bottom = (covered - peek).coerceAtLeast(0.dp) + 24.dp))
        }
        BottomSheetScaffold(
            scaffoldState = scaffold,
            sheetPeekHeight = peek,
            sheetSwipeEnabled = ride != null,
            sheetDragHandle = if (ride != null) ({ BottomSheetDefaults.DragHandle() }) else null,
            sheetContent = {
                if (ride != null) {
                    RideSheet(
                        rides,
                        importActions,
                        routeActions,
                        onNavigate = if (navigation != null) ({ rides.navigating = true }) else null,
                        maxBodyHeight = screen * 0.5f,
                        onHeaderHeight = { header = it },
                        saved = {
                            if (store != null) {
                                SavedRow(
                                    store,
                                    rides.savedId,
                                    rides.ride?.name,
                                    onSave = if (rides.isPreview) ({ rides.ride?.let { r -> store.update { d -> d.save(r, null, store.time()).also { rides.savedId = it.second }.first } } }) else null,
                                ) { name ->
                                    rides.ride = rides.ride?.copy(name = name.trim().ifEmpty { null })
                                }
                            }
                        },
                        zones = store?.data?.privateZones.orEmpty(),
                    )
                }
            },
        ) { _ ->
            val showFab = ride == null && importActions != null && !rides.busy
            Box(Modifier.fillMaxSize()) {
                MaplibreMap(
                    modifier = Modifier.fillMaxSize(),
                    state = state,
                    interactions = interactions,
                    // Scale bar and attribution stay clear of the back button and of the sheet.
                    viewportInsets = PaddingValues(
                        top = statusBar + TopBarHeight,
                        bottom = when {
                            ride != null -> peek
                            showFab -> navBar + FabHeight
                            else -> navBar
                        },
                    ),
                ) {
                    include(MapOverlay.Default)
                    if (editable && ride != null) {
                        StopHandles(
                            ride.stops,
                            selectedStop,
                            state,
                            onSelect = { selected = it },
                            onMove = { i, point -> selected = i; edit { it.withStopMoved(i, point) } },
                        )
                    }
                }
                Column(
                    Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (store != null) HomeButton(onClick = { routeActions?.cancel?.invoke(); rides.goHome() })
                    ImportStatus(rides)
                }
                if (ride != null && selectedStop != null) {
                    StopToolbar(
                        ride,
                        selectedStop,
                        onReorder = { from, to -> selected = to; edit { it.withStopReordered(from, to) } },
                        onRemove = { i -> selected = null; edit { it.withStopRemoved(i) } },
                        onDone = { selected = null },
                        modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(start = 80.dp, top = 12.dp, end = 12.dp),
                    )
                }
                if (showFab && importActions != null) {
                    ImportFab(importActions, Modifier.align(Alignment.BottomEnd).padding(bottom = navBar).padding(16.dp))
                }
            }
        }
    }
}

/** Material drag handle: a 4 dp bar with 22 dp of padding above and below. */
private val DragHandleHeight: Dp = 48.dp

/** Room taken at the bottom of the empty map by the import FAB (56 dp plus margins). */
private val FabHeight: Dp = 88.dp

/** Room taken at the top of the map by the back button (56 dp plus margins). */
private val TopBarHeight: Dp = 80.dp

/** Starting values of the map. */
object MapDefaults {
    /** OpenFreeMap: OpenStreetMap data, no key, no account. */
    const val STYLE = "https://tiles.openfreemap.org/styles/liberty"
    const val START_LAT = 46.0
    const val START_LON = 10.0
    /** Overview: RidePlan is global, no privileged area. */
    const val START_ZOOM = 3.0
}
