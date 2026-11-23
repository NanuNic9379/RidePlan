package io.github.nanunic9379.rideplan.ride

import io.github.nanunic9379.rideplan.route.RouteInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StopEditsTest {
    private val a = Stop("Start", LatLon(45.0, 10.0))
    private val b = Stop("Bar", LatLon(45.0, 10.5))
    private val c = Stop("Finish", LatLon(45.0, 11.0))

    private fun stopsOnly(vararg stops: Stop) = Ride("Ride", emptyList(), stops.toList(), Ride.Source.GOOGLE_LINK)

    private fun routed(vararg stops: Stop) = stopsOnly(*stops).copy(
        track = listOf(LatLon(45.0, 10.0), LatLon(45.1, 10.4), LatLon(45.0, 11.0)),
        route = RouteInfo(km = 90.0, seconds = 5400.0, regionId = "cells", osmDate = "2026-11-01", engine = "test"),
    )

    @Test
    fun addedStopGoesIntoTheCheapestGap() {
        val ride = stopsOnly(a, b, c)
        val near = ride.withStopAdded(LatLon(45.01, 10.8))
        assertEquals(listOf("Start", "Bar", null, "Finish"), near.stops.map { it.name })
        assertEquals(Stop.Kind.VIA, near.stops[2].kind)
        val early = ride.withStopAdded(LatLon(44.99, 10.2))
        assertEquals(LatLon(44.99, 10.2), early.stops[1].point)
    }

    @Test
    fun startAndFinishStayWhenAddingOutside() {
        val ride = stopsOnly(a, c)
        val added = ride.withStopAdded(LatLon(45.0, 12.0))
        assertEquals(a, added.stops.first())
        assertEquals(c, added.stops.last())
        assertEquals(3, added.stops.size)
    }

    @Test
    fun everyEditDropsTheOldRoad() {
        val ride = routed(a, b, c)
        listOf(
            ride.withStopAdded(LatLon(45.2, 10.3)),
            ride.withStopMoved(1, LatLon(45.2, 10.5)),
            ride.withStopRemoved(1),
            ride.withStopReordered(0, 2),
        ).forEach {
            assertNull(it.route)
            assertTrue(it.straightLines)
            assertTrue(it.needsRoute)
        }
    }

    @Test
    fun movedStopBecomesAnUnnamedPassThrough() {
        val moved = stopsOnly(a, b, c).withStopMoved(1, LatLon(45.3, 10.6))
        assertEquals(Stop(null, LatLon(45.3, 10.6), Stop.Kind.VIA), moved.stops[1])
        assertEquals(listOf(a, c), listOf(moved.stops[0], moved.stops[2]))
    }

    @Test
    fun movingToTheSamePlaceChangesNothing() {
        val ride = routed(a, b, c)
        assertSame(ride, ride.withStopMoved(1, b.point))
        assertSame(ride, ride.withStopMoved(7, LatLon(1.0, 1.0)))
    }

    @Test
    fun startAndFinishCannotBeRemoved() {
        val two = stopsOnly(a, c)
        assertFalse(two.canRemoveStop)
        assertSame(two, two.withStopRemoved(0))
        val three = stopsOnly(a, b, c)
        assertTrue(three.canRemoveStop)
        assertEquals(listOf(a, c), three.withStopRemoved(1).stops)
        assertEquals(listOf(b, c), three.withStopRemoved(0).stops)
    }

    @Test
    fun reorderMovesOneStop() {
        val ride = stopsOnly(a, b, c)
        assertEquals(listOf(b, a, c), ride.withStopReordered(0, 1).stops)
        assertEquals(listOf(a, c, b), ride.withStopReordered(1, 2).stops)
        assertEquals(listOf(c, a, b), ride.withStopReordered(2, 0).stops)
        assertSame(ride, ride.withStopReordered(1, 1))
        assertSame(ride, ride.withStopReordered(1, 3))
    }

    @Test
    fun gpxTracksAreNotEditable() {
        val gpx = Ride("Track", listOf(a.point, b.point, c.point), listOf(a, c), Ride.Source.GPX)
        assertFalse(gpx.stopsEditable)
        assertSame(gpx, gpx.withStopAdded(LatLon(45.1, 10.5)))
        assertSame(gpx, gpx.withStopMoved(0, LatLon(45.1, 10.5)))
        assertSame(gpx, gpx.withStopReordered(0, 1))
        assertTrue(routed(a, c).stopsEditable)
        assertTrue(stopsOnly(a, c).stopsEditable)
    }

    @Test
    fun insertIndexWithFewStops() {
        assertEquals(0, bestInsertIndex(emptyList(), LatLon(1.0, 1.0)))
        assertEquals(1, bestInsertIndex(listOf(LatLon(0.0, 0.0)), LatLon(1.0, 1.0)))
        assertEquals(1, bestInsertIndex(listOf(LatLon(0.0, 0.0), LatLon(0.0, 2.0)), LatLon(5.0, 5.0)))
    }

    @Test
    fun mapPositionsAreWrappedIntoRange() {
        assertEquals(LatLon(45.0, 10.0), mapLatLon(45.0, 10.0))
        assertEquals(10.0, mapLatLon(45.0, 370.0)!!.lon, 1e-9)
        assertEquals(170.0, mapLatLon(45.0, -190.0)!!.lon, 1e-9)
        assertEquals(90.0, mapLatLon(91.0, 0.0)!!.lat)
        assertNull(mapLatLon(Double.NaN, 0.0))
        assertNull(mapLatLon(0.0, Double.POSITIVE_INFINITY))
    }
}
