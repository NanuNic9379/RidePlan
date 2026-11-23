package io.github.nanunic9379.rideplan.ride

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RideScreenStateTest {
    private fun ride(source: Ride.Source) =
        Ride("Ride", emptyList(), listOf(Stop("A", LatLon(40.0, 15.0)), Stop("B", LatLon(40.5, 16.0))), source)

    /** #48: a ride received by link is not saved by itself; the others are. */
    @Test
    fun previewOnlyForReceivedRides() {
        val s = RideScreenState()
        s.show(Result.success(ride(Ride.Source.RIDEPLAN_LINK)))
        assertTrue(s.isPreview)
        s.savedId = "abc"
        assertFalse(s.isPreview, "after saving it is no longer a preview")
        s.show(Result.success(ride(Ride.Source.GPX)))
        assertFalse(s.isPreview)
        s.show(Result.success(ride(Ride.Source.GOOGLE_LINK)))
        assertFalse(s.isPreview)
    }
}
