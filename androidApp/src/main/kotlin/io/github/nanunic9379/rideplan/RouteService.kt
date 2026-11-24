package io.github.nanunic9379.rideplan

import android.content.Context
import com.valhalla.valhalla.Valhalla
import com.valhalla.valhalla.ValhallaException
import com.valhalla.valhalla.config.ValhallaConfigFactory
import com.valhalla.valhalla.config.ValhallaConfigManager
import com.valhalla.valhalla.files.ValhallaFile
import io.github.nanunic9379.rideplan.personal.MapDataItem
import io.github.nanunic9379.rideplan.ride.Ride
import io.github.nanunic9379.rideplan.route.RouteError
import io.github.nanunic9379.rideplan.route.RouteException
import io.github.nanunic9379.rideplan.route.RouteOptions
import io.github.nanunic9379.rideplan.route.CellsDownload
import io.github.nanunic9379.rideplan.route.FuelData
import io.github.nanunic9379.rideplan.route.FuelFile
import io.github.nanunic9379.rideplan.route.FuelStation
import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.route.RoutingPack
import io.github.nanunic9379.rideplan.route.RoutingCells
import io.github.nanunic9379.rideplan.route.RoutingData
import io.github.nanunic9379.rideplan.route.RoutingRegion
import io.github.nanunic9379.rideplan.route.SpeedLimits
import io.github.nanunic9379.rideplan.route.ValhallaSpeedLimits
import io.github.nanunic9379.rideplan.route.ValhallaRoute
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import kotlin.coroutines.cancellation.CancellationException

/**
 * On-phone routing (#3). Every function blocks: call it off the main thread.
 *
 * Network: only the list of published data and the chosen files, from the "routing-data" Release.
 * Grid-cell data (#45): the cells around the ride all go into the same tile directory, which
 * Valhalla reads directly. Whole regions downloaded before #45 remain usable.
 * The ride's stops never leave the phone.
 */
class RouteService(context: Context) {
    private val app = context.applicationContext
    /** Excluded from backup: hundreds of MB that can be downloaded again. */
    private val dir = File(app.noBackupFilesDir, "routing").apply { mkdirs() }

    /** Grid-cell data (#45): tile directory, copy of the build manifest, packs already extracted. */
    private val cellsDir = File(dir, "cells")
    private val tilesDir = File(cellsDir, "tiles")
    private val cellsManifest = File(cellsDir, "manifest.json")
    private val cellsInstalled = File(cellsDir, "installed.txt")
    /** Fuel stations of each installed cell (#7): <pack id>.tsv, only for builds that publish them. */
    private val fuelDir = File(cellsDir, "fuel")

    private var engine: Valhalla? = null
    private var engineData: String? = null

    /** The build of the cells on the phone; null if there are none. */
    fun localCells(): RoutingCells? = runCatching { RoutingCells.parse(cellsManifest.readText()) }.getOrNull()

    /** The packs already extracted into the tile directory. */
    fun installedPacks(): Set<String> =
        runCatching { cellsInstalled.readLines().map { it.trim() }.filter { it.isNotEmpty() }.toSet() }.getOrDefault(emptySet())

    /** The published cells; null if the Release does not have them yet (then whole regions are used). */
    fun publishedCells(): RoutingCells? = try {
        RoutingCells.parse(get(BuildConfig.ROUTING_DATA_URL + "cells.json").use { it.readBytes().decodeToString() })
    } catch (e: NotFound) {
        null
    } catch (e: IOException) {
        throw RouteException(RouteError.NETWORK, e.javaClass.simpleName)
    }

