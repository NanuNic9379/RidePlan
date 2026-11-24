package io.github.nanunic9379.rideplan.ride

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max

/**
 * How much riding a day of a multi-day trip may hold (#12): kilometres or riding hours.
 * Hours are turned into kilometres with the ride's own pace (see [Stages.secondsPerKm]).
 */
data class DayCap(val unit: Unit, val value: Double) {
    enum class Unit { KM, HOURS }

    val valid: Boolean get() = value.isFinite() && value > 0

    companion object {
        val DEFAULT_KM = DayCap(Unit.KM, 300.0)
        val DEFAULT_HOURS = DayCap(Unit.HOURS, 6.0)
    }
}

/**
 * One day of a multi-day trip (#12).
 * [day]: 1 for the first day. [startKm] and [endKm]: road kilometres from the start of the ride.
 * [seconds]: riding time, a share of the route time proportional to the kilometres.
 * [track]: the road of the day; empty when the ride only has stops (straight lines).
 * [stops]: the points of the day in order. The first is where the day starts, the last where it ends;
 * when a day ends on the road (no stop near the cap) that point is a new unnamed stop.
 * [startStop], [endStop]: index in the ride's stops of the first and last point, null when on the road.
 * [overnight]: the day ends at a stop the rider marked as overnight.
 */
data class Stage(
    val day: Int,
    val startKm: Double,
    val endKm: Double,
    val seconds: Double,
    val track: List<LatLon>,
    val stops: List<Stop>,
    val startStop: Int?,
    val endStop: Int?,
    val overnight: Boolean,
) {
    val km: Double get() = endKm - startKm

    /** The day as a ride of its own, for a GPX file. */
    fun toRide(ride: Ride, name: String): Ride = Ride(name, track, stops, ride.source)
}

/**
 * Splits a ride into days (#12).
 *
 * - Stops marked as overnight always end a day.
 * - Between them, with a [DayCap], the stretch is split into as few days as the cap allows, balanced:
 *   620 km with a 300 km cap become three days of about 207 km, not 300 + 300 + 20.
 * - A day prefers to end at an existing stop (kind [Stop.Kind.STOP]) near its ideal end, never beyond
 *   the cap; with none in reach it ends on the road where its share is reached.
 * - Time is estimated proportionally from the route time; a ride without a computed route uses
 *   [DEFAULT_KMH].
 */
object Stages {
    /** Average pace when the ride has no computed route time (an imported track). */
    const val DEFAULT_KMH = 50.0

    /** Guard against absurd caps (1 km a day on a 3000 km ride). */
    const val MAX_DAYS = 60

    /** How far before its ideal end (as a share of the cap) a day may end to sleep at a stop. */
    const val SNAP_WINDOW = 0.25

    private const val EPS = 1e-9

    /** True when the ride is split into days: a valid cap or at least one overnight stop. */
    fun active(ride: Ride): Boolean = ride.dayCap?.valid == true || overnightIndices(ride).isNotEmpty()

    /** Riding seconds per road kilometre. */
    fun secondsPerKm(ride: Ride): Double {
        val route = ride.route
        return if (route != null && route.km > 0 && route.seconds > 0) route.seconds / route.km else 3600.0 / DEFAULT_KMH
    }

    /** The cap in road kilometres. */
    fun capKm(ride: Ride, cap: DayCap): Double = when (cap.unit) {
        DayCap.Unit.KM -> cap.value
        DayCap.Unit.HOURS -> cap.value * 3600.0 / secondsPerKm(ride)
    }

    /** Intermediate stops where the rider sleeps (the start and finish do not count). */
    fun overnightIndices(ride: Ride): List<Int> =
        ride.stops.indices.filter { it != 0 && it != ride.stops.lastIndex && ride.stops[it].overnight }

