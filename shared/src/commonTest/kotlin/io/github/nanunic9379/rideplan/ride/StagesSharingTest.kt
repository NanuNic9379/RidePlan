package io.github.nanunic9379.rideplan.ride

import io.github.nanunic9379.rideplan.route.Curves
import io.github.nanunic9379.rideplan.route.Polyline
import io.github.nanunic9379.rideplan.route.RouteInfo
import io.github.nanunic9379.rideplan.route.RouteOptions
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** #12: the daily cap and the overnights travel in the link, the QR and the GPX; day ends on the map. */
class StagesSharingTest {
    /** A point [km] east of (0, 10) along the equator. */
    private fun at(km: Double) = LatLon(0.0, 10.0 + km / 111.19508)

    private val stops = listOf(
        Stop("Matera", at(0.0)),
        Stop("Bar", at(150.0)),
        Stop("Hotel Sila", at(300.0), overnight = true),
        Stop(null, at(420.0), Stop.Kind.VIA),
        Stop("Tropea", at(600.0)),
    )

    private val trip = Ride("Calabria", emptyList(), stops, Ride.Source.PLANNED, dayCap = DayCap(DayCap.Unit.KM, 250.0))

    private fun fields(link: String): List<String> =
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(link.substringAfter('#')).decodeToString().split('\n')

    private fun linkOf(fields: List<String>) =
        RideLink.PREFIX + Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(fields.joinToString("\n").encodeToByteArray())

    // ---- Link and QR ----

    @Test
    fun linkCarriesOvernightsAndCap() {
        val back = RideLink.decode(RideLink.encode(trip, RouteOptions(Curves.MORE))).ride
        assertEquals(listOf(false, false, true, false, false), back.stops.map { it.overnight })
        assertEquals(DayCap(DayCap.Unit.KM, 250.0), back.dayCap)
        assertEquals(stops.map { it.name to it.kind }, back.stops.map { it.name to it.kind })
        // 5 decimals in the link: about a metre.
        assertTrue(stops.zip(back.stops).all { (a, b) -> distanceKm(a.point, b.point) < 0.002 })
        // The receiver gets the same days as the sender.
        val sent = Stages.split(trip)
        val got = Stages.split(back)
        assertEquals(sent.map { it.endStop }, got.map { it.endStop })
        assertTrue(sent.zip(got).all { (a, b) -> kotlin.math.abs(a.endKm - b.endKm) < 0.01 })
    }

    @Test
    fun capInHoursWithDecimals() {
        for (cap in listOf(DayCap(DayCap.Unit.HOURS, 6.5), DayCap(DayCap.Unit.HOURS, 1.0), DayCap(DayCap.Unit.KM, 201.168))) {
            val back = RideLink.decode(RideLink.encode(trip.copy(dayCap = cap))).ride.dayCap!!
            assertEquals(cap.unit, back.unit)
            assertTrue(kotlin.math.abs(cap.value - back.value) < 0.01, "$cap -> $back")
        }
        assertEquals("H6.5", RideLink.dayCapCode(DayCap(DayCap.Unit.HOURS, 6.5)))
        assertEquals("K300", RideLink.dayCapCode(DayCap(DayCap.Unit.KM, 300.0)))
        assertEquals("", RideLink.dayCapCode(DayCap(DayCap.Unit.KM, 0.0)))
    }

    @Test
    fun brokenCapsAreIgnored() {
        for (code in listOf("", "X300", "K", "Kabc", "K-5", "K0", "KNaN", "KInfinity", "H99999")) {
            assertNull(RideLink.parseDayCap(code), code)
        }
    }

