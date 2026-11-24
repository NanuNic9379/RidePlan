package io.github.nanunic9379.rideplan.nav

import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.Ride
import io.github.nanunic9379.rideplan.ride.Stop
import io.github.nanunic9379.rideplan.route.Maneuver
import io.github.nanunic9379.rideplan.route.RoutingRegion
import io.github.nanunic9379.rideplan.route.TurnSign
import io.github.nanunic9379.rideplan.route.ValhallaRoute
import io.github.nanunic9379.rideplan.route.routed
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Test roads near 45°N, 9°E. [north] and [east] are metres from the origin, so positions
 * along the road are easy to read.
 */
class NavigatorTest {
    private val mPerDegLat = 6371008.8 * PI / 180
    private val mPerDegLon = mPerDegLat * cos(45.0 * PI / 180)

    private fun at(north: Double, east: Double = 0.0) = LatLon(45.0 + north / mPerDegLat, 9.0 + east / mPerDegLon)

    private val region = RoutingRegion("test", listOf(8.0, 44.0, 10.0, 46.0), "test.tar.gz", 1, 1, "ab", "2026-11-22T21:00:00Z", "3.9.2")

    /** 3 km north (points every 100 m), right turn, 2 km east: 5 km in all. */
    private val lShape: List<LatLon> = (0..30).map { at(it * 100.0) } + (1..20).map { at(3000.0, it * 100.0) }

    private val depart = Maneuver(1, "Head north.", 0, 30, km = 3.0, seconds = 300.0, verbalPost = "Continue for 3 kilometers.")
    private val right = Maneuver(10, "Turn right.", 30, 50, km = 2.0, seconds = 200.0, verbalAlert = "Turn right.", verbalPost = "Continue for 2 kilometers.")
    private val arrive = Maneuver(4, "You have arrived.", 50, 50)

    private fun routedRide(
        shape: List<LatLon> = lShape,
        stops: List<Stop> = listOf(Stop("Home", shape.first()), Stop("Bar", shape.last())),
        maneuvers: List<Maneuver> = listOf(depart, right, arrive),
        seconds: Double = 500.0,
    ): Ride = Ride("test", emptyList(), stops, Ride.Source.GOOGLE_LINK)
        .routed(ValhallaRoute.Result(shape, 5.0, seconds, maneuvers), region)

    private fun fix(p: LatLon, t: Long, speed: Double = 20.0) = Fix(p, t * 1000, speedMps = speed, accuracyM = 5.0)

    /** Rides the line from [from] to [to] metres in steps of [step], one fix per second. */
    private fun Navigator.ride(points: (Double) -> LatLon, from: Double, to: Double, step: Double = 50.0, t0: Long = 0): List<NavUpdate> {
        val out = ArrayList<NavUpdate>()
        var d = from
        var t = t0
        while (d <= to + 1e-6) {
            out += update(fix(points(d), t++))
            d += step
        }
        return out
    }

    /** The point [d] metres along [lShape]. */
    private fun onL(d: Double) = if (d <= 3000) at(d) else at(3000.0, d - 3000)

    private fun near(expected: Double, actual: Double, tolerance: Double = 3.0) =
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected ± $tolerance, was $actual")

    // RouteLine

    @Test
    fun lineLengthAndSnap() {
        val line = RouteLine(lShape)
        near(5000.0, line.length)
        val s = assertNotNull(line.snap(at(1000.0, 20.0)))
        near(1000.0, s.along)
        near(20.0, s.offset, 0.5)
        // Within a window only that part of the line counts.
        val w = assertNotNull(line.snap(at(3000.0, 1500.0), from = 0.0, to = 1000.0))
        assertTrue(w.along <= 1100)
        assertNull(RouteLine(listOf(at(0.0))).snap(at(0.0)))
    }

    @Test
    fun lineBearing() {
        val line = RouteLine(lShape)
        near(0.0, line.bearingAt(500.0), 0.5)
        near(90.0, line.bearingAt(4000.0), 0.5)
        near(180.0, RouteLine.bearing(at(100.0), at(0.0)), 0.5)
        near(270.0, RouteLine.bearing(at(0.0, 100.0), at(0.0)), 0.5)
    }

    @Test
    fun firstNearKeepsTheFirstPass() {
        // Out and back on the same road: 2 km north, then back.
        val line = RouteLine((0..20).map { at(it * 100.0) } + (19 downTo 0).map { at(it * 100.0) })
        near(4000.0, line.length)
        near(500.0, assertNotNull(line.firstNear(at(500.0, 10.0), 30.0)).along)
        near(3500.0, assertNotNull(line.firstNear(at(500.0, 10.0), 30.0, from = 2100.0)).along)
        assertNull(line.firstNear(at(500.0, 500.0), 30.0))
        // Never before [from]: at the turning point the pass ahead wins.
        near(2050.0, assertNotNull(line.snap(at(1950.0), from = 1980.0)).along)
    }

    // Navigator

