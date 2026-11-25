package io.github.nanunic9379.rideplan.nav

import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.Ride
import io.github.nanunic9379.rideplan.ride.Stop
import io.github.nanunic9379.rideplan.route.Maneuver
import io.github.nanunic9379.rideplan.route.RouteOptions
import io.github.nanunic9379.rideplan.route.RoutingRegion
import io.github.nanunic9379.rideplan.route.ValhallaRoute
import io.github.nanunic9379.rideplan.route.routed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Navigation in the background (#65): the notification text, the idle pause, when the service runs. */
class NavBackgroundTest {
    private val mPerDegLat = 6371008.8 * PI / 180
    private val mPerDegLon = mPerDegLat * cos(45.0 * PI / 180)
    private fun at(north: Double, east: Double = 0.0) = LatLon(45.0 + north / mPerDegLat, 9.0 + east / mPerDegLon)

    /** 3 km north, right turn, 2 km east. */
    private val shape = (0..30).map { at(it * 100.0) } + (1..20).map { at(3000.0, it * 100.0) }
    private fun onL(d: Double) = if (d <= 3000) at(d) else at(3000.0, d - 3000)
    private val region = RoutingRegion("test", listOf(8.0, 44.0, 10.0, 46.0), "test.tar.gz", 1, 1, "ab", "2026-11-22T21:00:00Z", "3.9.2")
    private val right = Maneuver(10, "Turn right.", 30, 50, km = 2.0, seconds = 200.0, verbalAlert = "Turn right.")
    private val ride: Ride = Ride("test", emptyList(), listOf(Stop("Home", shape.first()), Stop("Bar", shape.last())), Ride.Source.GOOGLE_LINK)
        .routed(
            ValhallaRoute.Result(
                shape, 5.0, 500.0,
                listOf(Maneuver(1, "Head north.", 0, 30, km = 3.0, seconds = 300.0), right, Maneuver(4, "You have arrived.", 50, 50)),
            ),
            region,
        )

    private val texts = NoticeTexts("Waiting for GPS…", "Recalculating…", "Follow the track", "You have arrived", "to the finish", "Stop navigation", "Navigation")
    private val metres: (Double) -> String = { "${(it / 10).roundToInt() * 10} m" }

    private fun update(next: PlacedManeuver?, toNext: Double?, remaining: Double, finished: Boolean = false) = NavUpdate(
        position = at(0.0), onRoute = true, offRoute = false, offsetM = 0.0, alongM = 0.0, remainingM = remaining,
        remainingSeconds = null, next = next, distanceToNextM = toNext, then = null, speedMps = 20.0, bearing = 0.0,
        announcements = emptyList(), reroute = false, finished = finished, lastArrival = null,
    )

    // Notification text

    @Test
    fun noticeShowsTheNextTurnAndWhatIsLeft() {
        val n = navNotice(update(PlacedManeuver(1, right, 3000.0), 300.0, 4200.0), false, texts, metres)
        assertEquals("300 m · Turn right.", n.title)
        assertEquals("4200 m to the finish", n.text)
        assertEquals("Stop navigation", n.stop)
        assertEquals("Navigation", n.channel)
    }

    @Test
    fun noticeWhileWaitingRecalculatingFollowingAndArrived() {
        assertEquals(NavNotice("Waiting for GPS…", "", "Stop navigation", "Navigation"), navNotice(null, false, texts, metres))
        assertEquals("Recalculating…", navNotice(update(PlacedManeuver(1, right, 3000.0), 300.0, 4200.0), true, texts, metres).title)
        assertEquals("Follow the track", navNotice(update(null, null, 1000.0), false, texts, metres).title)
        val done = navNotice(update(null, null, 0.0, finished = true), false, texts, metres)
        assertEquals("You have arrived", done.title)
        assertEquals("", done.text)
    }

    // Idle pause

    private fun fixAt(north: Double, t: Long, speed: Double? = 0.0) = Fix(at(north), t, speedMps = speed, accuracyM = 5.0)

    @Test
    fun idleAfterALongStopOnly() {
        val w = IdleWatch(limitMs = 1000)
        w.start(0)
        w.onFix(fixAt(0.0, 0), 0)
        // GPS jitter around the parked bike does not count as moving.
        w.onFix(fixAt(20.0, 500), 500)
        assertFalse(w.idle(999))
        assertTrue(w.idle(1000))
        // Riding again: the clock restarts.
        w.onFix(fixAt(25.0, 1100, speed = 15.0), 1100)
        assertFalse(w.idle(2000))
        assertTrue(w.idle(2100))
        // Pushed 200 m without speed: moved.
        w.onFix(fixAt(225.0, 2200), 2200)
        assertFalse(w.idle(3100))
    }

    @Test
    fun noFixAtAllAlsoCountsAsIdle() {
        val w = IdleWatch(limitMs = 1000)
        assertFalse(w.idle(5000))
        w.start(100)
        assertFalse(w.idle(1099))
        assertTrue(w.idle(1100))
    }

    // When the background service runs

    private class FakePlatform : NavigationPlatform {
        val notices = mutableListOf<NavNotice?>()
        var onFix: ((Fix) -> Unit)? = null
        var granted = true
        override suspend fun requestLocation() = granted
        override fun startLocation(onFix: (Fix) -> Unit) { this.onFix = onFix }
        override fun stopLocation() { onFix = null }
        override fun speak(text: String) = Unit
        override fun stopSpeaking() = Unit
        override fun keepScreenOn(on: Boolean) = Unit
        override fun background(notice: NavNotice?) { notices += notice }
        override suspend fun reroute(ride: Ride, from: LatLon, heading: Double?, stops: List<Stop>, options: RouteOptions): Ride? = null
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private var now = 0L

    @AfterTest
    fun cancelScope() = scope.cancel()

    private fun session(platform: FakePlatform, withTexts: Boolean = true) =
        NavigationSession(ride, platform, scope, say = { null }, noticeTexts = { texts.takeIf { withTexts } }, distance = metres, clock = { now })

    @Test
    fun serviceRunsFromStartToExit() {
        val p = FakePlatform()
        val s = session(p)
        s.start()
        assertTrue(s.inBackground)
        assertEquals("Waiting for GPS…", p.notices.single()?.title)
        now = 1000
        p.onFix!!(Fix(onL(100.0), 1000, speedMps = 20.0, accuracyM = 5.0))
        val turn = assertNotNull(p.notices.last())
        assertTrue(turn.title.endsWith(" · Turn right."), turn.title)
        // Same text again: no new notification.
        val count = p.notices.size
        p.onFix!!(Fix(onL(100.0), 1100, speedMps = 20.0, accuracyM = 5.0))
        assertEquals(count, p.notices.size)
        s.stop()
        assertNull(p.notices.last())
        assertFalse(s.inBackground)
        s.stop()
        assertEquals(count + 1, p.notices.size)
    }

    @Test
    fun noServiceWithoutPermission() {
        val p = FakePlatform().apply { granted = false }
        val s = session(p)
        s.start()
        assertTrue(p.notices.isEmpty())
        s.stop()
        assertTrue(p.notices.isEmpty())
    }

    @Test
    fun serviceStopsAtTheFinish() {
        val p = FakePlatform()
        val s = session(p)
        s.start()
        var d = 0.0
        var t = 0L
        while (d <= 5000.0) {
            now = t
            p.onFix!!(Fix(onL(d), t, speedMps = 20.0, accuracyM = 5.0))
            d += 50.0
            t += 1000
        }
        assertTrue(s.arrived)
        assertNull(p.notices.last())
        assertFalse(s.inBackground)
    }

    @Test
    fun idlePauseStopsEverythingAndResumeRestarts() {
        val p = FakePlatform()
        val s = session(p)
        s.start()
        p.onFix!!(Fix(onL(100.0), 0, speedMps = 0.0, accuracyM = 5.0))
        now = 44 * 60_000L
        s.checkIdle()
        assertFalse(s.paused)
        now = 45 * 60_000L
        s.checkIdle()
        assertTrue(s.paused)
        assertNull(p.notices.last())
        assertNull(p.onFix)
        s.start()
        assertFalse(s.paused)
        assertTrue(s.inBackground)
        assertNotNull(p.notices.last())
        s.stop()
    }

    @Test
    fun noNotificationWithoutTexts() {
        val p = FakePlatform()
        val s = session(p, withTexts = false)
        s.start()
        p.onFix!!(Fix(onL(100.0), 0, speedMps = 20.0, accuracyM = 5.0))
        assertTrue(p.notices.isEmpty())
        s.stop()
    }
}
