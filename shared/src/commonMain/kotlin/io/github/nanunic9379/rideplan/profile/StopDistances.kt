package io.github.nanunic9379.rideplan.profile

import io.github.nanunic9379.rideplan.nav.RouteLine
import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.distanceKm

/**
 * How far along a recorded track each stop is, for the ride detail. Only a real track gives a
 * real distance: without one, or for a stop that is not on it, the answer is null and nothing
 * is shown. Stops are taken in order, so a road ridden twice (out and back, loops) counts the
 * right pass.
 */
object StopDistances {
    /** A stop more than this far from the track is not on it. */
    const val MAX_OFFSET_M = 300.0

    /** Kilometres from the start of [track] for each of [stops]; null where unknown. */
    fun along(track: List<LatLon>, stops: List<LatLon>): List<Double?> {
        if (track.size < 2) return stops.map { null }
        val line = RouteLine(track)
        val km = line.length / 1000
        var from = 0.0
        return stops.mapIndexed { i, stop ->
            val metres = when {
                i == 0 && distanceKm(stop, track.first()) * 1000 <= MAX_OFFSET_M -> 0.0
                i == stops.lastIndex && i > 0 && distanceKm(stop, track.last()) * 1000 <= MAX_OFFSET_M -> line.length
                else -> line.firstNear(stop, MAX_OFFSET_M, from)?.along
            }
            if (metres != null) from = metres
            metres?.let { (it / 1000).coerceIn(0.0, km) }
        }
    }
}
