package io.github.nanunic9379.rideplan

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import io.github.nanunic9379.rideplan.profile.BackupActions
import io.github.nanunic9379.rideplan.profile.BackupNotice
import io.github.nanunic9379.rideplan.profile.ProfileStore
import io.github.nanunic9379.rideplan.ride.GpxExport
import io.github.nanunic9379.rideplan.ride.ImportActions
import io.github.nanunic9379.rideplan.ride.Page
import io.github.nanunic9379.rideplan.ride.Ride
import io.github.nanunic9379.rideplan.ride.RideScreenState
import io.github.nanunic9379.rideplan.route.RouteActions
import io.github.nanunic9379.rideplan.route.RouteError
import io.github.nanunic9379.rideplan.route.RouteException
import io.github.nanunic9379.rideplan.route.Curves
import io.github.nanunic9379.rideplan.route.RouteOptions
import io.github.nanunic9379.rideplan.route.routable
import io.github.nanunic9379.rideplan.route.unrouted
import io.github.nanunic9379.rideplan.route.RouteState
import io.github.nanunic9379.rideplan.route.RoutingRegion
import io.github.nanunic9379.rideplan.route.routed
import io.github.nanunic9379.rideplan.route.toggledStop
import io.github.nanunic9379.rideplan.update.UpdateOverlay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    private val rides = RideScreenState()
    private val router by lazy { RouteService(this) }
    private var routeJob: Job? = null
    private lateinit var store: ProfileStore

    /** Copia di sicurezza (#43): l'utente sceglie dove salvarla (Download, Drive, ...). */
    private val saveBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) backupIo {
            contentResolver.openOutputStream(uri, "wt")!!.use { it.write(store.backupText().toByteArray()) }
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
        enableEdgeToEdge()
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
        )
        rides.options = loadOptions()
        store = profileStore()
        val backupActions = BackupActions(
            save = { saveBackup.launch("rideplan-backup.json") },
            restore = { restoreBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
        )
        onBackPressedDispatcher.addCallback(this, backToHome)
        setContent { App(rides = rides, store = store, importActions = actions, routeActions = routeActions, backupActions = backupActions, overlay = { UpdateOverlay() }) }
        if (savedInstanceState == null) handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** "Condividi" da Google Maps (testo con il link) o apertura di un file GPX/KML/KMZ. */
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
     * Indietro dalla mappa o dal profilo torna alla schermata iniziale (#41);
     * dalla schermata iniziale fa quello che fa sempre Android.
     */
    private val backToHome = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (rides.onMap || rides.page != Page.HOME) {
                cancelRoute()
                rides.goHome()
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        }
    }

    /**
     * Profilo e giri salvati in un file dell'app (#41): restano sul telefono.
     * Si scrive in un file temporaneo e poi lo si rinomina: un'interruzione non rovina niente.
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

    /** Un giro da "I tuoi giri": subito sulla mappa, il percorso si ricalcola se serve. */
    private fun openRide(ride: Ride) {
        routeJob?.cancel()
        rides.show(Result.success(ride))
        if (ride.needsRoute) planRoute(ride)
    }

    @Suppress("DEPRECATION")
    private fun streamExtra(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else intent.getParcelableExtra(Intent.EXTRA_STREAM)

    /**
     * GPX nella cache dell'app, passato con il menu "Condividi" di Android (Garmin, WhatsApp,
     * Drive...). Solo il file del giro è leggibile da fuori, e solo dall'app scelta.
     */
    /** Il link del giro (#6) a una chat o alla mail. */
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

    private fun load(block: () -> Ride) {
        rides.error = null
        rides.busy = true
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching(block) }
            routeJob?.cancel()
            rides.show(result)
            result.getOrNull()?.takeIf { it.needsRoute }?.let(::planRoute)
        }
    }

    /**
     * Percorso su strada per un giro fatto solo di tappe (#3): con i dati già scaricati parte
     * subito, altrimenti propone di scaricare la zona giusta.
     */
    private fun planRoute(ride: Ride) {
        routeJob?.cancel()
        routeJob = lifecycleScope.launch {
            rides.route = RouteState.Checking
            routeStep(ride) {
                val points = ride.stops.map { it.point }
                val local = withContext(Dispatchers.IO) { RoutingRegion.bestFor(router.installed(), points) }
                if (local != null) {
                    compute(ride, local)
                } else {
                    val region = withContext(Dispatchers.IO) { RoutingRegion.bestFor(router.published(), points) }
                    rides.route = if (region == null) RouteState.NotCovered else RouteState.NeedsData(region)
                }
            }
        }
    }

    private fun download(region: RoutingRegion) {
        val ride = rides.ride ?: return
        routeJob?.cancel()
        routeJob = lifecycleScope.launch {
            rides.route = RouteState.Downloading(region, 0)
            routeStep(ride) {
                withContext(Dispatchers.IO) {
                    router.download(region, isActive = { isActive }) { done -> rides.route = RouteState.Downloading(region, done) }
                }
                compute(ride, region)
            }
        }
    }

    /**
     * Nuove preferenze (#4): restano per i prossimi giri e il giro sullo schermo si ricalcola.
     * Durante un download non si interrompe niente: il calcolo dopo il download le usa già.
     */
    private fun changeOptions(options: RouteOptions) {
        rides.options = options
        getSharedPreferences("route", MODE_PRIVATE).edit()
            .putString("curves", options.curves.name)
            .putBoolean("avoidHighways", options.avoidHighways)
            .putBoolean("avoidTolls", options.avoidTolls)
            .apply()
        rides.ride?.let { reroute(it.unrouted()) }
    }

    /** Giro cambiato (preferenze o tappe): si ricalcola, se si può già. */
    private fun reroute(fresh: Ride) {
        rides.ride = fresh
        if (fresh.routable && rides.route !is RouteState.Downloading && rides.route !is RouteState.NeedsData) planRoute(fresh)
    }

    private fun loadOptions(): RouteOptions {
        val p = getSharedPreferences("route", MODE_PRIVATE)
        return RouteOptions(
            curves = Curves.entries.firstOrNull { it.name == p.getString("curves", null) } ?: Curves.NORMAL,
            avoidHighways = p.getBoolean("avoidHighways", false),
            avoidTolls = p.getBoolean("avoidTolls", false),
        )
    }

    private fun cancelRoute() {
        routeJob?.cancel()
        val state = rides.route
        rides.route = if (state is RouteState.Downloading) RouteState.NeedsData(state.region) else RouteState.Idle
    }

    private suspend fun compute(ride: Ride, region: RoutingRegion) {
        rides.route = RouteState.Computing
        val options = rides.options
        val (result, baseline) = withContext(Dispatchers.Default) {
            val main = router.route(ride, region, options)
            // Con le curve calcolo anche il percorso normale, per dire quanto si allunga il giro.
            // Se il confronto fallisce non importa: il giro resta valido.
            val base = if (options.curves == Curves.NORMAL) null
            else runCatching { router.route(ride, region, options.copy(curves = Curves.NORMAL)) }.getOrNull()
            main to base
        }
        if (rides.ride === ride) {
            rides.ride = ride.routed(result, region, options, baseline)
            rides.route = RouteState.Idle
        }
    }

    /** Errori del percorso nella scheda del giro, solo se si guarda ancora lo stesso giro. */
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
}
