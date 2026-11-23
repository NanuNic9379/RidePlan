package io.github.nanunic9379.rideplan.update

/**
 * Regole dell'aggiornamento dalle Release di GitHub (#30), senza codice di piattaforma.
 * Tag delle Release: "v0.1.N", con N = versionCode = numero di commit su main + 100 (#46).
 * APK: "RidePlan-0.1.N-<abi>.apk".
 */
object UpdateRules {
    private val tagPattern = Regex("""^v\d+\.\d+\.(\d+)$""")
    private val hexPattern = Regex("^[0-9a-fA-F]{64}$")

    /** Numero di versione dal tag, oppure null se il tag non e' nel formato atteso. */
    fun versionCodeFromTag(tag: String): Int? =
        tagPattern.matchEntire(tag.trim())?.groupValues?.get(1)?.toIntOrNull()

    /** C'e' un aggiornamento solo se la Release ha un numero piu' alto di quello installato. */
    fun isNewer(tag: String, installedVersionCode: Int): Boolean =
        (versionCodeFromTag(tag) ?: return false) > installedVersionCode

    /**
     * APK adatto al telefono: il primo processore supportato (in ordine di preferenza)
     * per cui esiste un APK nella Release.
     */
    fun pickApk(assetNames: List<String>, supportedAbis: List<String>): String? =
        supportedAbis.firstNotNullOfOrNull { abi ->
            assetNames.firstOrNull { it.endsWith("-$abi.apk") }
        }

    /** SHA-256 dal campo "digest" di GitHub ("sha256:<64 esadecimali>"), in minuscolo. */
    fun sha256FromDigest(digest: String?): String? =
        digest?.removePrefix("sha256:")?.takeIf { hexPattern.matches(it) }?.lowercase()
}

/** Cosa mostra l'avviso di aggiornamento. */
sealed interface UpdateUi {
    data class Available(val versionName: String) : UpdateUi
    data class Downloading(val percent: Int?) : UpdateUi
    /** Android chiede il permesso "installa app sconosciute" per RidePlan. */
    data object NeedsPermission : UpdateUi
    data object Installing : UpdateUi
    /** [detail]: dato tecnico non tradotto (codice HTTP, messaggio di sistema). */
    data class Failed(val reason: UpdateError, val detail: String? = null) : UpdateUi
}

/** Perche' l'aggiornamento non e' riuscito. Il testo tradotto lo sceglie [UpdateBanner] (#33). */
enum class UpdateError { NETWORK, DOWNLOAD_HTTP, CHECKSUM, ABORTED, SIGNATURE_CONFLICT, OTHER }

/** Errore dell'aggiornamento con il suo motivo. */
class UpdateException(val reason: UpdateError, val detail: String? = null) : Exception("$reason${detail?.let { ": $it" } ?: ""}")
