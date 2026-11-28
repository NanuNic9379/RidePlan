package io.github.nanunic9379.rideplan.nav

import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.distanceKm
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sqrt

/** A tight bend detected from the road line, measured from its start. */
data class Hairpin(val alongM: Double, val radiusM: Double, val point: LatLon)

/** One spoken warning: either a single bend or a short series of bends. */
data class HairpinWarning(val fromM: Double, val toM: Double, val count: Int, val point: LatLon) {
    val isSeries: Boolean get() = count >= SERIES_MIN_COUNT

    companion object {
        const val SERIES_MIN_COUNT = 3
        const val SERIES_MAX_M = 5000.0
    }
}

/** Finds small-radius, sharp bends on a route and groups dense sequences into one warning. */
object Hairpins {
    const val RADIUS_MAX_M = 25.0
    const val TURN_MIN_DEGREES = 90.0
    const val WARNING_MIN_M = 120.0
    const val WARNING_MAX_M = 450.0

    /** Turn the configured speed into a warning lead distance (15 seconds, clamped). */
    fun warningDistanceM(speedMps: Double?): Double =
        ((speedMps ?: 16.0).coerceAtLeast(0.0) * 15.0).coerceIn(WARNING_MIN_M, WARNING_MAX_M)

    fun find(line: List<LatLon>): List<Hairpin> {
        if (line.size < 3) return emptyList()
        val points = sample(line)
        if (points.size < WINDOW_POINTS * 2 + 1) return emptyList()
        val candidates = ArrayList<Hairpin>()
        for (i in WINDOW_POINTS until points.size - WINDOW_POINTS) {
            val a = points[i - WINDOW_POINTS].first
            val b = points[i].first
            val c = points[i + WINDOW_POINTS].first
            val turn = turnDegrees(a, b, c)
            if (turn < TURN_MIN_DEGREES) continue
            val radius = circumradius(a, b, c) ?: continue
            if (radius <= RADIUS_MAX_M) candidates += Hairpin(points[i].second, radius, points[i].first)
        }
        // The sliding window marks several samples around the same bend. Keep its first point,
        // so navigation can warn before reaching the tightest part.
        val bends = ArrayList<Hairpin>()
        for (candidate in candidates) {
            val last = bends.lastOrNull()
            if (last == null || candidate.alongM - last.alongM > MERGE_BEND_M) bends += candidate
        }
        return bends
    }

    fun warnings(line: List<LatLon>): List<HairpinWarning> {
        return group(find(line))
    }

    fun group(bends: List<Hairpin>): List<HairpinWarning> {
        if (bends.isEmpty()) return emptyList()
        val result = ArrayList<HairpinWarning>()
        var start = 0
        while (start < bends.size) {
            var end = start + 1
            while (end < bends.size && bends[end].alongM - bends[start].alongM <= HairpinWarning.SERIES_MAX_M) end++
            val count = end - start
            if (count >= HairpinWarning.SERIES_MIN_COUNT) {
                result += HairpinWarning(bends[start].alongM, bends[end - 1].alongM, count, bends[start].point)
                start = end
            } else {
                result += HairpinWarning(bends[start].alongM, bends[start].alongM, 1, bends[start].point)
                start++
            }
        }
        return result
    }

    private fun turnDegrees(a: LatLon, b: LatLon, c: LatLon): Double {
        val (ax, ay) = xy(a, b)
        val (cx, cy) = xy(c, b)
        // incoming points from a to b; outgoing from b to c
        val inX = -ax
        val inY = -ay
        val cross = inX * cy - inY * cx
        val dot = inX * cx + inY * cy
        return abs(atan2(cross, dot) * 180.0 / PI)
    }

    private fun circumradius(a: LatLon, b: LatLon, c: LatLon): Double? {
        val (ax, ay) = xy(a, b)
        val (cx, cy) = xy(c, b)
        val ab = sqrt(ax * ax + ay * ay)
        val bc = sqrt(cx * cx + cy * cy)
        val ac = sqrt((cx - ax) * (cx - ax) + (cy - ay) * (cy - ay))
        val twiceArea = abs(ax * cy - ay * cx)
        if (twiceArea < MIN_TWICE_AREA) return null
        return ab * bc * ac / (2.0 * twiceArea)
    }

    /** Local equirectangular metres relative to [origin]. */
    private fun xy(point: LatLon, origin: LatLon): Pair<Double, Double> {
        val latScale = METRES_PER_DEGREE
        val lonScale = METRES_PER_DEGREE * cos(origin.lat * PI / 180.0)
        return (point.lon - origin.lon) * lonScale to (point.lat - origin.lat) * latScale
    }

    private fun sample(line: List<LatLon>): List<Pair<LatLon, Double>> {
        val out = ArrayList<Pair<LatLon, Double>>()
        var segment = 1
        var segmentStart = 0.0
        var segmentEnd = 0.0
        var nextSample = 0.0
        while (segment < line.size) {
            val a = line[segment - 1]
            val b = line[segment]
            val lengthM = distanceKm(a, b) * 1000.0
            segmentEnd = segmentStart + lengthM
            while (nextSample <= segmentEnd && lengthM > 0.0) {
                if (nextSample >= segmentStart) {
                    val fraction = ((nextSample - segmentStart) / lengthM).coerceIn(0.0, 1.0)
                    out += LatLon(a.lat + (b.lat - a.lat) * fraction, a.lon + (b.lon - a.lon) * fraction) to nextSample
                    nextSample += SAMPLE_M
                } else {
                    nextSample = segmentStart
                }
            }
            segmentStart = segmentEnd
            segment++
        }
        if (out.isEmpty() || out.last().first != line.last()) out += line.last() to segmentEnd
        return out
    }

    private const val SAMPLE_M = 10.0
    private const val WINDOW_POINTS = 5
    private const val MERGE_BEND_M = 80.0
    private const val MIN_TWICE_AREA = 1.0
    private const val METRES_PER_DEGREE = 111_320.0
}
