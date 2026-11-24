package io.github.nanunic9379.rideplan.route

import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.Stop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoverageTest {
    /** Two 1-degree cells around Turin (7-9 E, 45-46 N) and their main-road block. */
    private val cells = RoutingCells(
        build = "1",
        osmDate = "2026-11-23T02:19:24Z",
        valhalla = "3.9.2",
        packs = listOf(
            RoutingPack("l0-2000", 0, listOf(4.0, 42.0, 8.0, 46.0), "a.zip", 1, 1, "ff"),
            RoutingPack("l1-48787", 1, listOf(7.0, 45.0, 8.0, 46.0), "b.zip", 1, 1, "ff"),
            RoutingPack("l1-48788", 1, listOf(8.0, 45.0, 9.0, 46.0), "c.zip", 1, 1, "ff"),
        ),
    )

    private val turin = Stop("Torino", LatLon(45.07, 7.68))
    private val asti = Stop("Asti", LatLon(45.0, 8.0))
    private val nice = Stop("Nice", LatLon(43.70, 7.26))
    private val unnamed = Stop(null, LatLon(48.85, 2.35))

    @Test
    fun coveredRideHasNoOutsideStops() {
        assertEquals(emptyList(), outsideStops(listOf(turin, asti), cells))
        assertNotNull(cells.needed(listOf(turin.point, asti.point)))
    }

    @Test
    fun namesTheStopsOutsideTheData() {
        val outside = outsideStops(listOf(turin, nice, asti, unnamed), cells)
        // Numbered from 1, like on the map; a stop without a name keeps only its number.
        assertEquals(listOf(OutsideStop(2, "Nice"), OutsideStop(4, null)), outside)
        // Same check as the one that decides whether anything can be downloaded.
        assertNull(cells.needed(listOf(turin.point, nice.point)))
    }

    @Test
    fun blankNameCountsAsNoName() {
        assertEquals(listOf(OutsideStop(1, null)), outsideStops(listOf(Stop("  ", nice.point)), cells))
    }

    @Test
    fun unknownCoverageGivesTheGenericMessage() {
        assertEquals(emptyList(), outsideStops(listOf(nice), null))
        // Only main-road blocks published: no cell to compare with.
        assertEquals(emptyList(), outsideStops(listOf(nice), cells.copy(packs = cells.packs.filter { it.level == 0 })))
    }

    @Test
    fun cellEdgesCount() {
        // A stop exactly on the border between two cells, or on the outer edge, is covered.
        assertTrue(cells.outside(listOf(LatLon(45.5, 8.0), LatLon(46.0, 9.0))).isEmpty())
        assertEquals(listOf(0), cells.outside(listOf(LatLon(45.5, 9.01))))
    }

    @Test
    fun newStatesKeepOldBehaviour() {
        // The generic "not covered" state is still available without stop details.
        assertEquals(RouteState.NotCovered(), RouteState.NotCovered(emptyList()))
    }
}
