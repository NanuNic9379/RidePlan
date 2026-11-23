package io.github.nanunic9379.rideplan.profile

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.Json

/**
 * The profile on the phone (#41): a JSON file read and written by the platform.
 * If the file is corrupted the app is not lost: it starts again from a new profile and
 * [backup] sets the old file aside.
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

    /** Outcome of the last backup or restore, to show in the profile; null = nothing to say. */
    var backupNotice by mutableStateOf<BackupNotice?>(null)

    /** The text to save as a backup (#43): the same format as the file on the phone. */
    fun backupText(): String = encode(data)

    /**
     * Merges a previously saved backup (#43) and returns how many new rides arrived.
     * If the file is not a RidePlan backup it throws and touches nothing.
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

/** How the profile backup went (#43). */
sealed interface BackupNotice {
    data object Saved : BackupNotice
    data class Restored(val added: Int) : BackupNotice
    data object Failed : BackupNotice
}

/** Saving and restoring the profile backup, on this platform (#43). */
class BackupActions(
    val save: () -> Unit,
    val restore: () -> Unit,
)
