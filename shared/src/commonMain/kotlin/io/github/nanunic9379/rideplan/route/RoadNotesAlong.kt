package io.github.nanunic9379.rideplan.route

import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.distanceKm
import kotlin.math.PI
import kotlin.math.cos

/**
 * A closed road the ride goes over (#13), from km [fromKm] to km [toKm]. A pass is often split
 * into several OpenStreetMap ways: consecutive pieces with the same name and rule are one entry
 * ([pieces]). [closure]: the first piece, for its name and rule. [middle]: a point in the middle
 * of the closed stretch the ride uses, where the alternative route is blocked. [stretch]: points
 * of the closed road along the ride, to draw it over the ride's line.
 */
data class ClosureOnRide(
    val closure: Closure,
    val fromKm: Double,
    val toKm: Double,
    val middle: LatLon,
    val stretch: List<LatLon>,
    val pieces: Int = 1,
) {
    /** The rule as one entry: what the rider reads. */
    val rule: ClosureRule get() = closure.rule

    /** A road to keep the alternative route away from: a small square around [middle]. */
    fun avoided(language: String?): AvoidedRoad =
        AvoidedRoad(closure.id, closure.name(language), RoadNotesAlong.square(middle, RoadNotesAlong.AVOID_HALF_M))
}

/**
 * A road the rider chose to avoid (#13), e.g. a pass closed in winter. [area]: a closed ring
 * sent to the engine as `exclude_polygons`: every road crossing it is left out.
 */
data class AvoidedRoad(val id: String, val name: String?, val area: List<LatLon>)

/** A fixed speed camera along the ride, [km] from the start, [offM] metres from the road. */
data class CameraOnRide(val camera: SpeedCamera, val km: Double, val offM: Double)

object RoadNotesAlong {
    /** A point of a closed road is on the ride when it is this close to the ride's line. */
    const val NEAR_M = 20.0

    /** Spacing of the points checked along a closed road. */
    const val STEP_M = 20.0

    /** A long closed road counts once the ride follows it for this long. */
    const val MIN_FOLLOW_M = 200.0

    /** Below this the ride only touches the road (a junction, a bridge over it). */
    const val MIN_TOUCH_M = 25.0

    /** Two passes over the same closed road (there and back) are apart by more than this. */
    const val NEW_PASS_KM = 1.0

    /** Pieces of the same pass closer than this along the ride are one entry. */
    const val JOIN_KM = 2.0

    /**
     * A camera counts when it is this close to the ride's line: cameras stand at the roadside,
     * a camera on the other carriageway of a dual road may be included (direction is not used).
     */
    const val CAMERA_NEAR_M = 30.0

    /** Half side of the square that blocks a closed road for the alternative route. */
    const val AVOID_HALF_M = 25.0

    /** The closed roads the [line] goes over, in order along the ride. [totalKm]: as in [FuelAlong.report]. */
    fun closures(line: List<LatLon>, closures: List<Closure>, totalKm: Double? = null): List<ClosureOnRide> {
        if (line.size < 2 || closures.isEmpty()) return emptyList()
        val box = Box.of(line, marginM = 200.0)
        val geo = FuelAlong.Geometry(line, totalKm)
        val grid = FuelAlong.SegmentGrid(line)
        val found = ArrayList<ClosureOnRide>()
        for (c in closures) {
            if (c.rule == ClosureRule.Never || !box.touches(c.line)) continue
            val matched = ArrayList<Match>()
            for ((p, along) in densify(c.line, STEP_M)) {
                // One match per pass of the ride over this point (out and back: two), the closest.
                var best: Pair<Double, Double>? = null
                var lastKm = -1.0
                for (i in grid.near(p)) {
                    val r = geo.project(p, i)
                    if (r.second * 1000 > NEAR_M) continue
                    if (best != null && r.first - lastKm > NEW_PASS_KM) {
                        matched += Match(p, along, best.first)
                        best = null
                    }
                    if (best == null || r.second < best.second) best = r
                    lastKm = r.first
                }
                best?.let { matched += Match(p, along, it.first) }
            }
            if (matched.isEmpty()) continue
            val length = c.line.zipWithNext { a, b -> distanceKm(a, b) }.sum() * 1000
            for (pass in passes(matched)) {
                val covered = pass.maxOf { it.along } - pass.minOf { it.along }
                val rideSpanM = (pass.maxOf { it.km } - pass.minOf { it.km }) * 1000
                val needed = maxOf(MIN_TOUCH_M, minOf(MIN_FOLLOW_M, 0.5 * length))
                // Crossing the closed road (or riding under it) covers almost no ride: not counted.
                if (covered < needed || rideSpanM < 0.5 * covered) continue
                val byKm = pass.sortedBy { it.km }
                val middle = pass.sortedBy { it.along }[pass.size / 2].point
                found += ClosureOnRide(c, byKm.first().km, byKm.last().km, middle, byKm.map { it.point })
            }
        }
        return join(found.sortedBy { it.fromKm })
    }

