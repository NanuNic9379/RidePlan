package io.github.nanunic9379.rideplan.ride

import io.github.nanunic9379.rideplan.route.routable
import io.github.nanunic9379.rideplan.route.unrouted

/**
 * Editing the stops of a ride on the map (#2): add, move, remove, reorder.
 * Every edit drops the old road (the ride goes back to straight lines) so that the
 * platform routes it again. Rides with a real track from a file (GPX, KML) are not
 * editable: their track would no longer match the stops.
 */

/** The stops can be edited on the map: a ride made of stops, routed or not yet. */
val Ride.stopsEditable: Boolean get() = routable

/** A stop can be removed only while at least two stay: start and finish. */
val Ride.canRemoveStop: Boolean get() = stops.size > 2

/**
 * Adds an unnamed pass-through point where it costs the smallest detour: between the two
 * consecutive stops whose straight-line path gets the least longer. Start and finish stay.
 */
fun Ride.withStopAdded(point: LatLon): Ride {
    if (!stopsEditable) return this
    val index = bestInsertIndex(stops.map { it.point }, point)
    val fresh = stops.toMutableList().also { it.add(index, Stop(null, point, Stop.Kind.VIA)) }
    return copy(stops = fresh).unrouted()
}

/** Stop [index] dragged to [point]: its old name no longer applies and the route passes exactly there. */
fun Ride.withStopMoved(index: Int, point: LatLon): Ride {
    if (!stopsEditable || index !in stops.indices) return this
    if (stops[index].point == point) return this
    val fresh = stops.toMutableList().also { it[index] = Stop(null, point, Stop.Kind.VIA) }
    return copy(stops = fresh).unrouted()
}

/** Removes stop [index], as long as start and finish remain. */
fun Ride.withStopRemoved(index: Int): Ride {
    if (!stopsEditable || !canRemoveStop || index !in stops.indices) return this
    return copy(stops = stops.toMutableList().also { it.removeAt(index) }).unrouted()
}

/** Moves stop [from] to position [to] in the order of the ride. */
fun Ride.withStopReordered(from: Int, to: Int): Ride {
    if (!stopsEditable || from !in stops.indices || to !in stops.indices || from == to) return this
    val fresh = stops.toMutableList().also { it.add(to, it.removeAt(from)) }
    return copy(stops = fresh).unrouted()
}

/**
 * Where to insert [point] among [stops]: the index (1..size-1) between the pair of consecutive
 * stops with the smallest detour. With fewer than two stops it goes at the end.
 */
internal fun bestInsertIndex(stops: List<LatLon>, point: LatLon): Int {
    if (stops.size < 2) return stops.size
    return (1..stops.lastIndex).minBy { i ->
        val a = stops[i - 1]
        val b = stops[i]
        distanceKm(a, point) + distanceKm(point, b) - distanceKm(a, b)
    }
}

/**
 * A map position as [LatLon]: the map may report longitudes of another world copy
 * (beyond ±180°), which are wrapped back; latitude is clamped. Null for non-finite values.
 */
fun mapLatLon(lat: Double, lon: Double): LatLon? {
    if (!lat.isFinite() || !lon.isFinite()) return null
    var wrapped = (lon + 180.0) % 360.0
    if (wrapped < 0) wrapped += 360.0
    return LatLon(lat.coerceIn(-90.0, 90.0), (wrapped - 180.0).coerceIn(-180.0, 180.0))
}
