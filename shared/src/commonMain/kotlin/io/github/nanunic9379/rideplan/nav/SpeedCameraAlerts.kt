package io.github.nanunic9379.rideplan.nav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.nanunic9379.rideplan.personal.distanceText
import io.github.nanunic9379.rideplan.resources.Res
import io.github.nanunic9379.rideplan.resources.nav_camera
import io.github.nanunic9379.rideplan.resources.nav_say_camera
import io.github.nanunic9379.rideplan.resources.nav_say_camera_limit
import io.github.nanunic9379.rideplan.ride.pointsJson
import io.github.nanunic9379.rideplan.ride.rememberNotesOnRide
import io.github.nanunic9379.rideplan.route.CameraOnRide
import io.github.nanunic9379.rideplan.route.CameraWatch
import io.github.nanunic9379.rideplan.route.RoadNotes
import io.github.nanunic9379.rideplan.ui.AppIcons
import org.jetbrains.compose.resources.stringResource
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource

/**
 * Fixed speed cameras on the navigated line (#13), only where warnings are allowed and when the
 * rider left them on (the loader already leaves them out otherwise). Positions measured without
 * scaling, like [RouteLine]: they compare with the navigation's metres along the line.
 */
@Composable
internal fun rememberNavCameras(session: NavigationSession, load: (suspend () -> RoadNotes?)?): List<CameraOnRide> =
    rememberNotesOnRide(session.ride.line, totalKm = null, load)?.cameras.orEmpty()

/** The cameras as dots on the navigation map. */
@Composable
internal fun SpeedCameraLayer(cameras: List<CameraOnRide>) {
    val json = remember(cameras) { pointsJson(cameras.map { it.camera.point }) }
    val source = rememberGeoJsonSource(GeoJsonData.JsonString(json))
    val scheme = MaterialTheme.colorScheme
    CircleLayer(
        id = "nav-cameras", source = source, color = const(scheme.error), radius = const(7.dp),
        strokeColor = const(scheme.surface), strokeWidth = const(3.dp),
    )
}

/**
 * "Speed camera in 300 m" under the turn banner while one is within 500 m ahead; the voice says
 * it once per camera, unless the rider muted it.
 */
@Composable
internal fun SpeedCameraBanner(session: NavigationSession, cameras: List<CameraOnRide>, platform: NavigationPlatform) {
    if (cameras.isEmpty()) return
    val watch = remember(cameras) { CameraWatch(cameras) }
    var ahead by remember(watch) { mutableStateOf<CameraWatch.Ahead?>(null) }
    val u = session.update
    LaunchedEffect(watch, u) {
        ahead = if (u == null || u.offRoute || session.rerouting) null else watch.update(u.alongM)
    }
    val a = ahead ?: return
    val limit = a.camera.camera.maxspeedKmh
    val say = if (limit != null) stringResource(Res.string.nav_say_camera_limit, limit) else stringResource(Res.string.nav_say_camera)
    LaunchedEffect(a.camera, a.announce) {
        if (a.announce && !session.muted) platform.speak(say)
    }
    val scheme = MaterialTheme.colorScheme
    Surface(color = scheme.errorContainer, contentColor = scheme.onErrorContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.padding(top = 6.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(AppIcons.Warning, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(stringResource(Res.string.nav_camera, distanceText(a.distanceM / 1000)), style = MaterialTheme.typography.titleSmall)
            if (limit != null) Text("$limit", style = MaterialTheme.typography.titleMedium)
        }
    }
}