    /** The cameras within [CAMERA_NEAR_M] of the [line], with their km; one passed twice appears twice. */
    fun cameras(line: List<LatLon>, cameras: List<SpeedCamera>, totalKm: Double? = null): List<CameraOnRide> {
        if (line.size < 2 || cameras.isEmpty()) return emptyList()
        val box = Box.of(line, marginM = 100.0)
        val geo = FuelAlong.Geometry(line, totalKm)
        val grid = FuelAlong.SegmentGrid(line)
        val out = ArrayList<CameraOnRide>()
        for (cam in cameras) {
            if (!box.contains(cam.point)) continue
            var best: CameraOnRide? = null
            var lastKm = -1.0
            for (i in grid.near(cam.point)) {
                val (km, off) = geo.project(cam.point, i)
                val offM = off * 1000
                if (offM > CAMERA_NEAR_M) continue
                if (best != null && km - lastKm > NEW_PASS_KM) {
                    out += best
                    best = null
                }
                if (best == null || offM < best.offM) best = CameraOnRide(cam, km, offM)
                lastKm = km
            }
            best?.let { out += it }
        }
        return out.sortedBy { it.km }
    }

    /**
     * Only the cameras RidePlan may warn about (owner decision, #13/#15: Italy only). [countryOf]
     * is [Countries.at] outside tests; a camera with no known country is left out.
     */
    fun allowed(cameras: List<CameraOnRide>, countryOf: (LatLon) -> String? = Countries::at): List<CameraOnRide> =
        cameras.filter { CountryRules.speedCameraWarningsAllowed(countryOf(it.camera.point)) }

    /** A closed ring of four corners, [halfM] metres around [p]. */
    fun square(p: LatLon, halfM: Double): List<LatLon> {
        val dLat = halfM / 1000 / FuelData.KM_PER_DEG_LAT
        val dLon = halfM / 1000 / (FuelData.KM_PER_DEG_LON * cos(p.lat * PI / 180).coerceAtLeast(0.01))
        val sw = LatLon(p.lat - dLat, p.lon - dLon)
        return listOf(sw, LatLon(p.lat - dLat, p.lon + dLon), LatLon(p.lat + dLat, p.lon + dLon), LatLon(p.lat + dLat, p.lon - dLon), sw)
    }

    private class Match(val point: LatLon, val along: Double, val km: Double)

    /** The matched points split where the ride leaves the road for more than [NEW_PASS_KM]. */
    private fun passes(matched: List<Match>): List<List<Match>> {
        val sorted = matched.sortedBy { it.km }
        val out = ArrayList<MutableList<Match>>()
        for (m in sorted) {
            val last = out.lastOrNull()
            if (last == null || m.km - last.last().km > NEW_PASS_KM) out += mutableListOf(m) else last += m
        }
        return out
    }

