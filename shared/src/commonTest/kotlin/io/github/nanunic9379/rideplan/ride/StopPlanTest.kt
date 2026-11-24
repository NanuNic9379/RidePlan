package io.github.nanunic9379.rideplan.ride

import io.github.nanunic9379.rideplan.route.RouteInfo
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StopPlanTest {
    private val meeting = StopPlan(StopPlan.Purpose.MEETING, time = 8 * 60 + 30, pauseMinutes = 15, note = "Bar on the square")
    private val coffee = StopPlan(StopPlan.Purpose.COFFEE, pauseMinutes = 20)

    // About 100 km in a straight line north, stops every ~33 km.
    private val stops = listOf(
        Stop("Bar Roma, Matera", LatLon(40.0, 16.0), plan = meeting),
        Stop(null, LatLon(40.3, 16.0), Stop.Kind.VIA),
        Stop("Caffè Centrale", LatLon(40.6, 16.0), plan = coffee),
        Stop("Potenza", LatLon(40.9, 16.0)),
    )
    private val ride = Ride("Saturday ride", emptyList(), stops, Ride.Source.PLANNED)

    @Test
    fun parseAndFormatTimes() {
        assertEquals(510, StopPlan.parseTime("08:30"))
        assertEquals(510, StopPlan.parseTime("8.30"))
        assertEquals(510, StopPlan.parseTime("830"))
        assertEquals(540, StopPlan.parseTime("9"))
        assertEquals(0, StopPlan.parseTime("00:00"))
        assertNull(StopPlan.parseTime("24:00"))
        assertNull(StopPlan.parseTime("8:75"))
        assertNull(StopPlan.parseTime("soon"))
        assertNull(StopPlan.parseTime(""))
        assertEquals("08:30", StopPlan.timeText(510))
        assertEquals("00:10", StopPlan.timeText(24 * 60 + 10))
    }

    @Test
    fun normalizedPlans() {
        assertNull(StopPlan().normalized())
        assertNull(StopPlan(note = "   ").normalized())
        val p = StopPlan(time = 5000, pauseMinutes = -3, note = "a\nb" + "x".repeat(300)).normalized()
        assertNotNull(p)
        assertNull(p.time)
        assertEquals(0, p.pauseMinutes)
        assertEquals(StopPlan.MAX_NOTE, p.note.length)
        assertFalse('\n' in p.note)
        assertEquals(StopPlan.Purpose.FUEL, StopPlan.Purpose.of(" Fuel "))
        assertNull(StopPlan.Purpose.of("teleport"))
    }

    @Test
    fun timetableFromAMeetingTime() {
        // Two hours riding for three equal legs: 40 minutes each.
        val t = Timetable.of(ride, km = 100.0, seconds = 2 * 3600.0)
        assertFalse(t.roughSpeed)
        val r = t.rows
        assertEquals(510, r[0].arrival)
        assertFalse(r[0].estimated)
        assertEquals(525, r[0].departure) // 15 minutes to gather
        assertEquals(565, r[1].arrival)
        assertTrue(r[1].estimated)
        assertEquals(605, r[2].arrival)
        assertEquals(625, r[2].departure) // coffee, 20 minutes
        assertEquals(665, r[3].arrival)
        assertEquals(665, t.finish)
        assertEquals(35, t.pauseMinutes)
        assertEquals(2 * 3600.0 + 35 * 60, t.totalSeconds, 1e-6)
    }

    @Test
    fun noTimesWithoutAnAnchor() {
        val plain = ride.copy(stops = stops.map { it.copy(plan = null) })
        val t = Timetable.of(plain, km = 100.0, seconds = 7200.0)
        assertFalse(t.hasTimes)
        assertNull(t.finish)
        // A time in the middle starts the clock from there.
        val mid = plain.copy(stops = plain.stops.toMutableList().also { it[2] = it[2].copy(plan = StopPlan(time = 600)) })
        val m = Timetable.of(mid, km = 100.0, seconds = 7200.0)
        assertNull(m.rows[1].arrival)
        assertEquals(600, m.rows[2].arrival)
        assertEquals(640, m.rows[3].arrival)
    }

    @Test
    fun lateArrivalIsFlaggedAndPushesTheRest() {
        // Lunch at 09:00 is impossible after leaving at 08:45 with 80 minutes to ride.
        val lunch = stops.toMutableList().also { it[2] = it[2].copy(plan = StopPlan(StopPlan.Purpose.LUNCH, time = 540, pauseMinutes = 30)) }
        val t = Timetable.of(ride.copy(stops = lunch), km = 100.0, seconds = 7200.0)
        assertTrue(t.rows[2].late)
        assertEquals(540, t.rows[2].arrival)
        // Leaves 30 minutes after the real arrival (10:05), not after the planned time.
        assertEquals(635, t.rows[2].departure)
        assertEquals(675, t.rows[3].arrival)
    }

    @Test
    fun plannedTimeAfterMidnightIsTheNextDay() {
        val night = stops.toMutableList().also {
            it[0] = it[0].copy(plan = StopPlan(time = 23 * 60))
            it[3] = it[3].copy(plan = StopPlan(time = 120))
        }
        val t = Timetable.of(ride.copy(stops = night), km = 100.0, seconds = 7200.0)
        // 23:00 + 2 h riding + the coffee pause: before 02:00, so on time the next day.
        assertEquals(24 * 60 + 120, t.rows[3].arrival)
        assertFalse(t.rows[3].late)
    }

    @Test
    fun roughSpeedWithoutRoute() {
        val t = Timetable.of(ride, km = 100.0, seconds = null)
        assertTrue(t.roughSpeed)
        assertEquals(100.0 / Timetable.ROUGH_KMH * 3600, t.ridingSeconds, 1e-6)
        // The route's own time wins when the ride was computed.
        val routed = ride.copy(route = RouteInfo(km = 120.0, seconds = 5400.0, regionId = "r", osmDate = "d", engine = "e"))
        assertEquals(5400.0, Timetable.of(routed).ridingSeconds, 1e-6)
    }

    @Test
    fun legsFollowTheTrack() {
        // A track that goes far east and back before reaching the second stop: that leg is longer.
        val a = LatLon(40.0, 16.0)
        val b = LatLon(40.1, 16.0)
        val c = LatLon(40.2, 16.0)
        val track = listOf(a, LatLon(40.05, 16.5), b, c)
        val r = Ride(null, track, listOf(Stop("A", a), Stop("B", b), Stop("C", c)), Ride.Source.GPX)
        val legs = Timetable.legKm(r)
        assertEquals(2, legs.size)
        assertTrue(legs[0] > 3 * legs[1], "$legs")
    }

    private val labels = RideSheet.Labels(
        title = { v -> v?.let { "v$it" }.orEmpty() },
        distance = { k -> "${k.toInt()} km" },
        duration = { s -> "${(s / 60).toInt()} min" },
        totals = { r, t -> "riding $r, total $t" },
        purpose = { it.code },
        pause = { "$it min stop" },
        stop = { "Stop $it" },
        privatePlace = "Private place",
        start = "Start",
        finish = "Finish",
        approx = "~",
        late = "(late)",
        updated = { "What changed: $it" },
    )

    @Test
    fun sheetText() {
        val versioned = ride.copy(version = RideVersion("abc", 3, "landslide on the SP12", 0))
        val text = RideSheet.text(versioned, "Saturday ride", labels, km = 100.0, seconds = 7200.0)
        val lines = text.lines()
        assertEquals("Saturday ride · v3", lines[0])
        assertEquals("What changed: landslide on the SP12", lines[1])
        assertEquals("100 km · riding 120 min, total 155 min", lines[2])
        assertTrue("08:30  meeting · Bar Roma (15 min stop)" in lines, text)
        assertTrue("        Bar on the square" in lines, text)
        assertTrue("~10:05  coffee · Caffè Centrale (20 min stop)" in lines, text)
        assertTrue("~11:05  Potenza" in lines, text)
        // The dragged point is not a stop of the group.
        assertFalse("Stop 2" in text)
        // No coordinates in the sheet.
        assertFalse("40.0" in text)
    }

    @Test
    fun sheetHidesPrivatePlaces() {
        val home = PrivateZone("z", "Home", 40.0, 16.0, 500)
        val text = RideSheet.text(ride, "R", labels, km = 100.0, seconds = 7200.0, zones = listOf(home))
        assertFalse("Bar Roma" in text)
        assertTrue("Private place" in text)
        assertTrue(text.endsWith("\n"))
    }

    @Test
    fun movedStopKeepsItsPlan() {
        val r = Ride(null, emptyList(), stops, Ride.Source.PLANNED)
        val moved = r.withStopMoved(2, LatLon(40.61, 16.01))
        assertEquals(coffee, moved.stops[2].plan)
        assertEquals(Stop.Kind.STOP, moved.stops[2].kind)
        assertNull(moved.stops[2].name)
        // A plain point stays a pass-through when dragged.
        assertEquals(Stop.Kind.VIA, r.withStopMoved(1, LatLon(40.31, 16.0)).stops[1].kind)
    }

    @Test
    fun plansAndVersionTravelInTheLink() {
        val v = RideVersion("0123456789ab", 4, "new lunch place", 1_700_000_000_000)
        val link = RideLink.encode(ride.copy(version = v))
        val back = RideLink.decode(link).ride
        assertEquals(stops.map { it.plan }, back.stops.map { it.plan })
        assertEquals(v, back.version)
        // Older links (seven fields) still open, without plans or version.
        val old = RideLink.encode(Ride("x", emptyList(), stops.map { it.copy(plan = null) }, Ride.Source.PLANNED))
        val decoded = RideLink.decode(old).ride
        assertTrue(decoded.stops.all { it.plan == null })
        assertNull(decoded.version)
    }

    @Test
    fun brokenPlanFieldsAreIgnored() {
        assertEquals(listOf(null, StopPlan(StopPlan.Purpose.FUEL)), RideLink.parsePlans("\u001ffuel\u001e\u001ezz"))
        assertNull(RideLink.parseVersion(""))
        assertNull(RideLink.parseVersion("id\u001f0"))
        assertNull(RideLink.parseVersion("id\u001fx"))
        // A note with the separators cannot break the fields.
        val code = RideLink.plansCode(listOf(Stop(null, LatLon(1.0, 1.0), plan = StopPlan(note = "a\u001fb\u001ec"))))
        assertEquals(listOf(StopPlan(note = "a b c")), RideLink.parsePlans(code))
    }

    @Test
    fun linkWithPlansIsStillReadByOlderParsers() {
        // The web page and older apps read field 6 (kinds) and before: they must stay in place.
        val link = RideLink.encode(ride.copy(version = RideVersion("id", 2)))
        val text = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(link.substringAfter('#')).decodeToString()
        val f = text.split('\n')
        assertEquals(9, f.size)
        assertEquals("SVSS", f[6])
    }

    @Test
    fun plansAndVersionTravelInTheGpx() {
        val v = RideVersion("0123456789ab", 2, "road closed at Tricarico", 1_700_000_000_000)
        // Without a track the stops go as route points.
        val gpx = GpxExport.write(ride.copy(version = v), "x")
        assertTrue("<type>meeting</type>" in gpx)
        assertTrue("<cmt>Bar on the square</cmt>" in gpx)
        assertTrue("""<rideplan:stop time="08:30" pause="15"/>""" in gpx)
        assertTrue("<desc>v2: road closed at Tricarico</desc>" in gpx)
        val back = RideImport.parse(gpx)
        assertEquals(stops.map { it.plan }, back.stops.map { it.plan })
        assertEquals(v, back.version)
        assertEquals("Saturday ride", back.name)
        // With a track the stops are waypoints, plans included.
        val tracked = ride.copy(track = stops.map { it.point })
        val back2 = RideImport.parse(GpxExport.write(tracked, "x"))
        assertEquals(stops.map { it.plan }, back2.stops.map { it.plan })
        assertNull(back2.version)
        // A planned stop stays a real stop on import, even unnamed.
        assertEquals(Stop.Kind.STOP, back.stops[0].kind)
    }

    @Test
    fun otherAppsGpxTypeAndComment() {
        val gpx = """<gpx><rte><rtept lat="40" lon="16"><name>A</name><cmt>see you</cmt><type>Fuel</type></rtept>
            <rtept lat="41" lon="16"><name>B</name><type>Restaurant</type></rtept></rte></gpx>"""
        val r = RideImport.parse(gpx)
        assertEquals(StopPlan(StopPlan.Purpose.FUEL, note = "see you"), r.stops[0].plan)
        assertNull(r.stops[1].plan)
    }
}
