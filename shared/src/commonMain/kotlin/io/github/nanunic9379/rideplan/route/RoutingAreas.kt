package io.github.nanunic9379.rideplan.route

import io.github.nanunic9379.rideplan.ride.LatLon
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs

/**
 * One area of road data (#14): its own build, its own Release with its own cells.json, its own
 * folder on the phone. Cells of different areas never mix (each is a separate build).
 * [bbox]: lon min, lat min, lon max, lat max. [outline]: the outline of the OpenStreetMap extract,
 * where the data is complete; empty when unknown (then only [bbox] counts).
 */
class RoutingArea internal constructor(
    val id: String,
    val name: String,
    /** Release tag with cells.json, the packs and the fuel lists. */
    val release: String,
    val bbox: List<Double>,
    internal val outline: List<Ring> = emptyList(),
) {
    internal class Ring(val lats: DoubleArray, val lons: DoubleArray) {
        val minLat = lats.min()
        val maxLat = lats.max()
        val minLon = lons.min()
        val maxLon = lons.max()

        /** Shoelace formula, square degrees. */
        val area: Double
            get() {
                var s = 0.0
                var j = lats.lastIndex
                for (i in lats.indices) {
                    s += lons[j] * lats[i] - lons[i] * lats[j]
                    j = i
                }
                return abs(s) / 2
            }
    }

    val hasOutline: Boolean get() = outline.isNotEmpty()

    fun inBox(p: LatLon): Boolean = p.lon >= bbox[0] && p.lat >= bbox[1] && p.lon <= bbox[2] && p.lat <= bbox[3]

    /** The point is where this area's data is complete: inside the outline (even-odd rule), or the box if there is none. */
    fun covers(p: LatLon): Boolean {
        if (!inBox(p)) return false
        if (outline.isEmpty()) return true
        var inside = false
        for (r in outline) {
            if (p.lat < r.minLat || p.lat > r.maxLat || p.lon < r.minLon || p.lon > r.maxLon) continue
            var j = r.lats.lastIndex
            for (i in r.lats.indices) {
                val yi = r.lats[i]
                val yj = r.lats[j]
                if ((yi > p.lat) != (yj > p.lat)) {
                    val x = r.lons[i] + (p.lat - yi) / (yj - yi) * (r.lons[j] - r.lons[i])
                    if (p.lon < x) inside = !inside
                }
                j = i
            }
        }
        return inside
    }

    fun coversAll(points: List<LatLon>): Boolean = points.isNotEmpty() && points.all(::covers)

    /** Size used to prefer the smaller area: the outline's area, or the box's (square degrees). */
    val size: Double
        get() = if (outline.isEmpty()) (bbox[2] - bbox[0]) * (bbox[3] - bbox[1])
        else outline.sumOf { it.area }

    override fun toString(): String = "RoutingArea($id)"
}

/** The road data of one area on the phone: the copy of its build manifest and the packs already extracted. */
data class LocalArea(val id: String, val cells: RoutingCells?, val installed: Set<String>)

/** The published build of one area, read from its Release. */
data class PublishedArea(val area: RoutingArea, val cells: RoutingCells)

/**
 * The index of the areas (#14), areas.json in the "routing-index" Release, made by
 * routing-areas.yml, and the choice of the area for a ride.
 */
object RoutingAreas {
    const val FORMAT = 1

    /** Today's Italy: built by routing-data.yml, kept in routing/cells on the phone since #45. */
    const val ITALY = "it"
    const val ITALY_RELEASE = "routing-data"

    /** No id or tag can build a path or a URL outside its place. */
    val AREA_ID = Regex("""[a-z0-9][a-z0-9-]{0,31}""")
    val RELEASE = Regex("""routing-[a-z0-9-]{1,40}""")

    /**
     * Before the index is published, or offline with no copy of it: only Italy, exactly as before
     * #14 (the box is the old sanity box; the published cells decide what is covered).
     */
    val FALLBACK: List<RoutingArea> = listOf(RoutingArea(ITALY, "Italy", ITALY_RELEASE, listOf(6.0, 35.0, 19.0, 48.0)))

    /**
     * Reads the index. A wrong format fails (the fields could mean something else); a single
     * broken area is skipped, and so is a repeated id. Italy is always there: if the index forgot
     * it, the fallback entry is added, so the data already on the phone stays usable.
     */
    fun parse(json: String): List<RoutingArea> {
        val root = Json.parseToJsonElement(json).jsonObject
        require(root["format"]?.jsonPrimitive?.int == FORMAT) { "Unsupported index format" }
        val out = ArrayList<RoutingArea>()
        for (item in root.getValue("areas").jsonArray) {
            val area = runCatching { item.jsonObject.toArea() }.getOrNull() ?: continue
            if (out.none { it.id == area.id }) out += area
        }
        if (out.none { it.id == ITALY }) out += FALLBACK
        return out
    }

