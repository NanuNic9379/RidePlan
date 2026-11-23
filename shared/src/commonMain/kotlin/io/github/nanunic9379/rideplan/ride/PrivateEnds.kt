package io.github.nanunic9379.rideplan.ride

/**
 * Partenza e arrivo sfumati prima di condividere (#12): spesso sono casa o garage.
 *
 * Si tolgono i primi e gli ultimi [KM] lungo il giro (al massimo un quarto per parte, così un
 * giro corto non sparisce). Le tappe dentro i tratti tolti spariscono, quelle agli estremi
 * perdono il nome. È una prima versione: le zone private impostabili arriveranno con #12.
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

    /** Il punto a [km] dall'inizio della linea, seguendola. */
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
