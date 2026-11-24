package io.github.nanunic9379.rideplan

import android.content.Context
import io.github.nanunic9379.rideplan.personal.MapDataItem
import io.github.nanunic9379.rideplan.route.MapTiles
import io.github.nanunic9379.rideplan.route.RideMap
import io.github.nanunic9379.rideplan.route.RideMapActions
import io.github.nanunic9379.rideplan.route.RideMapError
import io.github.nanunic9379.rideplan.route.RideMapInfo
import io.github.nanunic9379.rideplan.route.RideMapRequest
import io.github.nanunic9379.rideplan.route.RideMapStatus
import io.github.nanunic9379.rideplan.route.SavedRideMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.maplibre.compose.map.DefaultMapRuntime
import org.maplibre.compose.offline.DownloadProgress
import org.maplibre.compose.offline.DownloadStatus
import org.maplibre.compose.offline.OfflinePack
import org.maplibre.compose.offline.OfflinePackDefinition
import org.maplibre.compose.offline.OfflineStorage
import org.maplibre.compose.offline.OfflineStorageState
import org.maplibre.compose.offline.offlineStorage
import org.maplibre.compose.resource.MapRequestInterceptor
import org.maplibre.compose.resource.MapResourceError
import org.maplibre.compose.resource.MapResourceLoad
import org.maplibre.compose.resource.MapResourceLoadRequest
import org.maplibre.compose.resource.MapResourceProvider
import org.maplibre.compose.util.ExperimentalMaplibreComposeApi
import kotlinx.io.files.Path
import org.maplibre.spatialk.geojson.MultiPolygon
import org.maplibre.spatialk.geojson.Position
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * How the map talks to OpenFreeMap (#1). Set up once per process, before the first map.
 *
 * - Every tile address the map asks for tells which weekly tile version OpenFreeMap serves:
 *   the newest one seen is remembered, so a saved map made with an older one shows as out of date.
 * - While a ride map downloads, OpenFreeMap requests go through [load]: at most
 *   [MapTiles.OPENFREEMAP_MAX_PARALLEL] at a time, as their offline rules ask, with a
 *   RidePlan User-Agent. The rest of the time MapLibre's own network stack does the work.
 */
@OptIn(ExperimentalTime::class, ExperimentalMaplibreComposeApi::class)
object RideMapNetwork {
    private const val HOST = "https://tiles.openfreemap.org/"
    private const val KEY_VERSION = "tiles_version"

    @Volatile private var configured = false
    @Volatile var downloading = false
        private set
    private val gate = Semaphore(MapTiles.OPENFREEMAP_MAX_PARALLEL)
    private var userAgent = "RidePlan"
    private var prefs: android.content.SharedPreferences? = null

    /** The newest OpenFreeMap tile version the map has asked for. */
    val latest = MutableStateFlow<String?>(null)

    /** Tile version of the running download, as seen in its tile addresses. */
    @Volatile var downloadVersion: String? = null
        private set

    fun configure(context: Context) {
        if (configured) return
        synchronized(this) {
            if (configured) return
            configured = true
            prefs = context.applicationContext.getSharedPreferences("map", Context.MODE_PRIVATE)
            latest.value = prefs?.getString(KEY_VERSION, null)
            userAgent = "RidePlan/${BuildConfig.VERSION_NAME} (+https://github.com/NanuNic9379/RidePlan)"
            // The old place held only the browsing cache: free its space.
            for (name in listOf("maplibre-cache.db", "maplibre-cache.db-journal", "maplibre-cache.db-wal", "maplibre-cache.db-shm")) {
                java.io.File(context.cacheDir, name).delete()
            }
            // Throws if a map already started: then the app simply works without these extras.
            runCatching {
                DefaultMapRuntime.configure {
                    // Saved maps are not a cache: in the files folder, where Android does not
                    // clear them when space runs low.
                    cacheFile = Path(context.filesDir.path, "maplibre.db")
                    requestInterceptor = MapRequestInterceptor(rewriteUrl = { request -> seen(request.url); request.url })
                    resourceProvider = MapResourceProvider(
                        accepts = { request -> downloading && request.url.startsWith(HOST) },
                        load = ::load,
                    )
                }
            }
        }
    }

    fun start() {
        downloadVersion = null
        downloading = true
    }

    fun stop() {
        downloading = false
    }

    /** "Delete all data": the remembered version goes with the "map" preferences. */
    fun forget() {
        latest.value = null
    }

    private fun seen(url: String) {
        if (!url.startsWith(HOST)) return
        val version = RideMap.tilesVersion(url) ?: return
        if (downloading) downloadVersion = version
        // Versions are dated names: the newest sorts last.
        val known = latest.value
        if (known == null || version > known) {
            latest.value = version
            prefs?.edit()?.putString(KEY_VERSION, version)?.apply()
        }
    }

    private suspend fun load(request: MapResourceLoadRequest): MapResourceLoad {
        // A download asks for every letter range of every font, Chinese and Japanese included:
        // about 50 MB, more than the map itself. Ranges past the European, Middle Eastern and
        // Indian scripts are left out; they come already expired, so with signal the map
        // still fetches them when it needs them.
        if (request.usage == MapResourceLoadRequest.Usage.Offline && glyphStart(request.url)?.let { it >= RideMap.GLYPHS_KEPT } == true) {
            return MapResourceLoad.NoContent(null, Instant.fromEpochMilliseconds(System.currentTimeMillis()))
        }
        return gate.withPermit { withContext(Dispatchers.IO) { fetch(request) } }
    }

    private fun glyphStart(url: String): Int? = if (url.startsWith(HOST)) RideMap.glyphRangeStart(url) else null

    private fun fetch(request: MapResourceLoadRequest): MapResourceLoad {
        seen(request.url)
        val connection = try {
            URL(request.url).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            return MapResourceLoad.Failed(MapResourceError.Other, e.message ?: "bad address")
        }
        return try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", userAgent)
            request.priorEtag?.let { connection.setRequestProperty("If-None-Match", it) }
            request.range?.let { connection.setRequestProperty("Range", "bytes=${it.first}-${it.last}") }
            val code = connection.responseCode
            val expires = expiresOf(connection)
            val modified = connection.lastModified.takeIf { it > 0 }?.let { Instant.fromEpochMilliseconds(it) }
            when {
                code == HttpURLConnection.HTTP_NOT_MODIFIED -> MapResourceLoad.NotModified(modified, expires)
                code == HttpURLConnection.HTTP_NO_CONTENT -> MapResourceLoad.NoContent(modified, expires)
                code in 200..299 -> MapResourceLoad.Bytes(
                    connection.inputStream.use { it.readBytes() },
                    connection.getHeaderField("ETag"),
                    false,
                    modified,
                    expires,
                )
                code == HttpURLConnection.HTTP_NOT_FOUND -> MapResourceLoad.Failed(MapResourceError.NotFound, "HTTP $code")
                code == 429 -> MapResourceLoad.Failed(
                    MapResourceError.RateLimit,
                    "HTTP $code",
                    retryAfter = connection.getHeaderField("Retry-After")?.toLongOrNull()
                        ?.let { Instant.fromEpochMilliseconds(System.currentTimeMillis() + it * 1000) },
                )
                code >= 500 -> MapResourceLoad.Failed(MapResourceError.Server, "HTTP $code")
                else -> MapResourceLoad.Failed(MapResourceError.Other, "HTTP $code")
            }
        } catch (e: IOException) {
            MapResourceLoad.Failed(MapResourceError.Connection, e.message ?: "connection")
        } finally {
            connection.disconnect()
        }
    }

    private fun expiresOf(connection: HttpURLConnection): Instant? {
        val maxAge = connection.getHeaderField("Cache-Control")
            ?.let { Regex("""max-age=(\d+)""").find(it)?.groupValues?.get(1)?.toLongOrNull() }
        val millis = when {
            maxAge != null -> System.currentTimeMillis() + maxAge * 1000
            connection.expiration > 0 -> connection.expiration
            else -> return null
        }
        return Instant.fromEpochMilliseconds(millis)
    }
}