    private fun JsonObject.toArea(): RoutingArea {
        val id = getValue("id").jsonPrimitive.content
        val release = getValue("release").jsonPrimitive.content
        require(AREA_ID.matches(id) && RELEASE.matches(release))
        // Italy's data is in its Release since #45: the index cannot move it elsewhere.
        require(id != ITALY || release == ITALY_RELEASE)
        val bbox = getValue("bbox").jsonArray.map { it.jsonPrimitive.double }
        require(bbox.size == 4 && bbox[0] < bbox[2] && bbox[1] < bbox[3])
        require(bbox[0] >= -180 && bbox[2] <= 180 && bbox[1] >= -90 && bbox[3] <= 90)
        // A broken outline only loses the outline: the box still works.
        val outline = runCatching {
            val o = get("outline")?.jsonObject ?: return@runCatching emptyList()
            val precision = o.getValue("precision").jsonPrimitive.int.also { require(it in 1..7) }
            o.getValue("rings").jsonArray.map { ring ->
                Countries.decode(ring.jsonPrimitive.content, precision).let { (lats, lons) ->
                    require(lats.size >= 3)
                    RoutingArea.Ring(lats, lons)
                }
            }
        }.getOrDefault(emptyList())
        val name = get("name")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: id
        return RoutingArea(id, name, release, bbox, outline)
    }

    /**
     * The areas worth asking online for this ride, best first: those whose outline holds every
     * stop (smallest first), then those whose box holds at least one stop, for a ride that
     * crosses the edge of the data (the published cells decide, and name the stops outside).
     */
    fun candidates(areas: List<RoutingArea>, points: List<LatLon>): List<RoutingArea> {
        if (points.isEmpty()) return emptyList()
        val full = areas.filter { it.coversAll(points) }.sortedBy { it.size }
        val partial = areas.filter { a -> a !in full && points.any(a::inBox) }.sortedWith(
            compareByDescending<RoutingArea> { a -> points.count(a::covers) }.thenBy { it.size },
        )
        return full + partial
    }

    /**
     * Data on the phone that routes the ride with no download. [strict]: only an area whose
     * outline holds every stop (an area with no known outline counts, as before #14). Not strict:
     * any area whose installed cells cover the stops, e.g. offline, or when no area covers them
     * fully (a border cell of Italy holds only the Italian side of its roads).
     * Among several: the one that fully covers, then the smaller.
     */
    fun local(areas: List<RoutingArea>, local: List<LocalArea>, points: List<LatLon>, strict: Boolean = true): LocalArea? {
        val byId = areas.associateBy { it.id }
        val usable = local.filter { CellsDownload.coveredLocally(it.cells, it.installed, points) }
            .filter { !strict || byId[it.id]?.coversAll(points) != false }
        return usable.minWithOrNull(
            compareBy<LocalArea> { if (byId[it.id]?.coversAll(points) == true) 0 else 1 }
                .thenBy { byId[it.id]?.size ?: Double.MAX_VALUE }
                .thenBy { it.id },
        )
    }

    /**
     * What to download for the ride, among the published areas: first the areas whose outline
     * holds every stop, then any whose cells cover them. Within each group the smallest
     * download wins (data already on the phone costs nothing), then the smaller area.
     * Null if no area's cells cover every stop.
     */
    fun plan(published: List<PublishedArea>, local: List<LocalArea>, points: List<LatLon>): CellsDownload? {
        val plans = published.mapNotNull { p ->
            val mine = local.firstOrNull { it.id == p.area.id }
            CellsDownload.plan(p.cells, mine?.cells, mine?.installed.orEmpty(), points)
                ?.copy(area = p.area.id, release = p.area.release)
                ?.let { p.area to it }
        }
        return plans.minWithOrNull(
            compareBy<Pair<RoutingArea, CellsDownload>> { (a, _) -> if (a.coversAll(points)) 0 else 1 }
                .thenBy { (_, d) -> d.bytes }
                .thenBy { (a, _) -> a.size },
        )?.second
    }

    /**
     * The stops outside the road data, for the message (#14): those of the published area that
     * leaves out the fewest stops (the area the ride mostly belongs to). Empty when nothing
     * is known.
     */
    fun outside(published: List<PublishedArea>, points: List<LatLon>): List<Int> =
        published.filter { p -> p.cells.packs.any { it.level == 1 } }
            .map { it.cells.outside(points) }
            .minByOrNull { it.size }
            .orEmpty()
}
