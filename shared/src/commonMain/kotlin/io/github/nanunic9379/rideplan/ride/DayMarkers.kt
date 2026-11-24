package io.github.nanunic9379.rideplan.ride

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.format
import org.maplibre.compose.expressions.dsl.span
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource

/**
 * Where each day of a multi-day trip ends (#12): the day number and the point.
 * The last day ends at the finish, which already has its own marker, so it is left out.
 * Without a real road only the days that end at a stop are shown: a point "on the road" would
 * sit on a straight line that the map does not draw.
 */
internal fun dayEnds(ride: Ride): List<Pair<Int, LatLon>> {
    if (!Stages.active(ride)) return emptyList()
    val days = Stages.split(ride)
    if (days.size < 2) return emptyList()
    return days.dropLast(1)
        .filter { !ride.straightLines || it.endStop != null }
        .map { it.day to it.stops.last().point }
}

/** Hand-made GeoJSON: one point per day end, with the day number as text. */
internal fun dayEndsJson(ends: List<Pair<Int, LatLon>>): String =
    ends.joinToString(",", """{"type":"FeatureCollection","features":[""", "]}") { (day, p) ->
        """{"type":"Feature","properties":{"day":"$day"},"geometry":{"type":"Point","coordinates":[${p.lon},${p.lat}]}}"""
    }

/** Small numbered markers at the end of each day (#12), above the stops. */
@Composable
fun DayEndLayers(ride: Ride) {
    val json = remember(ride) { dayEndsJson(dayEnds(ride)) }
    val source = rememberGeoJsonSource(GeoJsonData.JsonString(json))
    val colors = MaterialTheme.colorScheme
    CircleLayer(
        id = "ride-day-ends",
        source = source,
        color = const(colors.inverseSurface),
        radius = const(9.dp),
        strokeColor = const(colors.inverseOnSurface),
        strokeWidth = const(2.dp),
    )
    SymbolLayer(
        id = "ride-day-numbers",
        source = source,
        textField = format(span(feature["day"].asString())),
        // A font of the map style: it is in the saved offline map too.
        textFont = const(listOf("Noto Sans Regular")),
        textSize = const(11.sp),
        textColor = const(colors.inverseOnSurface),
        textAllowOverlap = const(true),
        textIgnorePlacement = const(true),
    )
}
