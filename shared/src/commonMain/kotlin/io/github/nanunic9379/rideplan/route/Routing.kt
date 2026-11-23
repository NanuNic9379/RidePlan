package io.github.nanunic9379.rideplan.route

import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.Ride
import io.github.nanunic9379.rideplan.ride.Stop
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
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
 * Una zona di dati stradali (Release "routing-data", #3).
 * [bbox]: lon min, lat min, lon max, lat max. [file]: tar di Valhalla compresso con gzip.
 */
data class RoutingRegion(
    val id: String,
    val bbox: List<Double>,
    val file: String,
    val bytes: Long,
    /** Spazio occupato sul telefono, decompresso. */
    val tarBytes: Long,
    val sha256: String,
    val osmDate: String,
    val valhalla: String,
) {
    fun contains(p: LatLon): Boolean = p.lon >= bbox[0] && p.lat >= bbox[1] && p.lon <= bbox[2] && p.lat <= bbox[3]

    private val area: Double get() = (bbox[2] - bbox[0]) * (bbox[3] - bbox[1])

    companion object {
        /** Il manifest regions.json pubblicato da routing-data.yml. */
        fun parseManifest(json: String): List<RoutingRegion> {
            val root = Json.parseToJsonElement(json).jsonObject
            require(root["format"]?.jsonPrimitive?.int == 1) { "Formato dei dati non supportato" }
            return root.getValue("regions").jsonArray.map { it.jsonObject.toRegion() }
        }

        /** La zona più piccola che contiene tutti i punti; null se nessuna basta. */
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
 * Il carattere del percorso scelto da chi guida (#4).
 * Lo sterrato si evita sempre: non è un interruttore.
 */
data class RouteOptions(
    val curves: Curves = Curves.NORMAL,
    val avoidHighways: Boolean = false,
    val avoidTolls: Boolean = false,
)

/**
 * Quanto cercare le curve: `use_curvature` di Valhalla (sopra 0,5 preferisce le strade tortuose).
 * Sul giro di prova San Basilio → Padula (#10): 0,75 allunga di circa il 14%, 1 del 24%.
 */
enum class Curves(val curvature: Double?) {
    NORMAL(null),
    SOME(0.6),
    MORE(0.75),
    MAX(1.0),
}

/** Come è stato calcolato il percorso: serve a ripetere lo stesso calcolo (stessi dati, stesso motore). */
data class RouteInfo(
    val km: Double,
    val seconds: Double,
    val regionId: String,
    val osmDate: String,
    val engine: String,
    val options: RouteOptions = RouteOptions(),
    /** Il percorso senza preferenza per le curve, per dire quanto si allunga il giro. */
    val baseline: Baseline? = null,
) {
    data class Baseline(val km: Double, val seconds: Double)

    /** Strada in più dovuta alle curve, in percentuale (0,18 = +18%). Null se non confrontabile. */
    val extraShare: Double? get() = baseline?.takeIf { it.km > 0 }?.let { km / it.km - 1 }

    companion object {
        /** Sopra questa soglia l'avviso si vede bene: il giro cambia davvero. */
        const val LONG_DETOUR = 0.20
    }
}

/** Richiesta e risposta di Valhalla, uguali su ogni piattaforma. */
object ValhallaRoute {
    /** Quanto lontano dal centro del paese può passare la strada. Il motore deve accettarlo (max_radius). */
    const val PASS_RADIUS_M = 2500

    /**
     * Profilo moto con i valori predefiniti: il più vicino al giro disegnato su Google.
     * Le soste sono "break"; punti trascinati e paesi sono "through", dove non si fa inversione.
     * Per un paese basta una strada entro [PASS_RADIUS_M]: così il percorso può girargli
     * intorno invece di entrare in centro (#29). Partenza e arrivo sono sempre soste.
     */
    fun request(stops: List<Stop>, options: RouteOptions = RouteOptions()): String = buildJsonObject {
        put("locations", buildJsonArray {
            stops.forEachIndexed { i, stop ->
                val end = i == 0 || i == stops.lastIndex
                add(buildJsonObject {
                    put("lat", stop.point.lat)
                    put("lon", stop.point.lon)
                    val kind = if (end) Stop.Kind.STOP else stop.kind
                    put("type", if (kind == Stop.Kind.STOP) "break" else "through")
                    if (kind == Stop.Kind.PASS) put("radius", PASS_RADIUS_M)
                })
            }
        })
        put("costing", "motorcycle")
        put("costing_options", buildJsonObject {
            put("motorcycle", buildJsonObject {
                put("use_trails", 0.0)
                options.curves.curvature?.let { put("use_curvature", it) }
                if (options.avoidHighways) put("use_highways", 0.0)
                if (options.avoidTolls) put("use_tolls", 0.0)
            })
        })
        put("units", "kilometers")
        put("shape_format", "polyline6")
        put("directions_type", "none")
    }.toString()

    data class Result(val shape: List<LatLon>, val km: Double, val seconds: Double)

    fun parse(json: String): Result {
        val trip = Json.parseToJsonElement(json).jsonObject.getValue("trip").jsonObject
        val summary = trip.getValue("summary").jsonObject
        val shape = (trip.getValue("legs") as JsonArray).flatMap { leg ->
            val points = Polyline.decode(leg.jsonObject.getValue("shape").jsonPrimitive.content, 6)
            points
        }.let(::dropRepeated)
        return Result(
            shape = shape,
            km = summary.getValue("length").jsonPrimitive.double,
            seconds = summary.getValue("time").jsonPrimitive.double,
        )
    }

    /** Ogni tratto ripete l'ultimo punto del precedente. */
    private fun dropRepeated(points: List<LatLon>): List<LatLon> =
        points.filterIndexed { i, p -> i == 0 || p != points[i - 1] }
}

/** Polyline di Google con precisione 5 o 6 (Valhalla usa 6). */
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

/** Il giro con la strada calcolata al posto delle linee dritte. */
fun Ride.routed(
    result: ValhallaRoute.Result,
    region: RoutingRegion,
    options: RouteOptions = RouteOptions(),
    baseline: ValhallaRoute.Result? = null,
): Ride = copy(
    track = result.shape,
    route = RouteInfo(
        result.km, result.seconds, region.id, region.osmDate, "valhalla ${region.valhalla}", options,
        baseline?.let { RouteInfo.Baseline(it.km, it.seconds) },
    ),
)

/** Il giro com'era prima del calcolo, per ricalcolarlo con altre preferenze. */
fun Ride.unrouted(): Ride = if (route != null) copy(track = emptyList(), route = null) else this

/** Ricalcolabile: un giro di sole tappe, già calcolato o ancora da calcolare (non una traccia GPX). */
val Ride.routable: Boolean get() = stops.size >= 2 && (route != null || straightLines)
