package io.github.nanunic9379.rideplan.ride

import io.github.nanunic9379.rideplan.route.RouteInfo
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StagesTest {
    /** A point [km] east of (0, 10) along the equator: distances are easy to reason about. */
    private fun at(km: Double) = LatLon(0.0, 10.0 + km / KM_PER_DEG)

    /** A straight track every 5 km from 0 to [totalKm]. */
    private fun track(totalKm: Double): List<LatLon> =
        (0..(totalKm / 5).toInt()).map { at(it * 5.0) }.let { if (abs(it.size * 5.0 - 5.0 - totalKm) > 1e-9) it + at(totalKm) else it }

    private fun ride(
        totalKm: Double,
        stops: List<Pair<Double, Stop.Kind>>,
        cap: DayCap? = null,
        overnight: Set<Int> = emptySet(),
        route: RouteInfo? = null,
        withTrack: Boolean = true,
    ) = Ride(
        name = "Tour",
        track = if (withTrack) track(totalKm) else emptyList(),
        stops = stops.mapIndexed { i, (km, kind) -> Stop("S$i", at(km), kind, overnight = i in overnight) },
        source = Ride.Source.GPX,
        route = route,
        dayCap = cap,
    )

    private fun near(expected: Double, actual: Double, tol: Double = 0.5) =
        assertTrue(abs(expected - actual) <= tol, "expected $expected, got $actual")

    /** GPX keeps 7 decimals: cut points come back rounded. */
    private fun samePoints(expected: List<LatLon>, actual: List<LatLon>) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (e, a) -> assertTrue(distanceKm(e, a) < 0.001, "$e vs $a") }
    }

    private val stop = Stop.Kind.STOP

    @Test
    fun noCapIsOneDay() {
        val r = ride(400.0, listOf(0.0 to stop, 200.0 to stop, 400.0 to stop))
        val days = Stages.split(r)
        assertEquals(1, days.size)
        near(400.0, days[0].km)
        assertEquals(0, days[0].startStop)
        assertEquals(2, days[0].endStop)
        assertEquals(r.stops, days[0].stops)
        assertFalse(Stages.active(r))
    }

    @Test
    fun underTheCapIsOneDay() {
        val days = Stages.split(ride(250.0, listOf(0.0 to stop, 250.0 to stop), DayCap.DEFAULT_KM))
        assertEquals(1, days.size)
    }

    @Test
    fun daysAreBalancedNotCapThenLeftover() {
        val r = ride(620.0, listOf(0.0 to stop, 620.0 to stop), DayCap(DayCap.Unit.KM, 300.0))
        val days = Stages.split(r)
        assertEquals(3, days.size)
        days.forEach { near(620.0 / 3, it.km) }
        assertTrue(days.all { it.km <= 300.0 + 1e-6 })
        near(r.lengthKm, days.sumOf { it.km }, 1e-6)
        assertEquals(listOf(1, 2, 3), days.map { it.day })
        // Days follow one another without gaps.
        days.zipWithNext().forEach { (a, b) -> assertEquals(a.endKm, b.startKm) }
    }

    @Test
    fun dayEndsAtStopNearTheCap() {
        val r = ride(520.0, listOf(0.0 to stop, 250.0 to stop, 520.0 to stop), DayCap(DayCap.Unit.KM, 300.0))
        val days = Stages.split(r)
        assertEquals(2, days.size)
        assertEquals(1, days[0].endStop)
        assertEquals("S1", days[0].stops.last().name)
        assertEquals(1, days[1].startStop)
        near(250.0, days[0].km)
        near(270.0, days[1].km)
    }

    @Test
    fun stopIsNeverPastTheCap() {
        val r = ride(600.0, listOf(0.0 to stop, 310.0 to stop, 600.0 to stop), DayCap(DayCap.Unit.KM, 300.0))
        val days = Stages.split(r)
        assertTrue(days.all { it.km <= 300.0 + 1e-6 }, days.map { it.km }.toString())
        assertNull(days[0].endStop)
        near(300.0, days[0].km)
        // The cut point is a new unnamed stop; the next day starts there.
        assertNull(days[0].stops.last().name)
        assertEquals(days[0].stops.last().point, days[1].stops.first().point)
    }

    @Test
    fun closestStopToTheIdealEndWins() {
        val r = ride(
            560.0,
            listOf(0.0 to stop, 230.0 to stop, 270.0 to stop, 560.0 to stop),
            DayCap(DayCap.Unit.KM, 300.0),
        )
        val days = Stages.split(r)
        assertEquals(2, days[0].endStop)
    }

    @Test
    fun stopTooEarlyIsNotUsed() {
        // Ideal end 280 km, window from 205 km: a stop at 150 km would make a short day.
        val r = ride(560.0, listOf(0.0 to stop, 150.0 to stop, 560.0 to stop), DayCap(DayCap.Unit.KM, 300.0))
        val days = Stages.split(r)
        assertNull(days[0].endStop)
        near(280.0, days[0].km)
        assertEquals(listOf("S0", "S1", null), days[0].stops.map { it.name })
    }

    @Test
    fun passAndViaPointsAreNotOvernights() {
        for (kind in listOf(Stop.Kind.PASS, Stop.Kind.VIA)) {
            val r = ride(560.0, listOf(0.0 to stop, 270.0 to kind, 560.0 to stop), DayCap(DayCap.Unit.KM, 300.0))
            val days = Stages.split(r)
            assertNull(days[0].endStop, "$kind")
            // Still part of the day it falls in.
            assertEquals(listOf("S0", "S1", null), days[0].stops.map { it.name })
            assertEquals(listOf(null, "S2"), days[1].stops.map { it.name })
        }
    }

    @Test
    fun overnightByHand() {
        val r = ride(250.0, listOf(0.0 to stop, 100.0 to stop, 250.0 to stop), overnight = setOf(1))
        assertTrue(Stages.active(r))
        val days = Stages.split(r)
        assertEquals(2, days.size)
        assertTrue(days[0].overnight)
        assertFalse(days[1].overnight)
        assertEquals(1, days[0].endStop)
        near(100.0, days[0].km)
        near(150.0, days[1].km)
    }

    @Test
    fun overnightOnStartOrFinishIsIgnored() {
        val r = ride(250.0, listOf(0.0 to stop, 250.0 to stop), overnight = setOf(0, 1))
        assertEquals(1, Stages.split(r).size)
        assertFalse(Stages.active(r))
    }

    @Test
    fun overnightAndCapTogether() {
        val r = ride(
            700.0,
            listOf(0.0 to stop, 100.0 to stop, 700.0 to stop),
            DayCap(DayCap.Unit.KM, 300.0),
            overnight = setOf(1),
        )
        val days = Stages.split(r)
        assertEquals(3, days.size)
        near(100.0, days[0].km)
        near(300.0, days[1].km)
        near(300.0, days[2].km)
        assertTrue(days[0].overnight)
    }

    @Test
    fun hoursCapUsesRoutePace() {
        // 600 km in 10 h: 60 km/h, so 5 h is 300 km a day.
        val info = RouteInfo(600.0, 36_000.0, "it", "2026-11-01", "test")
        val r = ride(600.0, listOf(0.0 to stop, 600.0 to stop), DayCap(DayCap.Unit.HOURS, 5.0), route = info)
        near(300.0, Stages.capKm(r, r.dayCap!!), 1e-9)
        val days = Stages.split(r)
        assertEquals(2, days.size)
        days.forEach { near(18_000.0, it.seconds, 30.0) }
        near(36_000.0, days.sumOf { it.seconds }, 1e-6)
    }

    @Test
    fun hoursCapWithoutRouteUsesDefaultPace() {
        val r = ride(600.0, listOf(0.0 to stop, 600.0 to stop), DayCap(DayCap.Unit.HOURS, 4.0))
        near(4 * Stages.DEFAULT_KMH, Stages.capKm(r, r.dayCap!!), 1e-9)
        assertEquals(3, Stages.split(r).size)
    }

    @Test
    fun routeKmScaleTheLine() {
        // The computed road says 660 km for a 600 km line: days are in road km.
        val info = RouteInfo(660.0, 39_600.0, "it", "2026-11-01", "test")
        val r = ride(600.0, listOf(0.0 to stop, 600.0 to stop), DayCap(DayCap.Unit.KM, 330.0), route = info)
        val days = Stages.split(r)
        assertEquals(2, days.size)
        near(330.0, days[0].km)
        near(660.0, days.sumOf { it.km }, 1e-6)
    }

    @Test
    fun tracksAreContiguous() {
        val r = ride(620.0, listOf(0.0 to stop, 620.0 to stop), DayCap(DayCap.Unit.KM, 300.0))
        val days = Stages.split(r)
        assertEquals(r.track.first(), days.first().track.first())
        assertEquals(r.track.last(), days.last().track.last())
        days.zipWithNext().forEach { (a, b) -> assertEquals(a.track.last(), b.track.first()) }
        days.forEach { d ->
            assertEquals(d.stops.first().point, d.track.first())
            assertEquals(d.stops.last().point, d.track.last())
            val len = d.track.zipWithNext { a, b -> distanceKm(a, b) }.sum()
            near(d.km, len, 1e-6)
        }
    }

    @Test
    fun straightLinesCutBetweenStops() {
        val r = ride(600.0, listOf(0.0 to stop, 200.0 to stop, 600.0 to stop), DayCap(DayCap.Unit.KM, 300.0), withTrack = false)
        assertTrue(r.straightLines)
        val days = Stages.split(r)
        assertTrue(days.all { it.track.isEmpty() })
        assertTrue(days.all { it.km <= 300.0 + 1e-6 })
        near(r.lengthKm, days.sumOf { it.km }, 1e-6)
        // 200 km is before the window of the first day (ideal 300 km, from 225 km): the day ends
        // on the line at 300 km, after passing S1.
        assertEquals(listOf("S0", "S1", null), days[0].stops.map { it.name })
    }

    @Test
    fun loopFinishIsAtTheEnd() {
        // Out 200 km and back: the finish is where the start is.
        val out = (0..40).map { at(it * 5.0) }
        val line = out + out.reversed().drop(1)
        val r = Ride("Loop", line, listOf(Stop("Home", at(0.0)), Stop("Far", at(200.0)), Stop("Home", at(0.0))), Ride.Source.GPX)
        val pos = Stages.stopPositions(r)
        near(0.0, pos[0], 1e-6)
        near(200.0, pos[1], 1e-6)
        near(400.0, pos[2], 1e-6)
        val days = Stages.split(r.copy(dayCap = DayCap(DayCap.Unit.KM, 250.0)))
        assertEquals(2, days.size)
        assertEquals(1, days[0].endStop)
    }

    @Test
    fun invalidCapsAreIgnored() {
        for (v in listOf(0.0, -10.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            val r = ride(600.0, listOf(0.0 to stop, 600.0 to stop), DayCap(DayCap.Unit.KM, v))
            assertEquals(1, Stages.split(r).size, "$v")
        }
    }

    @Test
    fun tinyCapIsBounded() {
        val r = ride(620.0, listOf(0.0 to stop, 620.0 to stop), DayCap(DayCap.Unit.KM, 1.0))
        val days = Stages.split(r)
        assertEquals(Stages.MAX_DAYS, days.size)
        near(r.lengthKm, days.sumOf { it.km }, 1e-6)
    }

    @Test
    fun nothingToSplit() {
        assertTrue(Stages.split(Ride(null, emptyList(), listOf(Stop("A", at(0.0))), Ride.Source.GPX)).isEmpty())
        val same = Ride(null, emptyList(), listOf(Stop("A", at(0.0)), Stop("B", at(0.0))), Ride.Source.GPX, dayCap = DayCap.DEFAULT_KM)
        assertEquals(1, Stages.split(same).size)
    }

    @Test
    fun markingOvernightKeepsTheRoad() {
        val info = RouteInfo(250.0, 15_000.0, "it", "2026-11-01", "test")
        val r = ride(250.0, listOf(0.0 to stop, 100.0 to stop, 250.0 to stop), route = info)
        val marked = r.withOvernight(1, true)
        assertTrue(marked.stops[1].overnight)
        assertEquals(r.route, marked.route)
        assertEquals(r.track, marked.track)
        assertFalse(marked.withOvernight(1, false).stops[1].overnight)
        assertSame(r, r.withOvernight(0, true))
        assertSame(r, r.withOvernight(2, true))
        assertSame(r, r.withOvernight(7, true))
    }

    @Test
    fun gpxOneFilePerDay() {
        val r = ride(520.0, listOf(0.0 to stop, 250.0 to stop, 400.0 to stop, 520.0 to stop), DayCap(DayCap.Unit.KM, 300.0))
        val days = Stages.split(r)
        val files = StageExport.files(r, days, "Ride") { "Day $it" }
        assertEquals(days.size, files.size)
        assertEquals(listOf("Tour - Day 1.gpx", "Tour - Day 2.gpx"), files.map { it.first })
        files.zip(days).forEach { (file, day) ->
            val back = RideImport.parse(file.second)
            assertEquals("Tour - Day ${day.day}", back.name)
            samePoints(day.track, back.track)
            samePoints(day.stops.map { it.point }, back.stops.map { it.point })
            assertTrue("<trk>" in file.second)
        }
        assertEquals(listOf("S0", "S1"), RideImport.parse(files[0].second).stops.map { it.name })
        assertEquals(listOf("S1", "S2", "S3"), RideImport.parse(files[1].second).stops.map { it.name })
    }

    @Test
    fun gpxPerDayWithoutTrackAndUniqueNames() {
        val r = ride(600.0, listOf(0.0 to stop, 600.0 to stop), DayCap(DayCap.Unit.KM, 300.0), withTrack = false)
            .copy(name = null)
        val days = Stages.split(r)
        // A day name that is the same for every day still gives distinct files.
        val files = StageExport.files(r, days, "A → B") { "Day" }
        assertEquals(listOf("A - B - Day.gpx", "A - B - Day (2).gpx"), files.map { it.first })
        files.forEach { (_, gpx) ->
            assertFalse("<trk>" in gpx)
            assertTrue("<rte>" in gpx)
        }
    }

    private companion object {
        const val KM_PER_DEG = 111.19508
    }
}
