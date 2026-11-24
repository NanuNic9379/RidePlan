package io.github.nanunic9379.rideplan.route

import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.distanceKm
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Which countries a ride crosses (#15), offline, from the coarse outlines in [CountryBorders]
 * (Natural Earth 1:10m, public domain, simplified to about 500 m).
 *
 * The outlines are coarse, so a road that runs along a border can look as if it stepped across.
 * A country counts only when at least [MIN_KM] of the ride falls inside it (less only for rides
 * shorter than that).
 */
object Countries {
    /** Kilometres of ride inside a country before it counts as crossed. */
    const val MIN_KM = 1.0

    /** Spacing of the points checked along the ride. */
    internal const val STEP_KM = 0.5

    private class Ring(val lats: DoubleArray, val lons: DoubleArray) {
        val minLat = lats.min()
        val maxLat = lats.max()
        val minLon = lons.min()
        val maxLon = lons.max()
    }

    private class Outline(val code: String, val rings: List<Ring>) {
        val minLat = rings.minOf { it.minLat }
        val maxLat = rings.maxOf { it.maxLat }
        val minLon = rings.minOf { it.minLon }
        val maxLon = rings.maxOf { it.maxLon }

        /** Even-odd rule over every ring: holes (San Marino inside Italy) work by themselves. */
        fun contains(lat: Double, lon: Double): Boolean {
            if (lat < minLat || lat > maxLat || lon < minLon || lon > maxLon) return false
            var inside = false
            for (r in rings) {
                if (lat < r.minLat || lat > r.maxLat || lon < r.minLon || lon > r.maxLon) continue
                var j = r.lats.lastIndex
                for (i in r.lats.indices) {
                    val yi = r.lats[i]
                    val yj = r.lats[j]
                    if ((yi > lat) != (yj > lat)) {
                        val x = r.lons[i] + (lat - yi) / (yj - yi) * (r.lons[j] - r.lons[i])
                        if (lon < x) inside = !inside
                    }
                    j = i
                }
            }
            return inside
        }
    }

    private val outlines: List<Outline> by lazy {
        CountryBorders.rings.map { (code, rings) ->
            Outline(code, rings.map { ring -> decode(ring, CountryBorders.PRECISION).let { Ring(it.first, it.second) } })
        }
    }

    /** The countries with an outline (ISO 3166 alpha-2). */
    val known: Set<String> get() = CountryBorders.rings.keys

    /** Google encoded polyline with [precision] decimals: latitudes and longitudes. */
    internal fun decode(s: String, precision: Int): Pair<DoubleArray, DoubleArray> {
        var factor = 1.0
        repeat(precision) { factor *= 10 }
        val lats = ArrayList<Double>()
        val lons = ArrayList<Double>()
        var i = 0
        var lat = 0L
        var lon = 0L
        fun next(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                val b = s[i++].code - 63
                result = result or ((b and 0x1F).toLong() shl shift)
                shift += 5
                if (b < 0x20) break
            }
            return if (result and 1L != 0L) (result shr 1).inv() else result shr 1
        }
        while (i < s.length) {
            lat += next()
            lon += next()
            lats += lat / factor
            lons += lon / factor
        }
        return lats.toDoubleArray() to lons.toDoubleArray()
    }

    /** The country of a point, or null outside every known outline (sea, countries not in the table). */
    fun at(p: LatLon): String? = findOutline(p.lat, p.lon, null)?.code

    private fun findOutline(lat: Double, lon: Double, hint: Outline?): Outline? {
        if (hint != null && hint.contains(lat, lon)) return hint
        return outlines.firstOrNull { it !== hint && it.contains(lat, lon) }
    }

    /**
     * Kilometres of [line] inside each country, in the order the ride enters them. Points are
     * checked every [STEP_KM]; long straight segments (rides without a road yet) are split.
     */
    fun kmByCountry(line: List<LatLon>): Map<String, Double> {
        val km = LinkedHashMap<String, Double>()
        if (line.isEmpty()) return km
        if (line.size == 1) {
            at(line[0])?.let { km[it] = 0.0 }
            return km
        }
        // The start point itself: a ride that begins in a country is in it even for a few metres.
        var hint: Outline? = findOutline(line[0].lat, line[0].lon, null)
        hint?.let { km[it.code] = 0.0 }
        var pending = 0.0
        fun count(lat: Double, lon: Double, length: Double) {
            val o = findOutline(lat, lon, hint) ?: return
            hint = o
            km[o.code] = (km[o.code] ?: 0.0) + length
        }
        for (n in 1..line.lastIndex) {
            val a = line[n - 1]
            val b = line[n]
            val d = distanceKm(a, b)
            val parts = max(1, ceil(d / STEP_KM).toInt())
            for (k in 1..parts) {
                pending += d / parts
                if (pending >= STEP_KM || (k == parts && n == line.lastIndex)) {
                    val t = k.toDouble() / parts
                    count(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t, pending)
                    pending = 0.0
                }
            }
        }
        return km
    }

    /**
     * The countries [line] really crosses, in order: at least [MIN_KM] inside each, or at least
     * half of a ride shorter than 2 × [MIN_KM].
     */
    fun crossed(line: List<LatLon>): List<String> =
        pick(kmByCountry(line), line.firstOrNull()?.let(::at))

    /** The countries of [km] that count (see [crossed]); [start] = country of the first point. */
    internal fun pick(km: Map<String, Double>, start: String?): List<String> {
        if (km.isEmpty()) return emptyList()
        val total = km.values.sum()
        val threshold = min(MIN_KM, total / 2)
        val crossed = km.filterValues { it >= threshold && (it > 0.0 || total == 0.0) }.keys.toList()
        // A very short ride all near a border: still name the country it starts in.
        return crossed.ifEmpty { listOfNotNull(start) }
    }
}
