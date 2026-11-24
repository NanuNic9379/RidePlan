package io.github.nanunic9379.rideplan.social

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/** A websocket to one relay, opened by the platform (OkHttp on Android). */
interface RelaySocket {
    fun send(text: String): Boolean
    fun close()
}

/** What a socket reports. Called from any thread. */
interface RelayListener {
    fun onOpen()
    fun onMessage(text: String)
    /** Connection failed or closed: no more messages. */
    fun onClosed()
}

fun interface RelayTransport {
    fun connect(url: String, listener: RelayListener): RelaySocket
}

/**
 * One exchange with one relay, without threads or sockets, so it can be tested:
 * [start] gives what to send when the socket opens, [receive] takes each message and says
 * when the exchange is over.
 */
interface RelayExchange {
    fun start(): List<String>
    /** True when nothing more is expected from this relay. */
    fun receive(message: RelayMessage): Boolean
}

/** Publishing one event: done when the relay answers OK (accepted or not) for that id. */
class PublishExchange(private val event: NostrEvent) : RelayExchange {
    var result: PublishResult = PublishResult.NO_ANSWER
        private set
    var message: String = ""
        private set

    override fun start() = listOf(ClientMessage.event(event))

    override fun receive(message: RelayMessage): Boolean {
        if (message is RelayMessage.Ok && message.eventId == event.id) {
            result = if (message.accepted) PublishResult.ACCEPTED else PublishResult.REJECTED
            this.message = message.message
            return true
        }
        return false
    }
}

enum class PublishResult { ACCEPTED, REJECTED, NO_ANSWER }

/**
 * Asking for stored events: done at the end of stored events (EOSE) or when the relay closes
 * the subscription. [answered] is true once the relay replied in any way to the request.
 */
class FetchExchange(private val filter: NostrFilter, private val subscription: String, private val maxEvents: Int = 20) : RelayExchange {
    val events = mutableListOf<NostrEvent>()
    var answered = false
        private set

    override fun start() = listOf(ClientMessage.req(subscription, filter))

    override fun receive(message: RelayMessage): Boolean = when (message) {
        is RelayMessage.Event -> {
            if (message.subscription == subscription) {
                answered = true
                message.event?.let { if (events.size < maxEvents) events += it }
            }
            events.size >= maxEvents
        }
        is RelayMessage.EndOfStored -> (message.subscription == subscription).also { if (it) answered = true }
        // Closed (e.g. auth required): the relay answered, with nothing.
        is RelayMessage.Closed -> (message.subscription == subscription).also { if (it) answered = true }
        else -> false
    }

    fun closeMessage() = ClientMessage.close(subscription)
}

/**
 * Talks to several relays at once, each with its own time limit. No connection stays open:
 * every call opens the sockets, does one exchange and closes them.
 */
class RelayPool(private val transport: RelayTransport, private val timeoutMs: Long = 10_000) {

    /** Sends [event] to every relay; the result per relay. */
    suspend fun publish(event: NostrEvent, relays: List<String>): Map<String, PublishResult> = coroutineScope {
        relays.map { url ->
            async {
                val ex = PublishExchange(event)
                run(url, ex)
                url to ex.result
            }
        }.awaitAll().toMap()
    }

    /** Stored events matching [filter], from every relay; [Fetched.answered]: how many relays replied. */
    suspend fun fetch(filter: NostrFilter, relays: List<String>): Fetched = coroutineScope {
        val all = relays.mapIndexed { i, url ->
            async {
                val ex = FetchExchange(filter, "rp$i")
                run(url, ex, closing = { ex.closeMessage() })
                ex
            }
        }.awaitAll()
        Fetched(all.flatMap { it.events }, all.count { it.answered })
    }

    data class Fetched(val events: List<NostrEvent>, val answered: Int)

    private sealed interface Signal {
        data object Open : Signal
        data class Text(val text: String) : Signal
        data object Closed : Signal
    }

    private suspend fun run(url: String, exchange: RelayExchange, closing: (() -> String)? = null) {
        val signals = Channel<Signal>(Channel.UNLIMITED)
        val socket = runCatching {
            transport.connect(url, object : RelayListener {
                override fun onOpen() { signals.trySend(Signal.Open) }
                override fun onMessage(text: String) { signals.trySend(Signal.Text(text)) }
                override fun onClosed() { signals.trySend(Signal.Closed) }
            })
        }.getOrNull() ?: return
        try {
            withTimeoutOrNull(timeoutMs) {
                while (true) {
                    when (val s = signals.receive()) {
                        Signal.Open -> exchange.start().forEach { socket.send(it) }
                        is Signal.Text -> if (exchange.receive(RelayMessage.parse(s.text))) break
                        Signal.Closed -> break
                    }
                }
            }
            closing?.let { socket.send(it()) }
        } finally {
            socket.close()
            signals.close()
        }
    }
}
