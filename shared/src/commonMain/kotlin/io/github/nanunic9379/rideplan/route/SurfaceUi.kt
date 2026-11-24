package io.github.nanunic9379.rideplan.route

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.nanunic9379.rideplan.resources.Res
import io.github.nanunic9379.rideplan.resources.route_unpaved
import io.github.nanunic9379.rideplan.resources.route_unpaved_dirt
import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.Ride
import org.jetbrains.compose.resources.stringResource
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.eq
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import kotlin.math.roundToInt

/**
 * How much of the computed ride is unpaved (#9), under the route notes in the ride sheet.
 * Nothing below [RoadSurfaces.WARN_M]; with dirt (not only gravel) it is highlighted.
 */
@Composable
fun SurfaceNote(surfaces: RoadSurfaces) {
    if (!surfaces.worthWarning) return
    val dirt = surfaces.dirtM >= RoadSurfaces.WARN_M
    val text = if (dirt) {
        stringResource(Res.string.route_unpaved_dirt, lengthText(surfaces.unpavedM), lengthText(surfaces.dirtM), lengthText(surfaces.longestM))
    } else {
        stringResource(Res.string.route_unpaved, lengthText(surfaces.unpavedM), lengthText(surfaces.longestM))
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (dirt) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** [m] metres in the rider's units (km or miles, #15), through the app's one formatter. */
@Composable
private fun lengthText(m: Double): String = io.github.nanunic9379.rideplan.personal.distanceText(m / 1000)

/** Kilometres for the rider: one decimal below 10 km ("0.4", "3.2"), whole above ("12"). */
fun kmText(m: Double): String {
    val km = m / 1000
    if (km >= 10) return "${km.roundToInt()}"
    val tenths = (km * 10).roundToInt().coerceAtLeast(1)
    return "${tenths / 10}.${tenths % 10}"
}

/** Unpaved stretches drawn darker over the ride's line (#9): gravel brown, dirt almost black. */
private val GravelColor = Color(0xFF6D4C41)
private val DirtColor = Color(0xFF3E2723)

@Composable
fun RoughLayers(ride: Ride) {
    val json = remember(ride.track, ride.surfaces) { roughJson(ride.surfaces.lines(ride.track)) }
    val source = rememberGeoJsonSource(GeoJsonData.JsonString(json))
    LineLayer(id = "ride-gravel", source = source, filter = isKind(Rough.GRAVEL), color = const(GravelColor), width = const(4.dp))
    LineLayer(id = "ride-dirt", source = source, filter = isKind(Rough.DIRT), color = const(DirtColor), width = const(4.dp))
}

private fun isKind(kind: Rough) =
    feature["kind"].asString() eq const(kind.name.lowercase())

/** Hand-made GeoJSON: one line per unpaved stretch, with its kind ("gravel" or "dirt"). */
internal fun roughJson(lines: List<Pair<Rough, List<LatLon>>>): String =
    lines.joinToString(",", """{"type":"FeatureCollection","features":[""", "]}") { (kind, points) ->
        val coords = points.joinToString(",") { "[${it.lon},${it.lat}]" }
        """{"type":"Feature","properties":{"kind":"${kind.name.lowercase()}"},"geometry":{"type":"LineString","coordinates":[$coords]}}"""
    }
