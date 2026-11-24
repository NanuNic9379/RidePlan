package io.github.nanunic9379.rideplan.social

import io.github.nanunic9379.rideplan.ride.ImportError
import io.github.nanunic9379.rideplan.ride.ImportException
import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.RideLink
import io.github.nanunic9379.rideplan.ride.SharePreview
import io.github.nanunic9379.rideplan.ride.distanceKm
import io.github.nanunic9379.rideplan.route.RouteOptions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.ceil

/**
 * An unsigned Nostr event: what goes to a signer (NIP-55 / NIP-46, or a key on the phone later).
 * No keys and no network here (#3, first slice; design in docs/social.md).
 */
data class NostrDraft(val kind: Int, val tags: List<List<String>>, val content: String) {
    fun tag(name: String): String? = tags.firstOrNull { it.size >= 2 && it[0] == name }?.get(1)
    fun tagValues(name: String): List<String> = tags.filter { it.size >= 2 && it[0] == name }.map { it[1] }

    /**
     * The unsigned event as JSON, the form NIP-55 / NIP-46 signers accept.
     * [pubkey]: lowercase hex, when already known.
     */
    fun toUnsignedJson(createdAt: Long, pubkey: String? = null): String = buildString {
        append('{')
        if (pubkey != null) append("\"pubkey\":").append(quote(pubkey)).append(',')
        append("\"created_at\":").append(createdAt)
        append(",\"kind\":").append(kind)
        append(",\"tags\":").append(tagsJson())
        append(",\"content\":").append(quote(content))
        append('}')
    }

    /**
     * NIP-01 serialization whose SHA-256 is the event id:
     * `[0,<pubkey>,<created_at>,<kind>,<tags>,<content>]`, no whitespace, exact escapes.
     */
    fun serializeForId(pubkey: String, createdAt: Long): String =
        "[0,${quote(pubkey)},$createdAt,$kind,${tagsJson()},${quote(content)}]"

    private fun tagsJson() = tags.joinToString(",", "[", "]") { t -> t.joinToString(",", "[", "]", transform = ::quote) }

    companion object {
        /**
         * JSON string with exactly the escapes listed in NIP-01; every other character verbatim.
         * Other control characters never get here: [clean] replaces them.
         */
        internal fun quote(s: String): String = buildString {
            append('"')
            for (c in s) {
                when (c) {
                    '\n' -> append("\\n")
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    else -> append(c)
                }
            }
            append('"')
        }

        /** Control characters that NIP-01 has no escape for become spaces (they would break JSON). */
        internal fun clean(s: String): String =
            s.map { c -> if (c < ' ' && c !in "\n\r\t\b\u000C") ' ' else c }.joinToString("")
    }
}

/**
 * A shared ride as a Nostr addressable event (#3). Format proposed in docs/social.md:
 * - kind [KIND] (addressable: same author + `d` = the newer version replaces the older one);
 * - `content`: the RidePlan link of the ride ([RideLink]), so any Nostr client shows something
 *   that opens, and the route data stays the one already used for QR and links;
 * - `g`: geohash cells along the ride, precision 3 and 4, for the search by area;
 * - no times of the ride (only the publishing time that every event has).
 */
object RideEvent {
    /** Provisional: no NIP for routes exists yet (owner decision, docs/social.md). */
    const val KIND = 30455

    /** Below the smallest common relay limit (NIP-11 example: 16 KiB per message). */
    const val MAX_BYTES = 12 * 1024

    const val MAX_TITLE = 120
    const val MAX_SUMMARY = 2000

    /** Above this many precision-4 cells only precision 3 is kept (very long trips). */
    const val MAX_FINE_CELLS = 48
    const val MAX_COARSE_CELLS = 24

