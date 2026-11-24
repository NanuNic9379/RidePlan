package io.github.nanunic9379.rideplan.profile

import io.github.nanunic9379.rideplan.ride.DayCap
import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.Ride
import io.github.nanunic9379.rideplan.ride.Stop
import io.github.nanunic9379.rideplan.ride.StopPlan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A saved stop carries both the overnight flag (#12) and the stop plan (#10). */
class SavedStopFieldsTest {
    private val plan = StopPlan(StopPlan.Purpose.LUNCH, time = 12 * 60 + 30, pauseMinutes = 45, note = "Trattoria")
    private val ride = Ride(
        "Two days",
        emptyList(),
        listOf(
            Stop("Matera", LatLon(40.66665, 16.6043)),
            Stop("Grassano", LatLon(40.63, 16.28), Stop.Kind.STOP, plan = plan),
            Stop("Potenza", LatLon(40.64005, 15.80507), Stop.Kind.STOP, overnight = true, plan = plan),
            Stop("Salerno", LatLon(40.68, 14.77)),
        ),
        Ride.Source.PLANNED,
        dayCap = DayCap(DayCap.Unit.KM, 250.0),
    )

    @Test
    fun overnightAndPlanSurviveSaveAndFile() {
        val saved = SavedRide.from(ride, "r1", 100)
        val data = ProfileData.fresh(0).copy(rides = listOf(saved))
        val back = ProfileStore.decode(ProfileStore.encode(data)).rides.single().toRide()
        assertEquals(ride.stops, back.stops)
        assertEquals(ride.dayCap, back.dayCap)
        assertTrue(back.stops[2].overnight)
        assertEquals(plan, back.stops[2].plan?.let { StopPlan(it.purpose, it.time, it.pauseMinutes, it.note) })
    }

    @Test
    fun oldStopWithoutNewFieldsStillOpens() {
        val old = ProfileStore.encode(ProfileData.fresh(0).copy(rides = listOf(SavedRide.from(ride, "r1", 100))))
            .replace(Regex(""","(overnight|purpose|time|pause|note)":[^,}]*"""), "")
        val stop = ProfileStore.decode(old).rides.single().toRide().stops[2]
        assertFalse(stop.overnight)
        assertNull(stop.plan)
    }

    @Test
    fun dayCapChangeIsANewVersionOfTheRoute() {
        val a = SavedRide.from(ride, "r1", 100)
        val b = SavedRide.from(ride.copy(dayCap = DayCap(DayCap.Unit.HOURS, 5.0)), "r1", 100)
        assertTrue(a.sameRouteAs(a))
        assertFalse(a.sameRouteAs(b))
    }
}
