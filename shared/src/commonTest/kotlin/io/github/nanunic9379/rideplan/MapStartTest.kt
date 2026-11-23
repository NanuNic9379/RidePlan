package io.github.nanunic9379.rideplan

import io.github.nanunic9379.rideplan.profile.SavedRide
import io.github.nanunic9379.rideplan.profile.SavedStop
import kotlin.test.Test
import kotlin.test.assertEquals

class MapStartTest {
    private fun ride(savedAt: Long, lat: Double, lon: Double) = SavedRide(
        id = "r$savedAt", name = null, savedAt = savedAt, km = 10.0, source = "GPX",
        stops = listOf(SavedStop(null, lat, lon)),
    )

    @Test
    fun lastViewedAreaWins() {
        val last = MapView(45.1, 7.6, 11.0)
        assertEquals(last, MapStart.of(last, listOf(ride(1, 40.0, 16.0))))
    }

    @Test
    fun withoutAViewTheMostRecentRideIsUsed() {
        val start = MapStart.of(null, listOf(ride(1, 45.0, 9.0), ride(5, 40.6, 16.6)))
        assertEquals(MapView(40.6, 16.6, MapStart.RIDE_ZOOM), start)
    }

    @Test
    fun aRideWithOnlyATrackUsesItsFirstPoint() {
        val r = ride(1, 0.0, 0.0).copy(stops = emptyList(), track = listOf(44.0, 11.0, 44.1, 11.1))
        assertEquals(MapView(44.0, 11.0, MapStart.RIDE_ZOOM), MapStart.of(null, listOf(r)))
    }

    @Test
    fun aBrokenViewIsIgnored() {
        val start = MapStart.of(MapView(Double.NaN, 500.0, 3.0), emptyList())
        assertEquals(MapView(MapDefaults.START_LAT, MapDefaults.START_LON, MapDefaults.START_ZOOM), start)
    }

    @Test
    fun aNewPhoneGetsTheOverview() {
        assertEquals(MapView(MapDefaults.START_LAT, MapDefaults.START_LON, MapDefaults.START_ZOOM), MapStart.of(null, emptyList()))
    }
}
