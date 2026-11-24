package io.github.nanunic9379.rideplan.social

/** Bech32 (BIP-173), as used by NIP-19. No 90-character limit: `naddr` with relays is longer. */
internal object Bech32 {
    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private val GEN = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)
    private const val MAX_LENGTH = 5000

    private fun polymod(values: IntArray): Int {
        var chk = 1
        for (v in values) {
            val top = chk ushr 25
            chk = (chk and 0x1ffffff shl 5) xor v
            for (i in 0 until 5) if ((top ushr i) and 1 == 1) chk = chk xor GEN[i]
        }
        return chk
    }

    private fun expand(hrp: String): IntArray =
        IntArray(hrp.length * 2 + 1) { i ->
            when {
                i < hrp.length -> hrp[i].code ushr 5
                i == hrp.length -> 0
                else -> hrp[i - hrp.length - 1].code and 31
            }
        }

    fun encode(hrp: String, data: ByteArray): String {
        val five = convert(data.map { it.toInt() and 0xff }, 8, 5, pad = true)!!
        val values = expand(hrp) + five.toIntArray() + IntArray(6)
        val mod = polymod(values) xor 1
        val checksum = IntArray(6) { i -> (mod ushr (5 * (5 - i))) and 31 }
        return hrp + "1" + (five + checksum.toList()).joinToString("") { CHARSET[it].toString() }
    }

    /** (hrp, bytes) or null when the text is not valid bech32. */
    fun decode(text: String): Pair<String, ByteArray>? {
        if (text.length > MAX_LENGTH || text.any { it.code < 33 || it.code > 126 }) return null
        if (text.lowercase() != text && text.uppercase() != text) return null
        val s = text.lowercase()
        val pos = s.lastIndexOf('1')
        if (pos < 1 || pos + 7 > s.length) return null
        val hrp = s.substring(0, pos)
        val values = s.substring(pos + 1).map { CHARSET.indexOf(it).takeIf { v -> v >= 0 } ?: return null }
        if (polymod(expand(hrp) + values.toIntArray()) != 1) return null
        val bytes = convert(values.dropLast(6), 5, 8, pad = false) ?: return null
        return hrp to ByteArray(bytes.size) { bytes[it].toByte() }
    }

    private fun convert(data: List<Int>, from: Int, to: Int, pad: Boolean): List<Int>? {
        var acc = 0
        var bits = 0
        val out = mutableListOf<Int>()
        val maxv = (1 shl to) - 1
        for (v in data) {
            if (v < 0 || v ushr from != 0) return null
            acc = (acc shl from) or v
            bits += from
            while (bits >= to) {
                bits -= to
                out += (acc ushr bits) and maxv
            }
        }
        if (pad) {
            if (bits > 0) out += (acc shl (to - bits)) and maxv
        } else if (bits >= from || ((acc shl (to - bits)) and maxv) != 0) {
            return null
        }
        return out
    }
}

/**
 * The address of an addressable event (NIP-01 "a" tag `kind:pubkey:d`): for a ride, the author
 * and the ride id. Every version of the ride has the same address (#11).
 * [relays]: hints where to find it, from an `naddr`.
 */
data class NostrAddress(val kind: Int, val pubkey: String, val d: String, val relays: List<String> = emptyList()) {
    /** The `a` tag value. */
    val coordinate: String get() = "$kind:$pubkey:$d"
}

/** NIP-19 text forms: `npub` (public key) and `naddr` (address of a ride), with or without `nostr:`. */
object Nip19 {
    private const val MAX_RELAYS = 5

    fun npub(pubkeyHex: String): String = Bech32.encode("npub", pubkeyHex.unhex(32) ?: error("Bad key"))

    /** Hex public key from an `npub` or a hex key, or null. Signers answer with either. */
    fun pubkey(text: String): String? {
        val t = text.trim().removePrefix("nostr:")
        t.lowercase().unhex(32)?.let { return it.hex() }
        val (hrp, data) = Bech32.decode(t) ?: return null
        return if (hrp == "npub" && data.size == 32) data.hex() else null
    }

    fun naddr(address: NostrAddress): String {
        val out = mutableListOf<Byte>()
        fun tlv(type: Int, value: ByteArray) {
            out += type.toByte()
            out += value.size.toByte()
            out += value.toList()
        }
        tlv(0, address.d.encodeToByteArray())
        address.relays.take(MAX_RELAYS).forEach { r -> r.encodeToByteArray().takeIf { it.size <= 255 }?.let { tlv(1, it) } }
        tlv(2, address.pubkey.unhex(32) ?: error("Bad key"))
        tlv(3, byteArrayOf((address.kind ushr 24).toByte(), (address.kind ushr 16).toByte(), (address.kind ushr 8).toByte(), address.kind.toByte()))
        return Bech32.encode("naddr", out.toByteArray())
    }

    /** The address inside an `naddr` (also as `nostr:naddr…` or inside a longer text), or null. */
    fun parseNaddr(text: String): NostrAddress? {
        val word = Regex("""naddr1[02-9ac-hj-np-z]+""").find(text.lowercase())?.value ?: return null
        val (hrp, data) = Bech32.decode(word) ?: return null
        if (hrp != "naddr") return null
        var d: String? = null
        var pubkey: String? = null
        var kind: Int? = null
        val relays = mutableListOf<String>()
        var i = 0
        while (i + 2 <= data.size) {
            val type = data[i].toInt() and 0xff
            val len = data[i + 1].toInt() and 0xff
            if (i + 2 + len > data.size) return null
            val value = data.copyOfRange(i + 2, i + 2 + len)
            when (type) {
                0 -> d = value.decodeToString()
                1 -> if (relays.size < MAX_RELAYS) relays += value.decodeToString()
                2 -> if (len == 32) pubkey = value.hex() else return null
                3 -> if (len == 4) kind = value.fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xff) } else return null
            }
            i += 2 + len
        }
        if (i != data.size) return null
        return NostrAddress(kind ?: return null, pubkey ?: return null, d ?: return null, relays)
    }
}
