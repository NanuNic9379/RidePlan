package io.github.nanunic9379.rideplan.route

import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.Stop
import io.github.nanunic9379.rideplan.ride.distanceKm
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min

/**
 * One piece of a ride that crosses areas of road data (#14): the stops [first]..[last] (positions
 * in the ride), routed with the data of [area]. [before] and [after]: the hidden junction where
 * the ride comes in from the previous area or goes on into the next one. The junction is routed
 * like a stop but it is not one: it never shows in the stop list, in the GPX or in a link.
 */
data class AreaLeg(
    val area: String,
    val first: Int,
    val last: Int,
    val before: LatLon? = null,
    val after: LatLon? = null,
) {
    init {
        require(first in 0..last) { "Empty leg" }
    }

    /** The points this leg is routed through: the junctions around the ride's own stops. */
    fun points(all: List<LatLon>): List<LatLon> =
        listOfNotNull(before) + all.subList(first, last + 1) + listOfNotNull(after)

    /** The same as stops, for the engine: a junction is a plain unnamed stop. */
    fun stops(all: List<Stop>): List<Stop> =
        listOfNotNull(before?.let(::junction)) + all.subList(first, last + 1) + listOfNotNull(after?.let(::junction))

    private fun junction(p: LatLon) = Stop(null, p)
}

/**
 * Rides across two or more areas of road data (#14). Each area is a separate build: the engine
 * cannot route from one into the other. So the ride is split into consecutive legs, each inside
 * one area; where a leg passes into the next area a hidden junction is placed where both areas
 * have complete data (their outlines overlap: the Alps and Italy share northern Italy). Each leg
 * is routed with its own area's data and the pieces are joined into one route
 * ([ValhallaRoute.join]).
 */
object CrossArea {
    /** A junction this far inside both outlines is deep enough: the roads around it are complete in both. */
    const val DEEP_KM = 10.0

    /** Closer than this to the edge of either outline is no place for a junction. */
    const val MIN_DEPTH_KM = 1.0

    /** Spacing of the places tried on the straight line between two stops. */
    const val STEP_KM = 1.0

    /** When the straight line misses the overlap: how far to the side the junction may move. */
    val SIDE_KM = listOf(5.0, 10.0, 20.0, 40.0, 80.0)

    /** Spacing along the line of the places tried to the side. */
    const val SIDE_STEP_KM = 5.0

    private const val MAX_SAMPLES = 2000

    /**
     * The ride's legs, each inside one of [areas] (the outline decides, see [RoutingArea.covers]).
     * Each leg goes as far as its area holds the stops in a row; the area holding more of them
     * in a row comes first, then the smaller one. Where the next stop is in the next area too,
     * the ride changes area at that stop; otherwise at a junction ([junction]).
     * A single leg when one area holds every stop. Null when a stop is in no area, or when two
     * areas in a row have no overlap to pass from one to the other.
     */
    fun split(areas: List<RoutingArea>, points: List<LatLon>): List<AreaLeg>? {
        if (points.isEmpty()) return null
        val held = points.map { p -> areas.filter { it.covers(p) } }
        if (held.any { it.isEmpty() }) return null

        /** The last stop of the run that [area] holds from [start] on. */
        fun end(area: RoutingArea, start: Int): Int {
            var e = start
            while (e < points.lastIndex && area in held[e + 1]) e++
            return e
        }

        fun order(start: Int, choices: List<RoutingArea>) = choices.sortedWith(
            compareByDescending<RoutingArea> { end(it, start) }.thenBy { it.size }.thenBy { it.id },
        )

        // An area that cannot go on from a stop fails the same way whatever came before.
        val failed = HashSet<Pair<Int, String>>()

        fun from(start: Int, before: LatLon?, choices: List<RoutingArea>): List<AreaLeg>? {
            for (a in order(start, choices)) {
                if (!failed.add(start to a.id)) continue
                val e = end(a, start)
                if (e == points.lastIndex) return listOf(AreaLeg(a.id, start, e, before = before))
                for (b in order(e + 1, held[e + 1])) {
                    if (b.covers(points[e])) {
                        // The stop is in both areas: the ride changes area right there.
                        if (e == start && before == null) continue
                        val rest = from(e, null, listOf(b)) ?: continue
                        return listOf(AreaLeg(a.id, start, e, before = before)) + rest
                    }
                    val j = junction(a, b, points[e], points[e + 1]) ?: continue
                    val rest = from(e + 1, j, listOf(b)) ?: continue
                    return listOf(AreaLeg(a.id, start, e, before = before, after = j)) + rest
                }
            }
            return null
        }
        return from(0, null, held[0])
    }

