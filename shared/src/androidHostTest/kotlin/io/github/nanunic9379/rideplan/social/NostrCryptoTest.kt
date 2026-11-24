package io.github.nanunic9379.rideplan.social

import fr.acinq.secp256k1.Secp256k1
import io.github.nanunic9379.rideplan.profile.ProfileStore
import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.Ride
import io.github.nanunic9379.rideplan.ride.RideVersion
import io.github.nanunic9379.rideplan.ride.SharePreview
import io.github.nanunic9379.rideplan.ride.Stop
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Real BIP-340 signatures (libsecp256k1 through secp256k1-kmp) and the whole publish / follow
 * path against fake relays (#3, #11). No network.
 */
class NostrCryptoTest {

    /** A key pair, as a signer app would hold it. */
    private class Key(seed: Int) {
        val secret: ByteArray = Random(seed).nextBytes(32)
        val pubkey: String = Secp256k1.pubKeyCompress(Secp256k1.pubkeyCreate(secret)).copyOfRange(1, 33).hex()

        fun sign(id: String): String = Secp256k1.signSchnorr(id.unhex(32)!!, secret, null).hex()

        fun event(draft: NostrDraft, createdAt: Long): NostrEvent {
            val id = NostrEvent.idOf(draft, pubkey, createdAt)
            return NostrEvent(id, pubkey, createdAt, draft.kind, draft.tags, draft.content, sign(id))
        }
    }

    private val alice = Key(1)
    private val mallory = Key(2)

    @Test
    fun realSignatureVerifies() {
        val draft = NostrDraft(1, listOf(listOf("t", "rideplan")), "hello")
        val e = alice.event(draft, 1_700_000_000)
        assertTrue(e.verify())
        assertFalse(e.copy(content = "changed", id = e.copy(content = "changed").computedId()).verify())
        assertFalse(e.copy(pubkey = mallory.pubkey, id = e.copy(pubkey = mallory.pubkey).computedId()).verify())
        assertFalse(e.copy(sig = "00".repeat(64)).verify())
        // Through JSON, as relays send it.
        assertTrue(NostrEvent.parse(e.toJson())!!.verify())
    }

    @Test
    fun signerAnswersAreChecked() {
        val draft = NostrDraft(RideEvent.KIND, listOf(listOf("d", "x")), "content")
        val at = 1_700_000_000L
        val good = alice.event(draft, at)
        // A bare signature or the signed event: both accepted.
        assertEquals(good, Signing.signedEvent(good.sig, draft, alice.pubkey, at, Secp256k1Verifier))
        assertEquals(good, Signing.signedEvent(good.sig.uppercase(), draft, alice.pubkey, at, Secp256k1Verifier))
        assertEquals(good, Signing.signedEvent(good.toJson(), draft, alice.pubkey, at, Secp256k1Verifier))
        // The signer changed the content, signed with another key, or answered nonsense.
        val other = alice.event(draft.copy(content = "something else"), at)
        assertNull(Signing.signedEvent(other.toJson(), draft, alice.pubkey, at, Secp256k1Verifier))
        assertNull(Signing.signedEvent(mallory.event(draft, at).toJson(), draft, alice.pubkey, at, Secp256k1Verifier))
        assertNull(Signing.signedEvent(mallory.event(draft, at).sig, draft, alice.pubkey, at, Secp256k1Verifier))
        assertNull(Signing.signedEvent("rejected", draft, alice.pubkey, at, Secp256k1Verifier))
        assertNull(Signing.signedEvent(alice.event(draft, at + 1).toJson(), draft, alice.pubkey, at, Secp256k1Verifier))
    }

    // --- Fake relays ---

    private enum class Mode { UP, DOWN, SILENT, REJECT }

    /** Relays in memory: they keep the newest event per address, like real ones. */
    private class FakeRelays : RelayTransport {
        val mode = mutableMapOf<String, Mode>()
        val stored = mutableMapOf<String, MutableList<NostrEvent>>()
        var connections = 0

        override fun connect(url: String, listener: RelayListener): RelaySocket {
            connections++
            val m = mode[url] ?: Mode.UP
            if (m == Mode.DOWN) {
                listener.onClosed()
                return object : RelaySocket {
                    override fun send(text: String) = false
                    override fun close() {}
                }
            }
            listener.onOpen()
            return object : RelaySocket {
                override fun send(text: String): Boolean {
                    if (m == Mode.SILENT) return true
                    val a = kotlinx.serialization.json.Json.parseToJsonElement(text) as kotlinx.serialization.json.JsonArray
                    val type = a[0].toString().trim('"')
                    when (type) {
                        "EVENT" -> {
                            val e = NostrEvent.from(a[1])!!
                            if (m == Mode.REJECT) {
                                listener.onMessage("""["OK","${e.id}",false,"blocked: no"]""")
                            } else {
                                val list = stored.getOrPut(url) { mutableListOf() }
                                list.removeAll { it.pubkey == e.pubkey && it.kind == e.kind && it.tag("d") == e.tag("d") && it.createdAt <= e.createdAt }
                                list += e
                                listener.onMessage("""["OK","${e.id}",true,""]""")
                            }
                        }
                        "REQ" -> {
                            val sub = a[1].toString().trim('"')
                            val f = a[2].toString()
                            stored[url].orEmpty().filter { e -> f.contains("\"${e.tag("d")}\"") }.forEach { e ->
                                listener.onMessage("""["EVENT","$sub",${e.toJson()}]""")
                            }
                            listener.onMessage("""["EOSE","$sub"]""")
                        }
                    }
                    return true
                }

                override fun close() {}
            }
        }
    }

    private val relays = listOf("wss://one.example.org", "wss://two.example.org", "wss://three.example.org")

    private class FakeSigner(private val key: Key, var answerWithEvent: Boolean = false, var refuse: Boolean = false) : NostrSigner {
        override fun installed() = true
        override suspend fun connect() = SignerInfo(key.pubkey, "com.example.signer")
        override suspend fun sign(unsignedJson: String, id: String, signer: SignerInfo): String? {
            if (refuse) return null
            val parsed = kotlinx.serialization.json.Json.parseToJsonElement(unsignedJson) as kotlinx.serialization.json.JsonObject
            assertEquals(key.pubkey, parsed["pubkey"].toString().trim('"'))
            val sig = key.sign(id)
            return if (answerWithEvent) NostrEvent.parse(unsignedJson.dropLast(1) + ",\"id\":\"$id\",\"sig\":\"$sig\"}")!!.toJson() else sig
        }
    }

    private var clock = 1_700_000_000_000L

    private fun profile() = ProfileStore(read = { null }, write = {}, now = { clock })

    private fun service(net: FakeRelays, signer: NostrSigner?, profile: ProfileStore = profile()) =
        NostrService(SocialStore({ null }, {}), profile, signer, RelayPool(net, timeoutMs = 300)).also {
            it.store.update { d -> d.copy(enabled = true, relays = relays) }
        }

    private val ride = Ride(
        "Lakes",
        emptyList(),
        listOf(Stop("Home", LatLon(45.0, 9.0)), Stop("Pass", LatLon(45.4, 9.3)), Stop("Lake", LatLon(45.7, 9.1))),
        Ride.Source.PLANNED,
    )

    @Test
    fun publishFollowAndUpdate() = runBlocking {
        val net = FakeRelays()
        net.mode[relays[2]] = Mode.REJECT
        val authorProfile = profile()
        authorProfile.update { it.save(ride, null, clock).first }
        val saved = authorProfile.data.rides.single()
        val author = service(net, FakeSigner(alice, answerWithEvent = true), authorProfile)
        assertTrue(author.connectSigner())

        val done = assertIs<PublishOutcome.Done>(author.publish(saved.id, SharePreview.of(saved.toRide(), emptyList(), trimEnds = false), null, "Coffee at the pass"))
        assertEquals(2, done.accepted)
        assertEquals(3, done.total)
        val address = assertNotNull(Nip19.parseNaddr(done.naddr))
        assertEquals(alice.pubkey, address.pubkey)
        assertEquals(saved.version!!.rideId, address.d)
        assertEquals(1, author.data.publishedFor(saved.id)?.versionNumber)

        // A follower pastes the address and gets the ride.
        val followerProfile = profile()
        val follower = service(net, null, followerProfile)
        val got = assertIs<FollowOutcome.Updated>(follower.follow("nostr:${done.naddr}"))
        assertEquals(1, got.version)
        val received = followerProfile.data.rides.single { it.id == got.savedId }
        assertEquals(saved.version!!.rideId, received.version?.rideId)
        assertEquals(3, received.stops.size)

        // Nothing new yet.
        assertIs<FollowOutcome.UpToDate>(follower.checkUpdate(got.savedId))

        // The author moves a stop and publishes version 2.
        clock += 60_000
        val v2 = saved.toRide().copy(
            stops = ride.stops.take(2) + Stop("Other lake", LatLon(45.9, 9.4)),
            version = saved.version!!.copy(number = 2, note = "Lake road closed", updatedAt = clock),
        )
        assertIs<PublishOutcome.Done>(author.publish(saved.id, SharePreview.of(v2, emptyList(), trimEnds = false), null, ""))
        val up = assertIs<FollowOutcome.Updated>(follower.checkUpdate(got.savedId))
        assertEquals(2, up.version)
        assertEquals(got.savedId, up.savedId)
        val now = followerProfile.data.rides.single { it.id == got.savedId }
        assertEquals(2, now.version?.number)
        assertEquals("Other lake", now.stops.last().name)

        // Someone else publishes a "version 9" at the same address: ignored, only the author counts.
        clock += 60_000
        val forged = RideEvent.draft(
            SharePreview.of(v2.copy(stops = ride.stops.take(2), version = v2.version!!.copy(number = 9)), emptyList(), trimEnds = false),
            address.d,
            null,
        )
        relays.forEach { url -> net.stored.getOrPut(url) { mutableListOf() } += mallory.event(forged, clock / 1000) }
        assertIs<FollowOutcome.UpToDate>(follower.checkUpdate(got.savedId))
        assertEquals(2, followerProfile.data.rides.single { it.id == got.savedId }.version?.number)

        // Relays down or silent: no change, the last version stays.
        relays.forEach { net.mode[it] = Mode.DOWN }
        assertEquals(FollowOutcome.NoAnswer, follower.checkUpdate(got.savedId))
        relays.forEach { net.mode[it] = Mode.SILENT }
        assertEquals(FollowOutcome.NoAnswer, follower.checkUpdate(got.savedId))
        assertEquals(2, followerProfile.data.rides.single { it.id == got.savedId }.version?.number)
        assertEquals(2, follower.data.followedFor(got.savedId)?.lastVersion)
    }

    @Test
    fun publishFailures() = runBlocking {
        val net = FakeRelays()
        val p = profile()
        p.update { it.save(ride, null, clock).first }
        val saved = p.data.rides.single()
        val preview = SharePreview.of(saved.toRide(), emptyList(), trimEnds = false)

        val noSigner = service(net, FakeSigner(alice), p)
        assertEquals(PublishOutcome.Failed(PublishError.NO_SIGNER), noSigner.publish(saved.id, preview, null, ""))

        val refusing = FakeSigner(alice, refuse = true)
        val s = service(net, refusing, p).also { it.connectSigner() }
        assertEquals(PublishOutcome.Failed(PublishError.CANCELLED), s.publish(saved.id, preview, null, ""))

        // The signer holds another key than the one it said: caught before anything is sent.
        val liar = object : NostrSigner {
            override fun installed() = true
            override suspend fun connect() = SignerInfo(alice.pubkey, "x")
            override suspend fun sign(unsignedJson: String, id: String, signer: SignerInfo) = mallory.sign(id)
        }
        val before = net.connections
        val l = service(net, liar, p).also { it.connectSigner() }
        assertEquals(PublishOutcome.Failed(PublishError.BAD_SIGNATURE), l.publish(saved.id, preview, null, ""))
        assertEquals(before, net.connections)

        relays.forEach { net.mode[it] = Mode.DOWN }
        val down = service(net, FakeSigner(alice), p).also { it.connectSigner() }
        assertEquals(PublishOutcome.Failed(PublishError.NOBODY_ACCEPTED), down.publish(saved.id, preview, null, ""))
        assertNull(down.data.publishedFor(saved.id))

        down.store.update { it.copy(relays = emptyList()) }
        assertEquals(PublishOutcome.Failed(PublishError.NO_RELAYS), down.publish(saved.id, preview, null, ""))
    }

    @Test
    fun followRejectsBadAddresses() = runBlocking {
        val f = service(FakeRelays(), null)
        assertEquals(FollowOutcome.BadAddress, f.follow("hello"))
        assertEquals(FollowOutcome.BadAddress, f.follow(Nip19.naddr(NostrAddress(1, alice.pubkey, "x"))))
        // A valid address nobody has.
        assertEquals(FollowOutcome.NotFound, f.follow(Nip19.naddr(NostrAddress(RideEvent.KIND, alice.pubkey, "missing"))))
    }

    @Test
    fun aRideSavedWithoutVersionGetsOneWhenPublished() = runBlocking {
        val p = profile()
        p.update { it.save(ride, null, clock).first }
        // As saved by an app older than versions (#11).
        p.update { d -> d.copy(rides = d.rides.map { it.copy(version = null) }) }
        val saved = p.data.rides.single()
        val s = service(FakeRelays(), FakeSigner(alice), p).also { it.connectSigner() }
        assertIs<PublishOutcome.Done>(s.publish(saved.id, SharePreview.of(saved.toRide(), emptyList(), trimEnds = false), null, ""))
        val v = assertNotNull(p.data.rides.single().version)
        assertEquals(v.rideId, s.data.publishedFor(saved.id)?.d)
    }

    @Test
    fun socialSettingsSurviveARestart() {
        var file: String? = null
        val store = SocialStore({ file }, { file = it })
        assertFalse(store.data.enabled)
        assertEquals(Relays.DEFAULTS.map { it.url }, store.data.relays)
        store.update { it.copy(enabled = true, signer = SignerInfo(alice.pubkey, "pkg")) }
        val again = SocialStore({ file }, { file = it })
        assertTrue(again.data.enabled)
        assertEquals(alice.pubkey, again.data.signer?.pubkey)
        again.wipe()
        assertFalse(SocialStore({ file }, { file = it }).data.enabled)
        // A broken file starts from the defaults.
        assertFalse(SocialStore({ "{broken" }, {}).data.enabled)
    }
}
