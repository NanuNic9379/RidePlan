package io.github.nanunic9379.rideplan.ride

import io.github.nanunic9379.rideplan.profile.ProfileData
import io.github.nanunic9379.rideplan.profile.ProfileStore
import io.github.nanunic9379.rideplan.profile.SavedRide
import io.github.nanunic9379.rideplan.route.RouteInfo
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PrivateZonesTest {
    // A line heading north, one point every ~111 m: 0.001° of latitude.
    private val line = (0..100).map { LatLon(40.0 + it * 0.001, 16.0) }
    private val home = line.first()
    private val garage = line.last()
    private val homeZone = PrivateZone("h", "Home", home.lat, home.lon, 500)

    private fun near(expectedKm: Double, actualKm: Double, tol: Double = 0.01) =
        assertTrue(abs(expectedKm - actualKm) < tol, "$actualKm instead of $expectedKm")

    private fun leaks(ride: Ride, zone: PrivateZone) =
        ride.track.any(zone::contains) || ride.stops.any { zone.contains(it.point) }

    @Test
    fun noZonesNothingChanges() {
        val ride = Ride("r", line, listOf(Stop("Home", home), Stop("Garage", garage)), Ride.Source.GPX)
        assertSame(ride, PrivateZones.apply(ride, emptyList()))
        val far = PrivateZone("f", "Far", 45.0, 9.0, 1000)
        assertSame(ride, PrivateZones.apply(ride, listOf(far)))
    }

    @Test
    fun trackStartsAtHome() {
        val ride = Ride("r", line, listOf(Stop("Home", home), Stop("Bar", line[50]), Stop("Garage", garage)), Ride.Source.GPX)
        val out = PrivateZones.apply(ride, listOf(homeZone))
        assertFalse(leaks(out, homeZone))
        // The first point kept is the first one outside the 500 m circle.
        assertTrue(distanceKm(home, out.track.first()) > 0.5)
        near(0.5, distanceKm(home, out.track.first()), 0.12)
        assertEquals(listOf(null, "Bar", "Garage"), out.stops.map { it.name })
        assertEquals(out.track.first(), out.stops.first().point)
        assertEquals(garage, out.track.last())
    }

    @Test
    fun startAndFinishInDifferentZones() {
        val garageZone = PrivateZone("g", "Garage", garage.lat, garage.lon, 1000)
        val ride = Ride("r", line, listOf(Stop("Home", home), Stop("Garage", garage)), Ride.Source.GPX)
        val out = PrivateZones.apply(ride, listOf(homeZone, garageZone))
        assertFalse(leaks(out, homeZone))
        assertFalse(leaks(out, garageZone))
        assertEquals(listOf<String?>(null, null), out.stops.map { it.name })
        assertEquals(out.track.last(), out.stops.last().point)
    }

    @Test
    fun middleStopInsideZoneDisappears() {
        val office = PrivateZone("o", "Office", line[50].lat, line[50].lon, 300)
        val ride = Ride("r", line, listOf(Stop("A", home), Stop("Office", line[50]), Stop("B", garage)), Ride.Source.GPX)
        val out = PrivateZones.apply(ride, listOf(office))
        assertEquals(listOf("A", "B"), out.stops.map { it.name })
        assertFalse(leaks(out, office))
        assertEquals(home, out.track.first())
    }

    @Test
    fun stopsOnlySnapsToNextStop() {
        val stops = listOf(Stop("Home", home), Stop("Bar", line[20]), Stop("Lake", garage))
        val ride = Ride(null, emptyList(), stops, Ride.Source.GOOGLE_LINK)
        val out = PrivateZones.apply(ride, listOf(homeZone))
        assertTrue(out.straightLines)
        // The snapped start lands on the bar: only the bar remains, with its name.
        assertEquals(listOf("Bar", "Lake"), out.stops.map { it.name })
    }

    @Test
    fun calculatedRouteGoesOutAsTrack() {
        val ride = Ride("r", line, listOf(Stop("Home", home), Stop("Lake", garage)), Ride.Source.GOOGLE_LINK, route = RouteInfo(11.0, 900.0, "it", "2026-01-01", "test"))
        val out = PrivateZones.apply(ride, listOf(homeZone))
        assertNull(out.route)
        assertFalse(out.straightLines)
        assertFalse(leaks(out, homeZone))
        // The link carries the cut track, not the stops to recalculate from home.
        val shared = RideLink.decode(RideLink.encode(out)).ride
        assertFalse(leaks(shared, homeZone))
    }

    @Test
    fun gpxNeverContainsHome() {
        val ride = Ride("r", line, listOf(Stop("Home", home), Stop("Garage", garage)), Ride.Source.GPX)
        val gpx = GpxExport.write(SharePreview.of(ride, listOf(homeZone), trimEnds = false).ride, "x")
        val back = RideImport.parse(gpx)
        assertFalse(leaks(back, homeZone))
        assertFalse(gpx.contains("Home"))
        assertFalse(gpx.contains("<time"))
    }

    @Test
    fun wholeRideInsideZone() {
        val short = Ride("r", line.take(3), listOf(Stop("Home", home), Stop("Corner", line[2])), Ride.Source.GPX)
        val preview = SharePreview.of(short, listOf(homeZone), trimEnds = true)
        assertTrue(preview.empty)
        assertTrue(preview.ride.stops.isEmpty())
    }

    @Test
    fun previewNumbers() {
        val ride = Ride("r", line, listOf(Stop("Home", home), Stop("Bar", line[2]), Stop("Garage", garage)), Ride.Source.GPX)
        val p = SharePreview.of(ride, listOf(homeZone), trimEnds = false)
        assertFalse(p.empty)
        assertEquals(1, p.hiddenStops)
        assertTrue(p.startMovedKm > 0.5)
        assertEquals(0.0, p.finishMovedKm)
        near(ride.lengthKm - p.startMovedKm, p.km)
    }

    @Test
    fun zonesAndTrimmedEnds() {
        val ride = Ride("r", line, listOf(Stop("Home", home), Stop("Garage", garage)), Ride.Source.GPX)
        val p = SharePreview.of(ride, listOf(homeZone), trimEnds = true)
        // The km are trimmed after the zone: the start is at least 1.5 km from home.
        assertTrue(p.startMovedKm > 1.5)
        near(1.0, p.finishMovedKm)
        val off = SharePreview.of(ride, listOf(homeZone), trimEnds = false, hideZones = false)
        assertSame(ride, off.ride)
    }

    @Test
    fun zoneCenterMovesButCoversThePlace() {
        val random = Random(42)
        repeat(200) {
            val z = PrivateZone.around(home, " Home ", 500, "id$it", random)
            val d = distanceKm(home, z.center) * 1000
            assertTrue(d <= 250.0 + 0.5, "moved $d m")
            assertTrue(z.contains(home))
            assertEquals("Home", z.name)
        }
        val moved = (0 until 20).map { PrivateZone.around(home, "", 1000, "x", random).center }.distinct()
        assertTrue(moved.size > 1, "The center must be random")
    }

    @Test
    fun moveIsAccurate() {
        val north = PrivateZone.move(home, 1000.0, 0.0)
        near(1.0, distanceKm(home, north), 0.001)
        assertTrue(north.lat > home.lat)
        val east = PrivateZone.move(home, 1000.0, kotlin.math.PI / 2)
        near(1.0, distanceKm(home, east), 0.001)
        assertTrue(east.lon > home.lon)
    }

    @Test
    fun candidatesFromSavedRides() {
        val a = SavedRide.from(Ride("A", line, emptyList(), Ride.Source.GPX), "a", 1)
        val b = SavedRide.from(Ride("B", listOf(LatLon(40.0005, 16.0), LatLon(41.0, 16.0)), emptyList(), Ride.Source.GPX), "b", 2)
        val c = PrivateZones.candidates(listOf(a, b), emptyList())
        // Home is where both start: first, counted twice.
        assertEquals(2, c.first().rides)
        assertEquals("A", c.first().rideName)
        assertEquals(3, c.size)
        val covered = PrivateZones.candidates(listOf(a, b), listOf(homeZone))
        assertTrue(covered.none { homeZone.contains(it.point) })
        assertEquals(2, covered.size)
    }

    @Test
    fun parseCoordinates() {
        assertEquals(LatLon(40.6667, 16.6043), PrivateZones.parseLatLon("40.6667, 16.6043"))
        assertEquals(LatLon(40.6667, 16.6043), PrivateZones.parseLatLon(" 40.6667 16.6043 "))
        assertEquals(LatLon(-33.8688, 151.2093), PrivateZones.parseLatLon("geo:-33.8688,151.2093?z=15"))
        assertNull(PrivateZones.parseLatLon("Matera"))
        assertNull(PrivateZones.parseLatLon("95.0, 16.0"))
        assertNull(PrivateZones.parseLatLon("40.0, 190.0"))
    }

    @Test
    fun zonesSavedAndRestored() {
        var disk: String? = null
        val store = ProfileStore(read = { disk }, write = { disk = it }, now = { 1 })
        store.update { it.addZone(homeZone) }
        val reopened = ProfileStore(read = { disk }, write = { disk = it }, now = { 2 })
        assertEquals(listOf(homeZone), reopened.data.privateZones)

        val other = ProfileData.fresh(3).addZone(homeZone).addZone(homeZone.copy(id = "o", name = "Office"))
        val (merged, _) = reopened.data.merge(other)
        assertEquals(listOf("h", "o"), merged.privateZones.map { it.id })
        assertEquals(listOf("o"), merged.deleteZone("h").privateZones.map { it.id })

        // Old profiles without zones still open.
        val old = ProfileStore.decode("""{"version":1,"profile":{"id":"x","createdAt":1}}""")
        assertNotNull(old)
        assertTrue(old.privateZones.isEmpty())
    }
}
