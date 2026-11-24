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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import io.github.nanunic9379.rideplan.profile.ProfileStore
import io.github.nanunic9379.rideplan.profile.SavedRow
import io.github.nanunic9379.rideplan.profile.ThemeMode
import io.github.nanunic9379.rideplan.profile.WelcomeScreen
import io.github.nanunic9379.rideplan.personal.LocalUnits
import io.github.nanunic9379.rideplan.profile.unitsFor
import io.github.nanunic9379.rideplan.personal.MainNavBar
import io.github.nanunic9379.rideplan.personal.PersonalArea
import io.github.nanunic9379.rideplan.personal.PlatformActions
import io.github.nanunic9379.rideplan.ride.ImportActions
import io.github.nanunic9379.rideplan.ride.ImportFab
import io.github.nanunic9379.rideplan.ride.ImportStatus
import io.github.nanunic9379.rideplan.ride.RideLayers
import io.github.nanunic9379.rideplan.ride.Ride
import io.github.nanunic9379.rideplan.ride.RideScreenState
import io.github.nanunic9379.rideplan.ride.RideSheet
import io.github.nanunic9379.rideplan.ride.StopHandles
import io.github.nanunic9379.rideplan.personal.updateNotice
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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.onSizeChanged
import io.github.nanunic9379.rideplan.resources.Res
import io.github.nanunic9379.rideplan.resources.plan_title
import io.github.nanunic9379.rideplan.resources.explore_locate
import io.github.nanunic9379.rideplan.resources.explore_locate_failed
import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.pointsJson
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds
import org.maplibre.compose.map.MapState
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import io.github.nanunic9379.rideplan.ride.PlanDraft
import io.github.nanunic9379.rideplan.ride.PlanLeaveDialog
import io.github.nanunic9379.rideplan.ride.PlanSheet
import io.github.nanunic9379.rideplan.ride.PlanTopBar
import io.github.nanunic9379.rideplan.ride.canRemoveStop
import io.github.nanunic9379.rideplan.ride.currentPosition
import io.github.nanunic9379.rideplan.route.RidingModeChips
import io.github.nanunic9379.rideplan.route.RouteSettingsSheet
import io.github.nanunic9379.rideplan.ui.AppIcons
import io.github.nanunic9379.rideplan.ui.Touch
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import org.jetbrains.compose.resources.stringResource
import org.maplibre.compose.camera.CameraAnimation
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
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
    mapViews: MapViewMemory? = null,
    /** Turn-by-turn navigation on this platform (#5); null = no "Navigate" button. */
    navigation: NavigationPlatform? = null,
    platform: PlatformActions? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val settings = store?.data?.settings
    val dark = when (settings?.theme) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        else -> isSystemInDarkTheme()
    }
    RidePlanTheme(dark = dark) {
      val region = platform?.region ?: androidx.compose.ui.text.intl.Locale.current.region
      val units = (settings ?: io.github.nanunic9379.rideplan.profile.AppSettings()).unitsFor(region)
      CompositionLocalProvider(
        LocalUnits provides units,
        io.github.nanunic9379.rideplan.personal.LocalFuelUnits provides io.github.nanunic9379.rideplan.profile.Regions.fuel(units, region),
      ) {
        val snackbar = remember { SnackbarHostState() }
        val ride = rides.ride
        // Every opened ride ends up in "Your rides" and stays up to date (route, preferences, name).
        // A ride received by link waits in "Received" until you keep or discard it (#48, #21).
        // A ride being planned (#33) is saved only on "Done", when the plan ends.
        val notices = rememberCoroutineScope()
        LaunchedEffect(ride, rides.plan == null) {
            if (ride != null && store != null && rides.plan == null) {
                val now = store.time()
                store.update { d ->
                    val (fresh, id) = if (rides.isPreview) d.receive(ride, now) else d.save(ride, rides.savedId, now)
                    rides.savedId = id
                    fresh
                }
                // A newer version of a ride you had replaced it (#11): say so on the map.
                updateNotice(store.data.rides.firstOrNull { it.id == rides.savedId }, now)?.let { notices.launch { snackbar.showSnackbar(it) } }
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
                    store == null || rides.ride != null -> MapScreen(rides, store, importActions, routeActions, mapViews, navigation, tabs = false, rideMap = platform?.rideMap)
                    !store.data.profile.welcomed -> Surface(Modifier.fillMaxSize()) { WelcomeScreen(store) }
                    rides.onMap -> Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f)) { MapScreen(rides, store, importActions, routeActions, mapViews, tabs = true) }
                        MainNavBar(rides)
                    }
                    else -> PersonalArea(store, rides, importActions, backupActions, platform, snackbar)
                }
                overlay()
            }
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
    mapViews: MapViewMemory?,
    navigation: NavigationPlatform? = null,
    /** Explore with the bottom bar under the map: it already keeps clear of the system bar. */
    tabs: Boolean,
    rideMap: io.github.nanunic9379.rideplan.route.RideMapActions? = null,
) {
    val ride = rides.ride
    val plan = rides.plan
    // Explore opens where you looked last, or around your rides: not on a whole continent.
    // Explore (#40): your position, shown as a dot once you ask for it.
    var me by remember { mutableStateOf<LatLon?>(null) }
    val start = remember { MapStart.of(mapViews?.load(), store?.data?.rides.orEmpty()) }
    val state = rememberMapState(
        baseStyle = BaseStyle.Uri(MapDefaults.STYLE),
        initialCameraPosition = CameraPosition(
            center = Position(latitude = start.lat, longitude = start.lon),
            zoom = start.zoom,
        ),
    ) {
        if (ride != null) RideLayers(ride) else me?.let { MyPositionLayer(it) }
    }
    // The area on screen is remembered each time the map stops moving (not the world overview).
    LaunchedEffect(state, mapViews) {
        if (mapViews == null) return@LaunchedEffect
        snapshotFlow { if (state.isCameraMoving) null else state.cameraPosition }
            .filter { it != null && it.zoom >= MapStart.RIDE_ZOOM - 4 }
            .collect { p -> p?.let { mapViews.save(MapView(it.center.latitude, it.center.longitude, it.zoom)) } }
    }
    // Editing the stops on the map (#2): only rides made of stops, and not while data is downloading.
    // A ride being planned (#33) is always editable: the data offer comes after the taps.
    val editable = ride != null && routeActions != null &&
        (plan != null || (ride.stopsEditable && rides.route !is RouteState.Downloading))
    var selected by remember { mutableStateOf<Int?>(null) }
    val selectedStop = selected?.takeIf { editable && ride != null && it in ride.stops.indices }
    fun edit(change: (Ride) -> Ride) {
        val current = rides.ride ?: return
        val fresh = change(current)
        if (fresh === current) return
        rides.stopsEdited = true
        routeActions?.editStops?.invoke(fresh)
    }
    // Planning (#33): every change goes through the draft (so it can be undone) and is routed again.
    fun replan(change: (PlanDraft) -> PlanDraft) {
        val old = rides.plan ?: return
        val fresh = change(old)
        if (fresh == old) return
        rides.plan = fresh
        if (fresh.points != old.points) routeActions?.editStops?.invoke(fresh.ride())
    }
    val interactions = rememberStopInteractions(
        onAdd = when {
            !editable -> null
            plan != null -> ({ point -> selected = null; replan { it.withTap(point) } })
            else -> ({ point -> selected = null; edit { it.withStopAdded(point) } })
        },
        onTapMap = { point ->
            when {
                selected != null -> { selected = null; true }
                rides.plan != null && point != null && routeActions != null -> { replan { it.withTap(point) }; true }
                else -> false
            }
        },
    )
    val scope = rememberCoroutineScope()
    var locating by remember { mutableStateOf(false) }
    var locateFailed by remember { mutableStateOf(false) }
    var exploreLocating by remember { mutableStateOf(false) }
    var exploreFailed by remember { mutableStateOf(false) }
    var modesOpen by remember { mutableStateOf(false) }
    var planSheet by remember { mutableStateOf(0.dp) }

    val sheet = rememberStandardBottomSheetState(
        initialValue = if (rides.panelOpen) SheetValue.Expanded else SheetValue.PartiallyExpanded,
    )
    val scaffold = rememberBottomSheetScaffoldState(sheet)
    val density = LocalDensity.current
    val navBar = if (tabs) 0.dp else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // Height of the sheet header, measured: the collapsed sheet shows exactly that.
    var header by remember { mutableStateOf(0.dp) }
    // While planning the ride sheet stays away: the plan has its own sheet (#33).
    val rideSheet = ride != null && plan == null
    val peek = if (!rideSheet) 0.dp else DragHandleHeight + header + navBar

    // Sheet <-> state, both ways: the platform opens a new ride expanded, the user drags it.
    LaunchedEffect(rides.panelOpen, rideSheet) {
        if (!rideSheet) return@LaunchedEffect
        if (rides.panelOpen) sheet.expand() else sheet.partialExpand()
    }
    LaunchedEffect(sheet) {
        snapshotFlow { sheet.currentValue }.collect { if (rides.ride != null && rides.plan == null) rides.panelOpen = it == SheetValue.Expanded }
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
            sheetSwipeEnabled = rideSheet,
            sheetDragHandle = if (rideSheet) ({ BottomSheetDefaults.DragHandle() }) else null,
            sheetContent = {
                if (ride != null && rideSheet) {
                    RideSheet(
                        rides,
                        importActions,
                        routeActions,
                        onNavigate = if (navigation != null) ({ rides.navigating = true }) else null,
                        bike = store?.data?.defaultBike,
                        maxBodyHeight = screen * 0.5f,
                        onHeaderHeight = { header = it },
                        offlineMap = { r -> rideMap?.let { io.github.nanunic9379.rideplan.route.RideMapSection(r, it) } },
                        saved = {
                            if (store != null) {
                                SavedRow(
                                    store,
                                    rides.savedId,
                                    rides.ride?.name,
                                    onSave = store.data.rides.firstOrNull { it.id == rides.savedId }?.takeIf { it.received != null }
                                        ?.let { saved -> { store.update { d -> d.keep(saved.id) } } },
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
                            plan != null -> planSheet
                            ride != null -> peek
                            showFab -> navBar + FabHeight
                            else -> navBar
                        },
                    ),
                ) {
                    include(CompactMapOverlay)
                    if (editable && ride != null) {
                        StopHandles(
                            ride.stops,
                            selectedStop,
                            state,
                            onSelect = { selected = it },
                            onMove = { i, point ->
                                selected = i
                                if (plan != null) replan { it.withMoved(i, point) } else edit { it.withStopMoved(i, point) }
                            },
                        )
                    }
                }
                StatusBarScrim()
                Column(
                    Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (plan != null) {
                        PlanTopBar(
                            plan,
                            onBack = { if (!rides.askBeforeLeavingPlan()) { routeActions?.cancel?.invoke(); rides.goHome() } },
                            onUndo = { selected = null; replan { it.undone() } },
                        )
                        RidingModeChips(
                            rides.options,
                            onPick = { mode -> routeActions?.changeOptions?.invoke(mode.options) },
                            onMore = { modesOpen = true },
                        )
                    } else if (store != null && !tabs) {
                        HomeButton(onClick = { routeActions?.cancel?.invoke(); rides.goHome() })
                    }
                    ImportStatus(rides)
                }
                if (ride != null && selectedStop != null) {
                    StopToolbar(
                        ride,
                        selectedStop,
                        onReorder = { from, to ->
                            selected = to
                            if (plan != null) replan { it.withReordered(from, to) } else edit { it.withStopReordered(from, to) }
                        },
                        onRemove = { i -> selected = null; if (plan != null) replan { it.withRemoved(i) } else edit { it.withStopRemoved(i) } },
                        onDone = { selected = null },
                        // While planning the toolbar goes under the top bar and the mode chips.
                        modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding()
                            .padding(start = 80.dp, top = if (plan != null) PlanTopHeight else 12.dp, end = 12.dp),
                        canRemove = if (plan != null) true else ride.canRemoveStop,
                    )
                }
                if (plan != null && ride != null) {
                    PlanSheet(
                        plan,
                        ride,
                        rides.route,
                        routeActions,
                        canLocate = navigation != null,
                        locating = locating,
                        locateFailed = locateFailed,
                        onName = { name -> rides.plan = rides.plan?.withName(name) },
                        onHere = {
                            val platform = navigation
                            if (platform != null) {
                                locating = true
                                locateFailed = false
                                scope.launch {
                                    val here = platform.currentPosition()
                                    locating = false
                                    if (here == null) {
                                        locateFailed = true
                                    } else {
                                        replan { it.withStartHere(here) }
                                        state.flyTo(here)
                                    }
                                }
                            }
                        },
                        onRoundTrip = { replan { it.withRoundTrip() } },
                        onDone = { selected = null; rides.finishPlan() },
                        modifier = Modifier.align(Alignment.BottomCenter).onSizeChanged { planSheet = with(density) { it.height.toDp() } },
                    )
                    if (rides.planLeaving) {
                        PlanLeaveDialog(
                            onKeep = { rides.planLeaving = false },
                            onDiscard = { routeActions?.cancel?.invoke(); rides.goHome() },
                        )
                    }
                    if (modesOpen && routeActions != null) {
                        RouteSettingsSheet(
                            ride = ride,
                            title = plan.name.ifBlank { stringResource(Res.string.plan_title) },
                            applied = rides.options,
                            presets = rides.presets,
                            bike = store?.data?.defaultBike,
                            onApply = { modesOpen = false; routeActions.changeOptions(it) },
                            onSavePreset = routeActions.savePreset,
                            onDeletePreset = routeActions.deletePreset,
                            onDismiss = { modesOpen = false },
                        )
                    }
                }
                if (showFab && store != null && routeActions != null) {
                    // Explore (#33): the main action on the empty map is planning a ride.
                    ExtendedFloatingActionButton(
                        onClick = { rides.startPlan() },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        icon = { Icon(AppIcons.Add, contentDescription = null) },
                        text = { Text(stringResource(Res.string.plan_title), style = MaterialTheme.typography.labelLarge) },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = navBar).padding(16.dp).heightIn(min = Touch.Glove),
                    )
                } else if (showFab && importActions != null) {
                    ImportFab(importActions, Modifier.align(Alignment.BottomEnd).padding(bottom = navBar).padding(16.dp))
                }
                if (ride == null && navigation != null) {
                    // Explore (#40): back to where you are, above the main action.
                    SmallFloatingActionButton(
                        onClick = {
                            if (!exploreLocating) {
                                exploreLocating = true
                                exploreFailed = false
                                scope.launch {
                                    val here = navigation.currentPosition()
                                    exploreLocating = false
                                    if (here == null) exploreFailed = true else { me = here; state.flyTo(here) }
                                }
                            }
                        },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = navBar + (if (showFab) FabHeight else 0.dp)).padding(16.dp).size(Touch.Glove),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ) {
                        if (exploreLocating) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(AppIcons.MyLocation, contentDescription = stringResource(Res.string.explore_locate))
                    }
                    if (exploreFailed) {
                        LaunchedEffect(Unit) { delay(4.seconds); exploreFailed = false }
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.align(Alignment.BottomStart).padding(bottom = navBar).padding(16.dp).padding(end = Touch.Glove + 16.dp),
                        ) {
                            Text(stringResource(Res.string.explore_locate_failed), Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

/** Room taken by the plan's top bar and mode chips (#33): the stop toolbar goes under them. */
private val PlanTopHeight: Dp = 148.dp

/** Zoom when "Use my position" moves the map: a few villages around, enough to tap the next stop. */
private const val PLAN_ZOOM = 11.0

/** Moves the map to a position at [PLAN_ZOOM] or closer, keeping the current angle and padding. */
private suspend fun MapState.flyTo(here: LatLon) {
    animateCamera(
        CameraUpdate(
            center = Position(latitude = here.lat, longitude = here.lon),
            zoom = maxOf(cameraPosition.zoom, PLAN_ZOOM),
            bearing = cameraPosition.bearing,
            pitch = cameraPosition.pitch,
            padding = cameraPosition.padding,
        ),
        CameraAnimation.Ease(CameraAnimation.Ease.Standard) { duration = 800.milliseconds },
    )
}

/** Your position on the Explore map (#40): the same blue dot as in navigation. */
@Composable
private fun MyPositionLayer(me: LatLon) {
    val source = rememberGeoJsonSource(GeoJsonData.JsonString(pointsJson(listOf(me))))
    CircleLayer(
        id = "explore-me", source = source, color = const(Color(0xFF1A73E8)), radius = const(9.dp),
        strokeColor = const(Color.White), strokeWidth = const(3.dp),
    )
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
