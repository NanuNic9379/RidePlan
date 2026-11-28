package io.github.nanunic9379.rideplan

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import io.github.nanunic9379.rideplan.nav.NowPlayingInfo

/** Process-local, transient media state. Track text is never persisted or logged. */
internal object MediaSessionBridge {
    private val currentState = mutableStateOf<NowPlayingInfo?>(null)
    val current: State<NowPlayingInfo?> get() = currentState
    private var listener: RidePlanMediaSessionListener? = null
    @Volatile private var navigationActive = false
    private var accessState = mutableStateOf(false)
    val accessGranted: State<Boolean> get() = accessState

    fun refreshAccess(context: Context) {
        val component = ComponentName(context, RidePlanMediaSessionListener::class.java).flattenToString()
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            ?.split(':')?.any { it.equals(component, ignoreCase = true) } == true
        accessState.value = enabled
        if (!enabled) clear()
        listener?.refresh()
    }

    fun attach(service: RidePlanMediaSessionListener?) {
        listener = service
        if (service == null) clear() else service.refresh()
    }

    fun navigationActive(active: Boolean) {
        navigationActive = active
        if (active) listener?.refresh() else clear()
    }

    internal fun isReadingAllowed(context: Context): Boolean = navigationActive && accessState.value

    internal fun publish(value: NowPlayingInfo?) {
        currentState.value = value
    }

    private fun clear() {
        currentState.value = null
        RidePlanMediaSessionListener.clearActiveController()
    }
}

/** Uses only public Android MediaSession APIs; no player-specific SDKs or notification text. */
class RidePlanMediaSessionListener : NotificationListenerService() {
    private val controllers = mutableMapOf<MediaController, MediaController.Callback>()
    private val callback = object : MediaSessionManager.OnActiveSessionsChangedListener {
        override fun onActiveSessionsChanged(sessions: MutableList<MediaController>?) = update(sessions.orEmpty())
    }

    override fun onListenerConnected() {
        MediaSessionBridge.attach(this)
        runCatching {
            getSystemService(MediaSessionManager::class.java).addOnActiveSessionsChangedListener(
                callback, ComponentName(this, RidePlanMediaSessionListener::class.java), android.os.Handler(mainLooper),
            )
        }
        refresh()
    }

    override fun onListenerDisconnected() {
        runCatching { getSystemService(MediaSessionManager::class.java).removeOnActiveSessionsChangedListener(callback) }
        controllers.keys.toList().forEach(::removeController)
        MediaSessionBridge.attach(null)
    }

    override fun onDestroy() {
        onListenerDisconnected()
        super.onDestroy()
    }

    internal fun refresh() {
        if (!MediaSessionBridge.isReadingAllowed(this)) {
            MediaSessionBridge.publish(null)
            return
        }
        val active = runCatching {
            getSystemService(MediaSessionManager::class.java).getActiveSessions(
                ComponentName(this, RidePlanMediaSessionListener::class.java),
            )
        }.getOrDefault(emptyList())
        update(active)
    }

    private fun update(sessions: List<MediaController>) {
        if (!MediaSessionBridge.isReadingAllowed(this)) {
            MediaSessionBridge.publish(null)
            return
        }
        (controllers.keys - sessions.toSet()).forEach(::removeController)
        sessions.filterNot(controllers::containsKey).forEach { controller ->
            val observer = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) = publishBest()
                override fun onPlaybackStateChanged(state: PlaybackState?) = publishBest()
                override fun onSessionDestroyed() { removeController(controller); publishBest() }
            }
            controllers[controller] = observer
            runCatching { controller.registerCallback(observer) }
        }
        publishBest()
    }

    private fun removeController(controller: MediaController) {
        controllers.remove(controller)?.let { observer -> runCatching { controller.unregisterCallback(observer) } }
    }

    private fun publishBest() {
        if (!MediaSessionBridge.isReadingAllowed(this)) {
            MediaSessionBridge.publish(null)
            return
        }
        val available = controllers.keys.mapNotNull { controller ->
            val state = runCatching { controller.playbackState }.getOrNull() ?: return@mapNotNull null
            if (state.state != PlaybackState.STATE_PLAYING && state.state != PlaybackState.STATE_PAUSED) return@mapNotNull null
            val metadata = runCatching { controller.metadata }.getOrNull() ?: return@mapNotNull null
            val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim()?.takeIf(String::isNotEmpty)
                ?: return@mapNotNull null
            val actions = state.actions
            val canToggle = actions and (PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE) != 0L
            val canSkip = actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L
            Triple(
                NowPlayingInfo(
                    title = title.take(100),
                    artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)?.trim()?.takeIf(String::isNotEmpty)?.take(80),
                    isPlaying = state.state == PlaybackState.STATE_PLAYING,
                    canToggle = canToggle,
                    canSkipNext = canSkip,
                ),
                controller,
                state.state == PlaybackState.STATE_PLAYING,
            )
        }
        val selected = available.firstOrNull { it.third } ?: available.firstOrNull()
        MediaSessionBridge.publish(selected?.first)
        activeController = selected?.second
    }

    companion object {
        @Volatile private var activeController: MediaController? = null
        internal fun clearActiveController() { activeController = null }
        internal fun togglePlayback() {
            val controller = activeController ?: return
            val state = runCatching { controller.playbackState?.state }.getOrNull()
            runCatching {
                if (state == PlaybackState.STATE_PLAYING) controller.transportControls.pause()
                else controller.transportControls.play()
            }
        }
        internal fun skipNext() { runCatching { activeController?.transportControls?.skipToNext() } }
    }
}