    /** Old app versions read only the first nine fields: they must be exactly those of before #12. */
    @Test
    fun oldAppsStillOpenNewLinks() {
        val plain = trip.copy(dayCap = null, stops = stops.map { it.copy(overnight = false) })
        val new = fields(RideLink.encode(trip, RouteOptions(Curves.MORE)))
        val before = fields(RideLink.encode(plain, RouteOptions(Curves.MORE)))
        assertEquals(11, new.size)
        assertEquals(before + List(9 - before.size) { "" }, new.take(9))
        // What an old version checks: at least six fields and the first one at "1".
        assertTrue(new.size >= 6 && new[0] == "1")
        // The web page reads the stops from field 3 and the names from field 4.
        assertEquals(stops.size, Polyline.decode(new[3], 5).size)
        assertEquals(listOf("Matera", "Bar", "Hotel Sila", "", "Tropea"), new[4].split('\u001f'))
    }

    /** A link made before #12 (nine fields) opens with no overnights and no cap. */
    @Test
    fun linksFromBefore12() {
        val old = listOf("1", "Old", "MAX", Polyline.encode(stops.map { it.point }, 5), "A\u001fB\u001fC\u001fD\u001fE", "", "SSSVS", "", "")
        val back = RideLink.decode(linkOf(old)).ride
        assertTrue(back.stops.none { it.overnight })
        assertNull(back.dayCap)
        assertFalse(Stages.active(back))
    }

    /** Without anything to say, the link does not grow: empty optional fields are left out. */
    @Test
    fun plainRideLinkStaysShort() {
        val plain = Ride(null, emptyList(), listOf(Stop("A", at(0.0)), Stop("B", at(50.0))), Ride.Source.GOOGLE_LINK)
        val f = fields(RideLink.encode(plain))
        assertEquals(7, f.size, f.toString()) // the six required fields and the kinds
        assertEquals("SS", f[6])
        val minimal = Ride(null, listOf(at(0.0), at(10.0)), emptyList(), Ride.Source.GPX)
        assertEquals(6, fields(RideLink.encode(minimal)).size)
        assertEquals(minimal.track.size, RideLink.decode(RideLink.encode(minimal)).ride.track.size)
    }

    /** Only intermediate stops can be overnights, whatever the link says. */
    @Test
    fun overnightOnStartOrFinishIsIgnored() {
        val f = fields(RideLink.encode(trip)).toMutableList()
        f[9] = "11111"
        val back = RideLink.decode(linkOf(f)).ride
        assertEquals(listOf(false, true, true, true, false), back.stops.map { it.overnight })
        assertEquals("001", RideLink.overnightsCode(stops))
        assertEquals("", RideLink.overnightsCode(stops.map { it.copy(overnight = false) }))
        assertEquals("", RideLink.overnightsCode(listOf(stops[0].copy(overnight = true), stops[4].copy(overnight = true))))
    }

    // ---- GPX ----