    fun split(ride: Ride, cap: DayCap? = ride.dayCap): List<Stage> {
        val line = ride.line
        if (line.size < 2) return emptyList()
        val geo = Polyline(line)
        val total = geo.length
        // Road km per line km: the computed route can be a little longer than the drawn line.
        val scale = if (total > EPS) ride.lengthKm / total else 1.0
        val positions = stopPositions(ride, geo)
        val capLine = cap?.takeIf { it.valid }?.let { capKm(ride, it) / scale }?.takeIf { it.isFinite() && it > EPS }

        // Fixed ends: overnight stops, then the finish.
        data class End(val pos: Double, val stop: Int?, val overnight: Boolean)
        val fixed = buildList {
            var last = 0.0
            for (i in overnightIndices(ride)) {
                val p = positions[i]
                if (p > last + EPS && p < total - EPS) { add(End(p, i, true)); last = p }
            }
            add(End(total, ride.stops.lastIndex.takeIf { it >= 0 }, false))
        }

        val ends = mutableListOf<End>()
        var start = 0.0
        for (f in fixed) {
            while (capLine != null && ends.size < MAX_DAYS - 1) {
                val rem = f.pos - start
                if (rem <= capLine * (1 + 1e-6)) break
                // Tolerance: a 600.000001 km ride with a 300 km cap is two days, not three.
                val n = ceil(rem / capLine - 1e-6).coerceAtLeast(2.0)
                val ideal = start + rem / n
                val lo = ideal - SNAP_WINDOW * capLine
                val hi = start + capLine
                val snap = ride.stops.indices
                    .filter { ride.stops[it].kind == Stop.Kind.STOP && positions[it] > start + EPS && positions[it] < f.pos - EPS }
                    .filter { positions[it] in lo..hi }
                    .minWithOrNull(compareBy<Int> { abs(positions[it] - ideal) }.thenByDescending { it })
                val end = if (snap != null) End(positions[snap], snap, false) else End(ideal, null, false)
                ends += end
                start = end.pos
            }
            ends += f
            start = f.pos
        }

        val pace = secondsPerKm(ride)
        var from = 0.0
        var fromStop: Int? = if (ride.stops.isNotEmpty()) 0 else null
        return ends.mapIndexed { d, end ->
            val stops = buildList {
                add(fromStop?.let { ride.stops[it] } ?: Stop(null, geo.pointAt(from)))
                ride.stops.forEachIndexed { i, s ->
                    if (i != fromStop && i != end.stop && positions[i] > from + EPS && positions[i] < end.pos - EPS) add(s)
                }
                add(end.stop?.let { ride.stops[it] } ?: Stop(null, geo.pointAt(end.pos)))
            }
            val km = (end.pos - from) * scale
            Stage(
                day = d + 1,
                startKm = from * scale,
                endKm = end.pos * scale,
                seconds = km * pace,
                track = if (ride.straightLines) emptyList() else geo.slice(from, end.pos),
                stops = stops,
                startStop = fromStop,
                endStop = end.stop,
                overnight = end.overnight,
            ).also { from = end.pos; fromStop = end.stop }
        }
    }

    /**
     * Where each stop sits along the line, in line km. Without a track the stops are the line itself;
     * with a track each stop is projected onto it, never going back from the previous stop.
     */
    internal fun stopPositions(ride: Ride, geo: Polyline = Polyline(ride.line)): List<Double> {
        if (ride.straightLines) return ride.stops.indices.map { geo.cum.getOrElse(it) { geo.length } }
        var fromSeg = 0
        var prev = 0.0
        return ride.stops.mapIndexed { i, s ->
            // The finish of a loop is also near the start: it takes the last pass, the others the first.
            val (seg, pos) = geo.project(s.point, fromSeg, preferLate = i == ride.stops.lastIndex && i > 0)
            fromSeg = seg
            prev = max(prev, pos)
            prev
        }
    }
}

/** A ride with stop [index] marked (or unmarked) as an overnight. The road does not change. */
fun Ride.withOvernight(index: Int, overnight: Boolean): Ride {
    if (index <= 0 || index >= stops.lastIndex) return this
    return copy(stops = stops.toMutableList().also { it[index] = it[index].copy(overnight = overnight) })
}

