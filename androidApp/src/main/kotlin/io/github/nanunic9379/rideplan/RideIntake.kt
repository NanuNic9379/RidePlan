package io.github.nanunic9379.rideplan

import android.content.Context
import android.net.Uri
import io.github.nanunic9379.rideplan.ride.ImportError
import io.github.nanunic9379.rideplan.ride.ImportException
import io.github.nanunic9379.rideplan.ride.Ride
import io.github.nanunic9379.rideplan.ride.RideArchive
import io.github.nanunic9379.rideplan.ride.RideImport
import io.github.nanunic9379.rideplan.ride.ShortLink
import java.net.HttpURLConnection
import java.net.URL

/**
 * Reading files and links on Android (#27, #5). Call off the main thread.
 * The content never leaves the phone. The only network request is opening Google's short link
 * (maps.app.goo.gl → full link): only the redirects are read, not the page.
 */
object RideIntake {
    private const val MAX_BYTES = RideArchive.MAX_BYTES

    fun fromUri(context: Context, uri: Uri): Ride {
        val bytes = try {
            read(context, uri)
        } catch (e: java.io.IOException) {
            throw ImportException(ImportError.FILE_UNREADABLE, e.javaClass.simpleName)
        } catch (e: SecurityException) {
            throw ImportException(ImportError.FILE_UNREADABLE, e.javaClass.simpleName)
        }
        return RideImport.parse(RideArchive.text(bytes))
    }

    private fun read(context: Context, uri: Uri): ByteArray =
        context.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > MAX_BYTES) throw ImportException(ImportError.FILE_TOO_BIG)
            }
            out.toByteArray()
        } ?: throw ImportException(ImportError.FILE_UNREADABLE)

    fun fromText(shared: String): Ride {
        val url = RideImport.findUrl(shared) ?: return RideImport.parse(shared)
        val full = if (RideImport.isShortLink(url)) {
            try { resolve(url) } catch (e: java.io.IOException) { throw ImportException(ImportError.NETWORK, e.javaClass.simpleName) }
        } else url
        return RideImport.parse(full)
    }

    /** Follows the short link's redirects, on Google hosts only (#113). */
    private fun resolve(shortUrl: String): String = ShortLink.resolve(shortUrl) { url ->
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) RidePlan")
        }
        try {
            conn.responseCode to conn.getHeaderField("Location")
        } finally {
            conn.disconnect()
        }
    }
}
