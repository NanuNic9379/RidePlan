package io.github.nanunic9379.rideplan.ride

import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * The version of a ride (#11): the group leader changes the route the evening before
 * ("v3: landslide on the SP12") and whoever has the old one sees that a newer one exists.
 *
 * [rideId]: the same ride on every phone, created by whoever made it; it travels in links
 * and GPX files. [number]: 1, 2, 3… it grows when the route changes in a new editing session.
 * [note]: what changed, written by the leader. [updatedAt]: when (milliseconds).
 */
@Serializable
data class RideVersion(
    val rideId: String,
    val number: Int = 1,
    val note: String = "",
    val updatedAt: Long = 0,
) {
    /** Same ride and newer than [other]. */
    fun isNewerThan(other: RideVersion?): Boolean = other != null && rideId == other.rideId && number > other.number

    fun sameRide(other: RideVersion?): Boolean = other != null && rideId == other.rideId

    companion object {
        const val MAX_NOTE = 140

        /** 12 hex characters: unique enough among one rider's rides, short in a QR. */
        @OptIn(ExperimentalStdlibApi::class)
        fun newId(random: Random = Random): String = random.nextBytes(6).toHexString()

        fun first(now: Long, random: Random = Random) = RideVersion(newId(random), 1, "", now)
    }
}

/**
 * A ride replaced by a newer version (#11): shown once to whoever had the old one.
 * [added] / [removed]: the stops, by name ("" for points without a name).
 * [replanned]: stops whose time, pause or purpose changed.
 */
@Serializable
data class RideUpdate(
    val from: Int,
    val to: Int,
    val note: String = "",
    val at: Long = 0,
    val added: List<String> = emptyList(),
    val removed: List<String> = emptyList(),
    val replanned: Int = 0,
    val renamed: Boolean = false,
) {
    val routeChanged: Boolean get() = added.isNotEmpty() || removed.isNotEmpty()

    companion object {
        /** What changed from [old] to [new]: stops are matched by their place (about 10 m). */
        fun between(old: Ride, new: Ride, at: Long): RideUpdate {
            fun key(s: Stop) = (s.point.lat * 10_000).toLong() to (s.point.lon * 10_000).toLong()
            val oldByKey = old.stops.associateBy(::key)
            val newKeys = new.stops.map(::key).toSet()
            return RideUpdate(
                from = old.version?.number ?: 1,
                to = new.version?.number ?: 1,
                note = new.version?.note.orEmpty(),
                at = at,
                added = new.stops.filter { key(it) !in oldByKey }.map { it.name.orEmpty() },
                removed = old.stops.filter { key(it) !in newKeys }.map { it.name.orEmpty() },
                replanned = new.stops.count { s -> oldByKey[key(s)]?.let { it.plan != s.plan } == true },
                renamed = new.name != null && new.name != old.name,
            )
        }
    }
}
