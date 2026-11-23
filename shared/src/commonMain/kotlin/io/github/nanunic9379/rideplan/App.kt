package io.github.nanunic9379.rideplan

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.nanunic9379.rideplan.profile.BackupActions
import io.github.nanunic9379.rideplan.profile.HomeButton
import io.github.nanunic9379.rideplan.profile.HomeScreen
import io.github.nanunic9379.rideplan.profile.ProfileScreen
import io.github.nanunic9379.rideplan.profile.ProfileStore
import io.github.nanunic9379.rideplan.profile.SavedRow
import io.github.nanunic9379.rideplan.profile.WelcomeScreen
import io.github.nanunic9379.rideplan.ride.ImportActions
import io.github.nanunic9379.rideplan.ride.Page
import io.github.nanunic9379.rideplan.ride.RideLayers
import io.github.nanunic9379.rideplan.ride.RidePanel
import io.github.nanunic9379.rideplan.ride.Ride
import io.github.nanunic9379.rideplan.ride.RideScreenState
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
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
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
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { _ ->
            Box(Modifier.fillMaxSize()) {
                when {
                    store == null || rides.onMap -> MapScreen(rides, store, importActions, routeActions)
                    !store.data.profile.welcomed -> Surface(Modifier.fillMaxSize()) { WelcomeScreen(store) }
                    rides.page == Page.PROFILE -> Surface(Modifier.fillMaxSize()) { ProfileScreen(store, onBack = { rides.page = Page.HOME }, backup = backupActions) }
                    else -> Surface(Modifier.fillMaxSize()) { HomeScreen(store, rides, importActions, snackbar) }
                }
                overlay()
            }
        }
    }
}

@Composable
private fun MapScreen(rides: RideScreenState, store: ProfileStore?, importActions: ImportActions?, routeActions: RouteActions?) {
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
    // After an edit on the map the camera stays where the rider is working.
    LaunchedEffect(ride, rides.panelOpen) { if (ride != null && !rides.stopsEdited) state.fitRide(ride, rides.panelOpen) }

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

    Box(Modifier.fillMaxSize()) {
        MaplibreMap(modifier = Modifier.fillMaxSize(), state = state, interactions = interactions) {
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
        if (ride != null && selectedStop != null) {
            StopToolbar(
                ride,
                selectedStop,
                onReorder = { from, to -> selected = to; edit { it.withStopReordered(from, to) } },
                onRemove = { i -> selected = null; edit { it.withStopRemoved(i) } },
                onDone = { selected = null },
                modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(start = 72.dp, top = 12.dp, end = 12.dp),
            )
        }
        if (store != null) {
            HomeButton(
                onClick = { routeActions?.cancel?.invoke(); rides.goHome() },
                modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(12.dp),
            )
        }
        RidePanel(
            rides,
            importActions,
            routeActions,
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
        )
    }
}

/** Starting values of the map. */
object MapDefaults {
    /** OpenFreeMap: OpenStreetMap data, no key, no account. */
    const val STYLE = "https://tiles.openfreemap.org/styles/liberty"
    const val START_LAT = 46.0
    const val START_LON = 10.0
    /** Overview: RidePlan is global, no privileged area. */
    const val START_ZOOM = 3.0
}
