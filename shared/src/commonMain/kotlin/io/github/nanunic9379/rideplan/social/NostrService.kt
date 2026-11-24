package io.github.nanunic9379.rideplan.social

import androidx.compose.runtime.Stable
import io.github.nanunic9379.rideplan.profile.ProfileStore
import io.github.nanunic9379.rideplan.ride.RideVersion
import io.github.nanunic9379.rideplan.ride.SharePreview
import io.github.nanunic9379.rideplan.route.RouteOptions

/**
 * A signer app on the phone (NIP-55, e.g. Amber). RidePlan never sees the secret key (#3).
 * Every call opens the signer, so it only happens when the user taps.
 */
interface NostrSigner {
    /** A signer app is installed. */
    fun installed(): Boolean

    /** Asks for the user's public key; null if cancelled or no signer. */
    suspend fun connect(): SignerInfo?

    /**
     * Asks to sign [unsignedJson] (event [id]). Returns the signed event JSON or just the
     * 64-byte signature in hex, as signers answer; null if refused or cancelled.
     */
    suspend fun sign(unsignedJson: String, id: String, signer: SignerInfo): String?
}

/** Why publishing stopped. */
enum class PublishError { NO_SIGNER, CANCELLED, BAD_SIGNATURE, NOTHING_TO_SHARE, TOO_BIG, NO_RELAYS, NOBODY_ACCEPTED }

sealed interface PublishOutcome {
    data class Done(val accepted: Int, val total: Int, val naddr: String) : PublishOutcome
    data class Failed(val error: PublishError) : PublishOutcome
}

/** Result of following a ride or checking it for a new version. */
sealed interface FollowOutcome {
    /** A newer version arrived (or the ride, the first time) and is saved as [savedId]. */
    data class Updated(val savedId: String, val version: Int) : FollowOutcome
    /** Relays have nothing newer than what is on the phone. */
    data class UpToDate(val savedId: String, val version: Int) : FollowOutcome
    /** No relay answered: the last version received stays. */
    data object NoAnswer : FollowOutcome
    /** Relays answered without a valid version of this ride. */
    data object NotFound : FollowOutcome
    /** The text is not the address of a RidePlan ride. */
    data object BadAddress : FollowOutcome
}

/**
 * Publishing rides and following them on Nostr (#3, #11), phase 1 of docs/social.md:
 * the key stays in a signer app, relays are public ones chosen by the user, no server of ours.
 * Called from the UI thread: the profile and these settings are Compose state.
 */
