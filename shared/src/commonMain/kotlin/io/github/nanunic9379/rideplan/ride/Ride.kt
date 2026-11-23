package io.github.nanunic9379.rideplan.ride

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double) {
    init {
        require(lat in -90.0..90.0 && lon in -180.0..180.0) { "Coordinate fuori scala: $lat, $lon" }
    }
}

/** Una tappa con il suo nome, se la sorgente lo dà. */
data class Stop(val name: String?, val point: LatLon)

/**
 * Un giro importato (#27, #5).
 * [track]: la strada vera (da GPX o KML). Se è vuota, sulla mappa le tappe sono unite
 * da linee dritte finché non arriva il calcolo del percorso (#3).
 */
data class Ride(
    val name: String?,
    val track: List<LatLon>,
    val stops: List<Stop>,
    val source: Source,
) {
    enum class Source { GPX, KML, GOOGLE_LINK }

    /** True quando non c'è una traccia vera ma solo le tappe. */
    val straightLines: Boolean get() = track.size < 2

    /** La linea da disegnare: la traccia, oppure le tappe in fila. */
    val line: List<LatLon> get() = if (straightLines) stops.map { it.point } else track

    val lengthKm: Double get() = line.zipWithNext { a, b -> distanceKm(a, b) }.sum()

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
