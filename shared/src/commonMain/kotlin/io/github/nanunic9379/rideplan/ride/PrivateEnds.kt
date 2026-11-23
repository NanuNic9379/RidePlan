package io.github.nanunic9379.rideplan.ride

/**
 * Start and finish fuzzed before sharing (#12): they are often home or the garage.
 *
 * The first and last [KM] along the ride are removed (at most a quarter on each side, so a
 * short ride does not vanish). Stops inside the removed stretches disappear, those at the ends
 * lose their name. The private zones of the profile (#6) are applied first, see [SharePreview].
 */
object PrivateEnds {
    const val KM = 1.0

    fun trim(ride: Ride, km: Double = KM): Ride {
        val line = ride.line
        if (line.size < 2) return ride
        val total = line.zipWithNext { a, b -> distanceKm(a, b) }.sum()
        if (total <= 0.0) return ride
        val cut = minOf(km, total / 4)
        val start = pointAt(line, cut)
        val end = pointAt(line, total - cut)
        val first = line.first()
        val last = line.last()

        val track = if (ride.straightLines) emptyList() else buildList {
            add(start)
            var walked = 0.0
            for (i in 1 until line.size) {
                walked += distanceKm(line[i - 1], line[i])
                if (walked > cut && walked < total - cut) add(line[i])
            }
            add(end)
        }
        val middle = ride.stops.drop(1).dropLast(1).filter {
            distanceKm(it.point, first) > cut && distanceKm(it.point, last) > cut
        }
        return ride.copy(
            track = track,
            stops = listOf(Stop(null, start)) + middle + Stop(null, end),
            route = null,
        )
    }

    /** The point [km] from the start of the line, following it. */
    internal fun pointAt(line: List<LatLon>, km: Double): LatLon {
        var left = km
        for (i in 1 until line.size) {
            val a = line[i - 1]
            val b = line[i]
            val d = distanceKm(a, b)
            if (d > 0 && left <= d) {
                val f = left / d
                return LatLon(a.lat + (b.lat - a.lat) * f, a.lon + (b.lon - a.lon) * f)
            }
            left -= d
        }
        return line.last()
    }
}
