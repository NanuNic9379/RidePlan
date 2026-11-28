package io.github.nanunic9379.rideplan.profile

import io.github.nanunic9379.rideplan.ride.DayCap
import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.PrivateZone
import io.github.nanunic9379.rideplan.ride.Ride
import io.github.nanunic9379.rideplan.ride.RideLink
import io.github.nanunic9379.rideplan.ride.RideUpdate
import io.github.nanunic9379.rideplan.ride.RideVersion
import io.github.nanunic9379.rideplan.ride.Stop
import io.github.nanunic9379.rideplan.ride.StopPlan
import io.github.nanunic9379.rideplan.route.ownOptions
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Who uses the app on this phone (#41). No account: everything stays here.
 * [id]: random, created on first launch; it will be used to recognize each other when rides
 * are exchanged between people (#7) or if a central archive arrives.
 * [bike]: the name of the default bike. Before the garage (#21) it was the only bike; it is kept
 * in step with the garage so older versions reading a newer backup still find it.
 * [color]: the colour behind your initial, an index into the app palette.
 */
@Serializable
data class Profile(
    val id: String,
    val name: String = "",
    val bike: String = "",
    /** The welcome is shown only once, even if you skip it. */
    val welcomed: Boolean = false,
    val createdAt: Long,
    val color: Int = 0,
)

/** A saved stop: [kind] is the name of [Stop.Kind], so the file stays readable. [overnight]: see [Stop.overnight]. */
@Serializable
data class SavedStop(
    val name: String?,
    val lat: Double,
    val lon: Double,
    val kind: String = Stop.Kind.STOP.name,
    val overnight: Boolean = false,
    /** The plan of the stop (#10): [StopPlan.Purpose.code], minutes after midnight, pause, note. */
    val purpose: String? = null,
    val time: Int? = null,
    val pause: Int = 0,
    val note: String = "",
) {
    val plan: StopPlan? get() = StopPlan(StopPlan.Purpose.of(purpose), time, pause, note).normalized()

    fun toStop(): Stop =
        Stop(name, LatLon(lat, lon), Stop.Kind.entries.firstOrNull { it.name == kind } ?: Stop.Kind.STOP, overnight, plan)

    companion object {
        fun of(stop: Stop): SavedStop {
            val p = stop.plan?.normalized()
            return SavedStop(
                stop.name, stop.point.lat, stop.point.lon, stop.kind.name, stop.overnight,
                purpose = p?.purpose?.code, time = p?.time, pause = p?.pauseMinutes ?: 0, note = p?.note.orEmpty(),
            )
        }
    }
}

/** A ride that arrived from someone else (#21): it waits in "Received" until you keep or discard it. */
@Serializable
data class Received(val at: Long, val via: String = "link")

/**
 * A ride in the "Your rides" list.
 * [track]: the track of a GPX, alternating lat and lon. Empty if RidePlan calculates
 * the route: it is recalculated from the stops when reopened.
 * [ridden]: when you rode it (milliseconds), once per outing.
 * [riddenWith]: the bike used for an outing (by its time in [ridden]), by name.
 * [notes]: private notes; they never end up in a GPX file or a link.
 * [openedAt]: last time you opened it, for the "last opened" order.
 * [received]: not null while it is a received ride not kept yet.
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
    val favourite: Boolean = false,
    val notes: String = "",
    val openedAt: Long? = null,
    val received: Received? = null,
    val riddenWith: Map<Long, String> = emptyMap(),
    /** Daily cap of a multi-day trip (#12), see [SavedDayCap]. */
    val dayCap: SavedDayCap? = null,
    /** Version of the ride (#11); null for rides saved before versions existed. */
    val version: RideVersion? = null,
    /** A newer version replaced this ride (#11): shown until you dismiss it. */
    val update: RideUpdate? = null,
    /**
     * Route options the ride was routed with (#64), in the link format ([RideLink.optionsCode]):
     * reopening, sharing and publishing use them instead of the current global preferences.
     * Null for rides saved before #64 or never routed with options.
     */
    val options: String? = null,
) {
    val lastOpened: Long get() = openedAt ?: savedAt
    val lastRidden: Long? get() = ridden.maxOrNull()

    /** Every point of the ride, for drawing its shape: the track or, without one, the stops. */
    val shape: List<LatLon>
        get() = if (track.size >= 4) track.chunked(2).filter { it.size == 2 }.map { LatLon(it[0], it[1]) }
        else stops.map { LatLon(it.lat, it.lon) }

    fun toRide(): Ride = Ride(
        name = name,
        track = track.chunked(2).filter { it.size == 2 }.map { LatLon(it[0], it[1]) },
        stops = stops.map { it.toStop() },
        source = Ride.Source.entries.firstOrNull { it.name == source } ?: Ride.Source.GPX,
        dayCap = dayCap?.toDayCap(),
        version = version,
        sharedOptions = options?.let(RideLink::parseOptions),
    )

    companion object {
        fun from(ride: Ride, id: String, now: Long, ridden: List<Long> = emptyList()): SavedRide = SavedRide(
            id = id,
            name = ride.name,
            savedAt = now,
            km = ride.lengthKm,
            seconds = ride.route?.seconds?.toLong(),
            source = ride.source.name,
            stops = ride.stops.map(SavedStop::of),
            // The route calculated by RidePlan is rebuilt from the stops: the file stays small.
            track = if (ride.route != null) emptyList() else ride.track.flatMap { listOf(it.lat, it.lon) },
            ridden = ridden,
            dayCap = ride.dayCap?.let(SavedDayCap::from),
            version = ride.version,
            options = ride.ownOptions?.let(RideLink::optionsCode),
        )
    }

    /**
     * Same road and same plans: what decides whether the ride got a new version (#11).
     * The stops carry their plans and overnights (#12); the daily cap decides where the days end.
     */
    internal fun sameRouteAs(other: SavedRide): Boolean =
        stops == other.stops && track == other.track && dayCap == other.dayCap
}

/**
 * Everything the app remembers about you. [version]: the format; [ProfileData.migrated] brings an
 * older file up to [CURRENT_VERSION]. New fields always have a default, so files written by older
 * versions (and their backups) still open.
 */
@Serializable
data class ProfileData(
    val version: Int = 1,
    val profile: Profile,
    val rides: List<SavedRide> = emptyList(),
    /** Places cut out of every shared ride (#6). */
    val privateZones: List<PrivateZone> = emptyList(),
    /** Photographer places saved on this device (#254). */
    val photoSpots: List<PhotoSpot> = emptyList(),
    /** The garage (#21). */
    val bikes: List<Bike> = emptyList(),
    val defaultBikeId: String? = null,
    val settings: AppSettings = AppSettings(),
) {
    val stats: Stats get() = Stats(
        saved = rides.size,
        ridden = rides.sumOf { it.ridden.size },
        kmRidden = rides.sumOf { it.km * it.ridden.size },
        longestKm = rides.filter { it.ridden.isNotEmpty() }.maxOfOrNull { it.km },
        lastRidden = rides.flatMap { it.ridden }.maxOrNull(),
    )

    /** The bike that sets the rules of new rides; the first one if none is marked. */
    val defaultBike: Bike? get() = bikes.firstOrNull { it.id == defaultBikeId } ?: bikes.firstOrNull()

    /**
     * The saved ride (new or updated), at the top of the list. What only lives in the list
     * (favourite, notes, history, received) is kept.
     */
    fun save(ride: Ride, id: String?, now: Long): Pair<ProfileData, String> {
        val old = rides.firstOrNull { it.id == id }
        // The same ride is already here (a link or GPX of a ride you have, #11): update it, no copy.
        if (old == null) sameRide(ride)?.let { return adopt(it, ride, now) }
        val newId = old?.id ?: newId()
        val fresh = SavedRide.from(ride, newId, now, old?.ridden.orEmpty())
        // An older version shown on the map must not overwrite the newer one you already have.
        if (old != null && isStale(old, fresh, ride.version)) return this to old.id
        val saved = if (old == null) fresh.copy(version = ride.version ?: RideVersion.first(now)) else fresh.copy(
            favourite = old.favourite,
            notes = old.notes,
            openedAt = old.openedAt,
            received = old.received,
            riddenWith = old.riddenWith,
            version = nextVersion(old, fresh, ride.version, now),
            update = old.update,
        )
        return copy(rides = listOf(saved) + rides.filter { it.id != newId }) to newId
    }

    /** A ride received by link (#48): saved at once, but in "Received" until you keep it. */
    fun receive(ride: Ride, now: Long): Pair<ProfileData, String> {
        val existing = sameRide(ride)
        if (existing != null) return adopt(existing, ride, now)
        val (data, id) = save(ride, null, now)
        return data.change(id) { it.copy(received = Received(now)) } to id
    }

    /** The saved ride that is the same ride as [ride] (same [RideVersion.rideId]), if any. */
    fun sameRide(ride: Ride): SavedRide? = ride.version?.let { v -> rides.firstOrNull { v.sameRide(it.version) } }

    /**
     * [ride] is another version of [old] (#11). A newer one replaces the road and the plans and
     * leaves an [RideUpdate] note; what only lives here (notes, outings, favourite) stays.
     * The same or an older version changes nothing.
     */
    private fun adopt(old: SavedRide, ride: Ride, now: Long): Pair<ProfileData, String> {
        val incoming = ride.version ?: return this to old.id
        if (!incoming.isNewerThan(old.version)) return this to old.id
        val saved = SavedRide.from(ride, old.id, now, old.ridden).copy(
            name = ride.name ?: old.name,
            favourite = old.favourite,
            notes = old.notes,
            openedAt = old.openedAt,
            received = old.received,
            riddenWith = old.riddenWith,
            version = incoming,
            update = RideUpdate.between(old.toRide(), ride, now),
        )
        return copy(rides = listOf(saved) + rides.filter { it.id != old.id }) to old.id
    }

    /**
     * The version after a save (#11). Unchanged road: same version. Changed in the same editing
     * session as the last version (made after the ride was last opened): same number, new date.
     * Otherwise the number grows and the old note no longer applies.
     */
    private fun nextVersion(old: SavedRide, fresh: SavedRide, incoming: RideVersion?, now: Long): RideVersion {
        val base = old.version ?: incoming ?: RideVersion.first(now)
        return when {
            fresh.sameRouteAs(old) -> base
            base.updatedAt >= old.lastOpened -> base.copy(updatedAt = now)
            else -> RideVersion(base.rideId, base.number + 1, "", now)
        }
    }

    /**
     * [incoming] is an older version of [old] with another road, and [old] was not edited in
     * this session: the map shows an old link or file, and the newer version wins.
     */
    private fun isStale(old: SavedRide, fresh: SavedRide, incoming: RideVersion?): Boolean {
        val v = old.version ?: return false
        return v.isNewerThan(incoming) && !fresh.sameRouteAs(old) && v.updatedAt < old.lastOpened
    }

    /** Plan of stop [index] (#10); null removes it. A changed plan counts as a change of the ride. */
    fun setStopPlan(id: String, index: Int, plan: StopPlan?, now: Long): ProfileData = change(id) { r ->
        val stop = r.stops.getOrNull(index) ?: return@change r
        val p = plan?.normalized()
        val updated = SavedStop.of(stop.toStop().copy(plan = p)).let {
            // A stop with a plan is a real stop: the route reaches it.
            if (p != null && stop.kind == Stop.Kind.VIA.name) it.copy(kind = Stop.Kind.STOP.name) else it
        }
        val fresh = r.copy(stops = r.stops.toMutableList().also { it[index] = updated })
        if (fresh.sameRouteAs(r)) r else fresh.copy(version = nextVersion(r, fresh, null, now))
    }

    /** The leader's note for the current version ("v3: landslide on the SP12", #11). */
    fun setVersionNote(id: String, note: String, now: Long): ProfileData = change(id) { r ->
        val v = r.version ?: RideVersion.first(now)
        r.copy(version = v.copy(note = note.replace('\n', ' ').trim().take(RideVersion.MAX_NOTE)))
    }

    /** The "updated from vN to vM" note was read. */
    fun dismissUpdate(id: String): ProfileData = change(id) { it.copy(update = null) }

    /** Keeps a received ride among your own. */
    fun keep(id: String): ProfileData = change(id) { it.copy(received = null) }

    /** An outing on [day], with the bike you rode, if any. Riding a received ride keeps it. */
    fun markRidden(id: String, day: Long, bike: String? = defaultBike?.name): ProfileData = change(id) {
        it.copy(
            ridden = it.ridden + day,
            riddenWith = if (bike.isNullOrBlank()) it.riddenWith else it.riddenWith + (day to bike),
            received = null,
        )
    }

    /** Removes the last marked outing, for those who tapped by mistake. */
    fun undoRidden(id: String): ProfileData = change(id) { r ->
        val last = r.ridden.lastOrNull() ?: return@change r
        val left = r.ridden.dropLast(1)
        r.copy(ridden = left, riddenWith = if (last in left) r.riddenWith else r.riddenWith - last)
    }

    fun rename(id: String, name: String): ProfileData = change(id) { it.copy(name = name.trim().ifEmpty { null }) }

    fun setFavourite(id: String, favourite: Boolean): ProfileData = change(id) { it.copy(favourite = favourite) }

    fun setNotes(id: String, notes: String): ProfileData = change(id) { it.copy(notes = notes.take(MAX_NOTES)) }

    fun opened(id: String, now: Long): ProfileData = change(id) { it.copy(openedAt = now) }

    fun delete(id: String): ProfileData = copy(rides = rides.filter { it.id != id })

    fun addZone(zone: PrivateZone): ProfileData = copy(privateZones = privateZones + zone)

    fun savePhotoSpot(spot: PhotoSpot): ProfileData = copy(
        photoSpots = if (photoSpots.any { it.id == spot.id }) photoSpots.map { if (it.id == spot.id) spot else it }
        else photoSpots + spot,
    )

    fun deletePhotoSpot(id: String): ProfileData = copy(photoSpots = photoSpots.filterNot { it.id == id })

    fun deleteZone(id: String): ProfileData = copy(privateZones = privateZones.filter { it.id != id })

    /** Adds or replaces a bike. The first bike, or one marked [default], becomes the default. */
    fun saveBike(bike: Bike, default: Boolean): ProfileData {
        val defaults = defaultMaintenanceRules(bike.finalDrive, bike.cooling)
        val configured = bike.copy(maintenance = defaults.map { base -> bike.maintenance.firstOrNull { it.task == base.task } ?: base })
        val list = if (bikes.any { it.id == bike.id }) bikes.map { if (it.id == bike.id) configured else it } else bikes + configured
        val defaultId = when {
            default || list.size == 1 -> configured.id
            defaultBikeId == configured.id -> list.firstOrNull { it.id != configured.id }?.id ?: configured.id
            else -> defaultBikeId?.takeIf { id -> list.any { it.id == id } } ?: list.first().id
        }
        return copy(bikes = list, defaultBikeId = defaultId).withBikeInProfile()
    }

    fun deleteBike(id: String): ProfileData {
        val list = bikes.filter { it.id != id }
        val defaultId = if (defaultBikeId == id || defaultBikeId == null) list.firstOrNull()?.id else defaultBikeId
        return copy(bikes = list, defaultBikeId = defaultId).withBikeInProfile()
    }

    private fun withBikeInProfile(): ProfileData = copy(profile = profile.copy(bike = defaultBike?.name.orEmpty()))

    /**
     * Restore from a backup (#43): it merges, it does not replace, so nothing you already
     * have on this phone is lost. The same ride (same id) is not duplicated: the version
     * saved last wins and the outings add up without duplicates.
     * The backup's name is used only if it is still empty here.
     * Private zones (#6) add up too: a zone is never lost by restoring.
     * Bikes add up as well; a bike with the same name is not doubled.
     * Also returns how many new rides arrived.
     */
    fun merge(backup: ProfileData): Pair<ProfileData, Int> {
        val other = backup.migrated()
        val merged = rides.associateBy { it.id }.toMutableMap()
        var added = 0
        for (r in other.rides) {
            val old = merged[r.id]
            if (old == null) added++
            val newest = if (old == null || r.savedAt > old.savedAt) r else old
            val older = if (newest === r) old else r
            merged[r.id] = newest.copy(
                ridden = (old?.ridden.orEmpty() + r.ridden).distinct().sorted(),
                riddenWith = r.riddenWith + old?.riddenWith.orEmpty(),
                favourite = r.favourite || old?.favourite == true,
                notes = newest.notes.ifBlank { older?.notes.orEmpty() },
                // Kept on either side means kept.
                received = if (old != null && (old.received == null || r.received == null)) null else newest.received,
            )
        }
        val names = bikes.map { it.name.trim().lowercase() }.toSet()
        val ids = bikes.map { it.id }.toSet()
        val newBikes = other.bikes.filter { it.id !in ids && it.name.trim().lowercase() !in names }
        val p = profile.copy(
            name = profile.name.ifBlank { other.profile.name },
            welcomed = true,
            createdAt = minOf(profile.createdAt, other.profile.createdAt),
        )
        val all = bikes + newBikes
        val result = copy(
            profile = p,
            rides = merged.values.sortedByDescending { it.savedAt },
            privateZones = (privateZones + other.privateZones).distinctBy { it.id },
            photoSpots = (photoSpots + other.photoSpots).distinctBy { it.id },
            bikes = all,
            defaultBikeId = defaultBikeId?.takeIf { id -> all.any { it.id == id } }
                ?: other.defaultBikeId?.takeIf { id -> newBikes.any { it.id == id } },
        )
        return result.withBikeInProfile() to added
    }

    /**
     * Brings a file written by an older version up to date. Version 1 had a single bike, a name
     * in the profile: it becomes the first bike of the garage.
     */
    fun migrated(): ProfileData {
        if (version >= CURRENT_VERSION) return this
        var d = this
        if (bikes.isEmpty() && profile.bike.isNotBlank()) {
            val bike = Bike(id = newId(), name = profile.bike.trim())
            d = d.copy(bikes = listOf(bike), defaultBikeId = bike.id)
        }
        return d.copy(version = CURRENT_VERSION)
    }

    /**
     * Delete everything (#21): a brand-new profile, as on the first launch. The welcome comes back.
     */
    fun wiped(now: Long): ProfileData = fresh(now)

    private fun change(id: String, block: (SavedRide) -> SavedRide): ProfileData =
        copy(rides = rides.map { if (it.id == id) block(it) else it })

    companion object {
        const val CURRENT_VERSION = 2
        const val MAX_NOTES = 2000

        fun fresh(now: Long) = ProfileData(version = CURRENT_VERSION, profile = Profile(id = newId(), createdAt = now))

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

/**
 * Daily cap of a multi-day trip (#12). [unit] is the name of [DayCap.Unit], so the file stays
 * readable; an unknown unit (from a newer version) is ignored.
 */
@Serializable
data class SavedDayCap(val unit: String, val value: Double) {
    fun toDayCap(): DayCap? = DayCap.Unit.entries.firstOrNull { it.name == unit }?.let { DayCap(it, value) }?.takeIf { it.valid }

    companion object {
        fun from(cap: DayCap) = SavedDayCap(cap.unit.name, cap.value)
    }
}
