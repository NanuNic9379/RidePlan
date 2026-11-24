package io.github.nanunic9379.rideplan.update

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
    /** Checked already in this launch: the start check runs once, not at every recreation. */
    var checkedAtStart = false
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

/** Checks once per launch, if the user wants it, whether there is a new Release and shows the banner. */
@Composable
fun BoxScope.UpdateOverlay(checkAtStart: Boolean) {
    // Test builds have a different signature: the update would not install.
    if (BuildConfig.DEBUG) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val release = UpdateStore.release

    LaunchedEffect(checkAtStart) {
        if (!checkAtStart || UpdateStore.checkedAtStart) return@LaunchedEffect
        UpdateStore.checkedAtStart = true
        val found = withContext(Dispatchers.IO) { runCatching { GithubUpdater.latestNewer() }.getOrNull() }
        if (found != null) {
            UpdateStore.release = found
            UpdateStore.ui = UpdateUi.Available(found.versionName)
        }
    }

    val ui = UpdateStore.ui ?: return
    UpdateBanner(
        state = ui,
        modifier = Modifier.align(Alignment.TopCenter),
        onDismiss = { UpdateStore.ui = null },
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
                        val apk = GithubUpdater.download(context, r) { UpdateStore.ui = UpdateUi.Downloading(it) }
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
