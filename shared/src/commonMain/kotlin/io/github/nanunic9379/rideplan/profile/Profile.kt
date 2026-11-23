package io.github.nanunic9379.rideplan.profile

import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.PrivateZone
import io.github.nanunic9379.rideplan.ride.Ride
import io.github.nanunic9379.rideplan.ride.Stop
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Who uses the app on this phone (#41). No account: everything stays here.
 * [id]: random, created on first launch; it will be used to recognize each other when rides
 * are exchanged between people (#7) or if a central archive arrives.
 */
@Serializable
data class Profile(
    val id: String,
    val name: String = "",
    val bike: String = "",
    /** The welcome is shown only once, even if you skip it. */
    val welcomed: Boolean = false,
    val createdAt: Long,
)

/** A saved stop: [kind] is the name of [Stop.Kind], so the file stays readable. */
@Serializable
data class SavedStop(val name: String?, val lat: Double, val lon: Double, val kind: String = Stop.Kind.STOP.name)

/**
 * A ride in the "Your rides" list.
 * [track]: the track of a GPX, alternating lat and lon. Empty if RidePlan calculates
 * the route: it is recalculated from the stops when reopened.
 * [ridden]: when you rode it (milliseconds), once per outing.
 */
@Serializable
data class SavedRide(
    val id: String,
    val name: String?,
    val savedAt: Long,
    val km: Double,
    val seconds: Long? = null,
    val source: String,
    val stops: List<SavedStop>,
    val track: List<Double> = emptyList(),
    val ridden: List<Long> = emptyList(),
) {
    fun toRide(): Ride = Ride(
        name = name,
        track = track.chunked(2).filter { it.size == 2 }.map { LatLon(it[0], it[1]) },
        stops = stops.map { s -> Stop(s.name, LatLon(s.lat, s.lon), Stop.Kind.entries.firstOrNull { it.name == s.kind } ?: Stop.Kind.STOP) },
        source = Ride.Source.entries.firstOrNull { it.name == source } ?: Ride.Source.GPX,
    )

    companion object {
        fun from(ride: Ride, id: String, now: Long, ridden: List<Long> = emptyList()): SavedRide = SavedRide(
            id = id,
            name = ride.name,
            savedAt = now,
            km = ride.lengthKm,
            seconds = ride.route?.seconds?.toLong(),
            source = ride.source.name,
            stops = ride.stops.map { SavedStop(it.name, it.point.lat, it.point.lon, it.kind.name) },
            // The route calculated by RidePlan is rebuilt from the stops: the file stays small.
            track = if (ride.route != null) emptyList() else ride.track.flatMap { listOf(it.lat, it.lon) },
            ridden = ridden,
        )
    }
}

/** Everything the app remembers about you. [version]: to change the format in the future. */
@Serializable
data class ProfileData(
    val version: Int = 1,
    val profile: Profile,
    val rides: List<SavedRide> = emptyList(),
    /** Places cut out of every shared ride (#6). */
    val privateZones: List<PrivateZone> = emptyList(),
) {
    val stats: Stats get() = Stats(
        saved = rides.size,
        ridden = rides.sumOf { it.ridden.size },
        kmRidden = rides.sumOf { it.km * it.ridden.size },
        longestKm = rides.filter { it.ridden.isNotEmpty() }.maxOfOrNull { it.km },
        lastRidden = rides.flatMap { it.ridden }.maxOrNull(),
    )

    /** The saved ride (new or updated), at the top of the list. */
    fun save(ride: Ride, id: String?, now: Long): Pair<ProfileData, String> {
        val old = rides.firstOrNull { it.id == id }
        val newId = old?.id ?: newId()
        val saved = SavedRide.from(ride, newId, now, old?.ridden.orEmpty())
        return copy(rides = listOf(saved) + rides.filter { it.id != newId }) to newId
    }

    fun markRidden(id: String, now: Long): ProfileData =
        copy(rides = rides.map { if (it.id == id) it.copy(ridden = it.ridden + now) else it })

    /** Removes the last marked outing, for those who tapped by mistake. */
    fun undoRidden(id: String): ProfileData =
        copy(rides = rides.map { if (it.id == id) it.copy(ridden = it.ridden.dropLast(1)) else it })

    fun rename(id: String, name: String): ProfileData =
        copy(rides = rides.map { if (it.id == id) it.copy(name = name.trim().ifEmpty { null }) else it })

    fun delete(id: String): ProfileData = copy(rides = rides.filter { it.id != id })

    fun addZone(zone: PrivateZone): ProfileData = copy(privateZones = privateZones + zone)

    fun deleteZone(id: String): ProfileData = copy(privateZones = privateZones.filter { it.id != id })

    /**
     * Restore from a backup (#43): it merges, it does not replace, so nothing you already
     * have on this phone is lost. The same ride (same id) is not duplicated: the version
     * saved last wins and the outings add up without duplicates.
     * The backup's name and bike are used only if they are still empty here.
     * Private zones (#6) add up too: a zone is never lost by restoring.
     * Also returns how many new rides arrived.
     */
    fun merge(other: ProfileData): Pair<ProfileData, Int> {
        val merged = rides.associateBy { it.id }.toMutableMap()
        var added = 0
        for (r in other.rides) {
            val old = merged[r.id]
            if (old == null) added++
            val newest = if (old == null || r.savedAt > old.savedAt) r else old
            merged[r.id] = newest.copy(ridden = (old?.ridden.orEmpty() + r.ridden).distinct().sorted())
        }
        val p = profile.copy(
            name = profile.name.ifBlank { other.profile.name },
            bike = profile.bike.ifBlank { other.profile.bike },
            welcomed = true,
            createdAt = minOf(profile.createdAt, other.profile.createdAt),
        )
        return copy(profile = p, rides = merged.values.sortedByDescending { it.savedAt }, privateZones = (privateZones + other.privateZones).distinctBy { it.id }) to added
    }

    companion object {
        fun fresh(now: Long) = ProfileData(profile = Profile(id = newId(), createdAt = now))

        @OptIn(ExperimentalStdlibApi::class)
        fun newId(): String = Random.nextBytes(16).toHexString()
    }
}

data class Stats(
    val saved: Int,
    val ridden: Int,
    val kmRidden: Double,
    val longestKm: Double?,
    val lastRidden: Long?,
)