    /**
     * Where the ride passes from area [a] (which holds [from]) into area [b] (which holds [to]):
     * a place inside both outlines, near the straight line between the two stops. On the line,
     * the first place (from [from]'s side) at least [DEEP_KM] inside both; if there is none, the
     * deepest one. If the line misses the overlap, places to the side of it, up to the largest of
     * [SIDE_KM]: the shortest detour among those deep enough, otherwise the deepest. Null when the
     * two areas have no overlap there at least [MIN_DEPTH_KM] deep.
     */
    fun junction(a: RoutingArea, b: RoutingArea, from: LatLon, to: LatLon): LatLon? {
        val km = distanceKm(from, to)
        val n = ceil(km / STEP_KM).toInt().coerceIn(1, MAX_SAMPLES)
        var best: LatLon? = null
        var bestDepth = 0.0
        for (i in 0..n) {
            val p = along(from, to, i.toDouble() / n, 0.0)
            val d = depth(a, b, p)
            if (d >= DEEP_KM) return p
            if (d >= MIN_DEPTH_KM && d > bestDepth) {
                best = p
                bestDepth = d
            }
        }
        if (best != null) return best

        val m = ceil(km / SIDE_STEP_KM).toInt().coerceIn(1, MAX_SAMPLES)
        var deep: LatLon? = null
        var deepDetour = Double.MAX_VALUE
        for (side in SIDE_KM) for (sign in listOf(1.0, -1.0)) for (i in 0..m) {
            val p = along(from, to, i.toDouble() / m, sign * side)
            val d = depth(a, b, p)
            if (d < MIN_DEPTH_KM) continue
            val detour = distanceKm(from, p) + distanceKm(p, to) - km
            if (d >= DEEP_KM) {
                if (detour < deepDetour) {
                    deep = p
                    deepDetour = detour
                }
            } else if (d > bestDepth) {
                best = p
                bestDepth = d
            }
        }
        return deep ?: best
    }

    /** How far [p] is inside both areas, in km: 0 when it is outside either. */
    internal fun depth(a: RoutingArea, b: RoutingArea, p: LatLon): Double {
        if (!a.covers(p) || !b.covers(p)) return 0.0
        return min(edgeKm(a, p), edgeKm(b, p))
    }

    /** Distance in km from [p] to the nearest edge of the area's outline (its box if it has none). */
    internal fun edgeKm(area: RoutingArea, p: LatLon): Double {
        val kx = KM_PER_DEG_LON * cos(p.lat * PI / 180)
        val ky = KM_PER_DEG_LAT
        if (!area.hasOutline) {
            val box = area.bbox
            return minOf((p.lon - box[0]) * kx, (box[2] - p.lon) * kx, (p.lat - box[1]) * ky, (box[3] - p.lat) * ky)
        }
        var best = Double.MAX_VALUE
        for (r in area.outline) {
            // A ring whose box is further than the best edge so far cannot hold a closer edge.
            val dx = maxOf(r.minLon - p.lon, 0.0, p.lon - r.maxLon) * kx
            val dy = maxOf(r.minLat - p.lat, 0.0, p.lat - r.maxLat) * ky
            if (hypot(dx, dy) >= best) continue
            var j = r.lats.lastIndex
            for (i in r.lats.indices) {
                val d = segmentKm(
                    (r.lons[j] - p.lon) * kx, (r.lats[j] - p.lat) * ky,
                    (r.lons[i] - p.lon) * kx, (r.lats[i] - p.lat) * ky,
                )
                if (d < best) best = d
                j = i
            }
        }
        return best
    }

    /** Distance from the origin to the segment a-b, on a local plane in km. */
    private fun segmentKm(ax: Double, ay: Double, bx: Double, by: Double): Double {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else ((-ax * dx - ay * dy) / len2).coerceIn(0.0, 1.0)
        return hypot(ax + t * dx, ay + t * dy)
    }

    /**
     * The place at share [t] of the straight line [from]-[to], moved [sideKm] to its left
     * (negative: to its right), on a local plane: accurate enough for tens of km.
     */
    internal fun along(from: LatLon, to: LatLon, t: Double, sideKm: Double): LatLon {
        val lat = from.lat + (to.lat - from.lat) * t
        val lon = from.lon + (to.lon - from.lon) * t
        if (sideKm == 0.0) return LatLon(lat, lon)
        val kx = KM_PER_DEG_LON * cos(lat * PI / 180)
        val ky = KM_PER_DEG_LAT
        val ex = (to.lon - from.lon) * kx
        val ey = (to.lat - from.lat) * ky
        val len = hypot(ex, ey)
        // Left of the direction of travel; a line of no length has no side: east.
        val (nx, ny) = if (len == 0.0) 1.0 to 0.0 else -ey / len to ex / len
        return LatLon(
            (lat + sideKm * ny / ky).coerceIn(-90.0, 90.0),
            (lon + sideKm * nx / kx).coerceIn(-180.0, 180.0),
        )
    }

    private const val KM_PER_DEG_LAT = 110.57
    private const val KM_PER_DEG_LON = 111.32

    /** Valhalla's maneuver types for the start of a leg (start, start right, start left). */
    private val DEPARTURES = setOf(1, 2, 3)