    /**
     * Downloads the missing packs one at a time, checks the SHA-256 of each and only then moves
     * its tiles into the shared directory. If interrupted, the completed packs are kept.
     */
    fun download(plan: CellsDownload, isActive: () -> Boolean, progress: (Long) -> Unit) {
        if (dir.usableSpace < plan.tileBytes + 200_000_000) throw RouteException(RouteError.NO_SPACE)
        val staging = File(cellsDir, "staging")
        try {
            closeEngine()
            if (plan.wipe || localCells()?.build != plan.cells.build) {
                // New build: the old cells do not match the new ones.
                cellsInstalled.delete()
                tilesDir.deleteRecursively()
                fuelDir.deleteRecursively()
                cellsDir.mkdirs()
                cellsManifest.writeText(plan.cells.toJson())
            }
            tilesDir.mkdirs()
            var base = 0L
            for (pack in plan.packs) {
                staging.deleteRecursively()
                staging.mkdirs()
                val digest = MessageDigest.getInstance("SHA-256")
                get(BuildConfig.ROUTING_DATA_URL + pack.file).use { raw ->
                    val start = base
                    val hashed = DigestInputStream(CountingStream(raw) { progress(start + it) }, digest)
                    // Do not close the zip stream: it would close the source before the hash is complete.
                    val zip = ZipInputStream(hashed)
                    while (true) {
                        if (!isActive()) throw CancellationException()
                        val entry = zip.nextEntry ?: break
                        if (entry.isDirectory) continue
                        // Only Valhalla tiles: no name can escape the directory.
                        if (!TILE.matches(entry.name)) throw RouteException(RouteError.CHECKSUM, entry.name.take(40))
                        val out = File(staging, entry.name)
                        out.parentFile?.mkdirs()
                        out.outputStream().buffered(1 shl 16).use { zip.copyTo(it, 1 shl 16) }
                    }
                    val buf = ByteArray(1 shl 16)
                    while (hashed.read(buf) >= 0) Unit
                }
                val sha = digest.digest().joinToString("") { "%02x".format(it) }
                if (sha != pack.sha256) throw RouteException(RouteError.CHECKSUM)
                staging.walkTopDown().filter { it.isFile }.forEach { f ->
                    val target = File(tilesDir, f.relativeTo(staging).path)
                    target.parentFile?.mkdirs()
                    target.delete()
                    if (!f.renameTo(target)) throw IOException("rename")
                }
                pack.fuel?.let { downloadFuel(pack, it) }
                cellsInstalled.appendText(pack.id + "\n")
                base += pack.bytes + (pack.fuel?.bytes ?: 0)
            }
        } catch (e: IOException) {
            throw RouteException(if (dir.usableSpace < 50_000_000) RouteError.NO_SPACE else RouteError.NETWORK, e.javaClass.simpleName)
        } finally {
            staging.deleteRecursively()
        }
    }

    /**
     * The fuel stations of one cell (#7), checked like the road packs. Never fatal: without the
     * list the ride still routes, and the ride sheet says the stations are unknown there.
     */
    private fun downloadFuel(pack: RoutingPack, fuel: FuelFile) {
        val part = File(fuelDir, pack.id + ".tsv.part")
        try {
            fuelDir.mkdirs()
            val bytes = get(BuildConfig.ROUTING_DATA_URL + fuel.file).use { it.readCapped(MAX_FUEL_BYTES) }
            val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            if (sha != fuel.sha256) return
            FuelData.parse(bytes.decodeToString())
            part.writeBytes(bytes)
            val target = File(fuelDir, pack.id + ".tsv")
            target.delete()
            part.renameTo(target)
        } catch (e: IOException) {
            // Only the station list is missing.
        } catch (e: IllegalArgumentException) {
            // Unknown list format: same as missing.
        } finally {
            part.delete()
        }
    }

    /**
     * The fuel stations that may be near [line] (#7), from the lists of the installed cells.
     * Null when part of the line has no list on the phone: then nothing is said about fuel.
     */
    fun fuelNear(line: List<LatLon>): List<FuelStation>? {
        val cells = localCells() ?: return null
        val installed = installedPacks()
        return FuelData.stationsAlong(cells, line) { id ->
            if (id in installed) File(fuelDir, "$id.tsv").takeIf { it.isFile }?.readText() else null
        }
    }

    /** Whole regions already downloaded and verified (before #45). */
    fun installed(): List<RoutingRegion> =
        dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty().mapNotNull { f ->
            runCatching { RoutingRegion.fromJson(f.readText()) }.getOrNull()?.takeIf { tar(it).isFile }
        }

    /** The list of published regions; empty if none is published yet. */
    fun published(): List<RoutingRegion> = try {
        RoutingRegion.parseManifest(get(BuildConfig.ROUTING_DATA_URL + "regions.json").use { it.readBytes().decodeToString() })
    } catch (e: NotFound) {
        emptyList()
    } catch (e: IOException) {
        throw RouteException(RouteError.NETWORK, e.javaClass.simpleName)
    }

