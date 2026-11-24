package io.github.nanunic9379.rideplan.nav

import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.distanceKm
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Where a position falls on the line. [along]: metres from the start; [offset]: metres off the line. */
data class Snap(val along: Double, val offset: Double, val point: LatLon, val segment: Int)

/**
 * The road to follow (#5), with the distance from the start at every point.
 * Distances to a segment use a flat projection around the position: errors are below a metre
 * at the scale of one segment, and it is fast enough to run on every GPS fix.
 */
class RouteLine(val points: List<LatLon>) {
    /** Metres from the start at each point. */
    val along: DoubleArray = DoubleArray(points.size).also { a ->
        for (i in 1 until points.size) a[i] = a[i - 1] + distanceKm(points[i - 1], points[i]) * 1000
    }

    val length: Double get() = if (along.isEmpty()) 0.0 else along.last()

    /**
     * Nearest point of the line between [from] and [to] metres from the start (never before [from]);
     * null if the line has fewer than two points.
     */
    fun snap(p: LatLon, from: Double = 0.0, to: Double = length): Snap? {
        if (points.size < 2) return null
        var best: Snap? = null
        for (i in firstSegment(from) until points.size - 1) {
            if (along[i] > to) break
            val s = project(p, i, from)
            if (best == null || s.offset < best.offset) best = s
        }
        return best
    }

    /**
     * The first stretch of the line, from [from] onwards, that passes within [maxOffset] of [p]:
     * its nearest point. Unlike [snap] it does not jump to a later pass on the same road
     * (out-and-back rides, loops); null if the line never comes that close.
     */
    fun firstNear(p: LatLon, maxOffset: Double, from: Double = 0.0): Snap? {
        if (points.size < 2) return null
        var best: Snap? = null
        for (i in firstSegment(from) until points.size - 1) {
            val s = project(p, i)
            if (s.offset <= maxOffset) {
                if (best == null || s.offset <= best.offset) best = s
            } else if (best != null) {
                break
            }
        }
        return best
    }

    /** Direction of travel at [at] metres, degrees from north. */
    fun bearingAt(at: Double): Double {
        if (points.size < 2) return 0.0
        val i = firstSegment(at).coerceAtMost(points.size - 2)
        return bearing(points[i], points[i + 1])
    }

    /** Index of the segment that contains [at] metres (binary search). */
    private fun firstSegment(at: Double): Int {
        if (at <= 0) return 0
        var lo = 0
        var hi = points.size - 1
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (along[mid + 1] < at) lo = mid + 1 else hi = mid
        }
        return lo.coerceIn(0, maxOf(0, points.size - 2))
    }

    /** Nearest point of segment [i] to [p], not before [minAlong] metres. */
    private fun project(p: LatLon, i: Int, minAlong: Double = 0.0): Snap {
        val a = points[i]
        val b = points[i + 1]
        val kx = cos(p.lat * PI / 180) * M_PER_DEG
        val ax = (a.lon - p.lon) * kx
        val ay = (a.lat - p.lat) * M_PER_DEG
        val bx = (b.lon - p.lon) * kx
        val by = (b.lat - p.lat) * M_PER_DEG
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val segment = along[i + 1] - along[i]
        val tMin = if (segment > 0) ((minAlong - along[i]) / segment).coerceIn(0.0, 1.0) else 0.0
        val t = if (len2 == 0.0) tMin else (-(ax * dx + ay * dy) / len2).coerceIn(tMin, 1.0)
        val x = ax + t * dx
        val y = ay + t * dy
        val point = LatLon(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
        return Snap(along[i] + (along[i + 1] - along[i]) * t, sqrt(x * x + y * y), point, i)
    }

    companion object {
        private const val M_PER_DEG = 6371008.8 * PI / 180

        /** Initial bearing from [a] to [b], degrees from north (0..360). */
        fun bearing(a: LatLon, b: LatLon): Double {
            val la1 = a.lat * PI / 180
            val la2 = b.lat * PI / 180
            val dLon = (b.lon - a.lon) * PI / 180
            val y = sin(dLon) * cos(la2)
            val x = cos(la1) * sin(la2) - sin(la1) * cos(la2) * cos(dLon)
            return (atan2(y, x) * 180 / PI + 360) % 360
        }
    }
}
