package io.github.nanunic9379.rideplan.profile

import io.github.nanunic9379.rideplan.ride.LatLon
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/**
 * The shape of one or more rides drawn in a box, without a map (#21): a preview that works
 * offline and downloads nothing. Simple equirectangular projection, north up, the same scale
 * on both axes, centred in the box.
 */
object Sketch {
    data class Pt(val x: Float, val y: Float)

    fun project(lines: List<List<LatLon>>, width: Float, height: Float, padding: Float): List<List<Pt>> {
        val all = lines.flatten()
        if (all.isEmpty() || width <= 2 * padding || height <= 2 * padding) return lines.map { emptyList() }
        val minLat = all.minOf { it.lat }
        val maxLat = all.maxOf { it.lat }
        val minLon = all.minOf { it.lon }
        val maxLon = all.maxOf { it.lon }
        val k = cos((minLat + maxLat) / 2 * PI / 180)
        val spanX = max((maxLon - minLon) * k, 1e-9)
        val spanY = max(maxLat - minLat, 1e-9)
        val w = width - 2 * padding
        val h = height - 2 * padding
        val scale = min(w / spanX, h / spanY)
        // Centre on the real extent: a single point or a straight north-south line sits in the middle.
        val offX = padding + (w - (maxLon - minLon) * k * scale) / 2
        val offY = padding + (h - (maxLat - minLat) * scale) / 2
        return lines.map { line ->
            line.map { p ->
                Pt(
                    (offX + (p.lon - minLon) * k * scale).toFloat(),
                    (offY + (maxLat - p.lat) * scale).toFloat(),
                )
            }
        }
    }
}