    /**
     * The event for a ride, always from a [SharePreview]: private zones and trimmed ends are
     * applied there, exactly as for GPX, QR and link.
     * [id]: the `d` tag, stable for the ride so that a new version replaces the old one.
     */
    fun draft(shared: SharePreview, id: String, options: RouteOptions?, summary: String = ""): NostrDraft {
        require(!shared.empty) { "Nothing to share: the ride is inside the private zones" }
        require(id.isNotBlank() && id.length <= 64) { "Bad ride id" }
        val ride = shared.ride
        val link = RideLink.encode(ride, options)
        val tags = buildList {
            add(listOf("d", id))
            ride.name?.let(NostrDraft::clean)?.trim()?.take(MAX_TITLE)?.takeIf { it.isNotEmpty() }?.let { add(listOf("title", it)) }
            NostrDraft.clean(summary).trim().take(MAX_SUMMARY).takeIf { it.isNotEmpty() }?.let { add(listOf("summary", it)) }
            add(listOf("distance", formatKm(shared.km)))
            add(listOf("t", "motorcycle"))
            add(listOf("t", "rideplan"))
            cells(ride.line).forEach { add(listOf("g", it)) }
        }
        val draft = NostrDraft(KIND, tags, link)
        // Created_at, pubkey, id and sig add less than 300 bytes.
        require(draft.toUnsignedJson(0).encodeToByteArray().size + 300 <= MAX_BYTES) { "Ride too big for a relay" }
        return draft
    }

    /** The ride inside a received event, after the same checks as a link. */
    fun parse(json: String): RideLink.Shared {
        if (json.encodeToByteArray().size > MAX_BYTES) throw ImportException(ImportError.FILE_TOO_BIG)
        val obj = runCatching { Json.parseToJsonElement(json) as JsonObject }.getOrElse { throw ImportException(ImportError.LINK_BROKEN) }
        val kind = (obj["kind"] as? JsonPrimitive)?.intOrNull
        if (kind != KIND) throw ImportException(ImportError.LINK_BROKEN)
        val content = (obj["content"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: throw ImportException(ImportError.LINK_BROKEN)
        val shared = RideLink.decode(content)
        val title = runCatching {
            (obj["tags"] as JsonArray).map { it.jsonArray }.firstOrNull { it.size >= 2 && it[0].jsonPrimitive.content == "title" }
                ?.get(1)?.jsonPrimitive?.content
        }.getOrNull()
        return if (title != null && shared.ride.name == null) shared.copy(ride = shared.ride.copy(name = title.take(MAX_TITLE))) else shared
    }

    /**
     * Geohash cells crossed by the line: precision 3 (~150 km) and 4 (~40 x 20 km).
     * Relays match tags exactly, so a search for an area asks for its cells at one precision.
     */
    internal fun cells(line: List<LatLon>): List<String> {
        val dense = densify(line, stepKm = 5.0)
        val fine = dense.map { Geohash.encode(it, 4) }.distinct()
        val coarse = fine.map { it.take(3) }.distinct().take(MAX_COARSE_CELLS)
        return coarse + if (fine.size <= MAX_FINE_CELLS) fine else emptyList()
    }

    private fun densify(line: List<LatLon>, stepKm: Double): List<LatLon> {
        if (line.size < 2) return line
        val out = mutableListOf(line.first())
        for ((a, b) in line.zipWithNext()) {
            val n = ceil(distanceKm(a, b) / stepKm).toInt().coerceAtLeast(1)
            for (i in 1..n) {
                val f = i.toDouble() / n
                out += LatLon(a.lat + (b.lat - a.lat) * f, a.lon + (b.lon - a.lon) * f)
            }
        }
        return out
    }

    private fun formatKm(km: Double): String {
        val tenths = kotlin.math.round(km * 10).toLong()
        return "${tenths / 10}.${tenths % 10}"
    }
}

/** Standard geohash (base 32, bits alternating longitude and latitude). */
object Geohash {
    private const val ALPHABET = "0123456789bcdefghjkmnpqrstuvwxyz"

    fun encode(p: LatLon, precision: Int): String {
        require(precision in 1..12)
        var latLo = -90.0
        var latHi = 90.0
        var lonLo = -180.0
        var lonHi = 180.0
        var even = true
        var bits = 0
        var ch = 0
        return buildString {
            while (length < precision) {
                if (even) {
                    val mid = (lonLo + lonHi) / 2
                    if (p.lon >= mid) { ch = ch * 2 + 1; lonLo = mid } else { ch *= 2; lonHi = mid }
                } else {
                    val mid = (latLo + latHi) / 2
                    if (p.lat >= mid) { ch = ch * 2 + 1; latLo = mid } else { ch *= 2; latHi = mid }
                }
                even = !even
                if (++bits == 5) {
                    append(ALPHABET[ch])
                    bits = 0
                    ch = 0
                }
            }
        }
    }
}
