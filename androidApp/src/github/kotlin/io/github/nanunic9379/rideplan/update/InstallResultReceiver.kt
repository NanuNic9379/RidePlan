package io.github.nanunic9379.rideplan.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build

/** Risposta di Android all'installazione: chiede conferma all'utente o segnala l'esito. */
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
            // Se va bene, Android chiude e sostituisce l'app: non c'e' altro da fare.
            PackageInstaller.STATUS_SUCCESS -> UpdateStore.ui = null
            PackageInstaller.STATUS_FAILURE_ABORTED -> UpdateStore.ui = UpdateUi.Failed("Installazione annullata.")
            PackageInstaller.STATUS_FAILURE_CONFLICT -> UpdateStore.ui = UpdateUi.Failed(
                "La versione installata e' firmata in modo diverso (es. una build di prova). " +
                    "Disinstallala e installa a mano l'APK dalla pagina Release.",
            )
            else -> UpdateStore.ui = UpdateUi.Failed(
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Errore sconosciuto.",
            )
        }
    }
}
