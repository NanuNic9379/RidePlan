package io.github.nanunic9379.rideplan.ride

import io.github.nanunic9379.rideplan.route.RouteInfo
import io.github.nanunic9379.rideplan.route.RouteOptions
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double) {
    init {
        require(lat in -90.0..90.0 && lon in -180.0..180.0) { "Coordinates out of range: $lat, $lon" }
    }
}

/**
 * A stop with its name, if the source provides one.
 * [kind]: how much the exact point matters (#29).
 */
data class Stop(val name: String?, val point: LatLon, val kind: Kind = Kind.STOP) {
    enum class Kind {
        /** I really go there (bar, fuel, farm restaurant): the route gets there and stops. */
        STOP,

        /** Point dragged on the map: the route passes exactly through it, without stopping. */
        VIA,

        /** "Pass around there": a town. A nearby road is enough, without entering the center. */
        PASS,
    }

    companion object {
        private val street = Regex(
            """(?i)(^|[\s,])(via|viale|piazza|piazzale|corso|largo|vicolo|strada|contrada|c\.da|località|loc\.|ss|sp|sr|str\.|rue|avenue|street|road|calle|avenida|straße|strasse|platz|weg)\b""",
        )
        private val business = Regex(
            """(?i)\b(bar|caff[eè]|cafe|ristorante|restaurant|trattoria|osteria|pizzeria|agriturismo|masseria|hotel|albergo|b&b|rifugio|camping|distributore|benzina|eni|q8|esso|tamoil|ip|museo|castello|santuario|chiesa|belvedere|lago|passo|parcheggio)\b""",
        )
        private val houseNumber = Regex("""^\d+[a-zA-Z/]*$""")
        private val postCode = Regex("""^\d{4,5}\s+""")

        /** The name looks like a town ("Matera", "75100 Matera MT", "Stigliano, Provincia di Matera"). */
        fun looksLikeTown(name: String): Boolean {
            val parts = name.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            return parts.isNotEmpty() && parts.size <= 3 &&
                !parts.first().replace(postCode, "").any(Char::isDigit) && parts.none(houseNumber::matches) &&
                !street.containsMatchIn(name) && !business.containsMatchIn(name)
        }

        /**
         * Kind of freshly imported stops: start and finish are stops, unnamed points are
         * exact pass-throughs; with [towns] the towns become wide pass-throughs (Google link, #29).
         */
        fun guessKinds(stops: List<Stop>, towns: Boolean): List<Stop> = stops.mapIndexed { i, s ->
            val kind = when {
                i == 0 || i == stops.lastIndex -> Kind.STOP
                s.name == null -> Kind.VIA
                towns && looksLikeTown(s.name) -> Kind.PASS
                else -> Kind.STOP
            }
            s.copy(kind = kind)
        }
    }
}

/**
 * An imported ride (#27, #5).
 * [track]: the actual road (from GPX or KML, or calculated on the phone, #3). If it is empty,
 * the stops are joined by straight lines on the map.
 * [route]: present when RidePlan calculated the road.
 */
data class Ride(
    val name: String?,
    val track: List<LatLon>,
    val stops: List<Stop>,
    val source: Source,
    val route: RouteInfo? = null,
    /** Preferences of whoever shared the ride by link (#50): the route is rebuilt with these. */
    val sharedOptions: RouteOptions? = null,
) {
    enum class Source { GPX, KML, GOOGLE_LINK, RIDEPLAN_LINK }

    /** True when there is no real track, only the stops. */
    val straightLines: Boolean get() = track.size < 2

    /** The line to draw: the track, or the stops in a row. */
    val line: List<LatLon> get() = if (straightLines) stops.map { it.point } else track

    /** To be calculated: stops only, at least two. */
    val needsRoute: Boolean get() = straightLines && stops.size >= 2

    val lengthKm: Double get() = route?.km ?: line.zipWithNext { a, b -> distanceKm(a, b) }.sum()

    /** South-west and north-east of everything on the map. */
    fun bounds(): Pair<LatLon, LatLon> {
        val all = line + stops.map { it.point }
        return LatLon(all.minOf { it.lat }, all.minOf { it.lon }) to LatLon(all.maxOf { it.lat }, all.maxOf { it.lon })
    }
}

/** Distance as the crow flies (haversine formula). */
fun distanceKm(a: LatLon, b: LatLon): Double {
    val r = 6371.0088
    val dLat = (b.lat - a.lat).toRadians()
    val dLon = (b.lon - a.lon).toRadians()
    val h = sin(dLat / 2).let { it * it } +
        cos(a.lat.toRadians()) * cos(b.lat.toRadians()) * sin(dLon / 2).let { it * it }
    return 2 * r * asin(sqrt(h.coerceIn(0.0, 1.0)))
}

private fun Double.toRadians() = this * kotlin.math.PI / 180.0
