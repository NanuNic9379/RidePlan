package io.github.nanunic9379.rideplan.ride

import io.github.nanunic9379.rideplan.profile.SavedRide
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * A place that must never leave the phone (#6): home, the garage, the office.
 *
 * The circle is stored with its center already moved away from the real place (see [around]):
 * whoever collects many shared rides and fits a circle on their cut ends finds [lat]/[lon],
 * not the house. The real place is not stored anywhere.
 */
@Serializable
data class PrivateZone(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val radiusM: Int = DEFAULT_RADIUS_M,
) {
    val center: LatLon get() = LatLon(lat, lon)

    fun contains(p: LatLon): Boolean = distanceKm(center, p) * 1000 <= radiusM

    companion object {
        const val DEFAULT_RADIUS_M = 500
        val RADII_M = listOf(500, 750, 1000)

        /** How far the center may move from the real place, as a share of the radius. */
        const val MAX_OFFSET = 0.5

        /**
         * A zone that hides [place]: the center moves by a random distance up to
         * [MAX_OFFSET] × radius in a random direction, so [place] stays at least half a radius
         * inside the circle.
         */
        fun around(place: LatLon, name: String, radiusM: Int, id: String, random: Random = Random): PrivateZone {
            val distanceM = random.nextDouble() * MAX_OFFSET * radiusM
            val center = move(place, distanceM, random.nextDouble() * 2 * PI)
            return PrivateZone(id, name.trim(), center.lat, center.lon, radiusM)
        }

        /** [p] moved by [meters] towards [bearing] (radians, clockwise from north). */
        internal fun move(p: LatLon, meters: Double, bearing: Double): LatLon {
            val r = 6_371_008.8
            val d = meters / r
            val lat1 = p.lat * PI / 180
            val lon1 = p.lon * PI / 180
            val lat2 = asin(sin(lat1) * cos(d) + cos(lat1) * sin(d) * cos(bearing))
            val lon2 = lon1 + atan2(sin(bearing) * sin(d) * cos(lat1), cos(d) - sin(lat1) * sin(lat2))
            val lon = ((lon2 * 180 / PI) + 540) % 360 - 180
            return LatLon((lat2 * 180 / PI).coerceIn(-90.0, 90.0), lon)
        }
    }
}

/**
 * Removes the private zones from a ride before it leaves the phone (#6).
 *
 * - Track points inside a zone are dropped.
 * - The start or finish inside a zone is snapped to the first (last) point of the ride's line
 *   that is outside every zone, and loses its name.
 * - Other stops inside a zone disappear.
 *
 * Points are dropped, not cut exactly on the circle: the first point kept depends on how the track
 * was sampled, which makes it harder to rebuild the circle from many rides.
 */
object PrivateZones {
    fun apply(ride: Ride, zones: List<PrivateZone>): Ride {
        if (zones.isEmpty()) return ride
        fun inside(p: LatLon) = zones.any { it.contains(p) }
        val line = ride.line
        if (line.none(::inside) && ride.stops.none { inside(it.point) }) return ride

        val track = if (ride.straightLines) emptyList() else ride.track.filterNot(::inside)
        val firstOut = line.firstOrNull { !inside(it) }
        val lastOut = line.lastOrNull { !inside(it) }
        if (firstOut == null || lastOut == null) return ride.copy(track = emptyList(), stops = emptyList(), route = null)

        val stops = ride.stops.mapIndexedNotNull { i, s ->
            when {
                !inside(s.point) -> s
                i == 0 -> Stop(null, firstOut)
                i == ride.stops.lastIndex -> Stop(null, lastOut)
                else -> null
            }
        }
        // A snapped end can land on the next stop: one stop is enough, the one with a name.
        val distinct = stops.fold(listOf<Stop>()) { acc, s ->
            val prev = acc.lastOrNull()
            when {
                prev == null || prev.point != s.point -> acc + s
                prev.name == null -> acc.dropLast(1) + s
                else -> acc
            }
        }
        // The road calculated on the phone went through the zone: what goes out is this track.
        return ride.copy(track = track, stops = distinct, route = null)
    }

    /**
     * Places worth protecting that are not yet covered by a zone: where the saved rides start
     * and finish, most frequent first. Points closer than [mergeM] count as one place.
     */
    fun candidates(rides: List<SavedRide>, zones: List<PrivateZone>, mergeM: Double = 300.0): List<Candidate> {
        val groups = mutableListOf<Candidate>()
        for (saved in rides) {
            val line = saved.toRide().line
            if (line.isEmpty()) continue
            for (p in listOf(line.first(), line.last()).distinct()) {
                if (zones.any { it.contains(p) }) continue
                val i = groups.indexOfFirst { distanceKm(it.point, p) * 1000 <= mergeM }
                if (i < 0) groups += Candidate(p, saved.name, 1) else groups[i] = groups[i].copy(rides = groups[i].rides + 1)
            }
        }
        return groups.sortedByDescending { it.rides }
    }

    /** A place where [rides] saved rides start or finish; [rideName]: the first of them. */
    data class Candidate(val point: LatLon, val rideName: String?, val rides: Int)

    private val coords = Regex("""(-?\d{1,2}(?:\.\d+)?)\s*[,;\s]\s*(-?\d{1,3}(?:\.\d+)?)""")

    /** "40.6667, 16.6043", "40.6667 16.6043" or "geo:40.6667,16.6043": as copied from a map app. */
    fun parseLatLon(text: String): LatLon? {
        val m = coords.find(text.trim()) ?: return null
        val lat = m.groupValues[1].toDoubleOrNull() ?: return null
        val lon = m.groupValues[2].toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return LatLon(lat, lon)
    }
}

/**
 * What actually leaves the phone when a ride is shared (#6, #12).
 *
 * Every way of sharing (GPX file, QR and link, Nostr later) must send [ride] from here, never
 * the original: this is the one place where the private zones and the trimmed ends are applied.
 */
data class SharePreview(
    val original: Ride,
    val ride: Ride,
) {
    /** Nothing is left to share: the whole ride is inside private zones. */
    val empty: Boolean get() = ride.line.size < 2

    val km: Double get() = ride.line.zipWithNext { a, b -> distanceKm(a, b) }.sum()

    /** Stops of the original that do not go out (snapped ends do not count as hidden). */
    val hiddenStops: Int get() = (original.stops.size - ride.stops.size).coerceAtLeast(0)

    /** How far the shared start is from the real one, in km; 0 if it did not move. */
    val startMovedKm: Double get() = moved(original.line.firstOrNull(), ride.line.firstOrNull())
    val finishMovedKm: Double get() = moved(original.line.lastOrNull(), ride.line.lastOrNull())

    private fun moved(a: LatLon?, b: LatLon?) = if (a == null || b == null) 0.0 else distanceKm(a, b)

    companion object {
        /**
         * [trimEnds]: also remove the first and last km ([PrivateEnds]).
         * [hideZones]: apply the private [zones]; on by default in the share dialog.
         */
        fun of(ride: Ride, zones: List<PrivateZone>, trimEnds: Boolean, hideZones: Boolean = true): SharePreview {
            // Zones first: the trimmed km are then counted from the edge of the zone.
            var out = if (hideZones) PrivateZones.apply(ride, zones) else ride
            if (trimEnds && out.line.size >= 2) out = PrivateEnds.trim(out)
            return SharePreview(ride, out)
        }
    }
}
