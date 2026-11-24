package io.github.nanunic9379.rideplan.profile

import io.github.nanunic9379.rideplan.ride.LatLon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StopDistancesTest {
    // About 1.11 km per 0.01 degree of latitude.
    private fun north(n: Int) = (0..n).map { LatLon(45.0 + it * 0.001, 9.0) }

    @Test
    fun startMiddleAndFinishAlongTheTrack() {
        val track = north(100)
        val km = StopDistances.along(track, listOf(track.first(), LatLon(45.05, 9.0), track.last()))
        assertEquals(0.0, km[0])
        assertEquals(5.56, assertNotNull(km[1]), 0.05)
        assertEquals(11.12, assertNotNull(km[2]), 0.05)
    }

    @Test
    fun stopOffTheTrackHasNoDistance() {
        val track = north(100)
        val km = StopDistances.along(track, listOf(track.first(), LatLon(45.05, 9.1), track.last()))
        assertNull(km[1])
        assertNotNull(km[2])
    }

    @Test
    fun noTrackNoDistances() {
        assertEquals(listOf(null, null), StopDistances.along(emptyList(), listOf(LatLon(45.0, 9.0), LatLon(45.1, 9.0))))
    }

    @Test
    fun loopFinishIsTheEndNotTheStart() {
        val out = north(50)
        val track = out + out.reversed().drop(1)
        val km = StopDistances.along(track, listOf(track.first(), out.last(), track.last()))
        assertEquals(0.0, km[0])
        assertEquals(5.56, assertNotNull(km[1]), 0.05)
        assertEquals(11.12, assertNotNull(km[2]), 0.05)
    }

    @Test
    fun outAndBackCountsTheLaterPass() {
        // Out north and back: a stop passed on both legs, after the turn, is on the way back.
        val out = north(50)
        val track = out + out.reversed().drop(1)
        val turn = out.last()
        val back = LatLon(45.02, 9.0)
        val km = StopDistances.along(track, listOf(track.first(), turn, back, track.last()))
        val k = assertNotNull(km[2])
        assertTrue(k > 5.56, "got $k")
        assertEquals(8.9, k, 0.05)
    }
}