    /**
     * Joins the routes of the legs ([parts], in order) into one route. Each part starts where the
     * previous one ended: that point appears once. At a hidden junction (a leg with
     * [AreaLeg.after]) the arrival and the next departure are no maneuvers of the rider: the
     * arrival is dropped and the departure joins the maneuver before it, so the rider hears
     * nothing there. At a stop shared by two legs both stay, as at any stop of a route.
     * Maneuver indices, speed limits and unpaved stretches move onto the joined line.
     */
    fun join(parts: List<ValhallaRoute.Result>, legs: List<AreaLeg>): ValhallaRoute.Result {
        require(parts.size == legs.size && parts.isNotEmpty()) { "One route per leg" }
        if (parts.size == 1) return parts[0]
        val shape = ArrayList<LatLon>()
        val maneuvers = ArrayList<Maneuver>()
        val limits = ArrayList<SpeedLimit>()
        val rough = ArrayList<RoughStretch>()
        var alongM = 0.0
        parts.forEachIndexed { i, part ->
            val same = shape.isNotEmpty() && part.shape.isNotEmpty() && shape.last() == part.shape.first()
            val base = if (same) shape.lastIndex else shape.size
            var startM = alongM
            part.shape.forEachIndexed { k, p ->
                if (k == 0 && same) return@forEachIndexed
                if (shape.isNotEmpty()) alongM += distanceKm(shape.last(), p) * 1000
                shape += p
                if (k == 0) startM = alongM
            }
            var mine = part.maneuvers.map { it.copy(beginIndex = it.beginIndex + base, endIndex = it.endIndex + base) }
            if (i < parts.lastIndex && legs[i].after != null && mine.lastOrNull()?.isArrival == true) mine = mine.dropLast(1)
            val start = mine.firstOrNull()
            if (i > 0 && legs[i - 1].after != null && start != null && start.type in DEPARTURES && maneuvers.isNotEmpty()) {
                val last = maneuvers.last()
                maneuvers[maneuvers.lastIndex] = last.copy(
                    endIndex = start.endIndex,
                    km = last.km + start.km,
                    seconds = last.seconds + start.seconds,
                )
                mine = mine.drop(1)
            }
            maneuvers += mine
            part.speedLimits.stretches.mapTo(limits) { it.copy(fromM = it.fromM + startM, toM = it.toM + startM) }
            part.surfaces.stretches.mapTo(rough) { it.copy(fromM = it.fromM + startM, toM = it.toM + startM) }
        }
        return ValhallaRoute.Result(
            shape = shape,
            km = parts.sumOf { it.km },
            seconds = parts.sumOf { it.seconds },
            maneuvers = maneuvers,
            speedLimits = SpeedLimits.of(limits),
            surfaces = RoadSurfaces.of(rough),
        )
    }

    /**
     * What to download for a ride across areas: the cells of each leg in its own area (an area
     * met twice gets the packs of both legs). Null when the ride stays in one area, cannot be
     * split, or a leg leaves its area's published cells.
     */
    fun plan(published: List<PublishedArea>, local: List<LocalArea>, points: List<LatLon>): AreasDownload? {
        val legs = split(published.map { it.area }, points)?.takeIf { it.size > 1 } ?: return null
        val parts = LinkedHashMap<String, CellsDownload>()
        for (leg in legs) {
            val p = published.first { it.area.id == leg.area }
            val mine = local.firstOrNull { it.id == leg.area }
            val d = CellsDownload.plan(p.cells, mine?.cells, mine?.installed.orEmpty(), leg.points(points))
                ?.copy(area = p.area.id, release = p.area.release) ?: return null
            parts[leg.area] = parts[leg.area]?.let { it.copy(packs = (it.packs + d.packs).distinctBy { pack -> pack.id }) } ?: d
        }
        return AreasDownload(parts.values.toList(), legs)
    }

    /**
     * The data on the phone that routes a ride across areas with no download: each leg inside an
     * area whose cells are already there. Null if the ride stays in one area or a leg misses cells.
     */
    fun local(areas: List<RoutingArea>, local: List<LocalArea>, points: List<LatLon>): RoutingData.Across? {
        val withData = areas.filter { a -> local.any { it.id == a.id && it.cells != null } }
        val legs = split(withData, points)?.takeIf { it.size > 1 } ?: return null
        return RoutingData.Across(
            legs.map { leg ->
                val mine = local.first { it.id == leg.area }
                if (!CellsDownload.coveredLocally(mine.cells, mine.installed, leg.points(points))) return null
                RoutingData.Across.Part(RoutingData.Cells(mine.cells!!, leg.area), leg)
            },
        )
    }
}

/**
 * The download for a ride across areas (#14): one [CellsDownload] per area, each into its own
 * folder, and the legs to route once the data is there.
 */
data class AreasDownload(val parts: List<CellsDownload>, val legs: List<AreaLeg>) : DataDownload {
    override val bytes: Long get() = parts.sumOf { it.bytes }
    override val tileBytes: Long get() = parts.sumOf { it.tileBytes }

    /** Everything is already on the phone. */
    val ready: Boolean get() = parts.all { it.packs.isEmpty() }

    /** The data to route the ride with, once downloaded. */
    val data: RoutingData.Across
        get() = RoutingData.Across(
            legs.map { leg ->
                val part = parts.first { it.area == leg.area }
                RoutingData.Across.Part(RoutingData.Cells(part.cells, part.area), leg)
            },
        )
}
