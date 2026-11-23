package io.github.nanunic9379.rideplan.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build

/** Android's response to the installation: asks the user to confirm or reports the outcome. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(context::startActivity)
            }
            // On success, Android closes and replaces the app: nothing else to do.
            PackageInstaller.STATUS_SUCCESS -> UpdateStore.ui = null
            PackageInstaller.STATUS_FAILURE_ABORTED -> UpdateStore.ui = UpdateUi.Failed(UpdateError.ABORTED)
            PackageInstaller.STATUS_FAILURE_CONFLICT -> UpdateStore.ui = UpdateUi.Failed(UpdateError.SIGNATURE_CONFLICT)
            else -> UpdateStore.ui = UpdateUi.Failed(
                UpdateError.OTHER,
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE),
            )
        }
    }
}
