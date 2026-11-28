package io.github.nanunic9379.rideplan.route

import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.Stop
import io.github.nanunic9379.rideplan.ride.distanceKm
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LongRouteTest {
    @Test
    fun splitAddsAHiddenPassThroughAndKeepsOriginalStopsInOrder() {
        val stops = listOf(
            Stop("Lecce", LatLon(40.35, 18.17)),
            Stop("Bari", LatLon(41.12, 16.87)),
            Stop("Rome", LatLon(41.90, 12.50)),
        )

        val split = assertNotNull(LongRoute.split(stops))

        assertEquals(stops, split.left.dropLast(1) + split.right.drop(1))
        assertEquals(split.junction, split.left.last().point)
        assertEquals(split.junction, split.right.first().point)
        assertEquals(Stop.Kind.VIA, split.left.last().kind)
        assertEquals(Stop.Kind.VIA, split.right.first().kind)
        assertTrue(distanceKm(stops[1].point, split.junction) > 1.0)
    }

    @Test
    fun splitUsesTheGreatCircleAcrossTheDateLine() {
        val stops = listOf(Stop(null, LatLon(10.0, 170.0)), Stop(null, LatLon(10.0, -170.0)))

        val split = assertNotNull(LongRoute.split(stops))

        assertTrue(kotlin.math.abs(split.junction.lon) > 179.0)
        assertTrue(distanceKm(stops.first().point, split.junction) < 1_200.0)
        assertTrue(distanceKm(split.junction, stops.last().point) < 1_200.0)
    }

    @Test
    fun coincidentStopsCannotBeSplit() {
        val point = LatLon(41.0, 12.0)
        assertNull(LongRoute.split(listOf(Stop(null, point), Stop(null, point))))
    }

    @Test
    fun engineDistanceLimitRetriesAndJoinsLongRideWithoutLosingRealStops() {
        val stops = listOf(
            Stop("Start", LatLon(0.0, 0.0)),
            Stop("Coffee", LatLon(0.0, 3.0)),
            Stop("Fuel", LatLon(0.0, 7.0)),
            Stop("Finish", LatLon(0.0, 10.0)),
        )
        val attemptedKm = mutableListOf<Double>()
        val successfulKm = mutableListOf<Double>()
        val successfulHeadings = mutableListOf<Double?>()

        val result = LongRoute.compute(
            stops = stops,
            isDistanceLimit = { it is PathTooLong },
            startHeading = 90.0,
        ) { piece, heading ->
            val km = piece.zipWithNext { a, b -> distanceKm(a.point, b.point) }.sum()
            attemptedKm += km
            if (km > 500.0) throw PathTooLong()
            successfulKm += km
            successfulHeadings += heading
            ValhallaRoute.Result(
                shape = piece.map { it.point },
                km = km,
                seconds = km * 60,
                maneuvers = listOf(
                    Maneuver(1, "start", 0, 0),
                    Maneuver(4, "arrive", piece.lastIndex, piece.lastIndex),
                ),
            )
        }

        assertTrue(attemptedKm.size > 2, "the long ride should be retried as shorter pieces")
        assertTrue(successfulKm.all { it <= 500.0 })
        assertEquals(90.0, successfulHeadings.first())
        assertTrue(successfulHeadings.drop(1).all { it == null })
        var previous = -1
        stops.forEach { stop ->
            val index = result.shape.indexOf(stop.point)
            assertTrue(index > previous, "${stop.name} should remain in the joined route")
            previous = index
        }
        assertEquals(1, result.maneuvers.count { it.type in 1..3 })
        assertEquals(1, result.maneuvers.count { it.isArrival })
        assertEquals(successfulKm.sum().roundToInt().toDouble(), result.km.roundToInt().toDouble())
    }

    private class PathTooLong : Exception("ValhallaError(code=154, Path distance exceeds the max distance limit: 500000 meters)")
}