/**
 * The saved ride maps (#1): MapLibre offline packs in the map's own database, so the map uses
 * them by itself when there is no signal. One download at a time, always started by the rider.
 */
class RideMapDownloads(context: Context, private val scope: CoroutineScope) {
    private val density = context.resources.displayMetrics.density
    private var job: Job? = null

    /** Set by the activity: "Download only on Wi-Fi" is on and the network is metered (#44). */
    var mustAskForMobile: () -> Boolean = { false }

    val actions = RideMapActions(download = ::download, cancel = ::cancel, delete = ::delete)

    init {
        RideMapNetwork.configure(context)
        scope.launch {
            val storage = storage() ?: return@launch
            combine(storage.state, RideMapNetwork.latest) { state, _ -> state }.collect { state ->
                if (state is OfflineStorageState.Ready) show(state.packs)
            }
        }
    }

    /** The saved ride maps as rows of "Downloaded map data" (#21). */
    fun items(): List<MapDataItem> = actions.saved.map {
        MapDataItem(ID_PREFIX + it.info.ride, it.bytes, isCells = false, rideMap = it.info.name.orEmpty())
    }

    fun isRideMap(item: MapDataItem): Boolean = item.id.startsWith(ID_PREFIX)

    fun delete(item: MapDataItem) = delete(item.id.removePrefix(ID_PREFIX))

