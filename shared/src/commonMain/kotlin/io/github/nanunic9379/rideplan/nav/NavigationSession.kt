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
import kotlinx.coroutines.launch

/**
 * What navigation needs from the phone (#5). Android: LocationManager, TextToSpeech and the
 * offline engine; iOS will bring its own. Location is used only while the navigation screen is open.
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
    private var rerouteJob: Job? = null
    private var lastFix: Fix? = null

    /** The road being followed: the ride's, or the one computed after leaving it. */
    val ride: Ride get() = navigator.route.ride

    fun start() {
        scope.launch {
            val granted = platform.requestLocation()
            permission = granted
            if (!granted) return@launch
            platform.keepScreenOn(true)
            platform.startLocation(::onFix)
        }
    }

    fun stop() {
        rerouteJob?.cancel()
        platform.stopLocation()
        platform.stopSpeaking()
        platform.keepScreenOn(false)
    }

    fun toggleMute() {
        muted = !muted
        if (muted) platform.stopSpeaking()
    }

    fun dismissArrival() {
        arrived = false
    }

    fun onFix(fix: Fix) {
        lastFix = fix
        val u = navigator.update(fix)
        update = u
        if (u.onRoute) rerouteFailed = false
        for (a in u.announcements) {
            if (a is Announcement.Arrived && a.stop.finish) arrived = true
            speak(a)
        }
        if (u.reroute && rerouteJob?.isActive != true) reroute(fix, u)
    }

    private fun speak(a: Announcement) {
        if (muted) return
        scope.launch { say(a)?.let(platform::speak) }
    }

    private fun reroute(fix: Fix, u: NavUpdate) {
        val options = original.route?.options ?: RouteOptions()
        val stops = navigator.remainingStops()
        rerouting = true
        rerouteJob = scope.launch {
            val fresh = runCatching { platform.reroute(original, fix.point, u.bearing ?: fix.bearing, stops, options) }.getOrNull()
            rerouting = false
            if (fresh == null || fresh.straightLines) {
                rerouteFailed = true
                return@launch
            }
            rerouteFailed = false
            navigator = Navigator(NavRoute(fresh))
            lastFix?.let(::onFix)
        }
    }
}
