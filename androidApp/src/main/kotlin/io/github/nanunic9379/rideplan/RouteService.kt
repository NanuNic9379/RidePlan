package io.github.nanunic9379.rideplan

import android.content.Context
import com.valhalla.valhalla.Valhalla
import com.valhalla.valhalla.ValhallaException
import com.valhalla.valhalla.config.ValhallaConfigFactory
import com.valhalla.valhalla.config.ValhallaConfigManager
import com.valhalla.valhalla.files.ValhallaFile
import io.github.nanunic9379.rideplan.ride.Ride
import io.github.nanunic9379.rideplan.route.RouteError
import io.github.nanunic9379.rideplan.route.RouteException
import io.github.nanunic9379.rideplan.route.RouteOptions
import io.github.nanunic9379.rideplan.route.RoutingRegion
import io.github.nanunic9379.rideplan.route.ValhallaRoute
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import kotlin.coroutines.cancellation.CancellationException

/**
 * Calcolo del percorso sul telefono (#3). Tutte le funzioni bloccano: da chiamare fuori dal
 * thread principale.
 *
 * Rete: solo la lista delle zone e il file della zona scelta, dalla Release "routing-data".
 * Le tappe del giro non escono mai dal telefono.
 */
class RouteService(context: Context) {
    private val app = context.applicationContext
    /** Fuori dal backup: sono centinaia di MB che si riscaricano. */
    private val dir = File(app.noBackupFilesDir, "routing").apply { mkdirs() }

    private var engine: Valhalla? = null
    private var engineRegion: String? = null

    /** Zone già scaricate e verificate. */
    fun installed(): List<RoutingRegion> =
        dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty().mapNotNull { f ->
            runCatching { RoutingRegion.fromJson(f.readText()) }.getOrNull()?.takeIf { tar(it).isFile }
        }

    /** La lista delle zone pubblicate; vuota se non ne è ancora pubblicata nessuna. */
    fun published(): List<RoutingRegion> = try {
        RoutingRegion.parseManifest(get(BuildConfig.ROUTING_DATA_URL + "regions.json").use { it.readBytes().decodeToString() })
    } catch (e: NotFound) {
        emptyList()
    } catch (e: IOException) {
        throw RouteException(RouteError.NETWORK, e.javaClass.simpleName)
    }

    /**
     * Scarica la zona: decomprime mentre scarica (nessuna copia doppia sul telefono) e controlla
     * lo SHA-256 del file pubblicato. Se qualcosa va storto non resta niente di mezzo.
     */
    fun download(region: RoutingRegion, isActive: () -> Boolean, progress: (Long) -> Unit) {
        if (dir.usableSpace < region.tarBytes + 200_000_000) throw RouteException(RouteError.NO_SPACE)
        val part = File(dir, region.id + ".tar.part")
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            get(BuildConfig.ROUTING_DATA_URL + region.file).use { raw ->
                val hashed = DigestInputStream(CountingStream(raw, progress), digest)
                // Non si chiude il gzip: chiuderebbe anche il flusso prima di finire l'hash.
                val gz = GZIPInputStream(hashed, 1 shl 16)
                val buf = ByteArray(1 shl 16)
                part.outputStream().buffered(1 shl 16).use { out ->
                    while (true) {
                        if (!isActive()) throw CancellationException()
                        val n = gz.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                    }
                }
                while (hashed.read(buf) >= 0) Unit
            }
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            if (sha != region.sha256) throw RouteException(RouteError.CHECKSUM)
            closeEngine()
            tar(region).delete()
            if (!part.renameTo(tar(region))) throw IOException("rename")
            File(dir, region.id + ".json").writeText(region.toJson())
        } catch (e: IOException) {
            throw RouteException(if (dir.usableSpace < 50_000_000) RouteError.NO_SPACE else RouteError.NETWORK, e.javaClass.simpleName)
        } finally {
            part.delete()
        }
    }

    /** Strada vera tra le tappe del giro, con i dati della zona. */
    @Synchronized
    fun route(ride: Ride, region: RoutingRegion, options: RouteOptions): ValhallaRoute.Result {
        val valhalla = engineFor(region)
        val response = try {
            valhalla.routeRaw(ValhallaRoute.request(ride.stops, options))
        } catch (e: ValhallaException) {
            // 44x: nessun percorso; 17x: tappa lontana da ogni strada o in una zona non collegata.
            val msg = e.message.orEmpty()
            if (Regex("""code=(44\d|17\d)""").containsMatchIn(msg)) throw RouteException(RouteError.NO_ROUTE, msg)
            throw RouteException(RouteError.ENGINE, msg.take(120))
        }
        return try {
            ValhallaRoute.parse(response)
        } catch (e: Exception) {
            throw RouteException(RouteError.ENGINE, e.javaClass.simpleName)
        }
    }

    private fun engineFor(region: RoutingRegion): Valhalla {
        engine?.let { if (engineRegion == region.id) return it }
        closeEngine()
        val base = ValhallaConfigFactory.usingTileExtract(tar(region).absolutePath)
        // I paesi come passaggio chiedono un raggio largo (#29); di base il motore lo rifiuta.
        val config = base.copy(serviceLimits = base.serviceLimits?.copy(maxRadius = maxOf(base.serviceLimits?.maxRadius ?: 0, ValhallaRoute.PASS_RADIUS_M)))
        val manager = ValhallaConfigManager(app, ValhallaFile(app, "valhalla.json", dir))
        return Valhalla(app, config, manager).also {
            engine = it
            engineRegion = region.id
        }
    }

    @Synchronized
    private fun closeEngine() {
        engine?.close()
        engine = null
        engineRegion = null
    }

    private fun tar(region: RoutingRegion) = File(dir, region.id + ".tar")

    /** GitHub risponde con un redirect verso il file vero. */
    private fun get(url: String): InputStream {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "RidePlan/${BuildConfig.VERSION_NAME}")
        }
        val code = conn.responseCode
        if (code != 200) {
            conn.disconnect()
            throw if (code == 404) NotFound() else IOException("HTTP $code")
        }
        return conn.inputStream
    }

    private class NotFound : IOException("HTTP 404")

    private class CountingStream(private val inner: InputStream, private val progress: (Long) -> Unit) : InputStream() {
        private var count = 0L
        private var reported = 0L
        override fun read(): Int = inner.read().also { if (it >= 0) add(1) }
        override fun read(b: ByteArray, off: Int, len: Int): Int = inner.read(b, off, len).also { if (it > 0) add(it.toLong()) }
        override fun close() = inner.close()
        private fun add(n: Long) {
            count += n
            if (count - reported >= 1_000_000) {
                reported = count
                progress(count)
            }
        }
    }
}