    @Test
    fun gpxCarriesOvernightsAndCap() {
        val gpx = GpxExport.write(trip, "x")
        assertTrue("""<rideplan:days cap="K250"/>""" in gpx, gpx)
        assertTrue("""overnight="1"""" in gpx)
        val back = RideImport.parse(gpx)
        assertEquals(listOf(false, false, true, false, false), back.stops.map { it.overnight })
        assertEquals(DayCap(DayCap.Unit.KM, 250.0), back.dayCap)
    }

    @Test
    fun gpxWithTrackKeepsAnUnnamedOvernightAsARealStop() {
        val track = (0..60).map { at(it * 10.0) }
        val ride = trip.copy(track = track, stops = stops.mapIndexed { i, s -> if (i == 2) s.copy(name = null) else s })
        val back = RideImport.parse(GpxExport.write(ride, "x"))
        assertEquals(Stop.Kind.STOP, back.stops[2].kind)
        assertTrue(back.stops[2].overnight)
        assertEquals(Stages.split(ride).size, Stages.split(back).size)
    }

    /** An overnight with a plan: both in the same extension. */
    @Test
    fun gpxOvernightWithPlan() {
        val plan = StopPlan(purpose = StopPlan.Purpose.entries.first(), time = 18 * 60, pauseMinutes = 0, note = "Dinner")
        val ride = trip.copy(stops = stops.mapIndexed { i, s -> if (i == 2) s.copy(plan = plan) else s })
        val gpx = GpxExport.write(ride, "x")
        assertTrue("""<rideplan:stop time="18:00" overnight="1"/>""" in gpx, gpx)
        val back = RideImport.parse(gpx)
        assertTrue(back.stops[2].overnight)
        assertEquals(plan.normalized(), back.stops[2].plan)
    }

    /** The cap and the version share the metadata extensions. */
    @Test
    fun gpxCapAndVersion() {
        val ride = trip.copy(dayCap = DayCap(DayCap.Unit.HOURS, 7.5), version = RideVersion("abc", 3, "new hotel", 1_700_000_000_000))
        val back = RideImport.parse(GpxExport.write(ride, "x"))
        assertEquals(DayCap(DayCap.Unit.HOURS, 7.5), back.dayCap)
        assertEquals(3, back.version?.number)
        assertEquals("new hotel", back.version?.note)
    }

    /** GPX files from other apps, or from before #12: nothing changes. */
    @Test
    fun gpxWithoutStages() {
        val plain = trip.copy(dayCap = null, stops = stops.map { it.copy(overnight = false) })
        val gpx = GpxExport.write(plain, "x")
        assertFalse("overnight" in gpx)
        assertFalse("rideplan:days" in gpx)
        val back = RideImport.parse(gpx)
        assertNull(back.dayCap)
        assertTrue(back.stops.none { it.overnight })
    }

    // ---- Day ends on the map ----

    @Test
    fun dayEndsOnTheMap() {
        val track = (0..60).map { at(it * 10.0) }
        val ride = trip.copy(track = track, route = RouteInfo(km = 600.0, seconds = 600.0 * 72, regionId = "r", osmDate = "d", engine = "t"))
        val days = Stages.split(ride)
        val ends = dayEnds(ride)
        assertEquals(days.size - 1, ends.size)
        assertEquals((1 until days.size).toList(), ends.map { it.first })
        // 300 km to the hotel with a 250 km cap: two days of 150 km, the first night at the bar.
        assertEquals(stops[1].point, ends[0].second)
        assertEquals(stops[2].point, ends[1].second)
        // The finish has its own marker already.
        assertFalse(ends.any { it.second == stops.last().point })
    }

    @Test
    fun noDayEndsForADayTrip() {
        val plain = trip.copy(dayCap = null, stops = stops.map { it.copy(overnight = false) })
        assertTrue(dayEnds(plain).isEmpty())
        assertEquals(EMPTY_FEATURES, dayEndsJson(dayEnds(plain)))
        // A cap the trip never reaches: one day, no marker.
        assertTrue(dayEnds(trip.copy(dayCap = DayCap(DayCap.Unit.KM, 1000.0), stops = stops.map { it.copy(overnight = false) })).isEmpty())
    }

    /** Before the road is known only the nights at a stop are shown: "on the road" points would float on straight lines. */
    @Test
    fun withoutRoadOnlyNightsAtStops() {
        val ride = trip.copy(dayCap = DayCap(DayCap.Unit.KM, 100.0), stops = stops.map { it.copy(kind = Stop.Kind.VIA) }.mapIndexed { i, s -> if (i == 2) s.copy(kind = Stop.Kind.STOP) else s })
        assertTrue(ride.straightLines)
        val days = Stages.split(ride)
        assertTrue(days.dropLast(1).any { it.endStop == null })
        val ends = dayEnds(ride)
        assertTrue(ends.isNotEmpty())
        val stopPoints = stops.map { it.point }.toSet()
        assertTrue(ends.all { it.second in stopPoints })
    }

    @Test
    fun dayEndsJsonShape() {
        val json = dayEndsJson(listOf(2 to LatLon(40.5, 16.25)))
        assertEquals(
            """{"type":"FeatureCollection","features":[{"type":"Feature","properties":{"day":"2"},"geometry":{"type":"Point","coordinates":[16.25,40.5]}}]}""",
            json,
        )
    }
}
