package io.github.nanunic9379.rideplan.route

import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.Ride

/**
 * One piece a ride needs on the phone to be ridden without signal (#67).
 * [ROAD_DATA]: the routing cells around the ride, which also carry fuel stations and the
 * seasonal closures and speed cameras: without them there is no re-routing off the planned road.
 * [MAP]: the offline ride map is not saved (or its download stopped half way).
 * [MAP_UPDATE]: the map is saved but OpenFreeMap has published newer tiles since, so with no
 * signal it may not show.
 */
enum class OfflineNeed { ROAD_DATA, MAP, MAP_UPDATE }

/** Where the saved map of one ride stands. */
enum class MapState { SAVED, STALE, MISSING }

/**
 * What a ride still needs for a day without signal. [missing] is empty when it is ready.
 * [mapPlan]: the map download that fixes [OfflineNeed.MAP] or [OfflineNeed.MAP_UPDATE]; null
 * when the map is fine, or the ride is too long for one map download.
 */
data class OfflineReadiness(val missing: List<OfflineNeed>, val mapPlan: RideMap.Plan? = null) {
    val ready: Boolean get() = missing.isEmpty()

    /** The map is missing or old (either way, no reliable map without signal). */
    val mapMissing: Boolean get() = OfflineNeed.MAP in missing || OfflineNeed.MAP_UPDATE in missing

    /** One tap can get something: the road data is always worth asking for, the map only if it fits. */
    val canDownload: Boolean get() = OfflineNeed.ROAD_DATA in missing || (mapMissing && mapPlan != null)
}

/**
 * What one "Get everything" tap downloads, in this order: the road data first (small, and the
 * map download takes several minutes), then the map. [bytes]: the total, for the one question
 * about mobile data.
 */
data class GetEverything(val road: DataDownload?, val map: RideMapRequest?) {
    val bytes: Long get() = (road?.bytes ?: 0) + (map?.plan?.estimate?.bytes ?: 0)
    val empty: Boolean get() = road == null && map == null
}

/** "Ready without signal" (#67) and the check before navigating (#68): plain decisions, no I/O. */
object ReadyOffline {
    /**
     * The points the road data must cover: the stops, as for routing and for re-routing on the
     * move. A GPX track without stops: its two ends.
     */
    fun points(ride: Ride): List<LatLon> = when {
        ride.stops.size >= 2 -> ride.stops.map { it.point }
        ride.track.size >= 2 -> listOf(ride.track.first(), ride.track.last())
        else -> emptyList()
    }

    /**
     * The saved map of the ride with [RideMap.rideKey] [key]: a complete map that is up to date
     * wins over an old or unfinished one of the same ride.
     */
    fun mapState(key: String, saved: List<SavedRideMap>): MapState {
        val mine = saved.filter { it.info.ride == key && it.info.complete }
        return when {
            mine.any { !it.stale } -> MapState.SAVED
            mine.isNotEmpty() -> MapState.STALE
            else -> MapState.MISSING
        }
    }

    /**
     * What the ride still needs. [offer]: the map download [RideMap.offer] proposes, needed only
     * when the map is not saved. [roadData]: the road data on the phone covers [points].
     */
    fun check(map: MapState, offer: RideMap.Offer?, roadData: Boolean): OfflineReadiness {
        val missing = buildList {
            if (!roadData) add(OfflineNeed.ROAD_DATA)
            when (map) {
                MapState.SAVED -> {}
                MapState.STALE -> add(OfflineNeed.MAP_UPDATE)
                MapState.MISSING -> add(OfflineNeed.MAP)
            }
        }
        return OfflineReadiness(missing, if (map == MapState.SAVED) null else planOf(offer))
    }

    /** The plan of a map offer that fits one download, at full or reduced detail. */
    fun planOf(offer: RideMap.Offer?): RideMap.Plan? = when (offer) {
        is RideMap.Offer.Full -> offer.plan
        is RideMap.Offer.Reduced -> offer.plan
        else -> null
    }

    /**
     * The part of a road-data plan that still has to come down; null when the phone already
     * has it all (the local check is stricter than the plan, e.g. near the edge of an area).
     */
    fun toGet(plan: DataDownload?): DataDownload? = when (plan) {
        null -> null
        is CellsDownload -> plan.takeIf { it.packs.isNotEmpty() }
        is AreasDownload -> plan.takeIf { !it.ready }
        is RegionDownload -> plan
    }

    /** What one tap downloads for [readiness], with the road-data [roadPlan] found online. */
    fun getEverything(readiness: OfflineReadiness, key: String, name: String?, roadPlan: DataDownload?): GetEverything = GetEverything(
        road = if (OfflineNeed.ROAD_DATA in readiness.missing) toGet(roadPlan) else null,
        map = if (readiness.mapMissing) readiness.mapPlan?.let { RideMapRequest(key, name, it) } else null,
    )
}