@Stable
class NostrService(
    val store: SocialStore,
    private val profile: ProfileStore,
    val signer: NostrSigner?,
    private val pool: RelayPool,
    private val verifier: SchnorrVerifier = Secp256k1Verifier,
) {
    val data: SocialData get() = store.data

    suspend fun connectSigner(): Boolean {
        val info = signer?.connect() ?: return false
        store.update { it.copy(signer = info) }
        return true
    }

    fun disconnectSigner() = store.update { it.copy(signer = null) }

    /**
     * Publishes saved ride [savedId] as it would be shared ([preview]: private zones and
     * trimmed ends already applied). A new publish of the same ride replaces the old one on
     * relays (same address), so followers get the new version.
     */
    suspend fun publish(savedId: String, preview: SharePreview, options: RouteOptions?, summary: String): PublishOutcome {
        val info = data.signer ?: return PublishOutcome.Failed(PublishError.NO_SIGNER)
        val s = signer ?: return PublishOutcome.Failed(PublishError.NO_SIGNER)
        if (preview.empty) return PublishOutcome.Failed(PublishError.NOTHING_TO_SHARE)
        val relays = data.relays
        if (relays.isEmpty()) return PublishOutcome.Failed(PublishError.NO_RELAYS)
        // Rides saved before versions existed get their first one now: the address needs it.
        val version = preview.ride.version ?: RideVersion.first(profile.time()).also { v ->
            profile.update { p -> p.copy(rides = p.rides.map { if (it.id == savedId && it.version == null) it.copy(version = v) else it }) }
        }
        val shared = preview.copy(ride = preview.ride.copy(version = version))
        val draft = runCatching { RideEvent.draft(shared, version.rideId, options, summary) }
            .getOrElse { return PublishOutcome.Failed(PublishError.TOO_BIG) }
        val createdAt = profile.time() / 1000
        val id = NostrEvent.idOf(draft, info.pubkey, createdAt)
        val answer = s.sign(draft.toUnsignedJson(createdAt, info.pubkey), id, info)
            ?: return PublishOutcome.Failed(PublishError.CANCELLED)
        val event = Signing.signedEvent(answer, draft, info.pubkey, createdAt, verifier)
            ?: return PublishOutcome.Failed(PublishError.BAD_SIGNATURE)
        val results = pool.publish(event, relays)
        val accepted = results.filterValues { it == PublishResult.ACCEPTED }.keys.toList()
        if (accepted.isEmpty()) return PublishOutcome.Failed(PublishError.NOBODY_ACCEPTED)
        val published = PublishedRide(savedId, info.pubkey, version.rideId, createdAt, version.number, accepted)
        store.update { it.withPublished(published) }
        return PublishOutcome.Done(accepted.size, relays.size, Nip19.naddr(published.address.copy(relays = accepted.take(2))))
    }

    /** Follows the ride at [text] (an `naddr`): fetches it and saves it in "Received". */
    suspend fun follow(text: String): FollowOutcome {
        val address = Nip19.parseNaddr(text)?.takeIf { it.kind == RideEvent.KIND } ?: return FollowOutcome.BadAddress
        val existing = profile.data.rides.firstOrNull { it.version?.rideId == address.d }
        val known = existing?.let { data.followedFor(it.id) }
        return check(address, existing?.id, known)
    }

    /** Looks for a newer version of followed ride [savedId]; does nothing if it is not followed. */
    suspend fun checkUpdate(savedId: String): FollowOutcome? {
        val followed = data.followedFor(savedId) ?: return null
        return check(followed.address, savedId, followed)
    }

    private suspend fun check(address: NostrAddress, savedId: String?, known: FollowedRide?): FollowOutcome {
        val relays = Relays.forReading(data.relays, address.relays)
        if (relays.isEmpty()) return FollowOutcome.NoAnswer
        val fetched = pool.fetch(NostrFilter.of(address), relays)
        val current = savedId?.let { id -> profile.data.rides.firstOrNull { it.id == id }?.version }
        val now = profile.time()
        return when (val outcome = RideUpdates.pick(address, fetched.events, fetched.answered > 0, current, verifier)) {
            RideUpdates.Outcome.NoAnswer -> FollowOutcome.NoAnswer
            RideUpdates.Outcome.NotFound -> FollowOutcome.NotFound
            is RideUpdates.Outcome.UpToDate -> {
                val id = savedId ?: return FollowOutcome.NotFound
                remember(address, id, known, outcome.best, now)
                FollowOutcome.UpToDate(id, current?.number ?: outcome.best.version.number)
            }
            is RideUpdates.Outcome.Newer -> {
                var newId = savedId
                profile.update { p ->
                    val (d, id) = p.receive(outcome.best.shared.ride, now)
                    newId = id
                    d
                }
                val id = newId ?: return FollowOutcome.NotFound
                remember(address, id, known, outcome.best, now)
                FollowOutcome.Updated(id, outcome.best.version.number)
            }
        }
    }

    private fun remember(address: NostrAddress, savedId: String, known: FollowedRide?, best: RideUpdates.Candidate, now: Long) {
        val keep = known?.takeIf { it.lastVersion > best.version.number }
        store.update {
            it.withFollowed(
                FollowedRide(
                    savedId = savedId,
                    kind = address.kind,
                    pubkey = address.pubkey,
                    d = address.d,
                    hints = address.relays.mapNotNull(Relays::normalize).ifEmpty { known?.hints.orEmpty() },
                    lastEvent = keep?.lastEvent ?: best.event.toJson(),
                    lastVersion = keep?.lastVersion ?: best.version.number,
                    checkedAt = now,
                ),
            )
        }
    }

    fun setEnabled(on: Boolean) = store.update { it.copy(enabled = on) }

    /** False when [text] is not a valid relay, is already there, or the list is full. */
    fun addRelay(text: String): Boolean {
        val list = Relays.add(data.relays, text) ?: return false
        store.update { it.copy(relays = list) }
        return true
    }

    fun removeRelay(url: String) = store.update { it.copy(relays = it.relays - url) }

    fun resetRelays() = store.update { it.copy(relays = Relays.DEFAULTS.map { d -> d.url }) }

    fun unfollow(savedId: String) = store.update { it.copy(followed = it.followed.filter { f -> f.savedId != savedId }) }
}

/** What the signer answers, turned into an event that is checked before it leaves the phone. */
object Signing {
    /**
     * [answer]: a signed event JSON or a hex signature. The result must have exactly the content
     * that was asked to be signed, by [pubkey], with a valid signature; otherwise null.
     */
    fun signedEvent(answer: String, draft: NostrDraft, pubkey: String, createdAt: Long, verifier: SchnorrVerifier): NostrEvent? {
        val id = NostrEvent.idOf(draft, pubkey, createdAt)
        val a = answer.trim()
        val event = if (a.startsWith("{")) {
            NostrEvent.parse(a)?.takeIf {
                it.pubkey == pubkey && it.createdAt == createdAt && it.kind == draft.kind && it.tags == draft.tags && it.content == draft.content
            }
        } else {
            a.lowercase().takeIf { it.unhex(64) != null }?.let { NostrEvent(id, pubkey, createdAt, draft.kind, draft.tags, draft.content, it) }
        } ?: return null
        return event.takeIf { it.id == id && it.verify(verifier) }
    }
}
