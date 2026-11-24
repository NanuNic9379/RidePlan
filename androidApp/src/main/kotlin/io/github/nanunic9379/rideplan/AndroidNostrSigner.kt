package io.github.nanunic9379.rideplan

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import io.github.nanunic9379.rideplan.social.Nip19
import io.github.nanunic9379.rideplan.social.NostrSigner
import io.github.nanunic9379.rideplan.social.SignerInfo
import kotlinx.coroutines.CompletableDeferred

/**
 * The key of the rider stays in a signer app such as Amber (NIP-55, #3): RidePlan asks it
 * for the public key and for each signature through intents, and never sees the private key.
 *
 * Create it while the activity is being created: the result launcher must be registered then.
 */
class AndroidNostrSigner(private val activity: ComponentActivity) : NostrSigner {
    private var pending: CompletableDeferred<ActivityResult>? = null

    private val launcher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        pending?.complete(result)
        pending = null
    }

    override fun installed(): Boolean =
        activity.packageManager.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse(SCHEME)), 0).isNotEmpty()

    override suspend fun connect(): SignerInfo? {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(SCHEME)).putExtra("type", "get_public_key")
        val data = launch(intent) ?: return null
        val key = (data.getStringExtra("result") ?: data.getStringExtra("signature"))?.let(Nip19::pubkey) ?: return null
        val pkg = data.getStringExtra("package")
            ?: activity.packageManager.queryIntentActivities(intent, 0).singleOrNull()?.activityInfo?.packageName
            ?: return null
        return SignerInfo(key, pkg)
    }

    override suspend fun sign(unsignedJson: String, id: String, signer: SignerInfo): String? {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(SCHEME + unsignedJson))
            .setPackage(signer.packageName)
            .putExtra("type", "sign_event")
            .putExtra("id", id)
            .putExtra("current_user", signer.pubkey)
        val data = launch(intent) ?: return null
        // The signed event when the signer gives it, else the bare signature: both are checked.
        return data.getStringExtra("event") ?: data.getStringExtra("result") ?: data.getStringExtra("signature")
    }

    private suspend fun launch(intent: Intent): Intent? {
        pending?.cancel()
        val wait = CompletableDeferred<ActivityResult>()
        pending = wait
        try {
            launcher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            pending = null
            return null
        }
        val result = wait.await()
        return result.data.takeIf { result.resultCode == Activity.RESULT_OK }
    }

    private companion object {
        const val SCHEME = "nostrsigner:"
    }
}
