package io.github.nanunic9379.rideplan.nav

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.text.TextStyle
import io.github.nanunic9379.rideplan.resources.nav_overview
import io.github.nanunic9379.rideplan.resources.nav_notice_channel
import io.github.nanunic9379.rideplan.resources.nav_notice_stop
import io.github.nanunic9379.rideplan.resources.nav_paused_text
import io.github.nanunic9379.rideplan.resources.nav_resume
import io.github.nanunic9379.rideplan.resources.nav_recenter
import io.github.nanunic9379.rideplan.ui.Figure
import io.github.nanunic9379.rideplan.ui.Motion
import io.github.nanunic9379.rideplan.ui.Radius
import io.github.nanunic9379.rideplan.ui.TabularFigures
import kotlinx.coroutines.launch
import org.maplibre.compose.camera.CameraFit
import org.maplibre.compose.camera.CameraMoveReason
import org.maplibre.spatialk.geojson.BoundingBox
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nanunic9379.rideplan.MapDefaults
import io.github.nanunic9379.rideplan.resources.Res
import io.github.nanunic9379.rideplan.resources.nav_arrived_text
import io.github.nanunic9379.rideplan.resources.nav_arrived_title
import io.github.nanunic9379.rideplan.resources.nav_exit
import io.github.nanunic9379.rideplan.resources.nav_follow_track
import io.github.nanunic9379.rideplan.resources.nav_mark_done
import io.github.nanunic9379.rideplan.resources.nav_mute
import io.github.nanunic9379.rideplan.resources.nav_not_now
import io.github.nanunic9379.rideplan.resources.nav_off_track
import io.github.nanunic9379.rideplan.resources.nav_permission_allow
import io.github.nanunic9379.rideplan.resources.nav_permission_text
import io.github.nanunic9379.rideplan.resources.nav_reroute_failed
import io.github.nanunic9379.rideplan.resources.nav_rerouting
import io.github.nanunic9379.rideplan.resources.nav_say_back
import io.github.nanunic9379.rideplan.resources.nav_say_finish
import io.github.nanunic9379.rideplan.resources.nav_say_follow_track
import io.github.nanunic9379.rideplan.resources.nav_say_in
import io.github.nanunic9379.rideplan.resources.nav_say_off_route
import io.github.nanunic9379.rideplan.resources.nav_say_off_track
import io.github.nanunic9379.rideplan.resources.nav_say_stop
import io.github.nanunic9379.rideplan.resources.nav_say_stop_unnamed
import io.github.nanunic9379.rideplan.resources.nav_stop_reached
import io.github.nanunic9379.rideplan.resources.nav_then
import io.github.nanunic9379.rideplan.resources.nav_to_finish
import io.github.nanunic9379.rideplan.resources.nav_unmute
import io.github.nanunic9379.rideplan.resources.nav_say_continue
import io.github.nanunic9379.rideplan.resources.nav_speed_kmh
import io.github.nanunic9379.rideplan.resources.nav_speed_limit
import io.github.nanunic9379.rideplan.resources.nav_speed_mph
import io.github.nanunic9379.rideplan.resources.nav_speed_over
import io.github.nanunic9379.rideplan.personal.LocalUnits
import io.github.nanunic9379.rideplan.personal.decimalComma
import io.github.nanunic9379.rideplan.profile.Measures
import io.github.nanunic9379.rideplan.profile.Units
import io.github.nanunic9379.rideplan.profile.of
import io.github.nanunic9379.rideplan.resources.nav_battery_help
import io.github.nanunic9379.rideplan.resources.nav_battery_settings
import io.github.nanunic9379.rideplan.resources.nav_battery_text
import io.github.nanunic9379.rideplan.resources.nav_battery_title
import io.github.nanunic9379.rideplan.resources.nav_waiting_gps
import io.github.nanunic9379.rideplan.resources.nav_break_reminder_title
import io.github.nanunic9379.rideplan.resources.nav_break_reminder_text
import io.github.nanunic9379.rideplan.resources.nav_break_reminder_dismiss
import io.github.nanunic9379.rideplan.resources.fuel_station
import io.github.nanunic9379.rideplan.resources.nav_break_reminder_no_place
import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.Ride
import io.github.nanunic9379.rideplan.ride.lineJson
import io.github.nanunic9379.rideplan.ride.pointsJson
import io.github.nanunic9379.rideplan.route.TurnSign
import io.github.nanunic9379.rideplan.route.durationText
import io.github.nanunic9379.rideplan.route.FuelAlong
import io.github.nanunic9379.rideplan.route.FuelStation
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.maplibre.compose.camera.CameraAnimation
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.DpPadding
import org.maplibre.spatialk.geojson.Position
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/** Navigation colours: kept here so the redesign can replace them in one place. */
private object NavColors {
    val Route = Color(0xFFFF8A1F)
    val RouteCasing = Color(0xFF7A3300)
    val Me = Color(0xFF1A73E8)
    val MeStroke = Color.White
    val Stop = Color.White
    val StopStroke = Color(0xFF1C1B26)
    /** The speed limit sign (#31): red ring on white, all red when riding faster. */
    val LimitRing = Color(0xFFD32F2F)
    val LimitFace = Color.White
    val LimitText = Color(0xFF1C1B26)
    val LimitOverText = Color.White
}

