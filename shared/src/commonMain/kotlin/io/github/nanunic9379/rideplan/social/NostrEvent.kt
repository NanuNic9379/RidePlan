package io.github.nanunic9379.rideplan.social

import fr.acinq.secp256k1.Secp256k1
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/** BIP-340 check: (signature 64 bytes, message 32 bytes, x-only public key 32 bytes). */
typealias SchnorrVerifier = (sig: ByteArray, message: ByteArray, pubkey: ByteArray) -> Boolean

/** The real check, Bitcoin Core's libsecp256k1 through secp256k1-kmp. */
val Secp256k1Verifier: SchnorrVerifier = { sig, message, pubkey ->
    runCatching { Secp256k1.verifySchnorr(sig, message, pubkey) }.getOrDefault(false)
}

/**
 * A signed Nostr event (NIP-01). Nothing here is trusted until [verify] says so: a relay can send
 * anything, and only a valid signature proves who wrote it (#3, #11: only the author can publish
 * a new version of a ride).
 */
data class NostrEvent(
    val id: String,
    val pubkey: String,
    val createdAt: Long,
    val kind: Int,
    val tags: List<List<String>>,
    val content: String,
    val sig: String,
) {
    val draft: NostrDraft get() = NostrDraft(kind, tags, content)

    fun tag(name: String): String? = draft.tag(name)

    /** The id this event must have: SHA-256 of the NIP-01 serialization. */
    fun computedId(): String = idOf(draft, pubkey, createdAt)

    /** Right id, and signed by [pubkey]. */
    fun verify(verifier: SchnorrVerifier = Secp256k1Verifier): Boolean {
        if (computedId() != id) return false
        val sigBytes = sig.unhex(64) ?: return false
        val idBytes = id.unhex(32) ?: return false
        val key = pubkey.unhex(32) ?: return false
        return verifier(sigBytes, idBytes, key)
    }

    /** The address of an addressable event (kind 30000-39999 with a `d` tag), else null. */
    val address: NostrAddress?
        get() = if (kind in 30000..39999) NostrAddress(kind, pubkey, tag("d") ?: "") else null

    /** JSON to send to a relay (exact NIP-01 escapes, so the id stays the same anywhere). */
    fun toJson(): String = buildString {
        append("{\"id\":").append(NostrDraft.quote(id))
        append(",\"pubkey\":").append(NostrDraft.quote(pubkey))
        append(",\"created_at\":").append(createdAt)
        append(",\"kind\":").append(kind)
        append(",\"tags\":").append(tags.joinToString(",", "[", "]") { t -> t.joinToString(",", "[", "]", transform = NostrDraft::quote) })
        append(",\"content\":").append(NostrDraft.quote(content))
        append(",\"sig\":").append(NostrDraft.quote(sig))
        append('}')
    }

    companion object {
        fun idOf(draft: NostrDraft, pubkey: String, createdAt: Long): String =
            Sha256.hash(draft.serializeForId(pubkey, createdAt).encodeToByteArray()).hex()

        /** The event in [json], or null if fields are missing or malformed (the signature is not checked here). */
        fun parse(json: String): NostrEvent? = runCatching { from(Json.parseToJsonElement(json)) }.getOrNull()

        fun from(element: JsonElement): NostrEvent? {
            val o = element as? JsonObject ?: return null
            fun str(name: String) = (o[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
            val id = str("id")?.takeIf { it.unhex(32) != null } ?: return null
            val pubkey = str("pubkey")?.takeIf { it.unhex(32) != null } ?: return null
            val sig = str("sig")?.takeIf { it.unhex(64) != null } ?: return null
            val createdAt = (o["created_at"] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull?.takeIf { it >= 0 } ?: return null
            val kind = (o["kind"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull?.takeIf { it in 0..65535 } ?: return null
            val content = str("content") ?: return null
            val tags = (o["tags"] as? JsonArray ?: return null).map { t ->
                (t as? JsonArray ?: return null).map { v -> (v as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null }
            }
            return NostrEvent(id, pubkey, createdAt, kind, tags, content, sig)
        }
    }
}
