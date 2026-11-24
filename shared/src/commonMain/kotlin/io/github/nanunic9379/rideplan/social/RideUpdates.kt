package io.github.nanunic9379.rideplan.social

import io.github.nanunic9379.rideplan.ride.RideLink
import io.github.nanunic9379.rideplan.ride.RideVersion

/**
 * Which version of a followed ride to take from what relays sent (#11 through #3).
 *
 * Relays are not trusted: an event counts only if it is a ride event at the right address
 * (same author key, same `d`), its id and signature are valid, its link decodes, and the ride
 * inside has the same ride id as the address. So only the author can publish a new version.
 * Whether it is newer is decided by [RideVersion.isNewerThan], the same rule as links and GPX.
 */
object RideUpdates {
    /** A valid version of the ride, with the ride it carries. */
    data class Candidate(val event: NostrEvent, val shared: RideLink.Shared) {
        val version: RideVersion get() = shared.ride.version!!
    }

    sealed interface Outcome {
        /** No relay answered (offline, relays down): keep the last version received. */
        data object NoAnswer : Outcome

        /** Relays answered but none has a valid version of this ride. */
        data object NotFound : Outcome

        /** The best version found is not newer than the one on the phone. */
        data class UpToDate(val best: Candidate) : Outcome

        /** A newer version to apply. */
        data class Newer(val best: Candidate) : Outcome
    }

    /** [event] as a version of the ride at [address], or null if it must be ignored. */
    fun check(event: NostrEvent, address: NostrAddress, verifier: SchnorrVerifier = Secp256k1Verifier): Candidate? {
        if (event.kind != RideEvent.KIND || address.kind != RideEvent.KIND) return null
        if (event.pubkey != address.pubkey || event.tag("d") != address.d) return null
        if (!event.verify(verifier)) return null
        val shared = runCatching { RideEvent.parse(event.toJson()) }.getOrNull() ?: return null
        val version = shared.ride.version ?: return null
        if (version.rideId != address.d) return null
        return Candidate(event, shared)
    }

    /**
     * The outcome of a check. [answered]: at least one relay replied (even with nothing).
     * [current]: the version on the phone, null if the ride is not there yet.
     * Among several valid versions the highest number wins, then the newest event.
     */
    fun pick(
        address: NostrAddress,
        events: List<NostrEvent>,
        answered: Boolean,
        current: RideVersion?,
        verifier: SchnorrVerifier = Secp256k1Verifier,
    ): Outcome {
        val best = events.distinctBy { it.id }
            .mapNotNull { check(it, address, verifier) }
            .maxWithOrNull(compareBy<Candidate>({ it.version.number }, { it.event.createdAt }))
        return when {
            best == null -> if (answered || events.isNotEmpty()) Outcome.NotFound else Outcome.NoAnswer
            current == null || best.version.isNewerThan(current) -> Outcome.Newer(best)
            else -> Outcome.UpToDate(best)
        }
    }
}
