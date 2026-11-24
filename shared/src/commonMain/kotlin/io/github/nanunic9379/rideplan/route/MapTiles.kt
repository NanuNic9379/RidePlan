package io.github.nanunic9379.rideplan.route

import io.github.nanunic9379.rideplan.ride.LatLon
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

/**
 * Web-map tile arithmetic for the offline map (#1, #14): how many map tiles an area or a ride
 * needs, before anything is downloaded, so the size can be shown first and the download kept
 * within the limits of the tile server.
 */
object MapTiles {
    /**
     * OpenFreeMap terms of service (openfreemap.org/tos): an offline download may hold at most
     * 3,000 tiles and 50 MB, use at most 4 parallel requests and send its own User-Agent.
     * Larger areas must be split into several downloads, each started by the user.
     */
    const val OPENFREEMAP_MAX_TILES = 3_000
    const val OPENFREEMAP_MAX_BYTES = 50_000_000L
    const val OPENFREEMAP_MAX_PARALLEL = 4

    /** Web Mercator stops at about 85.05 degrees. */
    private const val MAX_LAT = 85.0511

    fun x(lon: Double, zoom: Int): Int = floor((lon + 180) / 360 * (1 shl zoom)).toInt().coerceIn(0, (1 shl zoom) - 1)

    fun y(lat: Double, zoom: Int): Int {
        val r = lat.coerceIn(-MAX_LAT, MAX_LAT) * PI / 180
        return floor((1 - ln(tan(r) + 1 / kotlin.math.cos(r)) / PI) / 2 * (1 shl zoom)).toInt().coerceIn(0, (1 shl zoom) - 1)
    }

    /** lon min, lat min, lon max, lat max of tile [x], [y] at [zoom]. */
    fun bbox(zoom: Int, x: Int, y: Int): List<Double> {
        val n = (1 shl zoom).toDouble()
        fun lat(row: Int) = atan(sinh(PI * (1 - 2 * row / n))) * 180 / PI
        return listOf(x / n * 360 - 180, lat(y + 1), (x + 1) / n * 360 - 180, lat(y))
    }

    /** Tiles covering [bbox] (lon min, lat min, lon max, lat max) from [minZoom] to [maxZoom]. */
    fun count(bbox: List<Double>, minZoom: Int, maxZoom: Int): Long = (minZoom..maxZoom).sumOf { z ->
        (x(bbox[2], z) - x(bbox[0], z) + 1).toLong() * (y(bbox[1], z) - y(bbox[3], z) + 1)
    }

    /**
     * Tiles within [marginKm] of a calculated route [track], per zoom from [minZoom] to [maxZoom].
     * The map for a ride follows the real road, so a narrow strip is enough: a 300 km ride with
     * 5 km on each side stays within one OpenFreeMap download even at zoom 14.
     */
    fun corridor(track: List<LatLon>, marginKm: Double, minZoom: Int, maxZoom: Int): Long {
        if (track.isEmpty()) return 0
        val legs = if (track.size == 1) listOf(track[0] to track[0]) else track.zipWithNext()
        // Degrees of latitude for the margin, and of longitude at the worst latitude of the leg.
        val padLat = marginKm / 110.0
        var total = 0L
        for (z in minZoom..maxZoom) {
            val tiles = HashSet<Long>()
            for ((a, b) in legs) {
                val lat = maxOf(kotlin.math.abs(a.lat), kotlin.math.abs(b.lat)) + padLat
                val padLon = marginKm / (111.32 * kotlin.math.cos(minOf(lat, MAX_LAT) * PI / 180))
                val x0 = x(minOf(a.lon, b.lon) - padLon, z)
                val x1 = x(maxOf(a.lon, b.lon) + padLon, z)
                val y0 = y(maxOf(a.lat, b.lat) + padLat, z)
                val y1 = y(minOf(a.lat, b.lat) - padLat, z)
                for (tx in x0..x1) for (ty in y0..y1) {
                    val key = tx.toLong() shl 32 or ty.toLong()
                    if (key !in tiles && Corridor.near(a, b, bbox(z, tx, ty), marginKm)) tiles += key
                }
            }
            total += tiles.size
        }
        return total
    }
}
