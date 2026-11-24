package io.github.nanunic9379.rideplan

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import io.github.nanunic9379.rideplan.nav.Fix
import io.github.nanunic9379.rideplan.nav.NavigationPlatform
import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.Ride
import io.github.nanunic9379.rideplan.ride.Stop
import io.github.nanunic9379.rideplan.route.RouteOptions
import kotlinx.coroutines.CompletableDeferred

/**
 * Navigation on Android (#5): GPS from LocationManager (no Google services, so the F-Droid
 * build works the same), the phone's TextToSpeech with navigation audio (it goes to a
 * Bluetooth intercom and lowers the music), screen kept on.
 * Foreground only: location stops when the navigation screen closes.
 *
 * Create it in the activity's onCreate: the permission request must be registered there.
 */
class AndroidNavigation(
    private val activity: ComponentActivity,
    private val reroute: suspend (ride: Ride, from: LatLon, heading: Double?, stops: List<Stop>, options: RouteOptions) -> Ride?,
) : NavigationPlatform {
    private var pending: CompletableDeferred<Boolean>? = null
    private val permissionRequest = activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        pending?.complete(result.values.any { it })
        pending = null
    }
    // Lazy: system services are not available before the activity's onCreate.
    private val locations by lazy { activity.getSystemService(LocationManager::class.java) }
    private val audio by lazy { activity.getSystemService(AudioManager::class.java) }
    private var listener: LocationListener? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var queued: String? = null

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val focus: AudioFocusRequest? = if (Build.VERSION.SDK_INT >= 26) {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attributes).build()
    } else null

    private fun granted(): Boolean = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED }

    override suspend fun requestLocation(): Boolean {
        if (granted()) return true
        val wait = CompletableDeferred<Boolean>().also { pending = it }
        permissionRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        return wait.await()
    }

    @SuppressLint("MissingPermission")
    override fun startLocation(onFix: (Fix) -> Unit) {
        stopLocation()
        if (!granted() || locations == null) return
        val l = LocationListener { loc -> onFix(loc.toFix()) }
        listener = l
        val providers = locations.getProviders(true)
        // GPS for riding; the network position only helps while the GPS is still searching.
        val provider = when {
            LocationManager.GPS_PROVIDER in providers -> LocationManager.GPS_PROVIDER
            LocationManager.NETWORK_PROVIDER in providers -> LocationManager.NETWORK_PROVIDER
            else -> return
        }
        locations.requestLocationUpdates(provider, 1000L, 0f, l, Looper.getMainLooper())
    }

    override fun stopLocation() {
        listener?.let { locations?.removeUpdates(it) }
        listener = null
    }

    override fun speak(text: String) {
        if (tts == null) tts = createVoice()
        if (ttsReady) say(text) else queued = text
    }

    /** The voice engine starts asynchronously: the first text waits for it. */
    private fun createVoice(): TextToSpeech = TextToSpeech(activity) { status ->
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) {
            tts?.setAudioAttributes(attributes)
            tts?.language = activity.resources.configuration.locales[0]
            queued?.let(::say)
        }
        queued = null
    }.apply {
        setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = releaseFocus()
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = releaseFocus()
        })
    }

    private fun say(text: String) {
        if (Build.VERSION.SDK_INT >= 26 && focus != null) audio?.requestAudioFocus(focus)
        tts?.speak(text, TextToSpeech.QUEUE_ADD, Bundle(), "nav-${System.nanoTime()}")
    }

    private fun releaseFocus() {
        if (Build.VERSION.SDK_INT >= 26 && focus != null && tts?.isSpeaking != true) audio?.abandonAudioFocusRequest(focus)
    }

    override fun stopSpeaking() {
        tts?.stop()
        queued = null
        releaseFocus()
    }

    override fun keepScreenOn(on: Boolean) {
        if (on) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override suspend fun reroute(ride: Ride, from: LatLon, heading: Double?, stops: List<Stop>, options: RouteOptions): Ride? =
        reroute.invoke(ride, from, heading, stops, options)

    /** Frees the voice engine; call it when the activity is destroyed. */
    fun close() {
        stopLocation()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }

    private fun Location.toFix() = Fix(
        point = LatLon(latitude, longitude),
        // Monotonic clock: the wall clock can jump while riding.
        timeMs = elapsedRealtimeNanos / 1_000_000,
        speedMps = if (hasSpeed()) speed.toDouble() else null,
        bearing = if (hasBearing()) bearing.toDouble() else null,
        accuracyM = if (hasAccuracy()) accuracy.toDouble() else null,
    )
}
