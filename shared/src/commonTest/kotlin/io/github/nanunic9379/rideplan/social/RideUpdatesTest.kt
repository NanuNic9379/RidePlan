package io.github.nanunic9379.rideplan.social

import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.Ride
import io.github.nanunic9379.rideplan.ride.RideVersion
import io.github.nanunic9379.rideplan.ride.SharePreview
import io.github.nanunic9379.rideplan.ride.Stop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Which received version of a ride is accepted (#11 over Nostr, #3). The signature check is a
 * fake here; the real one is in the host tests (NostrCryptoTest).
 */
class RideUpdatesTest {
    private val author = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
    private val stranger = "aa".repeat(32)
    private val address = NostrAddress(RideEvent.KIND, author, "ride-abc")
    private val yes: SchnorrVerifier = { _, _, _ -> true }
    private val no: SchnorrVerifier = { _, _, _ -> false }

    private fun ride(version: RideVersion?, lat: Double = 45.6) = Ride(
        "Lakes",
        emptyList(),
        listOf(Stop("A", LatLon(45.0, 9.0)), Stop("B", LatLon(lat, 9.2))),
        Ride.Source.PLANNED,
        version = version,
    )

    private fun event(
        number: Int,
        createdAt: Long = 1_700_000_000L + number,
        pubkey: String = author,
        d: String = "ride-abc",
        rideId: String = "ride-abc",
        kind: Int = RideEvent.KIND,
    ): NostrEvent {
        val draft = RideEvent.draft(SharePreview.of(ride(RideVersion(rideId, number, "v$number", 1000L * number), 45.5 + number / 100.0), emptyList(), trimEnds = false), d, null)
        val withKind = draft.copy(kind = kind)
        val id = NostrEvent.idOf(withKind, pubkey, createdAt)
        return NostrEvent(id, pubkey, createdAt, withKind.kind, withKind.tags, withKind.content, "cd".repeat(64))
    }

    @Test
    fun acceptsAValidVersion() {
        val c = assertNotNull(RideUpdates.check(event(2), address, yes))
        assertEquals(RideVersion("ride-abc", 2, "v2", 2000), c.version)
        assertEquals("Lakes", c.shared.ride.name)
    }

    @Test
    fun onlyTheAuthorCanPublishAVersion() {
        // Someone else's event with the same d tag: ignored even with a valid signature.
        assertNull(RideUpdates.check(event(9, pubkey = stranger), address, yes))
        // The author's key but a signature that does not check out.
        assertNull(RideUpdates.check(event(9), address, no))
        // Changed after signing: the id no longer matches.
        val e = event(3)
        assertNull(RideUpdates.check(e.copy(content = e.content + "x"), address, yes))
    }

    @Test
    fun rejectsOtherRidesAndKinds() {
        assertNull(RideUpdates.check(event(2, d = "other"), address, yes))
        // Right address but the link inside is another ride.
        assertNull(RideUpdates.check(event(2, rideId = "other-ride"), address, yes))
        assertNull(RideUpdates.check(event(2, kind = 30023), address, yes))
        assertNull(RideUpdates.check(event(2), address.copy(kind = 30023), yes))
    }

    @Test
    fun rejectsAnEventWithoutVersion() {
        val draft = RideEvent.draft(SharePreview.of(ride(null), emptyList(), trimEnds = false), "ride-abc", null)
        val id = NostrEvent.idOf(draft, author, 1)
        val e = NostrEvent(id, author, 1, draft.kind, draft.tags, draft.content, "cd".repeat(64))
        assertNull(RideUpdates.check(e, address, yes))
    }

    @Test
    fun highestVersionWins() {
        val events = listOf(event(2), event(4), event(3), event(4).copy())
        val out = assertIs<RideUpdates.Outcome.Newer>(RideUpdates.pick(address, events, true, RideVersion("ride-abc", 2), yes))
        assertEquals(4, out.best.version.number)
    }

    @Test
    fun sameNumberNewestEventWins() {
        val old = event(3, createdAt = 100)
        val fresh = event(3, createdAt = 200)
        val out = assertIs<RideUpdates.Outcome.Newer>(RideUpdates.pick(address, listOf(fresh, old), true, null, yes))
        assertEquals(200, out.best.event.createdAt)
    }

    @Test
    fun aForgedHigherVersionDoesNotWin() {
        val forged = event(99, pubkey = stranger)
        val out = assertIs<RideUpdates.Outcome.Newer>(RideUpdates.pick(address, listOf(forged, event(3)), true, RideVersion("ride-abc", 2), yes))
        assertEquals(3, out.best.version.number)
    }

    @Test
    fun sameOrOlderVersionIsUpToDate() {
        val current = RideVersion("ride-abc", 3)
        assertIs<RideUpdates.Outcome.UpToDate>(RideUpdates.pick(address, listOf(event(3)), true, current, yes))
        assertIs<RideUpdates.Outcome.UpToDate>(RideUpdates.pick(address, listOf(event(2)), true, current, yes))
    }

    @Test
    fun firstFetchIsNewer() {
        assertIs<RideUpdates.Outcome.Newer>(RideUpdates.pick(address, listOf(event(1)), true, null, yes))
    }

    @Test
    fun noAnswerKeepsWhatYouHave() {
        assertEquals(RideUpdates.Outcome.NoAnswer, RideUpdates.pick(address, emptyList(), false, RideVersion("ride-abc", 3), yes))
    }

    @Test
    fun answeredWithNothingValidIsNotFound() {
        assertEquals(RideUpdates.Outcome.NotFound, RideUpdates.pick(address, emptyList(), true, null, yes))
        assertEquals(RideUpdates.Outcome.NotFound, RideUpdates.pick(address, listOf(event(5)), true, null, no))
    }
}