    @Test
    fun startsWithTheFirstInstruction() {
        val nav = Navigator(NavRoute(routedRide()))
        val u = nav.update(fix(at(0.0), 0))
        assertTrue(u.onRoute)
        val start = u.announcements.single() as Announcement.Start
        assertEquals(depart, start.maneuver)
        near(5000.0, start.remainingM)
        assertEquals(right, u.next?.maneuver)
        near(3000.0, assertNotNull(u.distanceToNextM))
        near(5000.0, u.remainingM)
    }

    @Test
    fun turnAnnouncedAtRoadSpeedDistances() {
        val nav = Navigator(NavRoute(routedRide()))
        val turns = nav.ride(::onL, 0.0, 2950.0).flatMap { it.announcements }.filterIsInstance<Announcement.Turn>()
        // At 72 km/h: about 800 m, 300 m and "now" (6 s ahead), each once.
        assertEquals(listOf(Stage.FAR, Stage.NEAR, Stage.NOW), turns.map { it.stage })
        assertTrue(turns.all { it.maneuver == right })
        assertTrue(turns[0].distanceM in 750..800, "${turns[0].distanceM}")
        assertTrue(turns[1].distanceM in 250..300, "${turns[1].distanceM}")
    }

    @Test
    fun announceDistancesGrowWithSpeed() {
        assertEquals(AnnounceDistances(2000.0, 800.0, 180.0), AnnounceDistances.forSpeed(30.0))
        assertEquals(AnnounceDistances(800.0, 300.0, 120.0), AnnounceDistances.forSpeed(20.0))
        assertEquals(AnnounceDistances(400.0, 150.0, 40.0), AnnounceDistances.forSpeed(5.0))
    }

    @Test
    fun spokenDistances() {
        assertEquals(1000, Navigator.spokenDistance(1234.0))
        assertEquals(1000, Navigator.spokenDistance(960.0))
        assertEquals(950, Navigator.spokenDistance(940.0))
        assertEquals(250, Navigator.spokenDistance(274.0))
        assertEquals(50, Navigator.spokenDistance(12.0))
        assertEquals(3000, Navigator.spokenDistance(2600.0))
    }

    @Test
    fun afterTheTurnContinueAndNextManeuver() {
        val nav = Navigator(NavRoute(routedRide()))
        val all = nav.ride(::onL, 0.0, 3100.0)
        val after = all.last()
        assertEquals(arrive, after.next?.maneuver)
        near(1900.0, assertNotNull(after.distanceToNextM))
        near(90.0, assertNotNull(after.bearing), 0.5)
        assertTrue(all.flatMap { it.announcements }.contains(Announcement.Continue("Continue for 2 kilometers.")))
    }

    @Test
    fun arrivalAtTheFinish() {
        val nav = Navigator(NavRoute(routedRide()))
        val all = nav.ride(::onL, 0.0, 5000.0)
        val arrivals = all.flatMap { it.announcements }.filterIsInstance<Announcement.Arrived>()
        assertEquals(1, arrivals.size)
        assertTrue(arrivals.single().stop.finish)
        assertTrue(all.last().finished)
        // The arrival maneuver is announced ahead but never as "now": the stop says it.
        val toArrival = all.flatMap { it.announcements }.filterIsInstance<Announcement.Turn>().filter { it.maneuver == arrive }
        assertFalse(toArrival.any { it.stage == Stage.NOW })
        assertTrue(toArrival.isNotEmpty())
    }

    @Test
    fun remainingTimeFromManeuvers() {
        val nav = Navigator(NavRoute(routedRide()))
        val u = nav.ride(::onL, 0.0, 1500.0).last()
        near(350.0, assertNotNull(u.remainingSeconds), 1.0)
        near(3500.0, u.remainingM)
    }

    @Test
    fun offRouteAfterSomeSecondsThenRetryAndBack() {
        val nav = Navigator(NavRoute(routedRide()))
        nav.ride(::onL, 0.0, 1000.0, t0 = 0) // t = 0..20 s
        val away = at(1000.0, 200.0)
        val first = nav.update(fix(away, 30))
        assertFalse(first.onRoute)
        assertFalse(first.offRoute) // one fix is not enough
        assertFalse(nav.update(fix(away, 33)).offRoute)
        val off = nav.update(fix(away, 36))
        assertTrue(off.offRoute)
        assertTrue(off.reroute)
        assertEquals(listOf<Announcement>(Announcement.OffRoute(canReroute = true)), off.announcements)
        assertFalse(nav.update(fix(away, 40)).reroute) // computing: wait
        assertTrue(nav.update(fix(away, 56)).reroute) // still away: try again
        // Towards the remaining stops: here only the finish.
        assertEquals(listOf("Bar"), nav.remainingStops().map { it.name })
        val back = nav.update(fix(at(1050.0, 5.0), 60))
        assertTrue(back.onRoute)
        assertFalse(back.offRoute)
        assertTrue(Announcement.BackOnRoute in back.announcements)
    }

    @Test
    fun noisyFixDoesNotLeaveTheRoad() {
        val nav = Navigator(NavRoute(routedRide()))
        nav.ride(::onL, 0.0, 1000.0)
        // 40 m off with 5 m accuracy: still on the road (45 m + accuracy).
        assertTrue(nav.update(fix(at(1050.0, 40.0), 100)).onRoute)
    }

