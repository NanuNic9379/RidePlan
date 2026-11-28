package io.github.nanunic9379.rideplan

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.nanunic9379.rideplan.ui.AppIcons
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.eq
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.VectorSource
import org.maplibre.compose.sources.getBaseSource
import org.maplibre.compose.expressions.value.SymbolAnchor

/** Replaces the basemap's tiny category glyphs with the same clear pin treatment as photo spots. */
@Composable
internal fun MapPoiMarkerLayers() {
    val source = getBaseSource<VectorSource>("openmaptiles") ?: return
    val fuel = rememberVectorPainter(AppIcons.FuelMarker)
    val viewpoint = rememberVectorPainter(AppIcons.ViewpointMarker)
    val camera = rememberVectorPainter(AppIcons.SpeedCameraMarker)
    MarkerLayer("poi-fuel-markers", source, "fuel", fuel)
    MarkerLayer("poi-viewpoint-markers", source, "viewpoint", viewpoint, subclass = true)
    MarkerLayer("poi-speed-camera-markers", source, "speed_camera", camera, subclass = true)
}

@Composable
private fun MarkerLayer(id: String, source: VectorSource, value: String, painter: androidx.compose.ui.graphics.painter.Painter, subclass: Boolean = false) {
    SymbolLayer(
        id = id,
        source = source,
        sourceLayer = "poi",
        minZoom = 13.5f,
        filter = (if (subclass) feature["subclass"] else feature["class"]) eq const(value),
        iconImage = image(painter, size = DpSize(40.dp, 48.dp)),
        iconAnchor = const(SymbolAnchor.Bottom),
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true),
    )
}