    /** Pieces of one pass (same name, same rule) next to each other along the ride become one entry. */
    private fun join(sorted: List<ClosureOnRide>): List<ClosureOnRide> {
        val out = ArrayList<ClosureOnRide>()
        for (c in sorted) {
            val i = out.indexOfLast { prev -> sameRoad(prev.closure, c.closure) && c.fromKm - prev.toKm <= JOIN_KM }
            if (i < 0) {
                out += c
                continue
            }
            val prev = out[i]
            val stretch = (prev.stretch + c.stretch)
            // The middle of the longer piece blocks the road best.
            val middle = if (c.toKm - c.fromKm > prev.toKm - prev.fromKm) c.middle else prev.middle
            out[i] = prev.copy(toKm = maxOf(prev.toKm, c.toKm), middle = middle, stretch = stretch, pieces = prev.pieces + c.pieces)
        }
        return out
    }

    private fun sameRoad(a: Closure, b: Closure): Boolean {
        val name = a.names["name"] ?: a.names["ref"] ?: return false
        return name == (b.names["name"] ?: b.names["ref"]) && a.rule == b.rule
    }

    /** Points every [step] metres along the line (its own points included), with metres from its start. */
    internal fun densify(line: List<LatLon>, step: Double): List<Pair<LatLon, Double>> {
        val out = ArrayList<Pair<LatLon, Double>>()
        var along = 0.0
        out += line.first() to 0.0
        for (i in 1 until line.size) {
            val a = line[i - 1]
            val b = line[i]
            val d = distanceKm(a, b) * 1000
            if (step < d) {
                var s = step
                while (s < d) {
                    val t = s / d
                    out += LatLon(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t) to along + s
                    s += step
                }
            }
            along += d
            out += b to along
        }
        return out
    }

    /** Bounding box of a line grown by a margin: a quick test before the real one. */
    private class Box(val minLat: Double, val minLon: Double, val maxLat: Double, val maxLon: Double) {
        fun contains(p: LatLon) = p.lat in minLat..maxLat && p.lon in minLon..maxLon
        fun touches(line: List<LatLon>): Boolean =
            line.maxOf { it.lat } >= minLat && line.minOf { it.lat } <= maxLat && line.maxOf { it.lon } >= minLon && line.minOf { it.lon } <= maxLon

        companion object {
            fun of(line: List<LatLon>, marginM: Double): Box {
                val maxAbsLat = line.maxOf { kotlin.math.abs(it.lat) }.coerceAtMost(85.0)
                val dLat = marginM / 1000 / FuelData.KM_PER_DEG_LAT
                val dLon = marginM / 1000 / (FuelData.KM_PER_DEG_LON * cos(maxAbsLat * PI / 180))
                return Box(line.minOf { it.lat } - dLat, line.minOf { it.lon } - dLon, line.maxOf { it.lat } + dLat, line.maxOf { it.lon } + dLon)
            }
        }
    }
}

/**
 * Speed-camera warnings while navigating (#13): the next camera within [warnM] ahead, and which
 * ones were already announced, so the voice says each one once. Positions in metres along the
 * navigated line ([CameraOnRide.km] computed without scaling, like [io.github.nanunic9379.rideplan.nav.RouteLine]).
 */
class CameraWatch(cameras: List<CameraOnRide>, private val warnM: Double = WARN_M) {
    private val ahead = cameras.sortedBy { it.km }
    private val announced = HashSet<Int>()

    /** The camera ahead and how far; [announce] true only the first time it comes within [warnM]. */
    data class Ahead(val camera: CameraOnRide, val distanceM: Double, val announce: Boolean)

    fun update(alongM: Double): Ahead? {
        for ((i, c) in ahead.withIndex()) {
            val d = c.km * 1000 - alongM
            // Just passed: still shown for a few metres, the GPS lags.
            if (d < -PASSED_M) continue
            if (d > warnM) return null
            val first = announced.add(i)
            return Ahead(c, d.coerceAtLeast(0.0), announce = first && d > 0)
        }
        return null
    }

    companion object {
        const val WARN_M = 500.0
        const val PASSED_M = 15.0
    }
}