    /**
     * Downloads the region: decompresses while downloading (no double copy on the phone) and checks
     * the SHA-256 of the published file. If anything goes wrong nothing half-done is left.
     */
    fun download(region: RoutingRegion, isActive: () -> Boolean, progress: (Long) -> Unit) {
        if (dir.usableSpace < region.tarBytes + 200_000_000) throw RouteException(RouteError.NO_SPACE)
        val part = File(dir, region.id + ".tar.part")
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            get(BuildConfig.ROUTING_DATA_URL + region.file).use { raw ->
                val hashed = DigestInputStream(CountingStream(raw, progress), digest)
                // Do not close the gzip stream: it would close the source before the hash is complete.
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

    /**
     * Real road between the ride's stops, using the grid cells or a whole region.
     * [instructions]: also the turn instructions (#5), in the app language.
     * [startHeading]: direction of travel at the start, when re-routing on the move.
     */
    @Synchronized
    fun route(
        ride: Ride,
        data: RoutingData,
        options: RouteOptions,
        instructions: Boolean = true,
        startHeading: Double? = null,
    ): ValhallaRoute.Result {
        val valhalla = engineFor(data)
        val response = try {
            val language = if (instructions) ValhallaRoute.language(app.resources.configuration.locales[0].toLanguageTag()) else null
            valhalla.routeRaw(ValhallaRoute.request(ride.stops, options, language, startHeading))
        } catch (e: ValhallaException) {
            // 44x: no route; 17x: stop far from any road or in an unconnected area.
            val msg = e.message.orEmpty()
            if (Regex("""code=(44\d|17\d)""").containsMatchIn(msg)) throw RouteException(RouteError.NO_ROUTE, msg)
            throw RouteException(RouteError.ENGINE, msg.take(120))
        }
        val result = try {
            ValhallaRoute.parse(response)
        } catch (e: Exception) {
            throw RouteException(RouteError.ENGINE, e.javaClass.simpleName)
        }
        // Speed limits (#31): only for the route that is ridden, once per computation. Any
        // failure only leaves the limits out: the route itself is fine.
        if (!instructions) return result
        val limits = try {
            ValhallaSpeedLimits.compute(result.shape) { valhalla.traceAttributesRaw(it) }
        } catch (e: Exception) {
            SpeedLimits.NONE
        }
        return result.copy(speedLimits = limits)
    }

    private fun engineFor(data: RoutingData): Valhalla {
        engine?.let { if (engineData == data.dataId) return it }
        closeEngine()
        val base = when (data) {
            is RoutingData.Region -> ValhallaConfigFactory.usingTileExtract(tar(data.region).absolutePath)
            // Tile directory: drop tile_extract, otherwise the engine first looks for the default tar.
            is RoutingData.Cells -> ValhallaConfigFactory.usingTilesDir(tilesDir.absolutePath)
                .let { it.copy(mjolnir = it.mjolnir?.copy(tileExtract = null)) }
        }
        // Towns passed near need a wide radius (#29); by default the engine rejects it.
        // Hard exclusions carry the motorway ban under 150 cc (#8): off by default in Valhalla.
        val config = base.copy(
            serviceLimits = base.serviceLimits?.copy(
                maxRadius = maxOf(base.serviceLimits?.maxRadius ?: 0, ValhallaRoute.PASS_RADIUS_M),
                allowHardExclusions = true,
            ),
        )
        val manager = ValhallaConfigManager(app, ValhallaFile(app, "valhalla.json", dir))
        return Valhalla(app, config, manager).also {
            engine = it
            engineData = data.dataId
        }
    }

    @Synchronized
    private fun closeEngine() {
        engine?.close()
        engine = null
        engineData = null
    }

    private fun tar(region: RoutingRegion) = File(dir, region.id + ".tar")

    /** Road data on the phone (#21): each whole region, and all the cells as one item. */
    fun mapData(): List<MapDataItem> {
        val regions = installed().map { MapDataItem(it.id, tar(it).length(), isCells = false) }
        val cells = cellsDir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
        return regions + listOfNotNull(MapDataItem(CELLS_ID, cells, isCells = true).takeIf { cells > 0 })
    }

    /** Deletes one item of [mapData]; it is downloaded again when a ride there needs it. */
    @Synchronized
    fun delete(item: MapDataItem) {
        closeEngine()
        if (item.isCells) cellsDir.deleteRecursively()
        else listOf(".tar", ".json").forEach { File(dir, item.id + it).delete() }
    }

    fun freeBytes(): Long = dir.usableSpace

    /** "Delete all data" (#21): every piece of road data. */
    @Synchronized
    fun wipe() {
        closeEngine()
        dir.listFiles().orEmpty().forEach { it.deleteRecursively() }
    }

    /** GitHub answers with a redirect to the real file. */

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

    private companion object {
        const val CELLS_ID = "cells"
        /** A cell's fuel list is a few kB: anything this big is not one. */
        const val MAX_FUEL_BYTES = 4_000_000
        val TILE = Regex("""[012](/\d{3})+\.gph""")
    }

    private class NotFound : IOException("HTTP 404")

    private fun InputStream.readCapped(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(1 shl 14)
        while (true) {
            val n = read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > max) throw IOException("fuel list too big")
        }
        return out.toByteArray()
    }

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
