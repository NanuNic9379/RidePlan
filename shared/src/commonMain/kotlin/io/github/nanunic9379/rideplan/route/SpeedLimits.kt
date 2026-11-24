package io.github.nanunic9379.rideplan.route

import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.distanceKm
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** A stretch of the route with a known speed limit: [fromM] to [toM] metres from the start. */
data class SpeedLimit(val fromM: Double, val toM: Double, val kmh: Int)

/**
 * The speed limits along a route computed on the phone (#31), from the OpenStreetMap `maxspeed`
 * tags already in the road data. Stretches without a tag are simply missing: the limit there is
 * unknown and nothing is shown. Roads with no limit at all ("none") are missing too.
 * Not saved: like the turn instructions they come back when the ride is routed again.
 */
data class SpeedLimits(val stretches: List<SpeedLimit>) {
    val isEmpty: Boolean get() = stretches.isEmpty()

    /** The limit at [alongM] metres from the start, in km/h; null if unknown there. */
    fun at(alongM: Double): Int? {
        var lo = 0
        var hi = stretches.lastIndex
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val s = stretches[mid]
            when {
                alongM < s.fromM -> hi = mid - 1
                alongM >= s.toM -> lo = mid + 1
                else -> return s.kmh
            }
        }
        // The very end of the last stretch belongs to it.
        return stretches.lastOrNull()?.takeIf { alongM == it.toM }?.kmh
    }

    companion object {
        val NONE = SpeedLimits(emptyList())

        /** Joins touching stretches with the same limit; sorts and drops empty ones. */
        fun of(stretches: List<SpeedLimit>): SpeedLimits {
            val out = ArrayList<SpeedLimit>()
            for (s in stretches.filter { it.toM > it.fromM }.sortedBy { it.fromM }) {
                val last = out.lastOrNull()
                when {
                    last != null && last.kmh == s.kmh && s.fromM - last.toM <= JOIN_M ->
                        out[out.lastIndex] = last.copy(toM = maxOf(last.toM, s.toM))
                    // Overlap (a matched shape a few metres longer): the later stretch starts where the earlier ends.
                    last != null && s.fromM < last.toM -> if (s.toM > last.toM) out += s.copy(fromM = last.toM)
                    else -> out += s
                }
            }
            return SpeedLimits(out)
        }

        /** Gaps up to this size between stretches with the same limit are closed. */
        private const val JOIN_M = 1.0
    }
}

/**
 * Speed limits from Valhalla's `trace_attributes` (#31): the computed road is walked again edge by
 * edge, and each edge tells its `speed_limit`. The engine limits the length of a trace, so long
 * routes are cut into pieces.
 */
object ValhallaSpeedLimits {
    /** Longest piece sent in one request: below Valhalla's default `trace.max_distance` (200 km). */
    const val PIECE_M = 100_000.0

    /** Most points in one piece: below Valhalla's default `trace.max_shape` (16000). */
    const val PIECE_POINTS = 4000

    /** A piece that fails is tried again in halves, at most this many times over. */
    private const val SPLITS = 3

    /** Below this many points a failing piece is given up: no limits there. */
    private const val MIN_SPLIT_POINTS = 40

    /**
     * Limits along [shape], the road of the route. [engine] runs one `trace_attributes` request
     * and returns the response; a piece it cannot walk is left without limits.
     */
    fun compute(shape: List<LatLon>, engine: (String) -> String): SpeedLimits {
        return SpeedLimits.of(walk(shape, engine) { json, offset -> parse(json, offset) }.flatten())
    }

    /**
     * Walks [shape] piece by piece with [engine] and reads each response with [read] (the JSON
     * and where the piece starts on the route, in metres). A piece the engine cannot walk is
     * tried again in halves, then left out. Shared with [ValhallaRoadAttributes] (#9) and, with
     * another [requestFor], with the ban check of imported tracks ([ValhallaTrackCheck]).
     */
    fun <T> walk(
        shape: List<LatLon>,
        engine: (String) -> String,
        requestFor: (List<LatLon>) -> String = ::request,
        read: (String, Double) -> T,
    ): List<T> {
        if (shape.size < 2) return emptyList()
        val along = DoubleArray(shape.size)
        for (i in 1 until shape.size) along[i] = along[i - 1] + distanceKm(shape[i - 1], shape[i]) * 1000
        val out = ArrayList<T>()
        fun walk(range: IntRange, splits: Int) {
            val piece = runCatching { read(engine(requestFor(shape.subList(range.first, range.last + 1))), along[range.first]) }
            when {
                piece.isSuccess -> out += piece.getOrThrow()
                splits > 0 && range.last - range.first >= MIN_SPLIT_POINTS -> {
                    val mid = (range.first + range.last) / 2
                    walk(range.first..mid, splits - 1)
                    walk(mid..range.last, splits - 1)
                }
            }
        }
        pieces(along).forEach { walk(it, SPLITS) }
        return out
    }

    /**
     * Indices of the pieces: each at most [PIECE_M] long and [PIECE_POINTS] points; each piece
     * starts at the last point of the previous one, so nothing is left out between them.
     */
    fun pieces(along: DoubleArray): List<IntRange> {
        if (along.size < 2) return emptyList()
        val out = ArrayList<IntRange>()
        var start = 0
        while (start < along.lastIndex) {
            var end = start + 1
            while (end < along.lastIndex && end - start + 1 < PIECE_POINTS && along[end + 1] - along[start] <= PIECE_M) end++
            out += start..end
            start = end
        }
        return out
    }

    /** The request for one piece: the exact road already computed, walked edge by edge. */
    fun request(points: List<LatLon>): String = buildJsonObject {
        put("encoded_polyline", Polyline.encode(points, 6))
        put("shape_match", "edge_walk")
        put("costing", "motorcycle")
        put("units", "kilometers")
        put("shape_format", "polyline6")
        put("filters", buildJsonObject {
            put("attributes", buildJsonArray {
                listOf(
                    "edge.speed_limit", "edge.begin_shape_index", "edge.end_shape_index", "shape",
                    // Unpaved stretches (#9), read by ValhallaRoadSurface from the same walk.
                    "edge.surface", "edge.unpaved",
                )
                    .forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
            })
            put("action", "include")
        })
    }.toString()

    /**
     * The stretches with a known limit in one response. Shape indices of the edges refer to the
     * matched shape in the response; [offsetM] is where the piece starts on the whole route.
     * A missing `speed_limit` (no `maxspeed` tag) and "unlimited" give no stretch.
     */
    fun parse(json: String, offsetM: Double): List<SpeedLimit> {
        val root = Json.parseToJsonElement(json).jsonObject
        val shape = Polyline.decode(root.getValue("shape").jsonPrimitive.content, 6)
        val along = DoubleArray(shape.size)
        for (i in 1 until shape.size) along[i] = along[i - 1] + distanceKm(shape[i - 1], shape[i]) * 1000
        val edges = root["edges"] as? JsonArray ?: return emptyList()
        return edges.mapNotNull { e ->
            val edge = e.jsonObject
            val kmh = edge["speed_limit"]?.jsonPrimitive?.intOrNull?.takeIf { it in 1..MAX_KMH } ?: return@mapNotNull null
            val begin = edge["begin_shape_index"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val end = edge["end_shape_index"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            if (along.isEmpty() || begin !in along.indices || end !in along.indices || end <= begin) return@mapNotNull null
            SpeedLimit(offsetM + along[begin], offsetM + along[end], kmh)
        }
    }

    /** Valhalla keeps limits in one byte; 255 means "unlimited" (some versions write it as a number). */
    private const val MAX_KMH = 250
}
