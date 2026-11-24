package io.github.nanunic9379.rideplan.update

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.nanunic9379.rideplan.BuildConfig
import io.github.nanunic9379.rideplan.personal.UpdateActions
import io.github.nanunic9379.rideplan.personal.UpdateCheck
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** State of the banner, shared with [InstallResultReceiver] and the card on Info & updates. */
object UpdateStore {
    var ui by mutableStateOf<UpdateUi?>(null)
    /** The newer Release found by the last check. */
    var release by mutableStateOf<ReleaseInfo?>(null)
    /** When the last automatic check started (#34); null until the first one. */
    var lastAutoCheckMs: Long? = null
    /** The version whose banner the rider closed: not offered again automatically (#34). */
    var dismissed: String? = null
}

/**
 * The card on Info & updates (#21): asks GitHub now and, if there is a new Release, brings the
 * banner back even after it was closed.
 */
fun updateActions(scope: CoroutineScope): UpdateActions {
    lateinit var actions: UpdateActions
    actions = UpdateActions(check = {
        if (actions.status != UpdateCheck.CHECKING) {
            actions.status = UpdateCheck.CHECKING
            scope.launch {
                withContext(Dispatchers.IO) { runCatching { GithubUpdater.latestNewer() } }
                    .onSuccess { r ->
                        if (r == null) {
                            actions.status = UpdateCheck.LATEST
                        } else {
                            UpdateStore.release = r
                            actions.available = r.versionName
                            actions.status = UpdateCheck.AVAILABLE
                            // Test builds never offer the install: the signature would not match.
                            if (!BuildConfig.DEBUG) UpdateStore.ui = UpdateUi.Available(r.versionName)
                        }
                    }
                    .onFailure { actions.status = UpdateCheck.FAILED }
            }
        }
    })
    return actions
}

/**
 * If the user wants it, checks whether there is a new Release every time the app comes to the
 * foreground (#34): at launch and when it returns from the background, at most once every
 * [UpdateRules.AUTO_CHECK_INTERVAL_MS], and shows the banner.
 */
@Composable
fun BoxScope.UpdateOverlay(checkAtStart: Boolean) {
    // Test builds have a different signature: the update would not install.
    if (BuildConfig.DEBUG) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val release = UpdateStore.release
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val enabled by rememberUpdatedState(checkAtStart)

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_START || !enabled) return@LifecycleEventObserver
            // A banner is already on screen: no polling, "Update" fetches the newest Release anyway.
            if (UpdateRules.pollingPaused(UpdateStore.ui)) return@LifecycleEventObserver
            val now = System.currentTimeMillis()
            if (!UpdateRules.shouldAutoCheck(now, UpdateStore.lastAutoCheckMs)) return@LifecycleEventObserver
            UpdateStore.lastAutoCheckMs = now
            scope.launch {
                val found = withContext(Dispatchers.IO) { runCatching { GithubUpdater.latestNewer() }.getOrNull() }
                if (found != null && UpdateRules.mayOfferAutomatically(found.versionName, UpdateStore.ui, UpdateStore.dismissed)) {
                    UpdateStore.release = found
                    UpdateStore.ui = UpdateUi.Available(found.versionName)
                }
            }
        }
        // Also delivers ON_START right away when the screen is already started.
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val ui = UpdateStore.ui ?: return
    UpdateBanner(
        state = ui,
        modifier = Modifier.align(Alignment.TopCenter),
        onDismiss = {
            (ui as? UpdateUi.Available)?.let { UpdateStore.dismissed = it.versionName }
            UpdateStore.ui = null
        },
        onOpenSettings = {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
            )
            UpdateStore.ui = release?.let { UpdateUi.Available(it.versionName) }
        },
        onUpdate = update@{
            val r = release ?: return@update
            if (!context.packageManager.canRequestPackageInstalls()) {
                UpdateStore.ui = UpdateUi.NeedsPermission
                return@update
            }
            scope.launch {
                UpdateStore.ui = UpdateUi.Downloading(null)
                try {
                    withContext(Dispatchers.IO) {
                        // The banner may be hours old: always install the newest Release (#34).
                        val target = runCatching { GithubUpdater.latestNewer() }.getOrNull() ?: r
                        UpdateStore.release = target
                        val apk = GithubUpdater.download(context, target) { UpdateStore.ui = UpdateUi.Downloading(it) }
                        UpdateStore.ui = UpdateUi.Installing
                        GithubUpdater.install(context, apk)
                    }
                } catch (e: UpdateException) {
                    UpdateStore.ui = UpdateUi.Failed(e.reason, e.detail)
                } catch (e: java.io.IOException) {
                    UpdateStore.ui = UpdateUi.Failed(UpdateError.NETWORK, e.javaClass.simpleName)
                } catch (e: Exception) {
                    UpdateStore.ui = UpdateUi.Failed(UpdateError.OTHER, e.message ?: e.javaClass.simpleName)
                }
            }
        },
    )
}
