package io.github.nanunic9379.rideplan.route

import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.Ride
import io.github.nanunic9379.rideplan.ride.distanceKm
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Why a stretch of an imported track is not for this bike (#9). */
enum class BanKind {
    /** A motorway (or its ramps) and the bike is under 150 cc (#8): banned by law. */
    MOTORWAY,

    /** A road the road data closes to motorcycles (`access=no`, `motor_vehicle=no`, `motorcycle=no`...). */
    NO_ACCESS,
}

/** A stretch of the track [fromM] to [toM] metres from its start, with the road's name if known. */
data class BanStretch(val fromM: Double, val toM: Double, val kind: BanKind, val name: String? = null) {
    val lengthM: Double get() = toM - fromM
}

/**
 * What the ban check of an imported track found (#9). A GPX or KML track drawn as it is (not
 * routed on the phone) never went through the engine's access rules, so it may use a road closed
 * to motorcycles, or a motorway with a bike under 150 cc.
 * [checkedM]: how much of the track could be matched to the road data; the rest is unknown.
 */
data class TrackBans(val stretches: List<BanStretch>, val checkedM: Double, val trackM: Double) {
    val isEmpty: Boolean get() = stretches.isEmpty()

    fun metres(kind: BanKind): Double = stretches.filter { it.kind == kind }.sumOf { it.lengthM }

    /** Up to [max] distinct road names, in track order, to find the stretches on the map. */
    fun names(kind: BanKind, max: Int = 3): List<String> =
        stretches.filter { it.kind == kind }.mapNotNull { it.name }.distinct().take(max)

    /** Less than [CHECKED_SHARE] of the track was matched: the check is partial. */
    val partial: Boolean get() = trackM > 0 && checkedM < trackM * CHECKED_SHARE

    companion object {
        const val CHECKED_SHARE = 0.9
    }
}

/** An imported track drawn as it is: never routed on the phone, so never checked by the engine. */
val Ride.unroutedTrack: Boolean get() = route == null && !straightLines

/**
 * The ban check of an imported track (#9, docs/surface-and-bans.md). The track is matched to the
 * road data twice with Valhalla's `trace_attributes` (`shape_match: map_snap`), piece by piece:
 *
 * 1. **open**: motorcycle costing told to ignore access, restrictions, one-ways and closures, so
 *    the track lands on the roads it really uses; each edge gives its OSM way, road class and name;
 * 2. **legal**: the same costing with the access rules on, as for a computed route: roads closed
 *    to motorcycles cannot be matched, so their ways are missing from this match.
 *
 * A way of the open match that is missing from the legal one, where the legal match covered that
 * part of the track, is a road closed to motorcycles ([BanKind.NO_ACCESS]). A motorway in the open
 * match is [BanKind.MOTORWAY] when the bike is under 150 cc. What the road data does not contain
 * (footpaths, `highway=path`: routing-data.yml keeps only roads) cannot be checked.
 */
object ValhallaTrackCheck {
    /** Stretches shorter than this after joining are left out: matching noise at junctions. */
    const val MIN_M = 50.0

    /** Stretches of the same kind closer than this are joined. */
    const val JOIN_M = 30.0

    private val OPEN_ATTRIBUTES = listOf(
        "edge.way_id", "edge.road_class", "edge.names", "edge.begin_shape_index", "edge.end_shape_index", "shape",
    )
    private val LEGAL_ATTRIBUTES = listOf("edge.way_id", "edge.begin_shape_index", "edge.end_shape_index", "shape")

    /** The open match: every road the track touches, whatever its access. */
    fun openRequest(points: List<LatLon>): String = request(points, OPEN_ATTRIBUTES, open = true)

    /** The legal match: only roads a motorcycle may use, as for a computed route. */
    fun legalRequest(points: List<LatLon>): String = request(points, LEGAL_ATTRIBUTES, open = false)

    private fun request(points: List<LatLon>, attributes: List<String>, open: Boolean): String = buildJsonObject {
        put("encoded_polyline", Polyline.encode(points, 6))
        put("shape_match", "map_snap")
        put("costing", "motorcycle")
        if (open) {
            put("costing_options", buildJsonObject {
                put("motorcycle", buildJsonObject {
                    put("ignore_access", true)
                    put("ignore_restrictions", true)
                    put("ignore_oneways", true)
                    put("ignore_closures", true)
                })
            })
        }
        put("units", "kilometers")
        put("shape_format", "polyline6")
        put("filters", buildJsonObject {
            put("attributes", buildJsonArray { attributes.forEach { add(JsonPrimitive(it)) } })
            put("action", "include")
        })
    }.toString()

    /** One matched road of the open match, in metres along the track. */
    data class Edge(val fromM: Double, val toM: Double, val wayId: Long?, val roadClass: String?, val name: String?)

    /** The legal match of one piece: the ways it used and the part of the track it covers. */
    data class Legal(val fromM: Double, val toM: Double, val ways: Set<Long>)

    fun parseOpen(json: String, offsetM: Double): List<Edge> {
        val root = Json.parseToJsonElement(json).jsonObject
        val along = along(root.getValue("shape").jsonPrimitive.content)
        val edges = root["edges"] as? JsonArray ?: return emptyList()
        return edges.mapNotNull { e ->
            val edge = e.jsonObject
            val begin = edge["begin_shape_index"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val end = edge["end_shape_index"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            if (begin !in along.indices || end !in along.indices || end <= begin) return@mapNotNull null
            Edge(
                fromM = offsetM + along[begin],
                toM = offsetM + along[end],
                wayId = edge["way_id"]?.jsonPrimitive?.longOrNull,
                roadClass = edge["road_class"]?.jsonPrimitive?.contentOrNull,
                name = (edge["names"] as? JsonArray)?.firstOrNull()?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
            )
        }
    }

    fun parseLegal(json: String, offsetM: Double): Legal {
        val root = Json.parseToJsonElement(json).jsonObject
        val along = along(root.getValue("shape").jsonPrimitive.content)
        val ways = (root["edges"] as? JsonArray).orEmpty().mapNotNull { it.jsonObject["way_id"]?.jsonPrimitive?.longOrNull }.toSet()
        return Legal(offsetM, offsetM + (along.lastOrNull() ?: 0.0), ways)
    }

    private fun along(encoded: String): DoubleArray {
        val shape = Polyline.decode(encoded, 6)
        val along = DoubleArray(shape.size)
        for (i in 1 until shape.size) along[i] = along[i - 1] + distanceKm(shape[i - 1], shape[i]) * 1000
        return along
    }

    /**
     * The banned stretches from the two matches. [noMotorways]: the bike is under 150 cc.
     * A way is closed only where a legal match covered the same part of the track: where the legal
     * match failed altogether nothing is claimed.
     */
    fun classify(open: List<Edge>, legal: List<Legal>, noMotorways: Boolean): List<BanStretch> {
        val legalWays = legal.flatMapTo(HashSet()) { it.ways }
        val raw = open.mapNotNull { e ->
            val kind = when {
                noMotorways && e.roadClass == "motorway" -> BanKind.MOTORWAY
                e.wayId != null && e.wayId !in legalWays && legal.any { (e.fromM + e.toM) / 2 in it.fromM..it.toM } -> BanKind.NO_ACCESS
                else -> return@mapNotNull null
            }
            BanStretch(e.fromM, e.toM, kind, e.name)
        }
        return join(raw).filter { it.lengthM >= MIN_M }
    }

    /** Joins touching or overlapping stretches of the same kind (pieces overlap by one point). */
    fun join(stretches: List<BanStretch>): List<BanStretch> {
        val out = ArrayList<BanStretch>()
        for (s in stretches.filter { it.toM > it.fromM }.sortedBy { it.fromM }) {
            val i = out.indexOfLast { it.kind == s.kind }
            val last = out.getOrNull(i)
            if (last != null && s.fromM - last.toM <= JOIN_M) {
                out[i] = last.copy(toM = maxOf(last.toM, s.toM), name = last.name ?: s.name)
            } else {
                out += s
            }
        }
        return out.sortedBy { it.fromM }
    }

    /**
     * Checks [track] with [engine] (one `trace_attributes` request in, the response out). Null when
     * no piece of the track could be matched at all.
     */
    fun check(track: List<LatLon>, noMotorways: Boolean, engine: (String) -> String): TrackBans? {
        if (track.size < 2) return null
        val open = ValhallaSpeedLimits.walk(track, engine, ::openRequest) { json, offset -> parseOpen(json, offset) }
        if (open.isEmpty()) return null
        val legal = ValhallaSpeedLimits.walk(track, engine, ::legalRequest) { json, offset -> parseLegal(json, offset) }
        val trackM = track.zipWithNext { a, b -> distanceKm(a, b) * 1000 }.sum()
        val edges = open.flatten()
        val checkedM = covered(edges.map { it.fromM to it.toM })
        return TrackBans(classify(edges, legal, noMotorways), checkedM.coerceAtMost(trackM), trackM)
    }

    /** Length of the union of [ranges] (pieces overlap where they meet). */
    fun covered(ranges: List<Pair<Double, Double>>): Double {
        var total = 0.0
        var from = Double.NaN
        var to = Double.NaN
        for ((a, b) in ranges.filter { it.second > it.first }.sortedBy { it.first }) {
            if (from.isNaN() || a > to) {
                if (!from.isNaN()) total += to - from
                from = a
                to = b
            } else {
                to = maxOf(to, b)
            }
        }
        if (!from.isNaN()) total += to - from
        return total
    }
}
