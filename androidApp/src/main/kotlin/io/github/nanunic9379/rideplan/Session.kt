package io.github.nanunic9379.rideplan

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.nanunic9379.rideplan.profile.ProfileStore
import io.github.nanunic9379.rideplan.ride.RideScreenState
import io.github.nanunic9379.rideplan.route.AreasDownload
import io.github.nanunic9379.rideplan.route.CellsDownload
import io.github.nanunic9379.rideplan.route.ReadyOfflineActions
import io.github.nanunic9379.rideplan.route.RegionDownload
import io.github.nanunic9379.rideplan.route.RoutingRegion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * What is on screen and the route work in progress. It outlives the activity, so a theme,
 * language or font change that recreates the activity does not close the open ride.
 */
class Session(app: Application) : AndroidViewModel(app) {
    val rides = RideScreenState()
    val router = RouteService(app)
    var routeJob: Job? = null
    /** Created by the first activity, then shared by the next ones. */
    var store: ProfileStore? = null
    /** Maps saved for rides, for use without signal (#1). */
    val rideMaps by lazy { RideMapDownloads(app, viewModelScope).also { it.actions.ready = readyOffline(it) } }

    /** "Ready without signal" (#67): the road data of the ride, then its map, behind one button. */
    private fun readyOffline(maps: RideMapDownloads) = ReadyOfflineActions(
        rideMap = maps.actions,
        scope = viewModelScope,
        hasRoadData = { points -> withContext(Dispatchers.IO) { router.localData(points, strict = false) != null } },
        planRoadData = { points ->
            withContext(Dispatchers.IO) {
                val published = router.publishedAreas(points)
                if (published != null) router.plan(published, points)
                else RoutingRegion.bestFor(router.published(), points)?.let(::RegionDownload)
            }
        },
        downloadRoadData = { data, progress ->
            withContext(Dispatchers.IO) {
                when (data) {
                    is RegionDownload -> router.download(data.region, isActive = { isActive }, progress)
                    is CellsDownload -> router.download(data, isActive = { isActive }, progress)
                    is AreasDownload -> router.download(data, isActive = { isActive }, progress)
                }
            }
        },
        mustAskForMobile = { maps.mustAskForMobile() },
    )
}

/** The last viewed map area (Explore): a small preference file, never in the backup. */
class MapViewPrefs(context: Context) : MapViewMemory {
    private val prefs = context.getSharedPreferences("map", Context.MODE_PRIVATE)

    override fun load(): MapView? {
        if (!prefs.contains("lat")) return null
        return MapView(
            prefs.getFloat("lat", 0f).toDouble(),
            prefs.getFloat("lon", 0f).toDouble(),
            prefs.getFloat("zoom", 0f).toDouble(),
        )
    }

    override fun save(view: MapView) {
        prefs.edit().putFloat("lat", view.lat.toFloat()).putFloat("lon", view.lon.toFloat()).putFloat("zoom", view.zoom.toFloat()).apply()
    }
}
