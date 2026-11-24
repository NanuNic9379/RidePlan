package io.github.nanunic9379.rideplan.route

import io.github.nanunic9379.rideplan.ride.LatLon
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToLong

/**
 * The map along one ride, for use without signal (#1, step 1 of docs/OFFLINE-MAP.md).
 *
 * The download is a strip of [MARGIN_KM] on each side of the calculated road, from zoom
 * [MIN_ZOOM] up to a maximum zoom chosen so the whole download stays within one OpenFreeMap
 * offline download (3,000 tiles and 50 MB, see [MapTiles]). Everything here is plain arithmetic:
 * the size is known and shown before anything is downloaded.
 */
object RideMap {
    /** Width of the strip on each side of the road. */
    const val MARGIN_KM = 5.0

    /** Below this zoom a ride is a line across a whole region: the ambient map is enough. */
    const val MIN_ZOOM = 5

    /** OpenFreeMap tiles stop at 14; MapLibre draws closer zooms from them (overzoom). */
    const val FULL_ZOOM = 14

    /** Below this maximum zoom the map no longer shows the small roads: not worth offering. */
    const val LOWEST_ZOOM = 11

    /**
     * Average size of one tile at [zoom], measured on a saved ride map (Turin, November 2026):
     * the few tiles far out cover whole regions and weigh 100-300 KB, the many close ones
     * 20-60 KB. The real size shows during the download, and [overLimit] stops it if the
     * estimate was too low.
     */
    fun tileBytes(zoom: Int): Long = when {
        zoom <= 8 -> 250_000L
        zoom <= 10 -> 150_000L
        zoom <= 12 -> 60_000L
        else -> 35_000L
    }

    /** Style, icons and the fonts of the scripts kept ([GLYPHS_KEPT]): measured 5 MB. */
    const val STYLE_BYTES = 5_000_000L

    /** The Liberty style also draws a shaded-relief raster up to this zoom: one more tile each. */
    const val RELIEF_MAX_ZOOM = 6

    /** Points closer than this along the track add nothing to a 5 km strip. */
    private const val STEP_KM = 0.25

    /** Each area is drawn 1% inside its tiles, so MapLibre does not also take the neighbours. */
    private const val INSET = 0.01

    /** How big the map for a ride is with tiles up to [maxZoom]. */
    data class Estimate(val maxZoom: Int, val tiles: Long, val bytes: Long) {
        val fits: Boolean get() = tiles <= MapTiles.OPENFREEMAP_MAX_TILES && bytes <= MapTiles.OPENFREEMAP_MAX_BYTES
    }

    /** One rectangle of the area to download: lon min, lat min, lon max, lat max. */
    data class Area(val west: Double, val south: Double, val east: Double, val north: Double)

    /** What to download: the [areas] (one row of tiles each) from [MIN_ZOOM] to [Estimate.maxZoom]. */
    data class Plan(val estimate: Estimate, val areas: List<Area>)

    /** What the ride sheet can offer for a ride. */
    sealed interface Offer {
        /** The whole ride at full detail, in one download. */
        data class Full(val plan: Plan) : Offer

        /** Full detail would be too big ([full]); [plan] is the most detail that fits. */
        data class Reduced(val full: Estimate, val plan: Plan) : Offer

        /** Too long even at [LOWEST_ZOOM] ([lowest]): the ride has to be split into stages. */
        data class TooLong(val lowest: Estimate) : Offer
    }

    /** The best download for [track], or null if there is no road to follow. */
    fun offer(track: List<LatLon>): Offer? {
        if (track.size < 2) return null
        val top = tiles(track, FULL_ZOOM)
        val levels = HashMap<Int, Set<Long>>()
        levels[FULL_ZOOM] = top
        for (z in FULL_ZOOM - 1 downTo MIN_ZOOM) levels[z] = parents(levels.getValue(z + 1))
        fun estimate(maxZoom: Int): Estimate {
            var count = 0L
            var bytes = STYLE_BYTES
            for (z in MIN_ZOOM..maxZoom) {
                val n = levels.getValue(z).size.toLong().let { if (z <= RELIEF_MAX_ZOOM) 2 * it else it }
                count += n
                bytes += n * tileBytes(z)
            }
            return Estimate(maxZoom, count, bytes)
        }
        val full = estimate(FULL_ZOOM)
        if (full.fits) return Offer.Full(Plan(full, areas(levels.getValue(FULL_ZOOM), FULL_ZOOM)))
        for (z in FULL_ZOOM - 1 downTo LOWEST_ZOOM) {
            val e = estimate(z)
            if (e.fits) return Offer.Reduced(full, Plan(e, areas(levels.getValue(z), z)))
        }
        return Offer.TooLong(estimate(LOWEST_ZOOM))
    }

    /**
     * The tiles at [zoom] within [marginKm] of the road. A tile one zoom out is near the road
     * exactly when one of its four children is, so the lower zooms are the parents of these.
     */
    fun tiles(track: List<LatLon>, zoom: Int, marginKm: Double = MARGIN_KM): Set<Long> {
        val points = thin(track)
        if (points.isEmpty()) return emptySet()
        val legs = if (points.size == 1) listOf(points[0] to points[0]) else points.zipWithNext()
        val padLat = marginKm / 110.0
        val out = HashSet<Long>()
        for ((a, b) in legs) {
            val lat = minOf(maxOf(kotlin.math.abs(a.lat), kotlin.math.abs(b.lat)) + padLat, 85.0)
            val padLon = marginKm / (111.32 * cos(lat * PI / 180))
            val x0 = MapTiles.x(minOf(a.lon, b.lon) - padLon, zoom)
            val x1 = MapTiles.x(maxOf(a.lon, b.lon) + padLon, zoom)
            val y0 = MapTiles.y(maxOf(a.lat, b.lat) + padLat, zoom)
            val y1 = MapTiles.y(minOf(a.lat, b.lat) - padLat, zoom)
            for (tx in x0..x1) for (ty in y0..y1) {
                val k = key(tx, ty)
                if (k !in out && Corridor.near(a, b, MapTiles.bbox(zoom, tx, ty), marginKm)) out += k
            }
        }
        return out
    }

