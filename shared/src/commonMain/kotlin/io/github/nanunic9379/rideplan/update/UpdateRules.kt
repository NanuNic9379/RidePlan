package io.github.nanunic9379.rideplan.update

/**
 * Rules for updating from the GitHub Releases (#30), without platform code.
 * Release tags: "v0.1.N", with N = versionCode = number of commits on main + 100 (#46).
 * APK: "RidePlan-0.1.N-<abi>.apk".
 */
object UpdateRules {
    private val tagPattern = Regex("""^v\d+\.\d+\.(\d+)$""")
    private val hexPattern = Regex("^[0-9a-fA-F]{64}$")

    /** Version number from the tag, or null if the tag is not in the expected format. */
    fun versionCodeFromTag(tag: String): Int? =
        tagPattern.matchEntire(tag.trim())?.groupValues?.get(1)?.toIntOrNull()

    /** There is an update only if the Release has a higher number than the installed one. */
    fun isNewer(tag: String, installedVersionCode: Int): Boolean =
        (versionCodeFromTag(tag) ?: return false) > installedVersionCode

    /**
     * APK suited to the phone: the first supported processor (in order of preference)
     * for which the Release has an APK.
     */
    fun pickApk(assetNames: List<String>, supportedAbis: List<String>): String? =
        supportedAbis.firstNotNullOfOrNull { abi ->
            assetNames.firstOrNull { it.endsWith("-$abi.apk") }
        }

    /** SHA-256 from GitHub's "digest" field ("sha256:<64 hex digits>"), lowercase. */
    fun sha256FromDigest(digest: String?): String? =
        digest?.removePrefix("sha256:")?.takeIf { hexPattern.matches(it) }?.lowercase()
}

/** What the update banner shows. */
sealed interface UpdateUi {
    data class Available(val versionName: String) : UpdateUi
    data class Downloading(val percent: Int?) : UpdateUi
    /** Android asks for the "install unknown apps" permission for RidePlan. */
    data object NeedsPermission : UpdateUi
    data object Installing : UpdateUi
    /** [detail]: untranslated technical detail (HTTP code, system message). */
    data class Failed(val reason: UpdateError, val detail: String? = null) : UpdateUi
}

/** Why the update failed. [UpdateBanner] picks the translated text (#33). */
enum class UpdateError { NETWORK, DOWNLOAD_HTTP, CHECKSUM, ABORTED, SIGNATURE_CONFLICT, OTHER }

/** Update error with its reason. */
class UpdateException(val reason: UpdateError, val detail: String? = null) : Exception("$reason${detail?.let { ": $it" } ?: ""}")
