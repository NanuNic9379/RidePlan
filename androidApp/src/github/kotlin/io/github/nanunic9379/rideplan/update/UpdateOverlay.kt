package io.github.nanunic9379.rideplan.update

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.nanunic9379.rideplan.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Stato dell'avviso, condiviso con [InstallResultReceiver]. */
object UpdateStore {
    var ui by mutableStateOf<UpdateUi?>(null)
}

/** Controlla una volta per avvio se c'e' una Release nuova e mostra l'avviso. */
@Composable
fun BoxScope.UpdateOverlay() {
    // Le build di prova hanno un'altra firma: l'aggiornamento non si installerebbe.
    if (BuildConfig.DEBUG) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var release by remember { mutableStateOf<ReleaseInfo?>(null) }

    LaunchedEffect(Unit) {
        val found = withContext(Dispatchers.IO) { runCatching { GithubUpdater.latestNewer() }.getOrNull() }
        if (found != null) {
            release = found
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
