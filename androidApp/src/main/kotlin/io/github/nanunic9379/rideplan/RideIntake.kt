package io.github.nanunic9379.rideplan

import android.content.Context
import android.net.Uri
import io.github.nanunic9379.rideplan.ride.ImportException
import io.github.nanunic9379.rideplan.ride.Ride
import io.github.nanunic9379.rideplan.ride.RideImport
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.zip.ZipInputStream

/**
 * Lettura di file e link su Android (#27, #5). Da chiamare fuori dal thread principale.
 * Il contenuto non lascia il telefono. L'unica richiesta di rete è l'apertura del link corto
 * di Google (maps.app.goo.gl → link completo): si leggono solo i redirect, la pagina no.
 */
object RideIntake {
    private const val MAX_BYTES = 30 * 1024 * 1024
    private const val MAX_REDIRECTS = 6

    fun fromUri(context: Context, uri: Uri): Ride {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > MAX_BYTES) throw ImportException("Il file è troppo grande (oltre 30 MB).")
            }
            out.toByteArray()
        } ?: throw ImportException("Non riesco ad aprire il file.")
        return RideImport.parse(text(bytes))
    }

    fun fromText(shared: String): Ride {
        val url = RideImport.findUrl(shared) ?: return RideImport.parse(shared)
        return RideImport.parse(if (RideImport.isShortLink(url)) resolve(url) else url)
    }

    /** KMZ = zip con dentro un KML (export di Google My Maps). */
    private fun text(bytes: ByteArray): String {
        val isZip = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        if (!isZip) return bytes.decodeToString()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name.endsWith(".kml", ignoreCase = true) || entry.name.endsWith(".gpx", ignoreCase = true)) {
                    return zip.readBytes().also { if (it.size > MAX_BYTES) throw ImportException("Il file è troppo grande.") }.decodeToString()
                }
            }
        }
        throw ImportException("Nel file compresso non c'è né un KML né un GPX.")
    }

    /** Segue i redirect del link corto finché non arriva al link completo di Google Maps. */
    private fun resolve(shortUrl: String): String {
        var url = shortUrl
        repeat(MAX_REDIRECTS) {
            if (!RideImport.isShortLink(url) && url.contains("/maps")) return url
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) RidePlan")
            }
            try {
                val location = conn.getHeaderField("Location")
                if (conn.responseCode !in 300..399 || location == null) {
                    throw ImportException("Il link di Google non porta a un percorso (risposta ${conn.responseCode}).")
                }
                url = consentTarget(URL(URL(url), location).toString())
            } finally {
                conn.disconnect()
            }
        }
        if (url.contains("/maps")) return url
        throw ImportException("Il link di Google fa troppi passaggi: aprilo in Google Maps e ricondividilo.")
    }

    /** In Europa Google può passare dalla pagina dei cookie: il link vero è nel parametro "continue". */
    private fun consentTarget(url: String): String {
        if (!url.contains("consent.google.")) return url
        val cont = url.substringAfter("continue=", "").substringBefore('&')
        return if (cont.isEmpty()) url else URLDecoder.decode(cont, "UTF-8")
    }
}
