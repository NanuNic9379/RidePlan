package io.github.nanunic9379.rideplan.route

import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.Ride
import io.github.nanunic9379.rideplan.ride.Stop
import io.github.nanunic9379.rideplan.ride.distanceKm
import io.github.nanunic9379.rideplan.ride.stopsEditable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot

/** A station the ride passes by: [km] from the start, [offKm] from the road. */
data class StationOnRide(val station: FuelStation, val km: Double, val offKm: Double)

/** A piece of the ride without fuel, from km [fromKm] to km [toKm]. */
data class FuelStretch(val fromKm: Double, val toKm: Double) {
    val km: Double get() = toKm - fromKm
}

/**
 * The stations along a ride and the stretches between them (#7). The rider is assumed to leave
 * with a full tank: the first stretch goes from the start to the first station, the last one
 * from the last station to the finish.
 */
data class FuelReport(val stations: List<StationOnRide>, val totalKm: Double) {
    /** Start, every station passed, finish: the stretches in between, in order. */
    val stretches: List<FuelStretch>
        get() {
            val marks = listOf(0.0) + stations.map { it.km.coerceIn(0.0, totalKm) } + totalKm
            return marks.zipWithNext { a, b -> FuelStretch(a, b) }
        }

    val longest: FuelStretch get() = stretches.maxBy { it.km }

    /** The stretches longer than the range: there the bike may stay dry. */
    fun overRange(rangeKm: Double): List<FuelStretch> = stretches.filter { it.km > rangeKm }
}

object FuelAlong {
    /** A station counts when it is this close to the road: a short detour, not a trip. */
    const val NEAR_KM = 1.0

    /**
     * Two passes by the same station are separate (an out-and-back ride) only when the ride
     * leaves it for more than this.
     */
    private const val NEW_PASS_KM = 3.0

    /** Side of the grid that speeds up the search, in km: at least [NEAR_KM]. */
    private const val GRID_KM = 2.0

    /**
     * The stations within [nearKm] of the [line], with their km from the start, sorted.
     * A station passed twice (there and back) appears twice. [totalKm]: the ride length shown to
     * the rider (the computed route); the km along the line are scaled to match it.
     */
    fun report(line: List<LatLon>, stations: List<FuelStation>, totalKm: Double? = null, nearKm: Double = NEAR_KM): FuelReport {
        val geo = Geometry(line, totalKm)
        if (line.size < 2) return FuelReport(emptyList(), geo.total)
        val grid = SegmentGrid(line)
        val out = ArrayList<StationOnRide>()
        for (s in stations) {
            var lastKm = -1.0
            var best: StationOnRide? = null
            for (i in grid.near(s.point)) {
                val (km, off) = geo.project(s.point, i)
                if (off > nearKm) continue
                if (best != null && km - lastKm > NEW_PASS_KM) {
                    out += best
                    best = null
                }
                if (best == null || off < best.offKm) best = StationOnRide(s, km, off)
                lastKm = km
            }
            best?.let { out += it }
        }
        return FuelReport(out.sortedBy { it.km }, geo.total)
    }

    /**
     * Where each stop falls along the [line], in the same km as [report]: the start at 0, the
     * finish at the end, the others in order (a ride that comes back to its start still puts
     * the finish at the end). Each stop is matched to the first close pass after the previous one.
     */
    fun stopKms(line: List<LatLon>, stops: List<LatLon>, totalKm: Double? = null): List<Double> {
        val geo = Geometry(line, totalKm)
        if (stops.isEmpty()) return emptyList()
        if (line.size < 2) return stops.indices.map { 0.0 }
        var from = 0
        return stops.mapIndexed { k, p ->
            when (k) {
                0 -> 0.0
                stops.lastIndex -> geo.total
                else -> {
                    val dist = (from until line.lastIndex).map { i -> geo.project(p, i).second }
                    val min = dist.min()
                    // The first segment almost as close as the best one, then down to its closest point.
                    var i = dist.indexOfFirst { it <= min + 0.5 }
                    while (i + 1 < dist.size && dist[i + 1] <= dist[i]) i++
                    val seg = from + i
                    from = seg
                    geo.project(p, seg).first
                }
            }
        }
    }

