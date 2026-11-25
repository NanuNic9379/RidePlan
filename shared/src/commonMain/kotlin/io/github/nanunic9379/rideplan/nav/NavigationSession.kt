package io.github.nanunic9379.rideplan.nav

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.Ride
import io.github.nanunic9379.rideplan.ride.Stop
import io.github.nanunic9379.rideplan.route.RouteOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

/**
 * What navigation needs from the phone (#5). Android: LocationManager, TextToSpeech and the
 * offline engine; iOS will bring its own. Location is used only while navigating: with the
 * screen off or another app open too (#65), never after Exit.
 */
interface NavigationPlatform {
    /** Asks for the foreground location permission if needed; true when granted. */
    suspend fun requestLocation(): Boolean

    /** GPS fixes, about one per second, until [stopLocation]. */
    fun startLocation(onFix: (Fix) -> Unit)

    fun stopLocation()

    /** Says [text] with the phone's voice (also over a Bluetooth intercom). */
    fun speak(text: String)

    fun stopSpeaking()

    fun keepScreenOn(on: Boolean)

    /**
     * Keeps guidance running with the screen off or the app in the background (#65), showing
     * [notice] as an ongoing notification; null stops it. Called again at every change of text.
     */
    fun background(notice: NavNotice?) {}

    /**
     * Offline route from [from] through [stops] with [options], on the road data already on
     * the phone; null if it cannot be computed (no data there, no road).
     */
    suspend fun reroute(ride: Ride, from: LatLon, heading: Double?, stops: List<Stop>, options: RouteOptions): Ride?
}

/** State of one navigation, from "Navigate" to "Exit". The screen reads it. */
@Stable
class NavigationSession(
    val original: Ride,
    private val platform: NavigationPlatform,
    private val scope: CoroutineScope,
    /** Turns an announcement into the text to say, in the app language. */
    private val say: suspend (Announcement) -> String?,
    /** Words of the background notification (#65); null = no notification. */
    private val noticeTexts: suspend () -> NoticeTexts? = { null },
    /** Metres as shown in the notification, in the unit of the settings. */
    private val distance: (Double) -> String = { "${it.toInt()} m" },
    /** Monotonic milliseconds, for the idle pause. */
    private val clock: () -> Long = TimeSource.Monotonic.markNow().let { start -> { start.elapsedNow().inWholeMilliseconds } },
) {
    var navigator by mutableStateOf(Navigator(NavRoute(original)))
        private set
    var update by mutableStateOf<NavUpdate?>(null)
        private set
    var muted by mutableStateOf(false)
    var permission by mutableStateOf<Boolean?>(null)
        private set
    var rerouting by mutableStateOf(false)
        private set
    /** The last re-route attempt failed: ride back to the road. */
    var rerouteFailed by mutableStateOf(false)
        private set
    /** Arrived at the finish: the screen asks whether to mark the ride as done. */
    var arrived by mutableStateOf(false)
        private set
    /** Paused after a long stop to save the battery (#65): the screen offers to resume. */
    var paused by mutableStateOf(false)
        private set
    /** The background service is wanted: from the start until Exit, the finish or the idle pause. */
    var inBackground = false
        private set
    private var rerouteJob: Job? = null
    private var idleJob: Job? = null
    private var lastFix: Fix? = null
    private val idle = IdleWatch()
    private var texts: NoticeTexts? = null
    private var lastNotice: NavNotice? = null

    /** The road being followed: the ride's, or the one computed after leaving it. */
    val ride: Ride get() = navigator.route.ride

    /** Starts navigation, also again after the permission was refused or after the idle pause. */
    fun start() {
        scope.launch {
            val granted = platform.requestLocation()
            permission = granted
            if (!granted) return@launch
            paused = false
            platform.keepScreenOn(true)
            platform.startLocation(::onFix)
            idle.start(clock())
            if (texts == null) texts = noticeTexts()
            inBackground = update?.finished != true
            publishNotice()
            idleJob?.cancel()
            idleJob = scope.launch {
                while (isActive) {
                    delay(IDLE_CHECK_MS)
                    checkIdle()
                }
            }
        }
    }

    fun stop() {
        rerouteJob?.cancel()
        idleJob?.cancel()
        stopBackground()
        platform.stopLocation()
        platform.stopSpeaking()
        platform.keepScreenOn(false)
    }

    /** Pauses when the bike has not moved for a long time: GPS, voice and notification stop. */
    fun checkIdle() {
        if (paused || permission != true || !idle.idle(clock())) return
        paused = true
        stop()
    }

    fun toggleMute() {
        muted = !muted
        if (muted) platform.stopSpeaking()
    }

    fun dismissArrival() {
        arrived = false
    }

    fun onFix(fix: Fix) {
        if (paused) return
        lastFix = fix
        idle.onFix(fix, clock())
        val u = navigator.update(fix)
        update = u
        if (u.onRoute) rerouteFailed = false
        var finished = false
        for (a in u.announcements) {
            if (a is Announcement.Arrived && a.stop.finish) {
                arrived = true
                finished = true
            }
            speak(a)
        }
        if (u.reroute && rerouteJob?.isActive != true) reroute(fix, u)
        // At the finish guidance is over: the screen stays, the background service goes.
        if (finished) stopBackground() else publishNotice()
    }

    private fun publishNotice() {
        if (!inBackground) return
        val t = texts ?: return
        val n = navNotice(update, rerouting, t, distance)
        if (n == lastNotice) return
        lastNotice = n
        platform.background(n)
    }

    private fun stopBackground() {
        if (!inBackground) return
        inBackground = false
        lastNotice = null
        platform.background(null)
    }

    private fun speak(a: Announcement) {
        if (muted) return
        scope.launch { say(a)?.let(platform::speak) }
    }

    private fun reroute(fix: Fix, u: NavUpdate) {
        val options = original.route?.options ?: RouteOptions()
        val stops = navigator.remainingStops()
        rerouting = true
        publishNotice()
        rerouteJob = scope.launch {
            val fresh = runCatching { platform.reroute(original, fix.point, u.bearing ?: fix.bearing, stops, options) }.getOrNull()
            rerouting = false
            if (fresh == null || fresh.straightLines) {
                rerouteFailed = true
                publishNotice()
                return@launch
            }
            rerouteFailed = false
            navigator = Navigator(NavRoute(fresh))
            lastFix?.let(::onFix)
        }
    }

    private companion object {
        const val IDLE_CHECK_MS = 60_000L
    }
}
