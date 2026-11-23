package io.github.nanunic9379.rideplan.ride

import io.github.nanunic9379.rideplan.route.Polyline
import kotlin.io.encoding.Base64
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Il giro dentro un link, senza server (#6): al bar il gruppo inquadra il QR e ha il giro.
 *
 * Tutto sta dopo il "#": il browser non lo manda mai al server della pagina.
 * Dentro ci sono le tappe (il percorso lo ricalcola ogni telefono, con le stesse curve)
 * oppure, per una traccia GPX, la traccia semplificata.
 */
object RideLink {
    const val PREFIX = "https://nanunic9379.github.io/RidePlan/r/#"
    private const val VERSION = "1"

    /** Oltre questi punti il QR diventa troppo fitto da inquadrare. */
    const val MAX_TRACK_POINTS = 150

    /** [curves]: il livello delle curve di chi condivide, per rifare lo stesso percorso. */
    data class Shared(val ride: Ride, val curves: String?)

    /** Il link web (dopo "#") o quello che la pagina web passa all'app (rideplan://r?d=...). */
    private val pattern = Regex("""(?:nanunic9379\.github\.io/RidePlan/r/?#|rideplan://r/?\?d=)([A-Za-z0-9_-]*)""")

    fun isLink(text: String): Boolean = pattern.containsMatchIn(text)

    fun encode(ride: Ride, curves: String? = null): String {
        // Un percorso calcolato si ricalcola dalle tappe: il link resta corto.
        val stopsOnly = ride.straightLines || ride.route != null
        val track = if (stopsOnly) emptyList() else simplify(ride.track)
        val fields = listOf(
            VERSION,
            ride.name.orEmpty(),
            curves.orEmpty(),
            Polyline.encode(ride.stops.map { it.point }, 5),
            ride.stops.joinToString(SEP) { it.name.orEmpty() },
            Polyline.encode(track, 5),
        ).map { it.replace('\n', ' ') }
        return PREFIX + Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(fields.joinToString("\n").encodeToByteArray())
    }

    fun decode(link: String): Shared {
        val data = pattern.find(link)?.groupValues?.get(1) ?: throw ImportException(ImportError.LINK_BROKEN)
        val text = runCatching {
            Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(data).decodeToString()
        }.getOrElse { throw ImportException(ImportError.LINK_BROKEN) }
        val f = text.split('\n')
        if (f.size < 6 || f[0] != VERSION) throw ImportException(ImportError.LINK_BROKEN)
        val points = runCatching { Polyline.decode(f[3], 5) }.getOrElse { throw ImportException(ImportError.LINK_BROKEN) }
        val names = f[4].split(SEP)
        val stops = points.mapIndexed { i, p -> Stop(names.getOrNull(i)?.ifEmpty { null }, p) }
        val track = runCatching { Polyline.decode(f[5], 5) }.getOrElse { throw ImportException(ImportError.LINK_BROKEN) }
        if (stops.size < 2 && track.size < 2) throw ImportException(ImportError.NO_STOPS)
        return Shared(
            Ride(f[1].ifEmpty { null }, track, stops, Ride.Source.RIDEPLAN_LINK),
            f[2].ifEmpty { null },
        )
    }

    private const val SEP = "\u001f"

    /** Douglas-Peucker con tolleranza crescente finché la traccia sta in [MAX_TRACK_POINTS]. */
    internal fun simplify(track: List<LatLon>): List<LatLon> {
        if (track.size <= 2) return track
        var tolerance = 0.02 // km
        var out = track
        while (true) {
            out = douglasPeucker(track, tolerance)
            if (out.size <= MAX_TRACK_POINTS) return out
            tolerance *= 1.5
        }
    }

    private fun douglasPeucker(points: List<LatLon>, tolKm: Double): List<LatLon> {
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.lastIndex] = true
        val stack = ArrayDeque(listOf(0 to points.lastIndex))
        while (stack.isNotEmpty()) {
            val (a, b) = stack.removeLast()
            var worst = -1
            var worstD = tolKm
            for (i in a + 1 until b) {
                val d = offsetKm(points[i], points[a], points[b])
                if (d > worstD) { worst = i; worstD = d }
            }
            if (worst >= 0) {
                keep[worst] = true
                stack += a to worst
                stack += worst to b
            }
        }
        return points.filterIndexed { i, _ -> keep[i] }
    }

    /** Distanza di [p] dal segmento [a]-[b], in piano: per pochi km va benissimo. */
    private fun offsetKm(p: LatLon, a: LatLon, b: LatLon): Double {
        val k = 111.32
        val c = cos(a.lat * kotlin.math.PI / 180)
        val bx = (b.lon - a.lon) * k * c
        val by = (b.lat - a.lat) * k
        val px = (p.lon - a.lon) * k * c
        val py = (p.lat - a.lat) * k
        val len2 = bx * bx + by * by
        val t = if (len2 == 0.0) 0.0 else ((px * bx + py * by) / len2).coerceIn(0.0, 1.0)
        val dx = px - t * bx
        val dy = py - t * by
        return sqrt(dx * dx + dy * dy)
    }
}
