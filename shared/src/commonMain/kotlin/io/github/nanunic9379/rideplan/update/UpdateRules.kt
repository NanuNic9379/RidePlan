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

    /** Minimum time between two automatic checks (#34): well under GitHub's anonymous rate limit. */
    const val AUTO_CHECK_INTERVAL_MS: Long = 15 * 60 * 1000L

    /**
     * Whether coming back to the foreground should ask GitHub again (#34): never checked yet,
     * or the last automatic check is at least [AUTO_CHECK_INTERVAL_MS] old. A clock that went
     * backwards counts as "time to check".
     */
    fun shouldAutoCheck(nowMs: Long, lastCheckMs: Long?): Boolean =
        lastCheckMs == null || nowMs < lastCheckMs || nowMs - lastCheckMs >= AUTO_CHECK_INTERVAL_MS

    /**
     * Whether an automatic check that found [found] may show the banner (#34): not over a
     * download, install or error in progress, and not for the version the rider dismissed.
     */
    fun mayOfferAutomatically(found: String, current: UpdateUi?, dismissed: String?): Boolean =
        (current == null || current is UpdateUi.Available) && found != dismissed
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
