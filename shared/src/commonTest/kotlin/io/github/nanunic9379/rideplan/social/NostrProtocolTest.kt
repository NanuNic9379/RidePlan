package io.github.nanunic9379.rideplan.social

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The pure parts of Nostr (#3, #11): hashing, NIP-19 addresses, events, relay messages, relay list. */
class NostrProtocolTest {
    private val key = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"

    // --- SHA-256 (FIPS 180-2 vectors) ---

    @Test
    fun sha256KnownVectors() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Sha256.hash(ByteArray(0)).hex())
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Sha256.hash("abc".encodeToByteArray()).hex())
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            Sha256.hash("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray()).hex(),
        )
        // One million "a": many blocks.
        assertEquals(
            "cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0",
            Sha256.hash(ByteArray(1_000_000) { 'a'.code.toByte() }).hex(),
        )
    }

    @Test
    fun sha256PaddingEdges() {
        // 55, 56 and 64 bytes: the length field fits, does not fit, and a full block.
        val sizes = listOf(55, 56, 63, 64, 65)
        val hashes = sizes.map { Sha256.hash(ByteArray(it) { 'x'.code.toByte() }).hex() }
        assertEquals(sizes.size, hashes.toSet().size)
        assertTrue(hashes.all { it.length == 64 })
    }

    @Test
    fun hexRoundTripAndRejects() {
        val bytes = key.unhex(32)
        assertNotNull(bytes)
        assertEquals(key, bytes.hex())
        assertNull(key.unhex(31))
        assertNull(key.uppercase().unhex(32))
        assertNull(("zz" + key.drop(2)).unhex(32))
    }

    // --- NIP-19 ---

    @Test
    fun npubMatchesNip19Example() {
        assertEquals("npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6", Nip19.npub(key))
        assertEquals(key, Nip19.pubkey("npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6"))
        assertEquals(key, Nip19.pubkey("nostr:npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6"))
        assertEquals(key, Nip19.pubkey(key.uppercase()))
    }

    @Test
    fun pubkeyRejectsBadInput() {
        assertNull(Nip19.pubkey(""))
        assertNull(Nip19.pubkey("hello"))
        // One character changed: the checksum fails.
        assertNull(Nip19.pubkey("npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w7"))
        // Another kind of bech32 entity is not a key.
        assertNull(Nip19.pubkey(Bech32.encode("nsec", ByteArray(32))))
    }

    @Test
    fun bech32KnownValidString() {
        // BIP-173 test vector: valid, empty data.
        val (hrp, data) = assertNotNull(Bech32.decode("a12uel5l"))
        assertEquals("a", hrp)
        assertEquals(0, data.size)
        assertNull(Bech32.decode("a12uel5m"))
    }

    @Test
    fun naddrRoundTrip() {
        val address = NostrAddress(RideEvent.KIND, key, "ride-42", listOf("wss://relay.damus.io", "wss://nos.lol"))
        val text = Nip19.naddr(address)
        assertTrue(text.startsWith("naddr1"))
        assertEquals(address, Nip19.parseNaddr(text))
        assertEquals(address, Nip19.parseNaddr("Look at this: nostr:$text !"))
        assertEquals(address, Nip19.parseNaddr(text.uppercase()))
        assertEquals("${RideEvent.KIND}:$key:ride-42", address.coordinate)
    }

    @Test
    fun naddrKeepsAtMostFiveRelays() {
        val many = (1..8).map { "wss://r$it.example.org" }
        val back = Nip19.parseNaddr(Nip19.naddr(NostrAddress(RideEvent.KIND, key, "x", many)))
        assertEquals(many.take(5), back?.relays)
    }

    @Test
    fun parseNaddrRejects() {
        assertNull(Nip19.parseNaddr("no address here"))
        assertNull(Nip19.parseNaddr(Nip19.npub(key)))
        val good = Nip19.naddr(NostrAddress(RideEvent.KIND, key, "ride"))
        assertNull(Nip19.parseNaddr(good.dropLast(1) + if (good.last() == 'q') 'p' else 'q'))
        // Without author or kind it is not an address.
        assertNull(Nip19.parseNaddr(Bech32.encode("naddr", byteArrayOf(0, 1, 'x'.code.toByte()))))
    }

    // --- Events ---

    private fun event(content: String = "hello \"world\"\n", tags: List<List<String>> = listOf(listOf("d", "ride-1"), listOf("t", "rideplan"))): NostrEvent {
        val draft = NostrDraft(RideEvent.KIND, tags, content)
        val id = NostrEvent.idOf(draft, key, 1_700_000_000)
        return NostrEvent(id, key, 1_700_000_000, draft.kind, draft.tags, draft.content, "ab".repeat(64))
    }

    @Test
    fun idIsSha256OfTheNip01Serialization() {
        val e = event()
        val serialized = """[0,"$key",1700000000,${RideEvent.KIND},[["d","ride-1"],["t","rideplan"]],"hello \"world\"\n"]"""
        assertEquals(serialized, e.draft.serializeForId(key, 1_700_000_000))
        assertEquals(Sha256.hash(serialized.encodeToByteArray()).hex(), e.id)
        assertEquals(e.id, e.computedId())
    }

    @Test
    fun eventJsonRoundTrip() {
        val e = event(content = "tab\tunicode è ✓ \u0001")
        val back = NostrEvent.parse(e.toJson())
        assertEquals(e, back)
        assertEquals(e.id, back?.computedId())
    }

    @Test
    fun parseRejectsMalformedEvents() {
        val good = event().toJson()
        assertNotNull(NostrEvent.parse(good))
        assertNull(NostrEvent.parse("not json"))
        assertNull(NostrEvent.parse("[]"))
        assertNull(NostrEvent.parse(good.replace("\"kind\":${RideEvent.KIND}", "\"kind\":\"${RideEvent.KIND}\"")))
        assertNull(NostrEvent.parse(good.replace("\"created_at\":1700000000", "\"created_at\":-1")))
        assertNull(NostrEvent.parse(good.replace("\"sig\":\"abab", "\"sig\":\"ab")))
        assertNull(NostrEvent.parse(good.replace(",\"content\":", ",\"contentx\":")))
        assertNull(NostrEvent.parse(good.replace("[\"t\",\"rideplan\"]", "[\"t\",1]")))
    }

    @Test
    fun verifyChecksIdBeforeTheSignature() {
        val e = event()
        var calls = 0
        val yes: SchnorrVerifier = { _, _, _ -> calls++; true }
        assertTrue(e.verify(yes))
        assertEquals(1, calls)
        // Content changed after signing: wrong id, the signature is not even looked at.
        assertFalse(e.copy(content = "changed").verify(yes))
        assertEquals(1, calls)
        assertFalse(e.verify { _, _, _ -> false })
        // The verifier gets id, signature and key as bytes.
        e.verify { sig, msg, pub ->
            assertContentEquals(e.sig.unhex(64), sig)
            assertContentEquals(e.id.unhex(32), msg)
            assertContentEquals(key.unhex(32), pub)
            true
        }
    }

    @Test
    fun addressOnlyForAddressableKinds() {
        assertEquals(NostrAddress(RideEvent.KIND, key, "ride-1"), event().address)
        assertNull(event().copy(kind = 1).address)
    }

    // --- Relay messages ---

    @Test
    fun clientMessages() {
        val filter = NostrFilter.of(NostrAddress(RideEvent.KIND, key, "ride-\"1\""))
        assertEquals("""{"kinds":[${RideEvent.KIND}],"authors":["$key"],"#d":["ride-\"1\""],"limit":5}""", filter.toJson())
        assertEquals("""["REQ","s1",${filter.toJson()}]""", ClientMessage.req("s1", filter))
        assertEquals("""["CLOSE","s1"]""", ClientMessage.close("s1"))
        val e = event()
        assertEquals("""["EVENT",${e.toJson()}]""", ClientMessage.event(e))
        assertEquals("{}", NostrFilter().toJson())
    }

    @Test
    fun relayMessages() {
        val e = event()
        assertEquals(RelayMessage.Event("s1", e), RelayMessage.parse("""["EVENT","s1",${e.toJson()}]"""))
        assertEquals(RelayMessage.Event("s1", null), RelayMessage.parse("""["EVENT","s1",{"id":"x"}]"""))
        assertEquals(RelayMessage.EndOfStored("s1"), RelayMessage.parse("""["EOSE","s1"]"""))
        assertEquals(RelayMessage.Ok(e.id, true, ""), RelayMessage.parse("""["OK","${e.id}",true,""]"""))
        assertEquals(RelayMessage.Ok(e.id, false, "blocked: spam"), RelayMessage.parse("""["OK","${e.id}",false,"blocked: spam"]"""))
        assertEquals(RelayMessage.Ok(e.id, true, ""), RelayMessage.parse("""["OK","${e.id}",true]"""))
        assertEquals(RelayMessage.Closed("s1", "auth-required: x"), RelayMessage.parse("""["CLOSED","s1","auth-required: x"]"""))
        assertEquals(RelayMessage.Notice("slow down"), RelayMessage.parse("""["NOTICE","slow down"]"""))
        assertEquals(RelayMessage.Auth("challenge"), RelayMessage.parse("""["AUTH","challenge"]"""))
    }

    @Test
    fun relayMessagesRejectGarbage() {
        assertEquals(RelayMessage.Unknown, RelayMessage.parse(""))
        assertEquals(RelayMessage.Unknown, RelayMessage.parse("{}"))
        assertEquals(RelayMessage.Unknown, RelayMessage.parse("""["COUNT","s1",{"count":3}]"""))
        assertEquals(RelayMessage.Unknown, RelayMessage.parse("""["OK","id","true"]"""))
        assertEquals(RelayMessage.Unknown, RelayMessage.parse("""["EOSE"]"""))
        assertEquals(RelayMessage.Unknown, RelayMessage.parse("""["EVENT",1,{}]"""))
        assertEquals(RelayMessage.Unknown, RelayMessage.parse("[\"NOTICE\",\"" + "x".repeat(RelayMessage.MAX_CHARS) + "\"]"))
    }

    // --- Exchanges with one relay ---

    @Test
    fun publishExchange() {
        val e = event()
        val ex = PublishExchange(e)
        assertEquals(listOf(ClientMessage.event(e)), ex.start())
        assertFalse(ex.receive(RelayMessage.Notice("hi")))
        assertFalse(ex.receive(RelayMessage.Ok("00".repeat(32), true, "")))
        assertEquals(PublishResult.NO_ANSWER, ex.result)
        assertTrue(ex.receive(RelayMessage.Ok(e.id, false, "pow: too low")))
        assertEquals(PublishResult.REJECTED, ex.result)
        assertEquals("pow: too low", ex.message)
        val ok = PublishExchange(e)
        assertTrue(ok.receive(RelayMessage.Ok(e.id, true, "")))
        assertEquals(PublishResult.ACCEPTED, ok.result)
    }

    @Test
    fun fetchExchange() {
        val filter = NostrFilter(kinds = listOf(1))
        val ex = FetchExchange(filter, "s1", maxEvents = 2)
        assertEquals(listOf(ClientMessage.req("s1", filter)), ex.start())
        assertFalse(ex.answered)
        assertFalse(ex.receive(RelayMessage.Event("other", event())))
        assertFalse(ex.answered)
        assertFalse(ex.receive(RelayMessage.Event("s1", event())))
        assertTrue(ex.answered)
        assertFalse(ex.receive(RelayMessage.EndOfStored("other")))
        assertTrue(ex.receive(RelayMessage.EndOfStored("s1")))
        assertEquals(1, ex.events.size)
        // Stops at the limit even without EOSE.
        val full = FetchExchange(filter, "s2", maxEvents = 2)
        assertFalse(full.receive(RelayMessage.Event("s2", event())))
        assertTrue(full.receive(RelayMessage.Event("s2", event("other"))))
        // Closed (e.g. auth required) is an answer with nothing.
        val closed = FetchExchange(filter, "s3")
        assertTrue(closed.receive(RelayMessage.Closed("s3", "auth-required")))
        assertTrue(closed.answered)
        assertTrue(closed.events.isEmpty())
        assertEquals("""["CLOSE","s3"]""", closed.closeMessage())
    }

    // --- Relay list ---

    @Test
    fun normalizeRelayAddresses() {
        assertEquals("wss://relay.damus.io", Relays.normalize("wss://relay.damus.io/"))
        assertEquals("wss://relay.damus.io", Relays.normalize("  relay.damus.io "))
        assertEquals("wss://nos.lol", Relays.normalize("WSS://NOS.LOL"))
        assertEquals("wss://relay.example.org:7447/nostr", Relays.normalize("wss://relay.example.org:7447/nostr/"))
        assertNull(Relays.normalize("ws://relay.damus.io"))
        assertNull(Relays.normalize("https://relay.damus.io"))
        assertNull(Relays.normalize("localhost"))
        assertNull(Relays.normalize("wss://relay .damus.io"))
        assertNull(Relays.normalize("wss://relay.damus.io:99999"))
        assertNull(Relays.normalize("wss://relay.damus.io/?x=1"))
        assertNull(Relays.normalize("wss://user@relay.damus.io"))
        assertNull(Relays.normalize(""))
        assertNull(Relays.normalize("wss://" + "a".repeat(300) + ".org"))
    }

    @Test
    fun defaultRelaysAreValidAndDistinct() {
        val urls = Relays.DEFAULTS.map { it.url }
        assertTrue(urls.size in 3..5)
        assertEquals(urls, urls.mapNotNull(Relays::normalize))
        assertEquals(urls.size, urls.toSet().size)
        assertTrue(Relays.DEFAULTS.all { it.why.isNotBlank() })
    }

    @Test
    fun addRelay() {
        val list = listOf("wss://nos.lol")
        assertEquals(list + "wss://relay.damus.io", Relays.add(list, "relay.damus.io"))
        assertNull(Relays.add(list, "wss://nos.lol/"))
        assertNull(Relays.add(list, "bad"))
        val full = (1..Relays.MAX).map { "wss://r$it.example.org" }
        assertNull(Relays.add(full, "wss://one.more.org"))
    }

    @Test
    fun relaysForReadingAddHintsAfterMine() {
        val mine = listOf("wss://nos.lol", "wss://relay.damus.io")
        val read = Relays.forReading(mine, listOf("wss://nos.lol", "relay.author.org", "ws://plain.org"))
        assertEquals(mine + "wss://relay.author.org", read)
    }
}
