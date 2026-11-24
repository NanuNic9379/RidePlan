package io.github.nanunic9379.rideplan

import android.app.LocaleManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import io.github.nanunic9379.rideplan.profile.BackupActions
import io.github.nanunic9379.rideplan.profile.BackupNotice
import io.github.nanunic9379.rideplan.profile.ProfileStore
import io.github.nanunic9379.rideplan.profile.RideExport
import io.github.nanunic9379.rideplan.profile.ThemeMode
import io.github.nanunic9379.rideplan.profile.forBike
import io.github.nanunic9379.rideplan.personal.LanguageActions
import io.github.nanunic9379.rideplan.personal.MapDataActions
import io.github.nanunic9379.rideplan.personal.PlatformActions
import io.github.nanunic9379.rideplan.ride.GpxExport
import io.github.nanunic9379.rideplan.ride.ImportActions
import io.github.nanunic9379.rideplan.ride.Page
import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.Ride
import io.github.nanunic9379.rideplan.ride.RideScreenState
import io.github.nanunic9379.rideplan.ride.Stop
import io.github.nanunic9379.rideplan.route.RouteActions
import io.github.nanunic9379.rideplan.route.RouteError
import io.github.nanunic9379.rideplan.route.RouteException
import io.github.nanunic9379.rideplan.route.Curves
import io.github.nanunic9379.rideplan.route.RouteOptions
import io.github.nanunic9379.rideplan.route.routable
import io.github.nanunic9379.rideplan.route.unrouted
import io.github.nanunic9379.rideplan.route.RouteState
import io.github.nanunic9379.rideplan.route.RoutingRegion
import io.github.nanunic9379.rideplan.route.RoutingData
import io.github.nanunic9379.rideplan.route.CellsDownload
import io.github.nanunic9379.rideplan.route.DataDownload
import io.github.nanunic9379.rideplan.route.RegionDownload
import io.github.nanunic9379.rideplan.route.routed
import io.github.nanunic9379.rideplan.route.toggledStop
import io.github.nanunic9379.rideplan.update.UpdateOverlay
import io.github.nanunic9379.rideplan.update.updateActions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MainActivity : ComponentActivity() {
    /** Survives the activity being recreated (theme, language, font size): see [Session]. */
    private val session by lazy { ViewModelProvider(this)[Session::class.java] }
    private val rides get() = session.rides
    private val router get() = session.router
    private var routeJob: Job?
        get() = session.routeJob
        set(value) { session.routeJob = value }
    private lateinit var store: ProfileStore
    /** Turn-by-turn navigation (#5): registered here because it asks for the location permission. */
    private val navigation = AndroidNavigation(this, ::rerouteFromHere)

    /** Backup (#43): the user chooses where to save it (Downloads, Drive, ...). */
    private val saveBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) backupIo {
            contentResolver.openOutputStream(uri, "wt")!!.use { it.write(store.backupText().toByteArray()) }
            withContext(Dispatchers.Main) {
                store.update { it.copy(settings = it.settings.copy(lastBackupAt = store.time())) }
            }
            BackupNotice.Saved
        }
    }

    private val restoreBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) backupIo {
            val text = contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
            val profile = ProfileStore.decode(text)
            withContext(Dispatchers.Main) { BackupNotice.Restored(store.restore(ProfileStore.encode(profile))) }
        }
    }

    /** "Export all rides" (#21): one .zip with a GPX per ride, where the user chooses. */
    private val exportRides = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@registerForActivityResult
        val saved = store.data.rides
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val files = RideExport.files(saved) { "RidePlan" }
                    contentResolver.openOutputStream(uri, "wt")!!.use { out ->
                        ZipOutputStream(out).use { zip ->
                            files.forEach { (name, text) ->
                                zip.putNextEntry(ZipEntry(name))
                                zip.write(text.toByteArray())
                                zip.closeEntry()
                            }
                        }
                    }
                }.isSuccess
            }
            platform.exportNotice = ok
        }
    }

    private lateinit var platform: PlatformActions

    private fun backupIo(block: suspend () -> BackupNotice) {
        lifecycleScope.launch {
            store.backupNotice = runCatching { withContext(Dispatchers.IO) { block() } }.getOrElse { BackupNotice.Failed }
        }
    }


    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) load { RideIntake.fromUri(this, uri) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val actions = ImportActions(
            openFile = { openDocument.launch(arrayOf("*/*")) },
            readClipboard = ::clipboardText,
            importText = { text -> load { RideIntake.fromText(text) } },
            openRide = ::openRide,
            shareGpx = ::shareGpx,
            shareText = ::shareText,
        )
        val routeActions = RouteActions(
            download = ::download,
            cancel = ::cancelRoute,
            retry = { rides.ride?.let(::planRoute) },
            changeOptions = ::changeOptions,
            toggleStop = { i -> rides.ride?.let { reroute(it.toggledStop(i)) } },
            editStops = ::editStops,
        )
        val fresh = session.store == null
        store = session.store ?: profileStore().also { session.store = it }
        if (fresh) {
            rides.options = loadOptions()
            // After the system closed the app in the background, the saved ride on screen comes back.
            savedInstanceState?.getString(STATE_RIDE)?.let { id -> store.data.rides.firstOrNull { it.id == id } }?.let { saved ->
                openRide(saved.toRide())
                rides.savedId = saved.id
                rides.panelOpen = savedInstanceState.getBoolean(STATE_PANEL, true)
            }
        }
        val backupActions = BackupActions(
            save = { saveBackup.launch("rideplan-backup.json") },
            restore = { restoreBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
        )
        platform = PlatformActions(
            versionName = BuildConfig.VERSION_NAME,
            openUrl = ::openUrl,
            exportAll = { exportRides.launch("rideplan-rides.zip") },
            language = if (Build.VERSION.SDK_INT >= 33) languageActions() else null,
            mapData = MapDataActions(
                list = router::mapData,
                delete = { item -> cancelRoute(); router.delete(item) },
                freeBytes = router::freeBytes,
            ),
            wipeDevice = ::wipeDevice,
            updates = updateActions(lifecycleScope),
        )
        onBackPressedDispatcher.addCallback(this, backToHome)
        setContent {
            // The system bars follow the theme chosen in Settings, not only the phone's.
            val dark = when (store.data.settings.theme) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            App(
                rides = rides,
                store = store,
                importActions = actions,
                routeActions = routeActions,
                backupActions = backupActions,
                mapViews = MapViewPrefs(this),
                platform = platform,
                navigation = navigation,
                overlay = { UpdateOverlay(checkAtStart = store.data.settings.checkUpdates) },
            )
        }
        if (savedInstanceState == null) handle(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        rides.savedId?.takeIf { rides.ride != null }?.let { outState.putString(STATE_RIDE, it) }
        outState.putBoolean(STATE_PANEL, rides.panelOpen)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** "Share" from Google Maps (text with the link) or opening a GPX/KML/KMZ file. */
    private fun handle(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val stream = streamExtra(intent)
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                when {
                    stream != null -> load { RideIntake.fromUri(this, stream) }
                    !text.isNullOrBlank() -> load { RideIntake.fromText(text) }
                }
            }
            Intent.ACTION_VIEW -> intent.data?.let { uri ->
                if (uri.scheme == "content" || uri.scheme == "file") load { RideIntake.fromUri(this, uri) }
                else load { RideIntake.fromText(uri.toString()) }
            }
        }
    }

    /**
     * Back always goes one level up (#21): off the map to the page it was opened from, then
     * page by page, then to the Rides tab; from there it does what Android always does.
     */
    private val backToHome = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (rides.navigating) {
                rides.navigating = false
            } else if (rides.onMap) {
                cancelRoute()
                rides.goHome()
            } else if (rides.back()) {
                Unit
            } else if (rides.page != Page.HOME) {
                rides.tab(Page.HOME)
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        }
    }

    /**
     * Profile and saved rides in an app file (#41): they stay on the phone.
     * Written to a temporary file and then renamed: an interruption cannot corrupt anything.
     */
    private fun profileStore(): ProfileStore {
        val file = File(filesDir, "profile.json")
        return ProfileStore(
            read = { file.takeIf { it.exists() }?.readText() },
            write = { text ->
                val tmp = File(filesDir, "profile.json.tmp")
                tmp.writeText(text)
                if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
            },
            backup = { text -> File(filesDir, "profile-rovinato-${System.currentTimeMillis()}.json").writeText(text) },
            now = System::currentTimeMillis,
        )
    }

    /** A ride from "Your rides": straight onto the map, routed again if needed. */
    private fun openRide(ride: Ride) {
        routeJob?.cancel()
        rides.options = loadOptions()
        rides.show(Result.success(ride))
        if (ride.needsRoute) planRoute(ride)
    }

    @Suppress("DEPRECATION")
    private fun streamExtra(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else intent.getParcelableExtra(Intent.EXTRA_STREAM)

    /**
     * GPX in the app cache, handed over through Android's "Share" menu (Garmin, WhatsApp,
     * Drive...). Only the ride file is readable from outside, and only by the chosen app.
     */
    /** The ride link (#6) to a chat or an email. */
    private fun shareText(text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        startActivity(Intent.createChooser(send, null))
    }

    private fun shareGpx(ride: Ride, fallbackName: String) {
        lifecycleScope.launch {
            val uri = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(cacheDir, "shared").apply { deleteRecursively(); mkdirs() }
                    val file = File(dir, GpxExport.fileName(ride, fallbackName))
                    file.writeText(GpxExport.write(ride, fallbackName))
                    FileProvider.getUriForFile(this@MainActivity, "$packageName.files", file)
                }.getOrNull()
            } ?: return@launch
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/gpx+xml"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TITLE, uri.lastPathSegment)
                clipData = ClipData.newRawUri(null, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, null))
        }
    }

    private fun clipboardText(): String? {
        val cm = getSystemService(ClipboardManager::class.java) ?: return null
        val clip = cm.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(this)?.toString()
    }

    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    /** App language (#21) through Android 13's per-app languages: it survives restarts. */
    @RequiresApi(33)
    private fun languageActions(): LanguageActions {
        val locales = getSystemService(LocaleManager::class.java)
        return LanguageActions(
            current = { locales.applicationLocales.takeIf { !it.isEmpty }?.get(0)?.toLanguageTag() },
            set = { tag -> locales.applicationLocales = if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag) },
        )
    }

    /** "Delete all data" (#21): what lives outside the profile file. */
    private fun wipeDevice() {
        cancelRoute()
        rides.goHome()
        getSharedPreferences("map", MODE_PRIVATE).edit().clear().apply()
        getSharedPreferences("route", MODE_PRIVATE).edit().clear().apply()
        rides.options = loadOptions()
        File(cacheDir, "shared").deleteRecursively()
        lifecycleScope.launch(Dispatchers.IO) { router.wipe() }
    }

    /** Road data only on Wi-Fi (#21): checked before every download, metered networks refused. */
    private fun checkNetwork() {
        if (!store.data.settings.wifiOnly) return
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        if (cm.isActiveNetworkMetered) throw RouteException(RouteError.WIFI_ONLY)
    }

    private fun load(block: () -> Ride) {
        rides.error = null
        rides.busy = true
        session.viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching(block) }
            routeJob?.cancel()
            // A ride received by link is calculated the way the sender set it up (#50);
            // your saved preferences do not change and come back with the next ride.
            result.getOrNull()?.let { rides.options = it.sharedOptions ?: loadOptions() }
            rides.show(result)
            result.getOrNull()?.takeIf { it.needsRoute }?.let(::planRoute)
        }
    }

    /**
     * Road route for a ride made only of stops (#3): with the data already downloaded it starts
     * right away, otherwise it offers to download the grid cells around the ride (#45). Whole
     * regions downloaded earlier still work; they are offered only while the cells are not published.
     */
    private fun planRoute(ride: Ride) {
        routeJob?.cancel()
        routeJob = session.viewModelScope.launch {
            rides.route = RouteState.Checking
            routeStep(ride) {
                val points = ride.stops.map { it.point }
                val local: RoutingData? = withContext(Dispatchers.IO) {
                    val cells = router.localCells()
                    if (cells != null && CellsDownload.coveredLocally(cells, router.installedPacks(), points)) RoutingData.Cells(cells)
                    else RoutingRegion.bestFor(router.installed(), points)?.let { RoutingData.Region(it) }
                }
                if (local != null) {
                    compute(ride, local)
                } else {
                    val download: DataDownload? = withContext(Dispatchers.IO) {
                        val published = router.publishedCells()
                        if (published != null) CellsDownload.plan(published, router.localCells(), router.installedPacks(), points)
                        else RoutingRegion.bestFor(router.published(), points)?.let(::RegionDownload)
                    }
                    when {
                        download == null -> rides.route = RouteState.NotCovered
                        download is CellsDownload && download.packs.isEmpty() -> compute(ride, RoutingData.Cells(download.cells))
                        else -> rides.route = RouteState.NeedsData(download)
                    }
                }
            }
        }
    }

    private fun download(data: DataDownload) {
        val ride = rides.ride ?: return
        routeJob?.cancel()
        routeJob = session.viewModelScope.launch {
            rides.route = RouteState.Downloading(data, 0)
            routeStep(ride) {
                checkNetwork()
                val progress = { done: Long -> rides.route = RouteState.Downloading(data, done) }
                when (data) {
                    is RegionDownload -> {
                        withContext(Dispatchers.IO) { router.download(data.region, isActive = { isActive }, progress) }
                        compute(ride, RoutingData.Region(data.region))
                    }
                    is CellsDownload -> {
                        withContext(Dispatchers.IO) { router.download(data, isActive = { isActive }, progress) }
                        compute(ride, RoutingData.Cells(data.cells))
                    }
                }
            }
        }
    }

    /**
     * New preferences (#4): they stay for the next rides and the ride on screen is routed again.
     * During a download nothing is interrupted: the routing after the download already uses them.
     */
    private fun changeOptions(options: RouteOptions) {
        rides.options = options
        getSharedPreferences("route", MODE_PRIVATE).edit()
            .putString("curves", options.curves.name)
            .putBoolean("avoidHighways", options.avoidHighways)
            .putBoolean("avoidTolls", options.avoidTolls)
            .putBoolean("avoidFerries", options.avoidFerries)
            .apply()
        rides.ride?.let { reroute(it.unrouted()) }
    }

    /** Ride changed (preferences or stops): route it again, if possible already. */
    private fun reroute(fresh: Ride) {
        rides.ride = fresh
        if (fresh.routable && rides.route !is RouteState.Downloading && rides.route !is RouteState.NeedsData) planRoute(fresh)
    }

    /**
     * Stops edited on the map (#2): route the new ride right away. A pending data offer is
     * planned again, since the new stops may need other cells.
     */
    private fun editStops(fresh: Ride) {
        rides.ride = fresh
        if (fresh.routable && rides.route !is RouteState.Downloading) planRoute(fresh)
    }

    private fun loadOptions(): RouteOptions {
        val p = getSharedPreferences("route", MODE_PRIVATE)
        return RouteOptions(
            curves = Curves.entries.firstOrNull { it.name == p.getString("curves", null) } ?: Curves.NORMAL,
            avoidHighways = p.getBoolean("avoidHighways", false),
            avoidTolls = p.getBoolean("avoidTolls", false),
            avoidFerries = p.getBoolean("avoidFerries", false),
        )
    }

    private fun cancelRoute() {
        routeJob?.cancel()
        val state = rides.route
        rides.route = if (state is RouteState.Downloading) RouteState.NeedsData(state.download) else RouteState.Idle
    }

    private suspend fun compute(ride: Ride, data: RoutingData) {
        rides.route = RouteState.Computing
        // Under 150 cc the bike forbids motorways, whatever the preferences say.
        val options = rides.options.forBike(store.data.defaultBike)
        val (result, baseline) = withContext(Dispatchers.Default) {
            val main = router.route(ride, data, options)
            // With curves, also compute the normal route, to tell how much longer the ride gets.
            // If the comparison fails it does not matter: the ride stays valid.
            val base = if (options.curves == Curves.NORMAL) null
            else runCatching { router.route(ride, data, options.copy(curves = Curves.NORMAL), instructions = false) }.getOrNull()
            main to base
        }
        if (rides.ride === ride) {
            rides.ride = ride.routed(result, data, options, baseline)
            rides.route = RouteState.Idle
        }
    }

    /** Routing errors in the ride card, only if the same ride is still on screen. */

    private suspend fun routeStep(ride: Ride, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: RouteException) {
            if (rides.ride === ride) rides.route = RouteState.Failed(e.error, e.detail)
        } catch (e: Exception) {
            if (rides.ride === ride) rides.route = RouteState.Failed(RouteError.ENGINE, e.javaClass.simpleName)
        }
    }

    override fun onDestroy() {
        navigation.close()
        super.onDestroy()
    }

    /**
     * Off-route during navigation (#5): a new route from where the rider is to the stops still
     * ahead, with the same options, only on the road data already on the phone (no download while
     * riding). Null if there is no data here or Valhalla finds no road.
     */
    private suspend fun rerouteFromHere(ride: Ride, from: LatLon, heading: Double?, stops: List<Stop>, options: RouteOptions): Ride? =
        withContext(Dispatchers.IO) {
            val all = listOf(Stop(null, from)) + stops
            val points = all.map { it.point }
            val cells = router.localCells()
            val data = if (cells != null && CellsDownload.coveredLocally(cells, router.installedPacks(), points)) RoutingData.Cells(cells)
            else RoutingRegion.bestFor(router.installed(), points)?.let { RoutingData.Region(it) } ?: return@withContext null
            val fresh = ride.copy(track = emptyList(), stops = all, route = null, maneuvers = emptyList())
            runCatching { fresh.routed(router.route(fresh, data, options, startHeading = heading), data, options) }.getOrNull()
        }
}

private const val STATE_RIDE = "ride"
private const val STATE_PANEL = "panel"
