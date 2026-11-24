package io.github.nanunic9379.rideplan.profile

import io.github.nanunic9379.rideplan.ride.DayCap
import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.Ride
import io.github.nanunic9379.rideplan.ride.Stop
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Overnights and the daily cap (#12) survive saving, and older files still open. */
class StagesSavedTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val empty = ProfileData(profile = Profile(id = "me", createdAt = 0L))

    private val ride = Ride(
        name = "Alps",
        track = emptyList(),
        stops = listOf(
            Stop("Como", LatLon(45.81, 9.08)),
            Stop("Bormio", LatLon(46.47, 10.37), overnight = true),
            Stop("Merano", LatLon(46.67, 11.16)),
        ),
        source = Ride.Source.PLANNED,
        dayCap = DayCap(DayCap.Unit.HOURS, 5.5),
    )

    @Test
    fun savedAndReopened() {
        val back = SavedRide.from(ride, "x", 1L).toRide()
        assertEquals(listOf(false, true, false), back.stops.map { it.overnight })
        assertEquals(ride.dayCap, back.dayCap)
    }

    @Test
    fun throughTheProfileFile() {
        val (data, id) = empty.save(ride, null, 5L)
        val back = ProfileStore.decode(ProfileStore.encode(data)).rides.first { it.id == id }.toRide()
        assertTrue(back.stops[1].overnight)
        assertEquals(DayCap(DayCap.Unit.HOURS, 5.5), back.dayCap)
    }

    @Test
    fun resavingKeepsThem() {
        val (data, id) = empty.save(ride, null, 5L)
        val (again, _) = data.save(data.rides.first().toRide(), id, 6L)
        val back = again.rides.first { it.id == id }.toRide()
        assertTrue(back.stops[1].overnight)
        assertEquals(ride.dayCap, back.dayCap)
    }

    @Test
    fun olderFilesOpen() {
        val old = """{"id":"a","name":"Old","savedAt":1,"km":12.5,"source":"GPX","stops":[{"name":"A","lat":1.0,"lon":2.0,"kind":"STOP"},{"name":"B","lat":1.5,"lon":2.5}]}"""
        val saved = json.decodeFromString(SavedRide.serializer(), old)
        assertNull(saved.dayCap)
        val r = saved.toRide()
        assertTrue(r.stops.none { it.overnight })
        assertNull(r.dayCap)
    }

    @Test
    fun unknownOrInvalidCapIsDropped() {
        assertNull(SavedDayCap("WEEKS", 2.0).toDayCap())
        assertNull(SavedDayCap("KM", 0.0).toDayCap())
        assertEquals(DayCap(DayCap.Unit.KM, 250.0), SavedDayCap("KM", 250.0).toDayCap())
    }

    @Test
    fun plainRidesWriteNoCap() {
        val saved = SavedRide.from(ride.copy(dayCap = null, stops = ride.stops.map { it.copy(overnight = false) }), "y", 1L)
        assertNull(saved.dayCap)
        assertFalse(saved.stops.any { it.overnight })
    }
}