/** Big touch targets: usable with gloves. */
private val ButtonHeight = 56.dp

/**
 * Turn-by-turn navigation of [ride] (#5): the map follows you and turns with the road, the
 * banner shows the next maneuver, the voice announces it. Location is used only while
 * navigating, also with the screen off or another app open (#65), with an ongoing notification.
 * Exit and Back ask first (#66): [exitAsked] shows the question, [onAskExit] changes it.
 * [onMarkDone]: marks the ride as done (null if it is not saved).
 */
@Composable
fun NavigationScreen(
    ride: Ride,
    platform: NavigationPlatform,
    onExit: () -> Unit,
    exitAsked: Boolean,
    onAskExit: (Boolean) -> Unit,
    onMarkDone: (() -> Unit)? = null,
    /** Speed cameras (#13); null = none known. */
    roadNotes: (suspend () -> io.github.nanunic9379.rideplan.route.RoadNotes?)? = null,
    breakReminderMinutes: Int = 120,
    fuelStations: (suspend (List<LatLon>) -> List<FuelStation>?)? = null,
) {
    val scope = rememberCoroutineScope()
    val units = LocalUnits.current
    val comma = decimalComma()
    val session = remember(ride) {
        NavigationSession(
            ride, platform, scope,
            say = { speech(it, units) },
            noticeTexts = { noticeTexts() },
            distance = { Measures.shortDistance(it, units, comma) },
            breakReminderMinutes = breakReminderMinutes,
        )
    }
    val navRide = session.ride
    var stations by remember(navRide) { mutableStateOf<List<FuelStation>?>(null) }
    LaunchedEffect(navRide, fuelStations) { stations = runCatching { fuelStations?.invoke(navRide.line) }.getOrNull() }
    val fuelStops = remember(stations, navRide) {
        stations?.let { FuelAlong.report(navRide.line, it, navRide.lengthKm).stations }
    }
    val nextFuel = fuelStops?.firstOrNull { it.km * 1000 > (session.update?.alongM ?: 0.0) + 100 }
    LaunchedEffect(session.breakReminderDue) {
        if (session.breakReminderDue) session.speakBreakReminder(getString(Res.string.nav_break_reminder_text))
    }
    DisposableEffect(session) {
        onAskExit(false)
        session.start()
        onDispose { session.stop() }
    }
    val update = session.update
    val line = session.ride.line
    val cameras = rememberNavCameras(session, roadNotes)
    val state = rememberMapState(
        baseStyle = BaseStyle.Uri(MapDefaults.STYLE),
        initialCameraPosition = CameraPosition(
            center = Position(latitude = line.first().lat, longitude = line.first().lon),
            zoom = 15.0,
            pitch = 45.0,
        ),
    ) {
        NavLayers(session.ride, session.update?.position)
        SpeedCameraLayer(cameras)
    }
    // Follows the rider until the map is moved by hand or "Route" is tapped (#37).
    var follow by remember { mutableStateOf(true) }
    LaunchedEffect(state) {
        snapshotFlow { state.cameraMoveReason }.collect { if (it == CameraMoveReason.Gesture) follow = false }
    }
    LaunchedEffect(update, follow) {
        if (!follow) return@LaunchedEffect
        val u = update ?: return@LaunchedEffect
        val kmh = (u.speedMps ?: 0.0) * 3.6
        val zoom = when {
            kmh < 40 -> 17.0
            kmh < 80 -> 16.0
            else -> 15.0
        }
        state.animateCamera(
            CameraUpdate(
                center = Position(latitude = u.position.lat, longitude = u.position.lon),
                zoom = zoom,
                bearing = u.bearing ?: state.cameraPosition.bearing,
                pitch = 45.0,
                // Position in the lower part of the screen: more road ahead is visible.
                padding = DpPadding(top = 260.dp),
            ),
            CameraAnimation.Ease(CameraAnimation.Ease.Standard) { duration = 900.milliseconds },
        )
    }

    Box(Modifier.fillMaxSize()) {
        MaplibreMap(modifier = Modifier.fillMaxSize(), state = state)
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(8.dp)) {
            // Landscape: banner and bar on the left, the road stays visible on the right.
            val panel = if (maxWidth > maxHeight) Modifier.width(380.dp) else Modifier.fillMaxWidth()
            Column(Modifier.align(Alignment.TopStart).then(panel)) {
                Banner(session, onExit)
                if (session.breakReminderDue) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(stringResource(Res.string.nav_break_reminder_title), style = MaterialTheme.typography.titleMedium)
                            val placeName = nextFuel?.station?.name ?: nextFuel?.station?.brand
                            Text(placeName ?: if (nextFuel != null) stringResource(Res.string.fuel_station) else stringResource(Res.string.nav_break_reminder_no_place), style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = session::dismissBreakReminder, Modifier.align(Alignment.End).heightIn(min = ButtonHeight)) {
                                Text(stringResource(Res.string.nav_break_reminder_dismiss))
                            }
                        }
                    }
                }
                SpeedCameraBanner(session, cameras, platform)
            }
            Column(
                Modifier.align(Alignment.BottomStart).then(panel),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End,
            ) {
                if (follow) {
                    FilledTonalButton(
                        onClick = {
                            follow = false
                            scope.launch {
                                val (sw, ne) = session.ride.bounds()
                                state.animateCameraToBounds(
                                    BoundingBox(sw.lon, sw.lat, ne.lon, ne.lat),
                                    CameraFit(bearing = 0.0, pitch = 0.0, fitPadding = DpPadding(top = 200.dp, bottom = 240.dp, left = 32.dp, right = 32.dp)),
                                )
                            }
                        },
                        modifier = Modifier.heightIn(min = ButtonHeight),
                        shape = Radius.Control,
                    ) { Text(stringResource(Res.string.nav_overview), style = MaterialTheme.typography.titleMedium) }
                } else {
                    Button(onClick = { follow = true }, modifier = Modifier.heightIn(min = ButtonHeight), shape = Radius.Control) {
                        Text(stringResource(Res.string.nav_recenter), style = MaterialTheme.typography.titleMedium)
                    }
                }
                BottomBar(session, { onAskExit(true) }, Modifier.fillMaxWidth())
            }
        }
    }

    if (exitAsked) EndNavigationDialog(onEnd = { onAskExit(false); onExit() }, onKeep = { onAskExit(false) })

    if (session.batteryPrompt) {
        AlertDialog(
            onDismissRequest = { session.continueAfterBatteryPrompt(openSettings = false) },
            title = { Text(stringResource(Res.string.nav_battery_title)) },
            text = {
                Column {
                    Text(stringResource(Res.string.nav_battery_text))
                    TextButton(onClick = session::openBatteryHelp, Modifier.heightIn(min = ButtonHeight)) {
                        Text(stringResource(Res.string.nav_battery_help))
                    }
                }
            },
            confirmButton = {
                Button(onClick = { session.continueAfterBatteryPrompt(openSettings = true) }, Modifier.heightIn(min = ButtonHeight), shape = Radius.Control) {
                    Text(stringResource(Res.string.nav_battery_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { session.continueAfterBatteryPrompt(openSettings = false) }, Modifier.heightIn(min = ButtonHeight)) {
                    Text(stringResource(Res.string.nav_not_now))
                }
            },
        )
    }

    if (session.arrived) {
        AlertDialog(
            onDismissRequest = session::dismissArrival,
            title = { Text(stringResource(Res.string.nav_arrived_title)) },
            text = if (onMarkDone != null) ({ Text(stringResource(Res.string.nav_arrived_text)) }) else null,
            confirmButton = {
                if (onMarkDone != null) {
                    Button(onClick = { onMarkDone(); session.dismissArrival(); onExit() }, Modifier.heightIn(min = ButtonHeight), shape = Radius.Control) {
                        Text(stringResource(Res.string.nav_mark_done))
                    }
                } else {
                    Button(onClick = { session.dismissArrival(); onExit() }, Modifier.heightIn(min = ButtonHeight), shape = Radius.Control) {
                        Text(stringResource(Res.string.nav_exit))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = session::dismissArrival, Modifier.heightIn(min = ButtonHeight)) {
                    Text(stringResource(Res.string.nav_not_now))
                }
            },
        )
    }
}

/** Road, stops and your position. */
@Composable
private fun NavLayers(ride: Ride, me: LatLon?) {
    val line = rememberGeoJsonSource(GeoJsonData.JsonString(lineJson(ride.line)))
    val stops = rememberGeoJsonSource(GeoJsonData.JsonString(pointsJson(ride.stops.drop(1).map { it.point })))
    val position = rememberGeoJsonSource(GeoJsonData.JsonString(pointsJson(listOfNotNull(me))))
    LineLayer(id = "nav-casing", source = line, color = const(NavColors.RouteCasing), width = const(10.dp))
    LineLayer(id = "nav-line", source = line, color = const(NavColors.Route), width = const(6.dp))
    CircleLayer(
        id = "nav-stops", source = stops, color = const(NavColors.Stop), radius = const(7.dp),
        strokeColor = const(NavColors.StopStroke), strokeWidth = const(3.dp),
    )
    CircleLayer(
        id = "nav-me", source = position, color = const(NavColors.Me), radius = const(10.dp),
        strokeColor = const(NavColors.MeStroke), strokeWidth = const(3.dp),
    )
}

/** Next maneuver, or what the navigation is doing (waiting for GPS, recalculating, off track). */
@Composable
private fun Banner(session: NavigationSession, onExit: () -> Unit) {
    val u = session.update
    val route = session.navigator.route
    val scheme = MaterialTheme.colorScheme
    when {
        session.permission == false -> Message(stringResource(Res.string.nav_permission_text), scheme.errorContainer, scheme.onErrorContainer) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = session::start, Modifier.heightIn(min = ButtonHeight), shape = Radius.Control) { Text(stringResource(Res.string.nav_permission_allow)) }
                OutlinedButton(onClick = onExit, Modifier.heightIn(min = ButtonHeight), shape = Radius.Control) { Text(stringResource(Res.string.nav_exit)) }
            }
        }
        session.paused -> Message(stringResource(Res.string.nav_paused_text), scheme.surface, scheme.onSurface) {
            Button(onClick = session::start, Modifier.heightIn(min = ButtonHeight), shape = Radius.Control) { Text(stringResource(Res.string.nav_resume)) }
        }
        u == null -> Message(stringResource(Res.string.nav_waiting_gps), scheme.surface, scheme.onSurface)
        session.rerouting -> Message(stringResource(Res.string.nav_rerouting), scheme.tertiaryContainer, scheme.onTertiaryContainer)
        u.offRoute && session.rerouteFailed -> Message(stringResource(Res.string.nav_reroute_failed), scheme.errorContainer, scheme.onErrorContainer)
        u.offRoute && !route.canReroute ->
            Message(stringResource(Res.string.nav_off_track, distanceText(u.offsetM)), scheme.errorContainer, scheme.onErrorContainer)
        u.finished -> TurnBanner(TurnSign.ARRIVE, distanceText(u.remainingM), stringResource(Res.string.nav_arrived_title), null)
        u.next != null && u.distanceToNextM != null -> TurnBanner(
            sign = u.next.maneuver.sign,
            distance = distanceText(u.distanceToNextM),
            text = u.next.maneuver.instruction,
            then = u.then,
        )
        else -> TurnBanner(TurnSign.STRAIGHT, distanceText(u.remainingM), stringResource(Res.string.nav_follow_track), null)
    }
    val arrival = u?.lastArrival
    if (u != null && arrival != null && !arrival.finish && u.alongM - arrival.along < 300) {
        Surface(color = scheme.secondaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.padding(top = 6.dp)) {
            Text(
                stringResource(Res.string.nav_stop_reached, arrival.stop.name ?: "·"),
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

@Composable
private fun TurnBanner(sign: TurnSign, distance: String, text: String, then: PlacedManeuver?) {
    val scheme = MaterialTheme.colorScheme
    // Dark on light, light on dark: the most contrast in direct sunlight. Orange stays the route's.
    Surface(color = scheme.inverseSurface, contentColor = scheme.inverseOnSurface, shape = MaterialTheme.shapes.large, shadowElevation = 4.dp) {
        Column {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TurnArrow(sign, scheme.inverseOnSurface, Modifier.size(76.dp))
                Column(Modifier.padding(start = 12.dp)) {
                    // The distance to the turn rolls down as it changes; the speed below does not,
                    // as a number that moves every second would be harder to read at a glance.
                    Figure(distance, TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Medium, lineHeight = 44.sp).merge(TabularFigures))
                    Text(text, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (then != null) {
                Surface(color = scheme.inverseOnSurface.copy(alpha = 0.12f), contentColor = scheme.inverseOnSurface) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(Res.string.nav_then), style = MaterialTheme.typography.titleSmall)
                        TurnArrow(then.maneuver.sign, scheme.inverseOnSurface, Modifier.padding(start = 8.dp).size(28.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String, color: Color, content: Color, actions: (@Composable () -> Unit)? = null) {
    Surface(color = color, contentColor = content, shape = MaterialTheme.shapes.large, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text, style = MaterialTheme.typography.titleLarge)
            actions?.invoke()
        }
    }
}

/** Speed, what is left to the finish, mute and exit. */
@Composable
private fun BottomBar(session: NavigationSession, onExit: () -> Unit, modifier: Modifier) {
    val u = session.update
    Surface(modifier, shape = MaterialTheme.shapes.large, shadowElevation = 4.dp) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (u != null) {
                val units = LocalUnits.current
                val shown = speedShown(u.speedMps, u.speedLimitKmh, units)
                val unit = stringResource(if (units == Units.MILES) Res.string.nav_speed_mph else Res.string.nav_speed_kmh)
                val speedColor by animateColorAsState(
                    if (shown.over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    Motion.feedback(),
                    label = "speed",
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Figure(
                            shown.speed?.toString() ?: "–",
                            style = TabularFigures.merge(TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Medium, lineHeight = 36.sp)),
                            color = speedColor,
                        )
                        Text(unit, style = MaterialTheme.typography.labelMedium)
                    }
                    shown.limit?.let { limit ->
                        Spacer(Modifier.width(12.dp))
                        LimitSign(limit, shown.over, unit)
                    }
                    Spacer(Modifier.width(20.dp))
                    Column(Modifier.weight(1f)) {
                        val parts = listOfNotNull(distanceText(u.remainingM), u.remainingSeconds?.let { durationText(it) })
                        Text(parts.joinToString(" · "), style = MaterialTheme.typography.titleLarge.merge(TabularFigures))
                        Text(stringResource(Res.string.nav_to_finish), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = session::toggleMute, Modifier.weight(1f).heightIn(min = ButtonHeight), shape = Radius.Control) {
                    Text(stringResource(if (session.muted) Res.string.nav_unmute else Res.string.nav_mute), style = MaterialTheme.typography.titleMedium)
                }
                Button(
                    onClick = onExit,
                    modifier = Modifier.weight(1f).heightIn(min = ButtonHeight),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                    shape = Radius.Control,
                ) {
                    Text(stringResource(Res.string.nav_exit), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** Round speed limit sign (#31); filled red when riding faster than the limit. No sound. */
@Composable
private fun LimitSign(limit: Int, over: Boolean, unit: String) {
    val description = stringResource(Res.string.nav_speed_limit, "$limit $unit") +
        if (over) ", " + stringResource(Res.string.nav_speed_over) else ""
    Box(
        Modifier.size(54.dp)
            .background(if (over) NavColors.LimitRing else NavColors.LimitFace, CircleShape)
            .border(6.dp, NavColors.LimitRing, CircleShape)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$limit",
            color = if (over) NavColors.LimitOverText else NavColors.LimitText,
            fontSize = if (limit >= 100) 18.sp else 22.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/** Current speed and limit as shown, in the unit of the settings (#31). */
internal data class SpeedShown(val speed: Int?, val limit: Int?, val over: Boolean)

/**
 * [speedMps] from the GPS, [limitKmh] of the road (null = unknown: no sign). Over the limit when
 * the speed on screen is higher than the number on the sign, so what is seen always agrees.
 */
internal fun speedShown(speedMps: Double?, limitKmh: Int?, units: Units): SpeedShown {
    val speed = speedMps?.takeIf { it >= 0 }?.let { Measures.speed(it * 3.6, units) }
    val limit = limitKmh?.takeIf { it > 0 }?.let { Measures.speed(it.toDouble(), units) }
    return SpeedShown(speed, limit, over = speed != null && limit != null && speed > limit)
}

/** Distance on screen, [m] metres, in the unit of the settings (#15): see [Measures.shortDistance]. */
@Composable
internal fun distanceText(m: Double): String = Measures.shortDistance(m, LocalUnits.current, decimalComma())

/** Distance for the voice, [m] metres, rounded the way it is said: see [Measures.spoken]. */
internal fun spokenText(m: Double, units: Units): String = Measures.spoken(m, units, decimalComma())

/** Words of the background notification (#65), in the app language. */
internal suspend fun noticeTexts() = NoticeTexts(
    waiting = getString(Res.string.nav_waiting_gps),
    rerouting = getString(Res.string.nav_rerouting),
    followTrack = getString(Res.string.nav_follow_track),
    arrived = getString(Res.string.nav_arrived_title),
    toFinish = getString(Res.string.nav_to_finish),
    stop = getString(Res.string.nav_notice_stop),
    channel = getString(Res.string.nav_notice_channel),
)

/** What the voice says for [a], in the app language and [units]. */
internal suspend fun speech(a: Announcement, units: Units = Units.KM): String? = when (a) {
    is Announcement.Start -> a.maneuver?.let { it.verbalPre ?: it.instruction }
        ?: getString(Res.string.nav_say_follow_track, spokenText(a.remainingM, units))
    is Announcement.Turn -> {
        val m = a.maneuver
        if (a.stage == Stage.NOW) m.verbalPre ?: m.instruction
        else getString(Res.string.nav_say_in, spokenText(a.distanceM.toDouble(), units), m.verbalAlert ?: m.verbalPre ?: m.instruction)
    }
    // Valhalla says "Continue for 12 kilometers": in miles the app says it itself (#15).
    is Announcement.Continue -> a.distanceM?.takeIf { units == Units.MILES }
        ?.let { getString(Res.string.nav_say_continue, spokenText(it, units)) } ?: a.text
    is Announcement.Arrived -> when {
        a.stop.finish -> getString(Res.string.nav_say_finish)
        a.stop.stop.name != null -> getString(Res.string.nav_say_stop, a.stop.stop.name)
        else -> getString(Res.string.nav_say_stop_unnamed)
    }
    is Announcement.OffRoute -> getString(if (a.canReroute) Res.string.nav_say_off_route else Res.string.nav_say_off_track)
    Announcement.BackOnRoute -> getString(Res.string.nav_say_back)
}?.takeIf { it.isNotBlank() }