    @Test
    fun outAndBackLoopFollowsTheRightPass() {
        val shape = (0..20).map { at(it * 100.0) } + (19 downTo 0).map { at(it * 100.0) }
        val stops = listOf(Stop("Home", at(0.0)), Stop("Top", at(2000.0)), Stop("Home", at(0.0)))
        val ride = routedRide(shape, stops, maneuvers = emptyList(), seconds = 400.0)
        val nav = Navigator(NavRoute(ride))
        fun onLoop(d: Double) = if (d <= 2000) at(d) else at(4000 - d)
        // At the start the finish (same place) is not reached.
        val first = nav.update(fix(at(0.0), 0))
        assertTrue(first.announcements.none { it is Announcement.Arrived })
        val all = nav.ride(::onLoop, 50.0, 4000.0, t0 = 1)
        // On the way back the position stays on the second pass.
        near(3500.0, all.first { abs(it.alongM - 3500) < 30 }.alongM)
        assertTrue(all.zipWithNext().all { (a, b) -> b.alongM >= a.alongM - 1 })
        val arrived = all.flatMap { it.announcements }.filterIsInstance<Announcement.Arrived>().map { it.stop }
        assertEquals(listOf("Top", "Home"), arrived.map { it.stop.name })
        assertTrue(arrived.last().finish)
        assertTrue(all.last().finished)
        // Time left in proportion to the route when there are no maneuvers.
        near(0.0, assertNotNull(all.last().remainingSeconds), 1.0)
    }

    @Test
    fun remainingStopsSkipWhatIsBehind() {
        val shape = (0..40).map { at(it * 100.0) }
        val stops = listOf(Stop("A", at(0.0)), Stop("B", at(1000.0)), Stop("C", at(2000.0), Stop.Kind.PASS), Stop("D", at(4000.0)))
        val nav = Navigator(NavRoute(routedRide(shape, stops, maneuvers = emptyList())))
        assertEquals(listOf("B", "C", "D"), nav.route.stops.map { it.stop.name })
        assertEquals(listOf(true, false, true), nav.route.stops.map { it.arrival })
        nav.ride(::at, 0.0, 1500.0)
        assertEquals(listOf("C", "D"), nav.remainingStops().map { it.name })
    }

    @Test
    fun stopsPlacedOnLegEnds() {
        // Two legs: the first arrival maneuver marks the intermediate stop.
        val ms = listOf(
            Maneuver(1, "Head north.", 0, 10), Maneuver(5, "You have arrived at B.", 10, 10),
            Maneuver(1, "Head north.", 10, 30), Maneuver(4, "You have arrived.", 30, 30),
        )
        val shape = (0..30).map { at(it * 100.0) }
        // The stop is 150 m off the road: it is still placed where its leg ends.
        val stops = listOf(Stop("A", at(0.0)), Stop("B", at(1000.0, 150.0)), Stop("C", at(3000.0)))
        val route = NavRoute(routedRide(shape, stops, ms))
        near(1000.0, route.stops[0].along)
        near(3000.0, route.stops[1].along)
        assertTrue(route.hasInstructions && route.canReroute)
    }

    @Test
    fun gpxTrackIsFollowedWithoutInstructions() {
        val ride = Ride("gpx", lShape, emptyList(), Ride.Source.GPX)
        val route = NavRoute(ride)
        assertFalse(route.hasInstructions)
        assertFalse(route.canReroute)
        val nav = Navigator(route)
        val start = nav.update(fix(at(0.0), 0))
        assertEquals(Announcement.Start(null, route.line.length), start.announcements.single())
        assertNull(start.next)
        assertNull(start.remainingSeconds)
        val u = nav.ride(::onL, 50.0, 2000.0, t0 = 1).last()
        near(3000.0, u.remainingM)
        val away = at(2000.0, 300.0)
        nav.update(fix(away, 100))
        val off = nav.update(fix(away, 106))
        assertTrue(off.offRoute)
        assertFalse(off.reroute)
        assertEquals(listOf<Announcement>(Announcement.OffRoute(canReroute = false)), off.announcements)
        assertFalse(nav.update(fix(away, 140)).reroute)
    }

    @Test
    fun speedFromPositionsWhenTheGpsGivesNone() {
        val nav = Navigator(NavRoute(routedRide()))
        nav.update(Fix(at(0.0), 0))
        val u = nav.update(Fix(at(100.0), 5000))
        near(20.0, assertNotNull(u.speedMps), 0.2)
    }

    @Test
    fun turnSigns() {
        assertEquals(TurnSign.RIGHT, right.sign)
        assertEquals(TurnSign.ARRIVE, arrive.sign)
        assertTrue(arrive.isArrival)
        assertEquals(TurnSign.ROUNDABOUT, TurnSign.of(26))
        assertEquals(TurnSign.KEEP_LEFT, TurnSign.of(24))
        assertEquals(TurnSign.STRAIGHT, TurnSign.of(8))
    }
}
