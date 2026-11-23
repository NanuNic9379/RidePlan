package io.github.nanunic9379.rideplan.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Avviso in alto sopra la mappa. Non blocca: "Dopo" lo chiude fino al prossimo avvio. */
@Composable
fun UpdateBanner(
    state: UpdateUi,
    onUpdate: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier.safeDrawingPadding().padding(12.dp).fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (state) {
                is UpdateUi.Available -> {
                    Text("Nuova versione ${state.versionName}", style = MaterialTheme.typography.titleMedium)
                    Text("Si scarica dalla pagina Release del progetto e Android ti chiede conferma.")
                    Actions(primary = "Aggiorna", onPrimary = onUpdate, onDismiss = onDismiss)
                }
                is UpdateUi.Downloading -> {
                    Text("Scarico l'aggiornamento...", style = MaterialTheme.typography.titleMedium)
                    val p = state.percent
                    if (p == null) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(progress = { p / 100f }, modifier = Modifier.fillMaxWidth())
                    }
                }
                UpdateUi.NeedsPermission -> {
                    Text("Serve un permesso", style = MaterialTheme.typography.titleMedium)
                    Text("Nelle impostazioni attiva \"Consenti da questa fonte\" per RidePlan, poi torna qui e premi di nuovo Aggiorna.")
                    Actions(primary = "Apri impostazioni", onPrimary = onOpenSettings, onDismiss = onDismiss)
                }
                UpdateUi.Installing -> {
                    Text("Installazione", style = MaterialTheme.typography.titleMedium)
                    Text("Conferma nella finestra di Android. Poi riapri RidePlan.")
                }
                is UpdateUi.Failed -> {
                    Text("Aggiornamento non riuscito", style = MaterialTheme.typography.titleMedium)
                    Text(state.message)
                    Actions(primary = "Riprova", onPrimary = onUpdate, onDismiss = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun Actions(primary: String, onPrimary: () -> Unit, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onDismiss) { Text("Dopo") }
        Button(onClick = onPrimary) { Text(primary) }
    }
}
