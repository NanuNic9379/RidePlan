package io.github.nanunic9379.rideplan.route

import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.distanceKm
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * How rough an unpaved stretch is (#9), from Valhalla's `surface` of the edge.
 * [GRAVEL]: compacted or gravel, fine on any bike at low speed.
 * [DIRT]: dirt, earth, grass or worse: an adventure bike's road, a problem for a tourer when wet.
 */
enum class Rough { GRAVEL, DIRT }

/** An unpaved stretch of the route: [fromM] to [toM] metres from the start. */
data class RoughStretch(val fromM: Double, val toM: Double, val kind: Rough) {
    val lengthM: Double get() = toM - fromM
}

/**
 * The unpaved stretches along a route computed on the phone (#9), from the OpenStreetMap
 * `surface` and `tracktype` tags already in the road data (Valhalla turns them into one
 * surface class per edge). Paved roads and roads whose surface is unknown are missing.
 * Not saved: like the speed limits they come back when the ride is routed again.
 */
data class RoadSurfaces(val stretches: List<RoughStretch>) {
    val isEmpty: Boolean get() = stretches.isEmpty()

    /** All unpaved metres. */
    val unpavedM: Double get() = stretches.sumOf { it.lengthM }

    /** Metres of the rougher kind ([Rough.DIRT]). */
    val dirtM: Double get() = stretches.filter { it.kind == Rough.DIRT }.sumOf { it.lengthM }

    /** The longest single unpaved stretch, gravel and dirt together when they touch. */
    val longestM: Double
        get() {
            var best = 0.0
            var from = Double.NaN
            var to = Double.NaN
            for (s in stretches) {
                if (from.isNaN() || s.fromM - to > JOIN_M) {
                    from = s.fromM
                }
                to = s.toM
                best = maxOf(best, to - from)
            }
            return best
        }

    /** Worth telling the rider: a few metres of gravel at a car park are not. */
    val worthWarning: Boolean get() = unpavedM >= WARN_M

    /** The unpaved stretch at [alongM] metres from the start, if any. */
    fun at(alongM: Double): RoughStretch? = stretches.firstOrNull { alongM >= it.fromM && alongM <= it.toM }

    /**
     * The pieces of [track] that are unpaved, to draw them on the map. [track] must be the line
     * the stretches were measured on (the route's shape); points are cut where a stretch begins
     * and ends.
     */
    fun lines(track: List<LatLon>): List<Pair<Rough, List<LatLon>>> {
        if (track.size < 2 || stretches.isEmpty()) return emptyList()
        val along = DoubleArray(track.size)
        for (i in 1 until track.size) along[i] = along[i - 1] + distanceKm(track[i - 1], track[i]) * 1000
        return stretches.mapNotNull { s ->
            val from = s.fromM.coerceIn(0.0, along.last())
            val to = s.toM.coerceIn(0.0, along.last())
            if (to <= from) return@mapNotNull null
            val out = ArrayList<LatLon>()
            out += pointAt(track, along, from)
            for (i in track.indices) if (along[i] > from && along[i] < to) out += track[i]
            out += pointAt(track, along, to)
            s.kind to out
        }
    }

    companion object {
        val NONE = RoadSurfaces(emptyList())

        /** Below this much unpaved road in total the ride sheet says nothing. */
        const val WARN_M = 300.0

        /** Gaps up to this size between stretches are closed. */
        private const val JOIN_M = 1.0

        /** Joins touching stretches of the same kind; sorts and drops empty or overlapping parts. */
        fun of(stretches: List<RoughStretch>): RoadSurfaces {
            val out = ArrayList<RoughStretch>()
            for (s in stretches.filter { it.toM > it.fromM }.sortedBy { it.fromM }) {
                val last = out.lastOrNull()
                when {
                    last != null && last.kind == s.kind && s.fromM - last.toM <= JOIN_M ->
                        out[out.lastIndex] = last.copy(toM = maxOf(last.toM, s.toM))
                    last != null && s.fromM < last.toM -> if (s.toM > last.toM) out += s.copy(fromM = last.toM)
                    else -> out += s
                }
            }
            return RoadSurfaces(out)
        }

        private fun pointAt(track: List<LatLon>, along: DoubleArray, m: Double): LatLon {
            val i = along.indexOfFirst { it >= m }
            if (i < 0) return track.last()
            if (i == 0) return track.first()
            val span = along[i] - along[i - 1]
            if (span <= 0) return track[i]
            val f = (m - along[i - 1]) / span
            val a = track[i - 1]
            val b = track[i]
            return LatLon(a.lat + (b.lat - a.lat) * f, a.lon + (b.lon - a.lon) * f)
        }
    }
}

/** What one walk of the route with `trace_attributes` tells about its roads. */
data class RoadAttributes(val speedLimits: SpeedLimits, val surfaces: RoadSurfaces) {
    companion object {
        val NONE = RoadAttributes(SpeedLimits.NONE, RoadSurfaces.NONE)
    }
}

/**
 * Speed limits (#31) and unpaved stretches (#9) from a single walk of the computed road: the
 * same `trace_attributes` request ([ValhallaSpeedLimits.request]) asks for both, so the phone
 * walks the route only once.
 */
object ValhallaRoadAttributes {
    fun compute(shape: List<LatLon>, engine: (String) -> String): RoadAttributes {
        val pieces = ValhallaSpeedLimits.walk(shape, engine) { json, offset ->
            ValhallaSpeedLimits.parse(json, offset) to ValhallaRoadSurface.parse(json, offset)
        }
        if (pieces.isEmpty()) return RoadAttributes.NONE
        return RoadAttributes(
            SpeedLimits.of(pieces.flatMap { it.first }),
            RoadSurfaces.of(pieces.flatMap { it.second }),
        )
    }
}

/** Unpaved stretches in one `trace_attributes` response (#9). */
object ValhallaRoadSurface {
    /**
     * Valhalla's surface classes, from best to worst (`paved_smooth`, `paved`, `paved_rough`,
     * `compacted`, `dirt`, `gravel`, `path`, `impassable`). Note that Valhalla ranks `gravel`
     * below `dirt` (loose stones), but for a motorcyclist gravel roads are usually the easier ones.
     */
    fun classify(surface: String?, unpaved: Boolean?): Rough? = when (surface) {
        "compacted", "gravel" -> Rough.GRAVEL
        "dirt", "path", "impassable" -> Rough.DIRT
        "paved_smooth", "paved", "paved_rough" -> null
        // An unknown class: trust the engine's own flag.
        else -> if (unpaved == true) Rough.GRAVEL else null
    }

    /**
     * The unpaved stretches in one response. Shape indices of the edges refer to the matched
     * shape in the response; [offsetM] is where the piece starts on the whole route.
     */
    fun parse(json: String, offsetM: Double): List<RoughStretch> {
        val root = Json.parseToJsonElement(json).jsonObject
        val shape = Polyline.decode(root.getValue("shape").jsonPrimitive.content, 6)
        val along = DoubleArray(shape.size)
        for (i in 1 until shape.size) along[i] = along[i - 1] + distanceKm(shape[i - 1], shape[i]) * 1000
        val edges = root["edges"] as? JsonArray ?: return emptyList()
        return edges.mapNotNull { e ->
            val edge = e.jsonObject
            val kind = classify(edge["surface"]?.jsonPrimitive?.contentOrNull, edge["unpaved"]?.jsonPrimitive?.booleanOrNull)
                ?: return@mapNotNull null
            val begin = edge["begin_shape_index"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val end = edge["end_shape_index"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            if (begin !in along.indices || end !in along.indices || end <= begin) return@mapNotNull null
            RoughStretch(offsetM + along[begin], offsetM + along[end], kind)
        }
    }
}
