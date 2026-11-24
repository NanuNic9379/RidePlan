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
import io.github.nanunic9379.rideplan.route.LocalArea
import io.github.nanunic9379.rideplan.route.PublishedArea
import io.github.nanunic9379.rideplan.route.RoadNotes
import io.github.nanunic9379.rideplan.route.RoutingArea
import io.github.nanunic9379.rideplan.route.RoutingAreas
import io.github.nanunic9379.rideplan.route.RoutingPack
import io.github.nanunic9379.rideplan.route.RoutingCells
import io.github.nanunic9379.rideplan.route.RoutingData
import io.github.nanunic9379.rideplan.route.RoutingRegion
import io.github.nanunic9379.rideplan.route.ValhallaRoadAttributes
import io.github.nanunic9379.rideplan.route.RoadAttributes
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
 * Network: only the index of the areas, their lists of published data and the chosen files, from
 * the Releases ("routing-data" for Italy, "routing-<area>" for the other areas, #14).
 * Grid-cell data (#45): the cells around the ride all go into the tile directory of their area,
 * which Valhalla reads directly. Each area (#14) has its own directory, so a ride in the Alps
 * does not wipe the cells of Italy; Italy stays where it was before #14 (routing/cells), so
 * nothing is downloaded again. Whole regions downloaded before #45 remain usable.
 * The ride's stops never leave the phone.
 */
class RouteService(context: Context) {
    private val app = context.applicationContext
    /** Excluded from backup: hundreds of MB that can be downloaded again. */
    private val dir = File(app.noBackupFilesDir, "routing").apply { mkdirs() }

    /** The other areas (#14): routing/areas/<area id>/, laid out like Italy's directory. */
    private val areasDir = File(dir, "areas")

    /** Copy of the last index read, so the choice of the area also works offline. */
    private val indexCopy = File(dir, "areas.json")

    /**
     * Grid-cell data (#45) of one area: tile directory, copy of the build manifest, packs already
     * extracted, fuel stations of each installed cell (#7, <pack id>.tsv).
     */
    private inner class AreaFiles(val id: String) {
        val root = if (id == RoutingAreas.ITALY) File(dir, "cells") else File(areasDir, id)
        val tiles = File(root, "tiles")
        val manifest = File(root, "manifest.json")
        val installed = File(root, "installed.txt")
        val fuel = File(root, "fuel")
        /** Seasonal closures and speed cameras of this area (#13), from its Release. */
        val notes = File(root, RoadNotes.FILE)

        fun cells(): RoutingCells? = runCatching { RoutingCells.parse(manifest.readText()) }.getOrNull()

        fun installedPacks(): Set<String> =
            runCatching { installed.readLines().map { it.trim() }.filter { it.isNotEmpty() }.toSet() }.getOrDefault(emptySet())
    }

    private fun files(area: String): AreaFiles {
        // No id can lead outside the routing directory.
        require(RoutingAreas.AREA_ID.matches(area)) { "Invalid area" }
        return AreaFiles(area)
    }

    /** Italy first, then every valid directory under routing/areas. */
    private fun areaIds(): List<String> = listOf(RoutingAreas.ITALY) +
        areasDir.listFiles { f -> f.isDirectory }.orEmpty().map { it.name }
            .filter { RoutingAreas.AREA_ID.matches(it) && it != RoutingAreas.ITALY }.sorted()

    /** Road notes read from each area's file, by file and modification time. */
    private val notesCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, RoadNotes>>()

    /** Areas whose road notes were already fetched in this run of the app. */
    private val notesTried = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private var engine: Valhalla? = null
    private var engineData: String? = null

    /** The areas with cells on the phone. */
    fun localAreas(): List<LocalArea> = areaIds().mapNotNull { id ->
        val f = files(id)
        f.cells()?.let { LocalArea(id, it, f.installedPacks()) }
    }

    /** The areas as last read from the index; only Italy before the first reading. */
    fun areas(): List<RoutingArea> = runCatching { RoutingAreas.parse(indexCopy.readText()) }.getOrDefault(RoutingAreas.FALLBACK)

    /** Reads the index again; when it cannot (offline, not published yet) the last copy, or Italy only. */
    private fun refreshAreas(): List<RoutingArea> = try {
        val text = get(INDEX_URL).use { it.readCapped(MAX_INDEX_BYTES) }.decodeToString()
        RoutingAreas.parse(text).also { indexCopy.writeText(text) }
    } catch (e: IOException) {
        areas()
    } catch (e: IllegalArgumentException) {
        // An index this version cannot read: as if there were none.
        areas()
    }

    /**
     * Road data already on the phone that routes the ride, with no network. [strict]: only an
     * area whose outline holds every stop (see [RoutingAreas.local]); otherwise any area whose
     * cells cover them. Then the whole regions from before #45.
     */
    fun localData(points: List<LatLon>, strict: Boolean = true): RoutingData? {
        RoutingAreas.local(areas(), localAreas(), points, strict)?.let { a -> a.cells?.let { return RoutingData.Cells(it, a.id) } }
        return RoutingRegion.bestFor(installed(), points)?.let { RoutingData.Region(it) }
    }

    /**
     * The published builds of the areas that may hold the ride (#14), best first. Null if no area
     * has published cells (then whole regions are used, as before #45). Without network but with
     * data on the phone that covers the stops: an empty list, so that data is used.
     */
    fun publishedAreas(points: List<LatLon>): List<PublishedArea>? {
        val candidates = RoutingAreas.candidates(refreshAreas(), points).take(MAX_AREAS_TRIED)
        if (candidates.isEmpty()) return emptyList()
        val found = ArrayList<PublishedArea>()
        try {
            for (area in candidates) {
                val cells = try {
                    RoutingCells.parse(get(releaseUrl(area.release) + "cells.json").use { it.readBytes().decodeToString() })
                } catch (e: NotFound) {
                    continue
                } catch (e: IllegalArgumentException) {
                    continue // a format this version cannot read: as if the area were not there
                }
                found += PublishedArea(area, cells)
            }
        } catch (e: IOException) {
            if (localData(points, strict = false) != null) return emptyList()
            throw RouteException(RouteError.NETWORK, e.javaClass.simpleName)
        }
        return found.ifEmpty { null }
    }

    /** What to download for the ride among the published areas; null if none covers it. */
    fun plan(published: List<PublishedArea>, points: List<LatLon>): CellsDownload? =
        RoutingAreas.plan(published, localAreas(), points)

    /**
     * Downloads the missing packs one at a time, checks the SHA-256 of each and only then moves
     * its tiles into the shared directory. If interrupted, the completed packs are kept.
     */
    fun download(plan: CellsDownload, isActive: () -> Boolean, progress: (Long) -> Unit) {
        if (dir.usableSpace < plan.tileBytes + 200_000_000) throw RouteException(RouteError.NO_SPACE)
        val area = files(plan.area)
        require(RoutingAreas.RELEASE.matches(plan.release)) { "Invalid release" }
        val base = releaseUrl(plan.release)
        val staging = File(area.root, "staging")
        try {
            closeEngine()
            if (plan.wipe || area.cells()?.build != plan.cells.build) {
                // New build: the old cells do not match the new ones. Only this area's.
                area.installed.delete()
                area.tiles.deleteRecursively()
                area.fuel.deleteRecursively()
                area.root.mkdirs()
                area.manifest.writeText(plan.cells.toJson())
            }
            area.tiles.mkdirs()
            var done = 0L
            for (pack in plan.packs) {
                staging.deleteRecursively()
                staging.mkdirs()
                val digest = MessageDigest.getInstance("SHA-256")
                get(base + pack.file).use { raw ->
                    val start = done
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
                    val target = File(area.tiles, f.relativeTo(staging).path)
                    target.parentFile?.mkdirs()
                    target.delete()
                    if (!f.renameTo(target)) throw IOException("rename")
                }
                pack.fuel?.let { downloadFuel(area, base, pack, it) }
                area.installed.appendText(pack.id + "\n")
                done += pack.bytes + (pack.fuel?.bytes ?: 0)
            }
            downloadRoadNotes(area, base, plan.cells)
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
    private fun downloadFuel(area: AreaFiles, base: String, pack: RoutingPack, fuel: FuelFile) {
        val fuelDir = area.fuel
        val part = File(fuelDir, pack.id + ".tsv.part")
        try {
            fuelDir.mkdirs()
            val bytes = get(base + fuel.file).use { it.readCapped(MAX_FUEL_BYTES) }
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
     * The fuel stations that may be near [line] (#7), from the lists of the installed cells of
     * the first area that has them all along the line (#14).
     * Null when part of the line has no list on the phone: then nothing is said about fuel.
     */
    fun fuelNear(line: List<LatLon>): List<FuelStation>? {
        for (local in localAreas()) {
            val cells = local.cells ?: continue
            val fuelDir = files(local.id).fuel
            FuelData.stationsAlong(cells, line) { id ->
                if (id in local.installed) File(fuelDir, "$id.tsv").takeIf { it.isFile }?.readText() else null
            }?.let { return it }
        }
        return null
    }

    /**
     * Closures and speed cameras (#13) of one area, from its Release ([base]), checked with the
     * manifest's SHA-256 when it lists one, otherwise kept only if the file reads correctly. Never
     * fatal: without it the ride routes the same and nothing is said about closures or cameras.
     */
    private fun downloadRoadNotes(area: AreaFiles, base: String, cells: RoutingCells?) {
        notesTried += area.id
        val part = File(area.root, RoadNotes.FILE + ".part")
        try {
            area.root.mkdirs()
            val listed = cells?.roadNotes
            val bytes = get(base + (listed?.file ?: RoadNotes.FILE)).use { it.readCapped(RoadNotes.MAX_BYTES) }
            if (listed != null) {
                val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                if (sha != listed.sha256) return
            }
            RoadNotes.parse(bytes.decodeToString())
            part.writeBytes(bytes)
            area.notes.delete()
            part.renameTo(area.notes)
        } catch (e: IOException) {
            // Not published for this area, or no network: the old file (if any) stays.
        } catch (e: IllegalArgumentException) {
            // Unknown format: same as missing.
        } catch (e: kotlinx.serialization.SerializationException) {
            // Not JSON.
        } finally {
            part.delete()
        }
    }

    /**
     * The closures and cameras of every area on the phone (#13), merged (areas overlap: each road
     * or camera once); null when no area has the file. With [fetch], each area's file is
     * downloaded once per app run when it is missing or older than that area's road data (cells
     * downloaded before #13).
     */
    fun roadNotes(fetch: Boolean): RoadNotes? {
        val releases = areas().associate { it.id to it.release }
        val all = localAreas().mapNotNull { local ->
            val area = files(local.id)
            val cells = local.cells
            val release = releases[local.id] ?: if (local.id == RoutingAreas.ITALY) RoutingAreas.ITALY_RELEASE else null
            if (fetch && cells != null && release != null && RoutingAreas.RELEASE.matches(release) && local.id !in notesTried &&
                (!area.notes.isFile || readNotes(area)?.osmDate != cells.osmDate)
            ) downloadRoadNotes(area, releaseUrl(release), cells)
            readNotes(area)
        }
        if (all.isEmpty()) return null
        if (all.size == 1) return all.single()
        return RoadNotes(
            osmDate = all.first().osmDate,
            closures = all.flatMap { it.closures }.distinctBy { it.id },
            cameras = all.flatMap { it.cameras }.distinctBy { it.id },
        )
    }

    private fun readNotes(area: AreaFiles): RoadNotes? {
        val file = area.notes
        if (!file.isFile) return null
        val stamp = file.lastModified()
        notesCache[area.id]?.let { (at, notes) -> if (at == stamp) return notes }
        return runCatching { RoadNotes.parse(file.readText()) }.getOrNull()?.also { notesCache[area.id] = stamp to it }
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
            valhalla.routeRaw(ValhallaRoute.request(ride.stops, options, language, startHeading, ride.avoid.map { it.area }))
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
        // The same walk also gives the unpaved stretches (#9).
        val roads = try {
            ValhallaRoadAttributes.compute(result.shape) { valhalla.traceAttributesRaw(it) }
        } catch (e: Exception) {
            RoadAttributes.NONE
        }
        return result.copy(speedLimits = roads.speedLimits, surfaces = roads.surfaces)
    }

    private fun engineFor(data: RoutingData): Valhalla {
        engine?.let { if (engineData == data.dataId) return it }
        closeEngine()
        val base = when (data) {
            is RoutingData.Region -> ValhallaConfigFactory.usingTileExtract(tar(data.region).absolutePath)
            // Tile directory: drop tile_extract, otherwise the engine first looks for the default tar.
            is RoutingData.Cells -> ValhallaConfigFactory.usingTilesDir(files(data.area).tiles.absolutePath)
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

    /**
     * Road data on the phone (#21): each whole region, and the cells of each area as one item
     * (#14). With a single area the item has no area name, as before.
     */
    fun mapData(): List<MapDataItem> {
        val regions = installed().map { MapDataItem(it.id, tar(it).length(), isCells = false) }
        val sizes = areaIds().map { id -> id to files(id).root.walkBottomUp().filter { it.isFile }.sumOf { it.length() } }
            .filter { it.second > 0 }
        val names = areas().associate { it.id to it.name }
        return regions + sizes.map { (id, bytes) ->
            MapDataItem(cellsItemId(id), bytes, isCells = true, area = if (sizes.size > 1) (names[id] ?: id) else null)
        }
    }

    /** Deletes one item of [mapData]; it is downloaded again when a ride there needs it. */
    @Synchronized
    fun delete(item: MapDataItem) {
        closeEngine()
        if (item.isCells) files(if (item.id == CELLS_ID) RoutingAreas.ITALY else item.id.removePrefix(CELLS_PREFIX)).root.deleteRecursively()
        else listOf(".tar", ".json").forEach { File(dir, item.id + it).delete() }
    }

    /** Italy keeps the id it had before #14. */
    private fun cellsItemId(area: String) = if (area == RoutingAreas.ITALY) CELLS_ID else CELLS_PREFIX + area

    private fun releaseUrl(release: String) = BuildConfig.ROUTING_RELEASES_URL + release + "/"

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
        const val CELLS_PREFIX = "cells-"
        const val INDEX_URL = BuildConfig.ROUTING_RELEASES_URL + "routing-index/areas.json"
        /** The index is a few kB per area: anything this big is not one. */
        const val MAX_INDEX_BYTES = 2_000_000
        /** Areas whose published list is read for one ride (each is one small request). */
        const val MAX_AREAS_TRIED = 4
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
            if (out.size() > max) throw IOException("file too big")
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
