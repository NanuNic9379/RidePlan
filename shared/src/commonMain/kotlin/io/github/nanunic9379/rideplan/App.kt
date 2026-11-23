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
import androidx.compose.runtime.remember
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
import io.github.nanunic9379.rideplan.ride.RideScreenState
import io.github.nanunic9379.rideplan.ride.fitRide
import io.github.nanunic9379.rideplan.route.RouteActions
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position

/**
 * L'app (#41): si apre sulla schermata iniziale con i tuoi giri; la mappa arriva
 * quando apri un giro o scegli "Esplora". Un giro aperto da un link va dritto sulla mappa.
 *
 * [rides]: giro importato da file o link (#27, #5), gestito dalla piattaforma.
 * [store]: profilo e giri salvati sul telefono; null = solo la mappa, come prima.
 * [importActions]: come importare su questa piattaforma; null = pulsanti nascosti.
 * [routeActions]: download dei dati e calcolo del percorso (#3); null = non disponibile.
 * [overlay]: elementi della singola piattaforma sopra tutto (es. l'avviso di aggiornamento, #30).
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
        // Ogni giro aperto finisce in "I tuoi giri" e resta aggiornato (percorso, preferenze, nome).
        // Un giro ricevuto con un link resta in anteprima finché non lo salvi tu (#48).
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
    LaunchedEffect(ride, rides.panelOpen) { if (ride != null) state.fitRide(ride, rides.panelOpen) }

    Box(Modifier.fillMaxSize()) {
        MaplibreMap(modifier = Modifier.fillMaxSize(), state = state)
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

/** Valori di partenza della mappa. */
object MapDefaults {
    /** OpenFreeMap: dati OpenStreetMap, nessuna chiave, nessun account. */
    const val STYLE = "https://tiles.openfreemap.org/styles/liberty"
    const val START_LAT = 46.0
    const val START_LON = 10.0
    /** Vista d'insieme: RidePlan e' globale, nessuna zona privilegiata. */
    const val START_ZOOM = 3.0
}
