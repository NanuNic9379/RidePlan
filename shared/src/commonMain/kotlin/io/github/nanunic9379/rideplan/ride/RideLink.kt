package io.github.nanunic9379.rideplan.ride

import io.github.nanunic9379.rideplan.route.Curves
import io.github.nanunic9379.rideplan.route.Polyline
import io.github.nanunic9379.rideplan.route.RouteOptions
import io.github.nanunic9379.rideplan.route.withInferredMode
import kotlin.io.encoding.Base64
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * The ride inside a link, without a server (#6): at the bar the group scans the QR and has the ride.
 *
 * Everything is after the "#": the browser never sends it to the page's server.
 * Inside are the stops (every phone recalculates the route, with the same curves)
 * or, for a GPX track, the simplified track.
 */
object RideLink {
    const val PREFIX = "https://nanunic9379.github.io/RidePlan/r/#"
    private const val VERSION = "1"

    /** Beyond this many points the QR becomes too dense to scan. */
    const val MAX_TRACK_POINTS = 150

    /**
     * [options]: the sharer's curves, motorway and toll choices (#50), to rebuild the same
     * route; null for links that do not carry them.
     */
    data class Shared(val ride: Ride, val options: RouteOptions?)

    /** The web link (after "#") or the one the web page passes to the app (rideplan://r?d=...). */
    private val pattern = Regex("""(?:nanunic9379\.github\.io/RidePlan/r/?#|rideplan://r/?\?d=)([A-Za-z0-9_-]*)""")

    fun isLink(text: String): Boolean = pattern.containsMatchIn(text)

    fun encode(ride: Ride, options: RouteOptions? = null): String {
        // A calculated route is recalculated from the stops: the link stays short.
        val stopsOnly = ride.straightLines || ride.route != null
        val track = if (stopsOnly) emptyList() else simplify(ride.track)
        val fields = listOf(
            VERSION,
            ride.name.orEmpty(),
            options?.let(::optionsCode).orEmpty(),
            Polyline.encode(ride.stops.map { it.point }, 5),
            ride.stops.joinToString(SEP) { it.name.orEmpty() },
            Polyline.encode(track, 5),
            // Fields added later (#50): older versions and the web page only read the first six.
            ride.stops.joinToString("") { it.kind.name.take(1) },
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
        val kinds = f.getOrNull(6).orEmpty()
        val stops = points.mapIndexed { i, p -> Stop(names.getOrNull(i)?.ifEmpty { null }, p, kindOf(kinds.getOrNull(i))) }
        val track = runCatching { Polyline.decode(f[5], 5) }.getOrElse { throw ImportException(ImportError.LINK_BROKEN) }
        if (stops.size < 2 && track.size < 2) throw ImportException(ImportError.NO_STOPS)
        val options = parseOptions(f[2])
        return Shared(Ride(f[1].ifEmpty { null }, track, stops, Ride.Source.RIDEPLAN_LINK, sharedOptions = options), options)
    }

    private const val SEP = "\u001f"

    /**
     * "MAX,h,t,f,n": curves, then h = no motorways, t = no tolls, f = no ferries, n = around
     * towns (#30; older apps ignore the letter). The riding mode is not sent: the receiver finds
     * it again from the switches.
     */
    internal fun optionsCode(o: RouteOptions): String =
        (
            listOf(o.curves.name) + listOfNotNull(
                "h".takeIf { o.avoidHighways },
                "t".takeIf { o.avoidTolls },
                "f".takeIf { o.avoidFerries },
                "n".takeIf { o.avoidTowns },
            )
        ).joinToString(",")

    internal fun parseOptions(code: String): RouteOptions? {
        val parts = code.split(',')
        val curves = Curves.entries.firstOrNull { it.name == parts[0] } ?: return null
        return RouteOptions(
            curves,
            avoidHighways = "h" in parts,
            avoidTolls = "t" in parts,
            avoidFerries = "f" in parts,
            avoidTowns = "n" in parts,
        ).withInferredMode()
    }

    /** One letter per stop: S stop, V dragged via point, P pass-by town. */
    private fun kindOf(c: Char?): Stop.Kind = Stop.Kind.entries.firstOrNull { it.name.first() == c } ?: Stop.Kind.STOP

    /** Douglas-Peucker with growing tolerance until the track fits in [MAX_TRACK_POINTS]. */
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

    /** Distance of [p] from segment [a]-[b], on a plane: fine for a few km. */
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
