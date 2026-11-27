package io.github.nanunic9379.rideplan.social

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The signer app (NIP-55) that holds the user's key: RidePlan only knows the public key. */
@Serializable
data class SignerInfo(val pubkey: String, val packageName: String) {
    val npub: String get() = Nip19.npub(pubkey)
}

/** A ride you published (#3): its address, so the link can be shared again and republished. */
@Serializable
data class PublishedRide(
    val savedId: String,
    val pubkey: String,
    val d: String,
    /** Publishing time of the last version sent, seconds. */
    val createdAt: Long,
    val versionNumber: Int,
    /** Relays that accepted it. */
    val relays: List<String> = emptyList(),
) {
    val address: NostrAddress get() = NostrAddress(RideEvent.KIND, pubkey, d, relays)
}

/** A photo spot's stable public Nostr address and the relays that accepted its latest version. */
@Serializable
data class PublishedPhotoSpot(
    val savedId: String,
    val pubkey: String,
    val d: String,
    val createdAt: Long,
    val relays: List<String> = emptyList(),
) {
    val address: NostrAddress get() = NostrAddress(PhotoSpotEvent.KIND, pubkey, d, relays)
}

/**
 * A ride followed from Nostr (#11): where it lives, which ride in "Your rides" it is, and the
 * last version received ([lastEvent], the signed event), kept when relays do not answer.
 */
@Serializable
data class FollowedRide(
    val savedId: String,
    val kind: Int,
    val pubkey: String,
    val d: String,
    val hints: List<String> = emptyList(),
    val lastEvent: String? = null,
    val lastVersion: Int = 0,
    /** Last time a check reached at least one relay, milliseconds; 0 = never. */
    val checkedAt: Long = 0,
) {
    val address: NostrAddress get() = NostrAddress(kind, pubkey, d, hints)
}

/**
 * Nostr settings and state (#3). [enabled] is off by default: until the user turns it on, and
 * then until they tap Publish or check a ride, nothing goes to any relay.
 */
@Serializable
data class SocialData(
    val enabled: Boolean = false,
    val relays: List<String> = Relays.DEFAULTS.map { it.url },
    val signer: SignerInfo? = null,
    val published: List<PublishedRide> = emptyList(),
    val publishedPhotoSpots: List<PublishedPhotoSpot> = emptyList(),
    val followed: List<FollowedRide> = emptyList(),
) {
    fun publishedFor(savedId: String): PublishedRide? = published.firstOrNull { it.savedId == savedId }
    fun publishedPhotoSpotFor(savedId: String): PublishedPhotoSpot? = publishedPhotoSpots.firstOrNull { it.savedId == savedId }
    fun followedFor(savedId: String): FollowedRide? = followed.firstOrNull { it.savedId == savedId }

    fun withPublished(p: PublishedRide) = copy(published = listOf(p) + published.filter { it.savedId != p.savedId })
    fun withPublishedPhotoSpot(p: PublishedPhotoSpot) = copy(publishedPhotoSpots = listOf(p) + publishedPhotoSpots.filter { it.savedId != p.savedId })
    fun withFollowed(f: FollowedRide) = copy(followed = listOf(f) + followed.filter { it.savedId != f.savedId && it.address.coordinate != f.address.coordinate })

    /** Rides deleted from "Your rides" no longer need their Nostr state. */
    fun keepOnly(savedIds: Set<String>) = copy(
        published = published.filter { it.savedId in savedIds },
        followed = followed.filter { it.savedId in savedIds },
    )
}

/** [SocialData] in its own file (nostr.json), next to the profile, never in the backup. */
@Stable
class SocialStore(private val read: () -> String?, private val write: (String) -> Unit) {
    var data by mutableStateOf(load())
        private set

    fun update(change: (SocialData) -> SocialData) {
        val fresh = change(data)
        if (fresh == data) return
        data = fresh
        write(encode(fresh))
    }

    /** "Delete all data": back to the defaults. */
    fun wipe() = update { SocialData() }

    private fun load(): SocialData = read()?.let { runCatching { decode(it) }.getOrNull() } ?: SocialData()

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun encode(data: SocialData): String = json.encodeToString(SocialData.serializer(), data)
        fun decode(text: String): SocialData = json.decodeFromString(SocialData.serializer(), text)
    }
}