    /** The same area one zoom out. */
    fun parents(tiles: Set<Long>): Set<Long> = tiles.mapTo(HashSet()) { key(tileX(it) / 2, tileY(it) / 2) }

    /**
     * The tiles as rectangles for MapLibre: one per run of neighbouring tiles in a row, a little
     * inside the tile edges. MapLibre then takes exactly these tiles at [zoom] and their parents.
     */
    fun areas(tiles: Set<Long>, zoom: Int): List<Area> {
        val out = ArrayList<Area>()
        tiles.groupBy { tileY(it) }.entries.sortedBy { it.key }.forEach { (y, row) ->
            val xs = row.map { tileX(it) }.sorted()
            var start = xs[0]
            for (i in 1..xs.size) {
                if (i < xs.size && xs[i] == xs[i - 1] + 1) continue
                val end = xs[i - 1]
                val first = MapTiles.bbox(zoom, start, y)
                val last = MapTiles.bbox(zoom, end, y)
                val w = first[2] - first[0]
                val h = first[3] - first[1]
                out += Area(first[0] + w * INSET, first[1] + h * INSET, last[2] - w * INSET, first[3] - h * INSET)
                if (i < xs.size) start = xs[i]
            }
        }
        return out
    }

    /** A download that grew past the OpenFreeMap limits must stop, whatever the estimate said. */
    fun overLimit(tiles: Long, bytes: Long): Boolean =
        tiles > MapTiles.OPENFREEMAP_MAX_TILES || bytes > MapTiles.OPENFREEMAP_MAX_BYTES

    /**
     * Identifies the road of a ride: the same calculated road gives the same key, a new route
     * (other stops, other preferences) a new one. Points rounded to about 10 m.
     */
    fun rideKey(track: List<LatLon>): String {
        var h = -0x340d631b7bdddcdbL // FNV-1a offset basis
        for (p in track) {
            for (v in longArrayOf((p.lat * 10_000).roundToLong(), (p.lon * 10_000).roundToLong())) {
                var x = v
                repeat(8) {
                    h = (h xor (x and 0xff)) * 0x100000001b3L
                    x = x ushr 8
                }
            }
        }
        return h.toULong().toString(16).padStart(16, '0')
    }

    /**
     * Version of the OpenFreeMap tiles in a tile address ("20251119_001001_pt"). OpenFreeMap
     * publishes new tiles every week under a new address: a map saved with an older version is
     * no longer used once the phone has seen the new one, so the ride sheet offers to update it.
     */
    fun tilesVersion(url: String): String? =
        Regex("""^https://tiles\.openfreemap\.org/planet/([^/]+)/\d+/\d+/\d+""").find(url)?.groupValues?.get(1)

    /**
     * Font ranges a saved map keeps: up to U+2FFF, so Latin, Greek, Cyrillic, Arabic, Hebrew,
     * Indian scripts and symbols. Chinese, Japanese and Korean would add about 40 MB.
     */
    const val GLYPHS_KEPT = 0x3000

    /** First character of a font range address (".../fonts/Noto Sans Regular/12288-12543.pbf"). */
    fun glyphRangeStart(url: String): Int? =
        Regex("""/fonts/[^/]+/(\d+)-\d+\.pbf$""").find(url)?.groupValues?.get(1)?.toIntOrNull()

    /** Points at least [STEP_KM] apart, always keeping the first and the last. */
    internal fun thin(track: List<LatLon>): List<LatLon> {
        if (track.size <= 2) return track
        val out = ArrayList<LatLon>()
        out += track[0]
        for (i in 1 until track.size - 1) {
            if (km(out.last(), track[i]) >= STEP_KM) out += track[i]
        }
        out += track.last()
        return out
    }

    private fun km(a: LatLon, b: LatLon): Double {
        val kx = 111.32 * cos((a.lat + b.lat) / 2 * PI / 180)
        return hypot((b.lon - a.lon) * kx, (b.lat - a.lat) * 110.57)
    }

    private fun key(x: Int, y: Int): Long = (x.toLong() shl 32) or y.toLong()
    private fun tileX(k: Long): Int = (k ushr 32).toInt()
    private fun tileY(k: Long): Int = (k and 0xffffffffL).toInt()
}

/**
 * What RidePlan stores with a saved map (MapLibre keeps it as the pack's metadata): which ride
 * it is for and what it holds. Unknown fields are ignored, so older versions can read newer ones.
 */
@Serializable
data class RideMapInfo(
    /** [RideMap.rideKey] of the ride's road. */
    val ride: String,
    val name: String? = null,
    val maxZoom: Int,
    val tiles: Long,
    /** Estimated bytes before the download; the real size comes from MapLibre. */
    val bytes: Long,
    /** [RideMap.tilesVersion] of the downloaded tiles, once known. */
    val version: String? = null,
    /** Milliseconds since 1970 when the download started. */
    val savedAt: Long = 0,
    /** Set when the download reached the end. */
    val complete: Boolean = false,
) {
    fun encode(): ByteArray = json.encodeToString(serializer(), this).encodeToByteArray()

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Null for packs not made by this feature, or unreadable. */
        fun decode(bytes: ByteArray?): RideMapInfo? =
            bytes?.let { runCatching { json.decodeFromString(serializer(), it.decodeToString()) }.getOrNull() }
    }
}
