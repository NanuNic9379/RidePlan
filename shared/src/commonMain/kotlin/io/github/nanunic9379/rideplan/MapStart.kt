package io.github.nanunic9379.rideplan

import io.github.nanunic9379.rideplan.profile.SavedRide

/** A map camera: centre and zoom. */
data class MapView(val lat: Double, val lon: Double, val zoom: Double) {
    val valid: Boolean
        get() = lat in -90.0..90.0 && lon in -180.0..180.0 && zoom in 0.0..22.0 && !lat.isNaN() && !lon.isNaN()
}

/**
 * Where the last viewed map area is kept, on this platform. It stays on the phone and is not
 * part of the profile backup: it is only a convenience.
 */
interface MapViewMemory {
    fun load(): MapView?
    fun save(view: MapView)
}

/**
 * Where "Explore" opens: the area you looked at last; otherwise the area of your most recent
 * ride; only on a brand-new phone the world overview of [MapDefaults].
 */
object MapStart {
    /** Close enough to see the roads around a ride's start. */
    const val RIDE_ZOOM = 9.0

    fun of(last: MapView?, rides: List<SavedRide>): MapView {
        if (last != null && last.valid) return last
        rides.maxByOrNull { it.savedAt }?.let { ride ->
            val point = ride.stops.firstOrNull()?.let { it.lat to it.lon }
                ?: ride.track.takeIf { it.size >= 2 }?.let { it[0] to it[1] }
            if (point != null) {
                val view = MapView(point.first, point.second, RIDE_ZOOM)
                if (view.valid) return view
            }
        }
        return MapView(MapDefaults.START_LAT, MapDefaults.START_LON, MapDefaults.START_ZOOM)
    }
}
