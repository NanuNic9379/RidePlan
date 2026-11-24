package io.github.nanunic9379.rideplan.ride

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    /** #2: after an edit on the map the camera stays put, until another ride is shown. */
    @Test
    fun stopsEditedResetsWithANewRide() {
        val s = RideScreenState()
        s.show(Result.success(ride(Ride.Source.GOOGLE_LINK)))
        assertFalse(s.stopsEdited)
        s.stopsEdited = true
        s.show(Result.success(ride(Ride.Source.GPX)))
        assertFalse(s.stopsEdited)
        s.stopsEdited = true
        s.goHome()
        assertFalse(s.stopsEdited)
    }

    /** #33: a plan is not a saved ride until "Done"; back asks only when points would be lost. */
    @Test
    fun planningFromStartToDone() {
        val s = RideScreenState()
        s.page = Page.MAP
        s.startPlan()
        assertTrue(s.onMap)
        assertTrue(s.stopsEdited, "the camera does not jump while tapping")
        assertFalse(s.askBeforeLeavingPlan(), "nothing tapped: leave at once")
        assertNull(s.finishPlan(), "no route without two points")
        s.plan = s.plan!!.withTap(LatLon(45.8, 9.1)).withTap(LatLon(46.0, 9.2)).withName("Lake")
        assertTrue(s.askBeforeLeavingPlan())
        assertTrue(s.planLeaving)
        s.planLeaving = false
        val done = s.finishPlan()!!
        assertEquals("Lake", done.name)
        assertNull(s.plan)
        assertTrue(s.panelOpen)
        assertFalse(s.stopsEdited, "the finished ride is framed")
        assertFalse(s.askBeforeLeavingPlan())
    }

    /** #33: leaving a plan started from the map goes back to the map, not to the rides. */
    @Test
    fun leavingAPlanKeepsTheTab() {
        val s = RideScreenState()
        s.page = Page.MAP
        s.startPlan()
        s.goHome()
        assertEquals(Page.MAP, s.page)
        assertNull(s.plan)
        assertNull(s.ride)
        s.startPlan()
        s.show(Result.success(ride(Ride.Source.GPX)))
        assertNull(s.plan, "an imported ride ends the plan")
    }
}
