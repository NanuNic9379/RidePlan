package io.github.nanunic9379.rideplan.social

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * A subscription filter (NIP-01). Only what RidePlan asks for: one ride by its address.
 * [limit] keeps relays that store several versions from sending them all.
 */
data class NostrFilter(
    val kinds: List<Int> = emptyList(),
    val authors: List<String> = emptyList(),
    val d: List<String> = emptyList(),
    val limit: Int? = null,
) {
    fun toJson(): String = buildList {
        if (kinds.isNotEmpty()) add("\"kinds\":" + kinds.joinToString(",", "[", "]"))
        if (authors.isNotEmpty()) add("\"authors\":" + authors.joinToString(",", "[", "]", transform = NostrDraft::quote))
        if (d.isNotEmpty()) add("\"#d\":" + d.joinToString(",", "[", "]", transform = NostrDraft::quote))
        limit?.let { add("\"limit\":$it") }
    }.joinToString(",", "{", "}")

    companion object {
        /** Every version of the ride at [address] that a relay still has (normally only the newest). */
        fun of(address: NostrAddress) = NostrFilter(listOf(address.kind), listOf(address.pubkey), listOf(address.d), limit = 5)
    }
}

/** What the app sends to a relay (NIP-01). */
object ClientMessage {
    fun event(event: NostrEvent): String = "[\"EVENT\",${event.toJson()}]"
    fun req(subscription: String, vararg filters: NostrFilter): String =
        "[\"REQ\",${NostrDraft.quote(subscription)}," + filters.joinToString(",") { it.toJson() } + "]"
    fun close(subscription: String): String = "[\"CLOSE\",${NostrDraft.quote(subscription)}]"
}

/** What a relay sends (NIP-01). Unknown or malformed messages become [Unknown] and are ignored. */
sealed interface RelayMessage {
    /** [event] is null when the relay sent something that is not a well-formed event. */
    data class Event(val subscription: String, val event: NostrEvent?) : RelayMessage
    data class EndOfStored(val subscription: String) : RelayMessage
    data class Ok(val eventId: String, val accepted: Boolean, val message: String) : RelayMessage
    data class Closed(val subscription: String, val message: String) : RelayMessage
    data class Notice(val message: String) : RelayMessage
    data class Auth(val challenge: String) : RelayMessage
    data object Unknown : RelayMessage

    companion object {
        /** Relays send messages up to their own limit; anything bigger than this is not a ride. */
        const val MAX_CHARS = 64 * 1024

        fun parse(text: String): RelayMessage {
            if (text.length > MAX_CHARS) return Unknown
            val a = runCatching { Json.parseToJsonElement(text) as? JsonArray }.getOrNull() ?: return Unknown
            fun str(i: Int) = (a.getOrNull(i) as? JsonPrimitive)?.takeIf { it.isString }?.content
            return when (str(0)) {
                "EVENT" -> {
                    val sub = str(1) ?: return Unknown
                    Event(sub, a.getOrNull(2)?.let(NostrEvent::from))
                }
                "EOSE" -> EndOfStored(str(1) ?: return Unknown)
                "OK" -> {
                    val id = str(1) ?: return Unknown
                    val ok = (a.getOrNull(2) as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull ?: return Unknown
                    Ok(id, ok, str(3).orEmpty())
                }
                "CLOSED" -> Closed(str(1) ?: return Unknown, str(2).orEmpty())
                "NOTICE" -> Notice(str(1).orEmpty())
                "AUTH" -> Auth(str(1) ?: return Unknown)
                else -> Unknown
            }
        }
    }
}
