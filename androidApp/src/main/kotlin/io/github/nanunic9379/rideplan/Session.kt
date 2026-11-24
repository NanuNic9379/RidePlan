package io.github.nanunic9379.rideplan

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import io.github.nanunic9379.rideplan.profile.ProfileStore
import io.github.nanunic9379.rideplan.ride.RideScreenState
import kotlinx.coroutines.Job

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
