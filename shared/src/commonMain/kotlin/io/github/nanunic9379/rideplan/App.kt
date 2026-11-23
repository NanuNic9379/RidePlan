package io.github.nanunic9379.rideplan

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position

/**
 * Schermata iniziale: solo la mappa, subito, senza benvenuto ne' permessi (issue #1).
 * Lo stile online e' provvisorio: la mappa offline arriva con la #2.
 *
 * [overlay]: elementi della singola piattaforma sopra la mappa (es. l'avviso di aggiornamento
 * della beta Android, #30). Su iPhone resta vuoto.
 */
@Composable
fun App(overlay: @Composable BoxScope.() -> Unit = {}) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        val state = rememberMapState(
            baseStyle = BaseStyle.Uri(MapDefaults.STYLE),
            initialCameraPosition = CameraPosition(
                center = Position(latitude = MapDefaults.START_LAT, longitude = MapDefaults.START_LON),
                zoom = MapDefaults.START_ZOOM,
            ),
        )
        Box(Modifier.fillMaxSize()) {
            MaplibreMap(modifier = Modifier.fillMaxSize(), state = state)
            overlay()
        }
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
