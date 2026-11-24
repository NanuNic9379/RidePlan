package io.github.nanunic9379.rideplan.route

import io.github.nanunic9379.rideplan.ride.LatLon
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * A pack of road data split into grid cells (#45), from cells.json of routing-data.yml.
 * Level 1: a 1-degree cell with all its roads. Level 0: the main roads of a 4-degree block,
 * needed by every cell inside that block.
 */
data class RoutingPack(
    val id: String,
    val level: Int,
    /** lon min, lat min, lon max, lat max. */
    val bbox: List<Double>,
    val file: String,
    val bytes: Long,
    /** Space used on the phone once extracted. */
    val tileBytes: Long,
    val sha256: String,
    /** The fuel stations of the cell (#7): only level 1, only builds made after #7. */
    val fuel: FuelFile? = null,
) {
    fun contains(p: LatLon): Boolean = p.lon >= bbox[0] && p.lat >= bbox[1] && p.lon <= bbox[2] && p.lat <= bbox[3]

    internal fun containsCenterOf(o: RoutingPack): Boolean = contains(LatLon((o.bbox[1] + o.bbox[3]) / 2, (o.bbox[0] + o.bbox[2]) / 2))
}

/**
 * The grid cells of one data build. Packs from different builds must never be mixed: the
 * numbering of roads and junctions at the cell edges changes with every build.
 */
data class RoutingCells(
    val build: String,
    val osmDate: String,
    val valhalla: String,
    val packs: List<RoutingPack>,
) {
    /**
     * The packs a ride needs: the cells near the straight line between consecutive stops, with a
     * margin because the real road (especially with curves) strays from the straight line, plus
     * the main-road blocks that contain them.
     * Null if a stop falls outside every published cell.
     */
    fun needed(points: List<LatLon>): List<RoutingPack>? {
        val cells = packs.filter { it.level == 1 }
        if (points.isEmpty() || outside(points).isNotEmpty()) return null
        val legs = if (points.size == 1) listOf(points[0] to points[0]) else points.zipWithNext()
        val chosen = cells.filter { cell -> legs.any { (a, b) -> Corridor.near(a, b, cell.bbox) } }
        val bases = packs.filter { it.level == 0 && chosen.any(it::containsCenterOf) }
        return bases + chosen
    }

    /**
     * The positions in [points] that fall outside every published cell (#14): the stops the app
     * has no road data for, to tell the rider which ones they are.
     */
    fun outside(points: List<LatLon>): List<Int> {
        val cells = packs.filter { it.level == 1 }
        return points.indices.filter { i -> cells.none { it.contains(points[i]) } }
    }

    /** Same format as cells.json: the phone keeps a copy of the downloaded build. */
    fun toJson(): String = buildJsonObject {
        put("format", FORMAT)
        put("build", build)
        put("osmDate", osmDate)
        put("valhalla", valhalla)
        put("packs", buildJsonArray {
            packs.forEach { p ->
                add(buildJsonObject {
                    put("id", p.id)
                    put("level", p.level)
                    put("bbox", buildJsonArray { p.bbox.forEach { add(JsonPrimitive(it)) } })
                    put("file", p.file)
                    put("bytes", p.bytes)
                    put("tileBytes", p.tileBytes)
                    put("sha256", p.sha256)
                    p.fuel?.let { f ->
                        put("fuel", buildJsonObject {
                            put("file", f.file)
                            put("bytes", f.bytes)
                            put("sha256", f.sha256)
                        })
                    }
                })
            }
        })
    }.toString()

    companion object {
        const val FORMAT = 1

        fun parse(json: String): RoutingCells {
            val root = Json.parseToJsonElement(json).jsonObject
            require(root["format"]?.jsonPrimitive?.int == FORMAT) { "Unsupported data format" }
            return RoutingCells(
                build = root.str("build"),
                osmDate = root.str("osmDate"),
                valhalla = root.str("valhalla"),
                packs = root.getValue("packs").jsonArray.map { it.jsonObject.toPack() },
            )
        }

        private fun JsonObject.toPack() = RoutingPack(
            id = str("id"),
            level = getValue("level").jsonPrimitive.int,
            bbox = getValue("bbox").jsonArray.map { it.jsonPrimitive.double }.also { require(it.size == 4) },
            file = str("file"),
            bytes = getValue("bytes").jsonPrimitive.long,
            tileBytes = getValue("tileBytes").jsonPrimitive.long,
            sha256 = str("sha256"),
            // Optional and never fatal: a manifest without it, or with a broken one, still routes.
            fuel = runCatching { get("fuel")?.jsonObject?.toFuelFile() }.getOrNull(),
        ).also { require(Regex("""l[01]-\d+""").matches(it.id)) { "Invalid pack" } }

        private fun JsonObject.toFuelFile() = FuelFile(
            file = str("file"),
            bytes = getValue("bytes").jsonPrimitive.long,
            sha256 = str("sha256"),
        ).takeIf { FuelFile.NAME.matches(it.file) }

        private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content
    }
}

/** What the ride card offers to download. */
sealed interface DataDownload {
    /** Download size. */
    val bytes: Long

    /** Space used on the phone afterwards. */
    val tileBytes: Long
}

/** A whole region: only while the grid cells are not published yet. */
data class RegionDownload(val region: RoutingRegion) : DataDownload {
    override val bytes: Long get() = region.bytes
    override val tileBytes: Long get() = region.tarBytes
}

/**
 * What to download for the ride with grid-cell data. [wipe]: the cells on the phone come from
 * an older build and must be replaced (they cannot be mixed with the new ones).
 */
data class CellsDownload(val cells: RoutingCells, val packs: List<RoutingPack>, val wipe: Boolean) : DataDownload {
    override val bytes: Long get() = packs.sumOf { it.bytes + (it.fuel?.bytes ?: 0) }
    override val tileBytes: Long get() = packs.sumOf { it.tileBytes }

    companion object {
        /**
         * Compares the published packs with those already on the phone ([local], [installed]).
         * Null if the ride leaves the published cells; empty list if everything is already there.
         */
        fun plan(published: RoutingCells, local: RoutingCells?, installed: Set<String>, points: List<LatLon>): CellsDownload? {
            val needed = published.needed(points) ?: return null
            val same = local?.build == published.build
            val missing = if (same) needed.filter { it.id !in installed } else needed
            return CellsDownload(published, missing, wipe = !same && installed.isNotEmpty())
        }

        /** The ride can be routed with the cells already on the phone, offline. */
        fun coveredLocally(local: RoutingCells?, installed: Set<String>, points: List<LatLon>): Boolean {
            val needed = local?.needed(points) ?: return false
            return needed.all { it.id in installed }
        }
    }
}

/** How much margin to keep around the straight line between two stops. */
internal object Corridor {
    /**
     * Minimum margin in km: covers the radius of "pass near" towns (2.5 km) and roads that wind
     * around a hill. To be tuned on real rides (#45).
     */
    const val MIN_MARGIN_KM = 25.0

    /** The longer the leg, the further the real road can stray from the straight line. */
    const val MARGIN_SHARE = 0.25

    const val MAX_MARGIN_KM = 80.0

    fun marginKm(legKm: Double): Double = min(MAX_MARGIN_KM, MIN_MARGIN_KM + MARGIN_SHARE * legKm)

    /** Is the cell [bbox] within the margin of leg a-b? [fixedMarginKm]: instead of [marginKm]. */
    fun near(a: LatLon, b: LatLon, bbox: List<Double>, fixedMarginKm: Double? = null): Boolean {
        // Local plane in km around the leg's latitude: accurate enough for tens of km.
        val kx = 111.32 * cos((a.lat + b.lat) / 2 * PI / 180)
        val ky = 110.57
        val ax = a.lon * kx
        val ay = a.lat * ky
        val bx = b.lon * kx
        val by = b.lat * ky
        val margin = fixedMarginKm ?: marginKm(hypot(bx - ax, by - ay))
        val x0 = bbox[0] * kx
        val y0 = bbox[1] * ky
        val x1 = bbox[2] * kx
        val y1 = bbox[3] * ky
        // Quick reject: the cell grown by the margin does not even touch the leg's bounding box.
        if (max(ax, bx) < x0 - margin || min(ax, bx) > x1 + margin || max(ay, by) < y0 - margin || min(ay, by) > y1 + margin) return false
        if (segmentHitsBox(ax, ay, bx, by, x0, y0, x1, y1)) return true
        val d = minOf(
            pointBox(ax, ay, x0, y0, x1, y1),
            pointBox(bx, by, x0, y0, x1, y1),
            pointSegment(x0, y0, ax, ay, bx, by),
            pointSegment(x1, y0, ax, ay, bx, by),
            pointSegment(x0, y1, ax, ay, bx, by),
            pointSegment(x1, y1, ax, ay, bx, by),
        )
        return d <= margin
    }

    private fun pointBox(x: Double, y: Double, x0: Double, y0: Double, x1: Double, y1: Double): Double =
        hypot(max(max(x0 - x, 0.0), x - x1), max(max(y0 - y, 0.0), y - y1))

    private fun pointSegment(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    /** Liang-Barsky: does the segment cross the rectangle? */
    private fun segmentHitsBox(ax: Double, ay: Double, bx: Double, by: Double, x0: Double, y0: Double, x1: Double, y1: Double): Boolean {
        var t0 = 0.0
        var t1 = 1.0
        val dx = bx - ax
        val dy = by - ay
        val p = doubleArrayOf(-dx, dx, -dy, dy)
        val q = doubleArrayOf(ax - x0, x1 - ax, ay - y0, y1 - ay)
        for (i in 0..3) {
            if (p[i] == 0.0) {
                if (q[i] < 0) return false
            } else {
                val r = q[i] / p[i]
                if (p[i] < 0) t0 = max(t0, r) else t1 = min(t1, r)
                if (t0 > t1) return false
            }
        }
        return true
    }
}