    /** "Delete all data": every saved ride map. */
    fun wipe() {
        cancel()
        RideMapNetwork.forget()
        scope.launch {
            val storage = storage() ?: return@launch
            for (pack in packs(storage)) if (info(pack) != null) runCatching { storage.delete(pack) }
            refresh(storage)
        }
    }

    private fun download(request: RideMapRequest, mobileConfirmed: Boolean) {
        if (job?.isActive == true) return
        if (!mobileConfirmed && mustAskForMobile()) {
            actions.status = RideMapStatus.AskMobile(request)
            return
        }
        val estimate = request.plan.estimate
        actions.status = RideMapStatus.Downloading(request.ride, 0, estimate.tiles, 0)
        job = scope.launch {
            val storage = storage()
            if (storage == null) {
                actions.status = RideMapStatus.Failed(request, RideMapError.OTHER, "map storage")
                return@launch
            }
            val mine = packs(storage).filter { info(it)?.ride == request.ride }
            // The same unfinished map resumes; anything else of this ride (older tiles, other
            // detail) goes once the new one is complete, so the old map works until then.
            var pack = mine.firstOrNull { p -> info(p)?.let { !it.complete && it.maxZoom == estimate.maxZoom } == true }
            val older = mine - setOfNotNull(pack)
            var done = false
            try {
                if (pack == null) {
                    val info = RideMapInfo(
                        ride = request.ride,
                        name = request.name,
                        maxZoom = estimate.maxZoom,
                        tiles = estimate.tiles,
                        bytes = estimate.bytes,
                        savedAt = System.currentTimeMillis(),
                    )
                    pack = storage.create(definition(request.plan), info.encode())
                }
                val running = pack
                RideMapNetwork.start()
                storage.resume(running)
                var end: DownloadProgress? = null
                running.downloadProgress.transformWhile { progress ->
                    emit(progress)
                    val stop = when (progress) {
                        is DownloadProgress.Healthy -> progress.status == DownloadStatus.Complete ||
                            RideMap.overLimit(progress.completedTileCount, progress.completedResourceBytes)
                        is DownloadProgress.Error, is DownloadProgress.TileLimitExceeded -> true
                        else -> false
                    }
                    if (stop) end = progress
                    !stop
                }.collect { progress ->
                    if (progress is DownloadProgress.Healthy) {
                        actions.status = RideMapStatus.Downloading(
                            request.ride,
                            progress.completedTileCount.coerceAtMost(estimate.tiles),
                            estimate.tiles,
                            progress.completedResourceBytes,
                            (progress.completedResourceCount.toFloat() / progress.requiredResourceCount.coerceAtLeast(1)).coerceIn(0f, 1f),
                        )
                    }
                }
                val last = end
                when {
                    last is DownloadProgress.Healthy && last.status == DownloadStatus.Complete -> {
                        val info = info(running) ?: RideMapInfo(request.ride, request.name, estimate.maxZoom, 0, 0)
                        running.setMetadata(
                            info.copy(
                                tiles = last.completedTileCount,
                                bytes = last.completedResourceBytes,
                                version = RideMapNetwork.downloadVersion ?: RideMapNetwork.latest.value,
                                complete = true,
                            ).encode(),
                        )
                        for (old in older) runCatching { storage.delete(old) }
                        done = true
                        actions.status = RideMapStatus.Idle
                    }
                    last is DownloadProgress.Healthy || last is DownloadProgress.TileLimitExceeded -> {
                        // Bigger than one OpenFreeMap download allows: stop and remove it.
                        storage.pause(running)
                        storage.delete(running)
                        pack = null
                        actions.status = RideMapStatus.Failed(request, RideMapError.LIMIT)
                    }
                    else -> {
                        storage.pause(running)
                        actions.status = RideMapStatus.Failed(request, RideMapError.NETWORK, (last as? DownloadProgress.Error)?.message)
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                actions.status = RideMapStatus.Failed(request, RideMapError.OTHER, e.message ?: e::class.simpleName)
            } finally {
                RideMapNetwork.stop()
                withContext(NonCancellable) {
                    val left = pack
                    if (!done && left != null) {
                        runCatching { storage.pause(left) }
                        // Cancel: what was saved so far goes too.
                        if (actions.status is RideMapStatus.Downloading) {
                            runCatching { storage.delete(left) }
                            actions.status = RideMapStatus.Idle
                        }
                    }
                    refresh(storage)
                }
            }
        }
    }

    private fun cancel() {
        job?.cancel()
        if (actions.status !is RideMapStatus.Downloading) actions.status = RideMapStatus.Idle
    }

    private fun delete(ride: String) {
        if (actions.status.let { it is RideMapStatus.Downloading && it.ride == ride }) cancel()
        scope.launch {
            job?.join()
            val storage = storage() ?: return@launch
            for (pack in packs(storage)) if (info(pack)?.ride == ride) runCatching { storage.delete(pack) }
            refresh(storage)
        }
    }

    private suspend fun storage(): OfflineStorage? = runCatching {
        DefaultMapRuntime.instance.offlineStorage.also { s -> s.state.first { it is OfflineStorageState.Ready || it is OfflineStorageState.Failed } }
    }.getOrNull()

    private fun packs(storage: OfflineStorage): Set<OfflinePack> =
        (storage.state.value as? OfflineStorageState.Ready)?.packs.orEmpty()

    private fun refresh(storage: OfflineStorage) = show(packs(storage))

    private fun show(packs: Set<OfflinePack>) {
        val latest = RideMapNetwork.latest.value
        actions.saved = packs.mapNotNull { pack ->
            val info = info(pack) ?: return@mapNotNull null
            val bytes = (pack.downloadProgress.value as? DownloadProgress.Healthy)?.completedResourceBytes
                ?.takeIf { it > 0 } ?: if (info.complete) info.bytes else 0
            SavedRideMap(info, bytes, stale = info.complete && info.version != null && latest != null && info.version != latest)
        }.sortedByDescending { it.info.savedAt }
    }

    private fun info(pack: OfflinePack): RideMapInfo? = RideMapInfo.decode(pack.metadata.value)

    /** The rows of tiles as one shape: MapLibre takes exactly those tiles and the ones above. */
    private fun definition(plan: RideMap.Plan): OfflinePackDefinition = OfflinePackDefinition.Shape(
        styleUrl = MapDefaults.STYLE,
        shape = MultiPolygon(
            plan.areas.map { a ->
                listOf(
                    listOf(
                        Position(a.west, a.south),
                        Position(a.east, a.south),
                        Position(a.east, a.north),
                        Position(a.west, a.north),
                        Position(a.west, a.south),
                    ),
                )
            },
        ),
        pixelRatio = density,
        minZoom = RideMap.MIN_ZOOM.toDouble(),
        maxZoom = plan.estimate.maxZoom.toDouble(),
    )

    private companion object {
        const val ID_PREFIX = "ridemap:"
    }
}
