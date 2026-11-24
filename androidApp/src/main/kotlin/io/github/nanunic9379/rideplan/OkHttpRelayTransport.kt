package io.github.nanunic9379.rideplan

import io.github.nanunic9379.rideplan.social.RelayListener
import io.github.nanunic9379.rideplan.social.RelaySocket
import io.github.nanunic9379.rideplan.social.RelayTransport
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * Websockets to Nostr relays (#3) with OkHttp: plain Java, no Google services, so the F-Droid
 * build works the same. Only wss:// addresses reach here (see Relays.normalize).
 */
class OkHttpRelayTransport(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build(),
) : RelayTransport {
    override fun connect(url: String, listener: RelayListener): RelaySocket {
        val request = Request.Builder().url(url).header("User-Agent", "RidePlan").build()
        val socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) = listener.onOpen()
            override fun onMessage(webSocket: WebSocket, text: String) = listener.onMessage(text)
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                listener.onClosed()
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = listener.onClosed()
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = listener.onClosed()
        })
        return object : RelaySocket {
            override fun send(text: String): Boolean = socket.send(text)
            override fun close() {
                if (!socket.close(1000, null)) socket.cancel()
            }
        }
    }
}
