package io.github.nanunic9379.rideplan.social

/**
 * Public Nostr relays (#3): servers run by others, never by the project (docs/social.md).
 * The user can change the list; nothing is contacted until they publish or check a ride.
 */
object Relays {
    /** [why]: the reason it is a default, for the docs and code review (not shown in the app). */
    data class Default(val url: String, val why: String)

    /**
     * Checked in November 2026 with their NIP-11 documents: all are free, need no account or
     * payment, run strfry (which keeps the newest version of addressable events, NIP-01), accept
     * messages of 128 KB or more (a ride event is at most 12 KB), and belong to four different
     * operators, so one closing or deleting old events does not lose the ride.
     */
    val DEFAULTS = listOf(
        Default("wss://relay.damus.io", "one of the largest relays, read by most clients (Damus, Amethyst, Primal)"),
        Default("wss://nos.lol", "large general relay with long retention and spam filtering"),
        Default("wss://relay.primal.net", "Primal's public relay, high uptime, also read by its web client"),
        Default("wss://offchain.pub", "independent operator, so the defaults do not depend on the big apps only"),
    )

    const val MAX = 8
    private const val MAX_LENGTH = 200

    /**
     * The relay address in the form the app keeps: `wss://host[:port][/path]`, lowercase host,
     * no trailing slash. A bare host gets `wss://`. Plain `ws://` is refused: the app allows no
     * unencrypted traffic. Null when it is not a usable address.
     */
    fun normalize(text: String): String? {
        var t = text.trim()
        if (t.isEmpty() || t.length > MAX_LENGTH || t.any { it.isWhitespace() }) return null
        if (!t.contains("://")) t = "wss://$t"
        val scheme = t.substringBefore("://").lowercase()
        if (scheme != "wss") return null
        val rest = t.substringAfter("://")
        val hostPort = rest.substringBefore('/').lowercase()
        val path = rest.substringAfter('/', "").trimEnd('/')
        val host = hostPort.substringBefore(':')
        val port = hostPort.substringAfter(':', "")
        if (host.isEmpty() || !host.contains('.') || host.startsWith('.') || host.endsWith('.')) return null
        if (!host.all { it.isLetterOrDigit() || it == '.' || it == '-' }) return null
        if (port.isNotEmpty() && port.toIntOrNull()?.takeIf { it in 1..65535 } == null) return null
        if (path.any { it == '?' || it == '#' }) return null
        return "wss://$hostPort" + if (path.isNotEmpty()) "/$path" else ""
    }

    /** [list] with [text] added at the end, or null if it is not valid, already there, or the list is full. */
    fun add(list: List<String>, text: String): List<String>? {
        val url = normalize(text) ?: return null
        if (url in list || list.size >= MAX) return null
        return list + url
    }

    /** Where to look for a ride: the user's relays first, then the hints of its address. */
    fun forReading(mine: List<String>, hints: List<String>): List<String> =
        (mine + hints.mapNotNull(::normalize)).distinct().take(MAX)
}