    internal class Geometry(private val line: List<LatLon>, totalKm: Double?) {
        /** km from the start at every point of the line. */
        private val cum = DoubleArray(line.size).also { c ->
            for (i in 1 until line.size) c[i] = c[i - 1] + distanceKm(line[i - 1], line[i])
        }
        private val scale = if (totalKm != null && totalKm > 0 && line.size >= 2 && cum.last() > 0) totalKm / cum.last() else 1.0
        val total: Double = if (line.size >= 2) cum.last() * scale else (totalKm ?: 0.0)

        /** Closest point of segment [i] to [p]: its km from the start, and the distance from [p]. */
        fun project(p: LatLon, i: Int): Pair<Double, Double> {
            val a = line[i]
            val b = line[i + 1]
            // Flat plane in km around the point: plenty accurate within a few km.
            val kx = FuelData.KM_PER_DEG_LON * cos(p.lat * PI / 180)
            val ky = FuelData.KM_PER_DEG_LAT
            val ax = (a.lon - p.lon) * kx
            val ay = (a.lat - p.lat) * ky
            val bx = (b.lon - p.lon) * kx
            val by = (b.lat - p.lat) * ky
            val dx = bx - ax
            val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
            val off = hypot(ax + t * dx, ay + t * dy)
            return (cum[i] + t * (cum[i + 1] - cum[i])) * scale to off
        }
    }

    /** The line's segments in a grid of [GRID_KM] squares, to look only at those near a station. */
    private class SegmentGrid(line: List<LatLon>) {
        private val maxLat = line.maxOf { abs(it.lat) }.coerceAtMost(85.0)
        private val gLat = GRID_KM / FuelData.KM_PER_DEG_LAT
        // Widest at the highest latitude of the ride: never narrower than GRID_KM anywhere on it.
        private val gLon = GRID_KM / (FuelData.KM_PER_DEG_LON * cos(maxLat * PI / 180))
        private val cells = HashMap<Long, MutableList<Int>>()

        init {
            for (i in 0 until line.lastIndex) {
                val a = line[i]
                val b = line[i + 1]
                val x0 = col(minOf(a.lon, b.lon))
                val x1 = col(maxOf(a.lon, b.lon))
                val y0 = row(minOf(a.lat, b.lat))
                val y1 = row(maxOf(a.lat, b.lat))
                // A very long straight segment (a gap in a GPX track) only gets its bounding cells
                // up to a limit; beyond it, it goes in the ends' cells only.
                if ((x1 - x0 + 1) * (y1 - y0 + 1) > MAX_CELLS) {
                    add(col(a.lon), row(a.lat), i)
                    add(col(b.lon), row(b.lat), i)
                    continue
                }
                for (x in x0..x1) for (y in y0..y1) add(x, y, i)
            }
        }

        private fun col(lon: Double) = floor(lon / gLon).toLong()
        private fun row(lat: Double) = floor(lat / gLat).toLong()
        private fun key(x: Long, y: Long) = x * 1_000_003L + y
        private fun add(x: Long, y: Long, i: Int) {
            val list = cells.getOrPut(key(x, y)) { ArrayList() }
            if (list.lastOrNull() != i) list += i
        }

        /** The segments in the station's square and the eight around it, in order along the line. */
        fun near(p: LatLon): List<Int> {
            val x = col(p.lon)
            val y = row(p.lat)
            val found = HashSet<Int>()
            for (dx in -1L..1L) for (dy in -1L..1L) cells[key(x + dx, y + dy)]?.let(found::addAll)
            return found.sorted()
        }

        companion object {
            const val MAX_CELLS = 2_500
        }
    }
}

/**
 * "Refuel here" (#7): the station becomes a stop of the ride, between the stops it really falls
 * between along the road, never before the start or after the finish. The ride goes back to
 * straight lines so that it is routed again. No change if the stops cannot be edited or the
 * station is already a stop.
 */
fun Ride.withRefuelStop(at: StationOnRide): Ride {
    if (!stopsEditable || stops.size < 2) return this
    if (stops.any { distanceKm(it.point, at.station.point) < 0.03 }) return this
    val kms = FuelAlong.stopKms(line, stops.map { it.point }, lengthKm)
    val index = (1 until stops.lastIndex).count { kms[it] <= at.km } + 1
    val fresh = stops.toMutableList().also { it.add(index, Stop(at.station.label, at.station.point, Stop.Kind.STOP)) }
    return copy(stops = fresh).unrouted()
}
