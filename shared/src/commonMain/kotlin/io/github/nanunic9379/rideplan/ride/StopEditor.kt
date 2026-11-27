package io.github.nanunic9379.rideplan.ride

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.nanunic9379.rideplan.resources.Res
import io.github.nanunic9379.rideplan.resources.stop_edit_done
import io.github.nanunic9379.rideplan.resources.stop_edit_earlier
import io.github.nanunic9379.rideplan.resources.stop_edit_later
import io.github.nanunic9379.rideplan.resources.stop_edit_remove
import io.github.nanunic9379.rideplan.resources.stop_edit_title
import io.github.nanunic9379.rideplan.ui.Radius
import org.jetbrains.compose.resources.stringResource
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.map.MapState
import org.maplibre.compose.overlay.MapOverlayScope
import org.maplibre.spatialk.geojson.Position
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

private const val STOP_DRAG_LONG_PRESS_MILLIS = 1_000L

/**
 * Map gestures for editing the stops (#2): a long press adds a stop, a tap on the map
 * clears the selected stop. [onAdd] is null when the ride cannot be edited: the map behaves as usual.
 * [onTapMap] gets the tapped point (when the map gives one) and returns true when it used the tap:
 * while planning a ride (#33) every tap adds a stop.
 */
@Composable
fun rememberStopInteractions(onAdd: ((LatLon) -> Unit)?, onTapMap: (LatLon?) -> Boolean): MapInteractions {
    val add by rememberUpdatedState(onAdd)
    val tap by rememberUpdatedState(onTapMap)
    return remember {
        MapInteractions {
            callbacks {
                longClick {
                    onEvent { e ->
                        val target = add
                        val point = e.position?.let { mapLatLon(it.latitude, it.longitude) }
                        if (target != null && point != null) {
                            target(point)
                            ClickResult.Consume
                        } else {
                            ClickResult.Pass
                        }
                    }
                }
                click {
                    onEvent { e ->
                        val point = e.position?.let { mapLatLon(it.latitude, it.longitude) }
                        if (tap(point)) ClickResult.Consume else ClickResult.Pass
                    }
                }
            }
        }
    }
}

/**
 * A 48 dp handle on every stop (usable with gloves): tap to select it; hold for a second and
 * drag to move it. Moving before the hold completes is left to the map, so a pan cannot move a stop.
 * The stop itself is drawn by [RideLayers]; the handle shows a ring only while selected or dragged.
 */
@Composable
fun MapOverlayScope.StopHandles(
    stops: List<Stop>,
    selected: Int?,
    map: MapState,
    onSelect: (Int) -> Unit,
    onMove: (index: Int, point: LatLon) -> Unit,
) {
    val move by rememberUpdatedState(onMove)
    val select by rememberUpdatedState(onSelect)
    stops.forEachIndexed { i, stop ->
        key(i, stop.point) {
            var drag by remember { mutableStateOf(Offset.Zero) }
            var dragging by remember { mutableStateOf(false) }
            val position = Position(longitude = stop.point.lon, latitude = stop.point.lat)
            val label = stringResource(Res.string.stop_edit_title, i + 1, stops.size)
            Box(
                Modifier
                    .placedAt(position, Alignment.Center)
                    .offset { IntOffset(drag.x.roundToInt(), drag.y.roundToInt()) }
                    .size(48.dp)
                    .semantics { contentDescription = label }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var cancelled = false
                            val waitResult = withTimeoutOrNull(STOP_DRAG_LONG_PRESS_MILLIS) {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change == null || !change.pressed || change.isConsumed ||
                                        (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                                    ) {
                                        cancelled = true
                                        break
                                    }
                                }
                                false
                            }
                            if (!cancelled && waitResult == null &&
                                currentEvent.changes.any { it.id == down.id && it.pressed }
                            ) {
                                dragging = true
                                select(i)
                                var completed = false
                                try {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Main)
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        val amount = change.positionChange()
                                        if (amount != Offset.Zero) {
                                            change.consume()
                                            drag += amount
                                        }
                                        if (!change.pressed) {
                                            completed = true
                                            break
                                        }
                                    }
                                } finally {
                                    val start = map.screenLocationFromPosition(position)
                                    val end = start?.let { DpOffset(it.x + drag.x.toDp(), it.y + drag.y.toDp()) }
                                    val target = end?.let(map::positionFromScreenLocation)?.let { mapLatLon(it.latitude, it.longitude) }
                                    dragging = false
                                    drag = Offset.Zero
                                    if (completed && target != null) move(i, target)
                                }
                            }
                        }
                    }
                    .clickable(role = Role.Button) { select(i) },
                contentAlignment = Alignment.Center,
            ) {
                if (dragging || selected == i) {
                    Box(
                        Modifier
                            .size(if (dragging) 30.dp else 24.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), CircleShape)
                            .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    )
                }
            }
        }
    }
}

/** What to do with the selected stop: move it earlier or later in the ride, or remove it. */
@Composable
fun StopToolbar(
    ride: Ride,
    index: Int,
    onReorder: (from: Int, to: Int) -> Unit,
    onRemove: (Int) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    /** While planning (#33) any stop can go; on a saved ride start and finish stay. */
    canRemove: Boolean = ride.canRemoveStop,
) {
    val stop = ride.stops.getOrNull(index) ?: return
    Card(modifier.widthIn(max = 360.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(Res.string.stop_edit_title, index + 1, ride.stops.size), style = MaterialTheme.typography.titleSmall)
            stop.name?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { onReorder(index, index - 1) },
                    enabled = index > 0,
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = Radius.Control,
                ) { Text("◀ " + stringResource(Res.string.stop_edit_earlier), maxLines = 1) }
                OutlinedButton(
                    onClick = { onReorder(index, index + 1) },
                    enabled = index < ride.stops.lastIndex,
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = Radius.Control,
                ) { Text(stringResource(Res.string.stop_edit_later) + " ▶", maxLines = 1) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = { onRemove(index) },
                    enabled = canRemove,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(Res.string.stop_edit_remove)) }
                TextButton(onClick = onDone, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(Res.string.stop_edit_done)) }
            }
        }
    }
}