/** GPX files of the days (#12): one per day, with unique file names. */
object StageExport {
    /** [dayName]: "Day 2" in the app language. */
    fun files(ride: Ride, stages: List<Stage>, fallbackName: String, dayName: (Int) -> String): List<Pair<String, String>> {
        val base = ride.name ?: fallbackName
        val used = mutableSetOf<String>()
        return stages.map { stage ->
            val day = stage.toRide(ride, "$base - ${dayName(stage.day)}")
            val stem = GpxExport.fileName(day, fallbackName).removeSuffix(".gpx")
            var name = "$stem.gpx"
            var n = 2
            while (!used.add(name.lowercase())) name = "$stem ($n).gpx".also { n++ }
            name to GpxExport.write(day, fallbackName)
        }
    }
}

/** A line with its cumulative length, to find and cut points along it. */
internal class Polyline(val points: List<LatLon>) {
    /** Km from the first point to each point. */
    val cum: DoubleArray = DoubleArray(points.size).also { c ->
        for (i in 1 until points.size) c[i] = c[i - 1] + distanceKm(points[i - 1], points[i])
    }
    val length: Double get() = if (cum.isEmpty()) 0.0 else cum.last()

    fun pointAt(pos: Double): LatLon {
        if (points.size == 1 || pos <= 0) return points.first()
        if (pos >= length) return points.last()
        var j = 0
        while (j < points.size - 2 && cum[j + 1] < pos) j++
        val seg = cum[j + 1] - cum[j]
        val t = if (seg > 0) ((pos - cum[j]) / seg).coerceIn(0.0, 1.0) else 0.0
        return lerp(points[j], points[j + 1], t)
    }

    /** The part of the line between [from] and [to] km, with interpolated ends. */
    fun slice(from: Double, to: Double): List<LatLon> = buildList {
        add(pointAt(from))
        for (i in points.indices) if (cum[i] > from + 1e-9 && cum[i] < to - 1e-9) add(points[i])
        add(pointAt(to))
    }.fold(mutableListOf()) { acc, p -> if (acc.lastOrNull() != p) acc += p; acc }

    /** The closest point of the line to [p], from segment [fromSeg] on: (segment, position in km).
     * Among equally close points the first one wins, or the last one with [preferLate]. */
    fun project(p: LatLon, fromSeg: Int, preferLate: Boolean = false): Pair<Int, Double> {
        if (points.size < 2) return 0 to 0.0
        var best = Double.MAX_VALUE
        var bestSeg = fromSeg.coerceIn(0, points.size - 2)
        var bestPos = cum[bestSeg]
        for (j in fromSeg.coerceIn(0, points.size - 2) until points.size - 1) {
            val t = segmentT(points[j], points[j + 1], p)
            val q = lerp(points[j], points[j + 1], t)
            val d = distanceKm(p, q)
            if (d < best - 1e-9 || (preferLate && d <= best + 1e-9)) {
                best = d
                bestSeg = j
                bestPos = cum[j] + t * (cum[j + 1] - cum[j])
            }
        }
        return bestSeg to bestPos
    }

    private fun segmentT(a: LatLon, b: LatLon, p: LatLon): Double {
        // Local flat projection: good enough over one segment of a road.
        val k = cos((a.lat + b.lat) / 2 * kotlin.math.PI / 180.0)
        val bx = (b.lon - a.lon) * k
        val by = b.lat - a.lat
        val px = (p.lon - a.lon) * k
        val py = p.lat - a.lat
        val len2 = bx * bx + by * by
        return if (len2 <= 0) 0.0 else ((px * bx + py * by) / len2).coerceIn(0.0, 1.0)
    }

    private fun lerp(a: LatLon, b: LatLon, t: Double) =
        LatLon(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t).let { if (t <= 0) a else if (t >= 1) b else it }
}
