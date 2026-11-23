package io.github.nanunic9379.rideplan.ride

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RideLayersTest {
    private val stops = listOf(Stop("A", LatLon(40.0, 15.0)), Stop("B", LatLon(40.5, 16.0)))

    /** While the road is being calculated the stops are not joined by straight lines. */
    @Test
    fun noStraightLinesBeforeTheRoad() {
        val ride = Ride("Ride", emptyList(), stops, Ride.Source.GOOGLE_LINK)
        assertTrue(ride.straightLines)
        assertEquals(EMPTY_FEATURES, roadJson(ride))
    }

    /** Once the road is known, it is what the map draws. */
    @Test
    fun roadIsDrawn() {
        val road = listOf(LatLon(40.0, 15.0), LatLon(40.2, 15.4), LatLon(40.5, 16.0))
        val ride = Ride("Ride", road, stops, Ride.Source.GOOGLE_LINK)
        assertEquals(lineJson(road), roadJson(ride))
        assertTrue("[15.4,40.2]" in roadJson(ride))
    }
}
