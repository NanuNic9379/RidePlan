package io.github.nanunic9379.rideplan

import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import io.github.nanunic9379.rideplan.ride.ImportActions
import io.github.nanunic9379.rideplan.ride.Ride
import io.github.nanunic9379.rideplan.ride.RideScreenState
import io.github.nanunic9379.rideplan.update.UpdateOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val rides = RideScreenState()

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) load { RideIntake.fromUri(this, uri) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val actions = ImportActions(
            openFile = { openDocument.launch(arrayOf("*/*")) },
            readClipboard = ::clipboardText,
            importText = { text -> load { RideIntake.fromText(text) } },
        )
        setContent { App(rides = rides, importActions = actions, overlay = { UpdateOverlay() }) }
        if (savedInstanceState == null) handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** "Condividi" da Google Maps (testo con il link) o apertura di un file GPX/KML/KMZ. */
    private fun handle(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val stream = streamExtra(intent)
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                when {
                    stream != null -> load { RideIntake.fromUri(this, stream) }
                    !text.isNullOrBlank() -> load { RideIntake.fromText(text) }
                }
            }
            Intent.ACTION_VIEW -> intent.data?.let { uri ->
                if (uri.scheme == "content" || uri.scheme == "file") load { RideIntake.fromUri(this, uri) }
                else load { RideIntake.fromText(uri.toString()) }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun streamExtra(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else intent.getParcelableExtra(Intent.EXTRA_STREAM)

    private fun clipboardText(): String? {
        val cm = getSystemService(ClipboardManager::class.java) ?: return null
        val clip = cm.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(this)?.toString()
    }

    private fun load(block: () -> Ride) {
        rides.message = null
        rides.busy = true
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching(block) }
            rides.show(result)
        }
    }
}
