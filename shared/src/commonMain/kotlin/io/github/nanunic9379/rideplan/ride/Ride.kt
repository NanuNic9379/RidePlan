package io.github.nanunic9379.rideplan.ride

import io.github.nanunic9379.rideplan.route.RouteInfo
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double) {
    init {
        require(lat in -90.0..90.0 && lon in -180.0..180.0) { "Coordinate fuori scala: $lat, $lon" }
    }
}

/**
 * Una tappa con il suo nome, se la sorgente lo dà.
 * [kind]: quanto conta il punto esatto (#29).
 */
data class Stop(val name: String?, val point: LatLon, val kind: Kind = Kind.STOP) {
    enum class Kind {
        /** Ci vado davvero (bar, benzina, agriturismo): il percorso arriva lì e si ferma. */
        STOP,

        /** Punto trascinato sulla mappa: il percorso ci passa esattamente, senza fermarsi. */
        VIA,

        /** "Passa da quelle parti": un paese. Basta una strada vicina, senza entrare in centro. */
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

        /** Il nome sembra un comune ("Matera", "75100 Matera MT", "Stigliano, Provincia di Matera"). */
        fun looksLikeTown(name: String): Boolean {
            val parts = name.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            return parts.isNotEmpty() && parts.size <= 3 &&
                !parts.first().replace(postCode, "").any(Char::isDigit) && parts.none(houseNumber::matches) &&
                !street.containsMatchIn(name) && !business.containsMatchIn(name)
        }

        /**
         * Tipo delle tappe appena importate: partenza e arrivo sono soste, i punti senza nome
         * passaggi esatti; con [towns] i paesi diventano passaggi larghi (link di Google, #29).
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
 * Un giro importato (#27, #5).
 * [track]: la strada vera (da GPX o KML, oppure calcolata sul telefono, #3). Se è vuota,
 * sulla mappa le tappe sono unite da linee dritte.
 * [route]: presente quando la strada l'ha calcolata RidePlan.
 */
data class Ride(
    val name: String?,
    val track: List<LatLon>,
    val stops: List<Stop>,
    val source: Source,
    val route: RouteInfo? = null,
) {
    enum class Source { GPX, KML, GOOGLE_LINK, RIDEPLAN_LINK }

    /** True quando non c'è una traccia vera ma solo le tappe. */
    val straightLines: Boolean get() = track.size < 2

    /** La linea da disegnare: la traccia, oppure le tappe in fila. */
    val line: List<LatLon> get() = if (straightLines) stops.map { it.point } else track

    /** Da calcolare: solo tappe, almeno due. */
    val needsRoute: Boolean get() = straightLines && stops.size >= 2

    val lengthKm: Double get() = route?.km ?: line.zipWithNext { a, b -> distanceKm(a, b) }.sum()

    /** Sud-ovest e nord-est di tutto quello che è sulla mappa. */
    fun bounds(): Pair<LatLon, LatLon> {
        val all = line + stops.map { it.point }
        return LatLon(all.minOf { it.lat }, all.minOf { it.lon }) to LatLon(all.maxOf { it.lat }, all.maxOf { it.lon })
    }
}

/** Distanza in linea d'aria (formula dell'emisenoverso). */
fun distanceKm(a: LatLon, b: LatLon): Double {
    val r = 6371.0088
    val dLat = (b.lat - a.lat).toRadians()
    val dLon = (b.lon - a.lon).toRadians()
    val h = sin(dLat / 2).let { it * it } +
        cos(a.lat.toRadians()) * cos(b.lat.toRadians()) * sin(dLon / 2).let { it * it }
    return 2 * r * asin(sqrt(h.coerceIn(0.0, 1.0)))
}

private fun Double.toRadians() = this * kotlin.math.PI / 180.0
