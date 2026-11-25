package io.github.nanunic9379.rideplan.route

import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.Ride
import io.github.nanunic9379.rideplan.ride.Stop
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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

/**
 * A region of road data (Release "routing-data", #3).
 * [bbox]: lon min, lat min, lon max, lat max. [file]: Valhalla tar compressed with gzip.
 */
data class RoutingRegion(
    val id: String,
    val bbox: List<Double>,
    val file: String,
    val bytes: Long,
    /** Space used on the phone, uncompressed. */
    val tarBytes: Long,
    val sha256: String,
    val osmDate: String,
    val valhalla: String,
) {
    fun contains(p: LatLon): Boolean = p.lon >= bbox[0] && p.lat >= bbox[1] && p.lon <= bbox[2] && p.lat <= bbox[3]

    private val area: Double get() = (bbox[2] - bbox[0]) * (bbox[3] - bbox[1])

    companion object {
        /** The regions.json manifest published by routing-data.yml (before #45). */
        fun parseManifest(json: String): List<RoutingRegion> {
            val root = Json.parseToJsonElement(json).jsonObject
            require(root["format"]?.jsonPrimitive?.int == 1) { "Unsupported data format" }
            return root.getValue("regions").jsonArray.map { it.jsonObject.toRegion() }
        }

        /** The smallest region containing all points; null if none is enough. */
        fun bestFor(regions: List<RoutingRegion>, points: List<LatLon>): RoutingRegion? =
            regions.filter { r -> points.all(r::contains) }.minByOrNull { it.area }

        fun fromJson(json: String): RoutingRegion = Json.parseToJsonElement(json).jsonObject.toRegion()

        private fun JsonObject.toRegion() = RoutingRegion(
            id = str("id"),
            bbox = getValue("bbox").jsonArray.map { it.jsonPrimitive.double }.also { require(it.size == 4) },
            file = str("file"),
            bytes = getValue("bytes").jsonPrimitive.long,
            tarBytes = get("tarBytes")?.jsonPrimitive?.long ?: (getValue("bytes").jsonPrimitive.long * 3),
            sha256 = str("sha256"),
            osmDate = str("osmDate"),
            valhalla = str("valhalla"),
        )

        private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content
    }

    fun toJson(): String = buildJsonObject {
        put("id", id)
        put("bbox", buildJsonArray { bbox.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
        put("file", file)
        put("bytes", bytes)
        put("tarBytes", tarBytes)
        put("sha256", sha256)
        put("osmDate", osmDate)
        put("valhalla", valhalla)
    }.toString()
}

/**
 * The route character chosen by the rider (#4).
 * [unpaved]: avoid (default), allow or prefer unpaved roads (#9), see [Unpaved].
 * [mode]: the riding mode the switches come from (#30); null = none (preferences from before
 * #30 that match no mode, or plain defaults). See [custom].
 * New fields need a default: these options are saved on the phone and read back with
 * [RouteSettings.decode].
 */
@Serializable
data class RouteOptions(
    val curves: Curves = Curves.NORMAL,
    val avoidHighways: Boolean = false,
    val avoidTolls: Boolean = false,
    val avoidFerries: Boolean = false,
    /** Go around towns where possible (#30): best effort, see [ValhallaRoute.request]. */
    val avoidTowns: Boolean = false,
    val mode: RidingMode? = null,
    /**
     * Motorways are forbidden, not just avoided (#8): set by the bike (under 150 cc, see
     * `forBike`), never by a switch. The engine may not use a motorway even when it is the only
     * road; stops are never placed on one.
     */
    val noMotorways: Boolean = false,
    /** Unpaved roads (#9): avoided unless the rider allows or prefers them. */
    val unpaved: Unpaved = Unpaved.DEFAULT,
)

/**
 * How much to seek curves: Valhalla's `use_curvature` (above 0.5 it prefers winding roads).
 * On the San Basilio → Padula test ride (#10): 0.75 makes it about 14% longer, 1 about 24%.
 * What it really does in the engine, and its limits, is in docs/ROUTING.md (#4).
 */
@Serializable
enum class Curves(val curvature: Double?) {
    NORMAL(null),
    SOME(0.6),
    MORE(0.75),
    MAX(1.0),
}

/** How the route was computed: needed to repeat the same computation (same data, same engine). */
data class RouteInfo(
    val km: Double,
    val seconds: Double,
    val regionId: String,
    val osmDate: String,
    val engine: String,
    val options: RouteOptions = RouteOptions(),
    /** The route without curve preference, to tell how much longer the ride gets. */
    val baseline: Baseline? = null,
    /**
     * The curves level the rider asked for, when the route was computed one level lower because
     * it went over unpaved road (#9, [CurvesFallback]); [options] then hold the level used.
     */
    val loweredFrom: Curves? = null,
) {
    data class Baseline(val km: Double, val seconds: Double)

    /** Extra distance due to curves, as a share (0.18 = +18%). Null if not comparable. */
    val extraShare: Double? get() = baseline?.takeIf { it.km > 0 }?.let { km / it.km - 1 }

    companion object {
        /** Above this threshold the warning stands out: the ride really changes. */
        const val LONG_DETOUR = 0.20
    }
}

/** Valhalla request and response, the same on every platform. */
object ValhallaRoute {
    /** How far from the town centre the road may pass. The engine must accept it (max_radius). */
    const val PASS_RADIUS_M = 2500

    /** Seconds added for entering a service road when towns are avoided (#30). */
    const val TOWN_SERVICE_PENALTY = 60

    /**
     * Seconds added for entering a road open only to reach a destination there (access =
     * destination / private / delivery, residents-only zones): the engine default is 600. The
     * route still ends on such a road when a stop is on it, but never crosses one to save time
     * or to find bends (#4).
     */
    const val DESTINATION_ONLY_PENALTY = 1800

    /**
     * Motorcycle profile with default values: the closest to the ride drawn on Google.
     * Stops are "break"; dragged points and towns are "through", where no U-turn is made.
     * For a town any road within [PASS_RADIUS_M] is enough: so the route can go around it
     * instead of entering the centre (#29). Start and finish are always stops.
     *
     * [language]: Valhalla locale for the turn instructions (#5), see [language]; null = no
     * instructions (e.g. the comparison route without curves).
     * [startHeading]: direction of travel at the first stop, in degrees from north: when
     * re-routing on the move it keeps the engine from starting with a U-turn.
     */
    fun request(
        stops: List<Stop>,
        options: RouteOptions = RouteOptions(),
        language: String? = null,
        startHeading: Double? = null,
        /** Areas the route must not cross (#13): Valhalla `exclude_polygons`, closed rings. */
        avoid: List<List<LatLon>> = emptyList(),
    ): String = buildJsonObject {
        put("locations", buildJsonArray {
            stops.forEachIndexed { i, stop ->
                val end = i == 0 || i == stops.lastIndex
                add(buildJsonObject {
                    put("lat", stop.point.lat)
                    put("lon", stop.point.lon)
                    val kind = if (end) Stop.Kind.STOP else stop.kind
                    put("type", if (kind == Stop.Kind.STOP) "break" else "through")
                    if (kind == Stop.Kind.PASS) put("radius", PASS_RADIUS_M)
                    // A bike banned from motorways must not be placed on one (#8): without this a
                    // stop next to a motorway could snap onto it.
                    if (options.noMotorways) put("search_filter", buildJsonObject { put("max_road_class", "trunk") })
                    if (i == 0 && startHeading != null) {
                        put("heading", ((startHeading % 360 + 360) % 360).toInt())
                        put("heading_tolerance", 60)
                    }
                })
            }
        })
        put("costing", "motorcycle")
        put("costing_options", buildJsonObject {
            put("motorcycle", buildJsonObject {
                // Unpaved roads (#9). Avoid (the default) keeps the safety settings of #4/#49:
                // bad surfaces (dirt, gravel) cost the most the engine allows, tracks (farm and
                // forest roads) 4 times as much plus 5 minutes to enter, and a ban on unpaved
                // roads that Valhalla 3.9.2 reads but its motorcycle profile does not apply yet
                // (sent so a newer engine does). Allow is neutral, Prefer a small bonus.
                val unpaved = options.unpaved
                put("use_trails", unpaved.useTrails)
                put("use_tracks", unpaved.useTracks)
                if (unpaved.excludeUnpaved) put("exclude_unpaved", true)
                put("destination_only_penalty", DESTINATION_ONLY_PENALTY)
                options.curves.curvature?.let { put("use_curvature", it) }
                if (options.avoidHighways || options.noMotorways) put("use_highways", 0.0)
                else options.mode?.highwayBias?.let { put("use_highways", it) }
                // The legal ban (#8): a hard exclusion, not a cost. It needs
                // service_limits.allow_hard_exclusions in the engine config (RouteService).
                if (options.noMotorways) put("exclude_highways", true)
                if (options.avoidTolls) put("use_tolls", 0.0)
                if (options.avoidFerries) put("use_ferry", 0.0)
                if (options.avoidTowns) {
                    // Valhalla has no "town" penalty: residential living streets and service
                    // roads are what a route crosses inside a town, so they are made expensive.
                    put("use_living_streets", 0.0)
                    put("service_penalty", TOWN_SERVICE_PENALTY)
                }
            })
        })
        if (avoid.isNotEmpty()) {
            put("exclude_polygons", buildJsonArray {
                avoid.forEach { ring ->
                    add(buildJsonArray { ring.forEach { p -> add(buildJsonArray { add(JsonPrimitive(p.lon)); add(JsonPrimitive(p.lat)) }) } })
                }
            })
        }
        put("units", "kilometers")
        put("shape_format", "polyline6")
        if (language == null) {
            put("directions_type", "none")
        } else {
            put("directions_type", "instructions")
            put("directions_options", buildJsonObject {
                put("language", language)
                put("units", "kilometers")
            })
        }
    }.toString()

    /** Locales with turn instructions, one per app language. */
    private val LOCALES = mapOf("en" to "en-US", "it" to "it-IT", "de" to "de-DE", "es" to "es-ES", "fr" to "fr-FR")

    /** Valhalla locale for an app language tag ("it", "it-IT", "de_CH"); English if not translated. */
    fun language(tag: String): String = LOCALES[tag.lowercase().substringBefore('-').substringBefore('_')] ?: "en-US"

    data class Result(
        val shape: List<LatLon>,
        val km: Double,
        val seconds: Double,
        /** Turn instructions (#5); empty if they were not requested. */
        val maneuvers: List<Maneuver> = emptyList(),
        /** Speed limits along [shape] (#31); empty if not computed or not in the data. */
        val speedLimits: SpeedLimits = SpeedLimits.NONE,
        /** Unpaved stretches along [shape] (#9); empty if not computed or none. */
        val surfaces: RoadSurfaces = RoadSurfaces.NONE,
    )

    /**
     * Joins the legs into one line: each leg repeats the last point of the previous one, and
     * that point appears only once. Maneuver indices are moved onto the joined line.
     */
    fun parse(json: String): Result {
        val trip = Json.parseToJsonElement(json).jsonObject.getValue("trip").jsonObject
        val summary = trip.getValue("summary").jsonObject
        val shape = ArrayList<LatLon>()
        val maneuvers = ArrayList<Maneuver>()
        for (leg in trip.getValue("legs") as JsonArray) {
            val points = Polyline.decode(leg.jsonObject.getValue("shape").jsonPrimitive.content, 6)
            val index = IntArray(points.size)
            points.forEachIndexed { i, p ->
                if (shape.isEmpty() || shape.last() != p) shape += p
                index[i] = shape.lastIndex
            }
            (leg.jsonObject["maneuvers"] as? JsonArray)?.forEach { m ->
                maneuvers += Maneuver.fromJson(m.jsonObject) { i -> if (index.isEmpty()) 0 else index[i.coerceIn(0, index.lastIndex)] }
            }
        }
        return Result(
            shape = shape,
            km = summary.getValue("length").jsonPrimitive.double,
            seconds = summary.getValue("time").jsonPrimitive.double,
            maneuvers = maneuvers,
        )
    }
}

/** Google polyline with precision 5 or 6 (Valhalla uses 6). */
object Polyline {
    fun encode(points: List<LatLon>, precision: Int): String {
        var factor = 1.0
        repeat(precision) { factor *= 10 }
        val out = StringBuilder()
        var lastLat = 0L
        var lastLon = 0L
        for (p in points) {
            val lat = kotlin.math.round(p.lat * factor).toLong()
            val lon = kotlin.math.round(p.lon * factor).toLong()
            value(out, lat - lastLat)
            value(out, lon - lastLon)
            lastLat = lat
            lastLon = lon
        }
        return out.toString()
    }

    private fun value(out: StringBuilder, delta: Long) {
        var v = if (delta < 0) (delta shl 1).inv() else delta shl 1
        while (v >= 0x20) {
            out.append(((0x20 or (v and 0x1F).toInt()) + 63).toChar())
            v = v shr 5
        }
        out.append((v.toInt() + 63).toChar())
    }

    fun decode(s: String, precision: Int): List<LatLon> {
        var factor = 1.0
        repeat(precision) { factor *= 10 }
        val out = ArrayList<LatLon>()
        var i = 0
        var lat = 0L
        var lon = 0L
        while (i < s.length) {
            for (k in 0..1) {
                var shift = 0
                var result = 0L
                while (true) {
                    val b = s[i++].code - 63
                    result = result or ((b and 0x1F).toLong() shl shift)
                    shift += 5
                    if (b < 0x20) break
                }
                val d = if (result and 1L != 0L) (result shr 1).inv() else result shr 1
                if (k == 0) lat += d else lon += d
            }
            out += LatLon(lat / factor, lon / factor)
        }
        return out
    }
}

/** Which data the route is computed with: a whole region (before #45) or the grid cells around the ride. */
sealed interface RoutingData {
    /** To repeat the same computation: the region or the build of the grid cells. */
    val dataId: String
    val osmDate: String
    val valhalla: String

    data class Region(val region: RoutingRegion) : RoutingData {
        override val dataId get() = region.id
        override val osmDate get() = region.osmDate
        override val valhalla get() = region.valhalla
    }

    /** [area]: the area the cells belong to (#14), each with its own tile directory. */
    data class Cells(val cells: RoutingCells, val area: String = RoutingAreas.ITALY) : RoutingData {
        // Italy keeps the id it had before #14: rides routed then still match.
        override val dataId get() = if (area == RoutingAreas.ITALY) "cells-${cells.build}" else "cells-$area-${cells.build}"
        override val osmDate get() = cells.osmDate
        override val valhalla get() = cells.valhalla
    }

    /**
     * A ride across areas (#14): each leg routed with the cells of its own area, see [CrossArea].
     * The id names every area's build once, in the order the ride meets them.
     */
    data class Across(val parts: List<Part>) : RoutingData {
        init {
            require(parts.isNotEmpty()) { "No legs" }
        }

        data class Part(val data: Cells, val leg: AreaLeg)

        override val dataId get() = parts.map { it.data.dataId }.distinct().joinToString("+")

        /** The oldest data used: what the ride's roads can be trusted up to. */
        override val osmDate get() = parts.minOf { it.data.osmDate }
        override val valhalla get() = parts.first().data.valhalla
    }
}

/** The ride with the computed road instead of straight lines. */
fun Ride.routed(
    result: ValhallaRoute.Result,
    data: RoutingData,
    options: RouteOptions = RouteOptions(),
    baseline: ValhallaRoute.Result? = null,
    loweredFrom: Curves? = null,
): Ride = copy(
    track = result.shape,
    maneuvers = result.maneuvers,
    speedLimits = result.speedLimits,
    surfaces = result.surfaces,
    route = RouteInfo(
        result.km, result.seconds, data.dataId, data.osmDate, "valhalla ${data.valhalla}", options,
        baseline?.let { RouteInfo.Baseline(it.km, it.seconds) },
        loweredFrom,
    ),
)

fun Ride.routed(
    result: ValhallaRoute.Result,
    region: RoutingRegion,
    options: RouteOptions = RouteOptions(),
    baseline: ValhallaRoute.Result? = null,
): Ride = routed(result, RoutingData.Region(region), options, baseline)

/** The ride as it was before routing, to route it again with other preferences. */
fun Ride.unrouted(): Ride = if (route != null) copy(track = emptyList(), route = null, maneuvers = emptyList(), speedLimits = SpeedLimits.NONE, surfaces = RoadSurfaces.NONE) else this

/** Re-routable: a ride made only of stops, already routed or not yet (not a GPX track). */

val Ride.routable: Boolean get() = stops.size >= 2 && (route != null || straightLines)
