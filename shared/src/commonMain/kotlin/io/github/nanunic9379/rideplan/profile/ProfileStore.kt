package io.github.nanunic9379.rideplan.profile

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.Json

/**
 * Il profilo sul telefono (#41): un file JSON che legge e scrive la piattaforma.
 * Se il file è rovinato non si perde l'app: si riparte da un profilo nuovo e
 * il file vecchio lo mette da parte [backup].
 */
@Stable
class ProfileStore(
    private val read: () -> String?,
    private val write: (String) -> Unit,
    private val backup: (String) -> Unit = {},
    private val now: () -> Long,
) {
    var data by mutableStateOf(load())
        private set

    fun update(change: (ProfileData) -> ProfileData) {
        val fresh = change(data)
        if (fresh == data) return
        data = fresh
        write(encode(fresh))
    }

    fun time(): Long = now()

    /** Esito dell'ultima copia o ripristino, da mostrare nel profilo; null = niente da dire. */
    var backupNotice by mutableStateOf<BackupNotice?>(null)

    /** Il testo da salvare come copia (#43): lo stesso formato del file sul telefono. */
    fun backupText(): String = encode(data)

    /**
     * Unisce una copia salvata prima (#43) e dice quanti giri nuovi sono arrivati.
     * Se il file non è una copia di RidePlan lancia un'eccezione e non tocca niente.
     */
    fun restore(text: String): Int {
        val (merged, added) = data.merge(decode(text))
        update { merged }
        return added
    }

    private fun load(): ProfileData {
        val text = read()
        if (text.isNullOrBlank()) return ProfileData.fresh(now()).also { write(encode(it)) }
        return runCatching { decode(text) }.getOrElse {
            backup(text)
            ProfileData.fresh(now()).also { write(encode(it)) }
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun encode(data: ProfileData): String = json.encodeToString(ProfileData.serializer(), data)
        fun decode(text: String): ProfileData = json.decodeFromString(ProfileData.serializer(), text)
    }
}

/** Come è andata la copia del profilo (#43). */
sealed interface BackupNotice {
    data object Saved : BackupNotice
    data class Restored(val added: Int) : BackupNotice
    data object Failed : BackupNotice
}

/** Salvare e ripristinare la copia del profilo, su questa piattaforma (#43). */
class BackupActions(
    val save: () -> Unit,
    val restore: () -> Unit,
)
