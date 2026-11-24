package io.github.nanunic9379.rideplan.ride

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nanunic9379.rideplan.nav.NavigationPlatform
import io.github.nanunic9379.rideplan.resources.Res
import io.github.nanunic9379.rideplan.resources.plan_done
import io.github.nanunic9379.rideplan.resources.plan_here
import io.github.nanunic9379.rideplan.resources.plan_here_failed
import io.github.nanunic9379.rideplan.resources.plan_hint_next
import io.github.nanunic9379.rideplan.resources.plan_hint_start
import io.github.nanunic9379.rideplan.resources.plan_leave
import io.github.nanunic9379.rideplan.resources.plan_leave_discard
import io.github.nanunic9379.rideplan.resources.plan_leave_keep
import io.github.nanunic9379.rideplan.resources.plan_leave_text
import io.github.nanunic9379.rideplan.resources.plan_leave_title
import io.github.nanunic9379.rideplan.resources.plan_locating
import io.github.nanunic9379.rideplan.resources.plan_name
import io.github.nanunic9379.rideplan.resources.plan_one_more
import io.github.nanunic9379.rideplan.resources.plan_round_trip
import io.github.nanunic9379.rideplan.resources.plan_undo
import io.github.nanunic9379.rideplan.route.RouteActions
import io.github.nanunic9379.rideplan.route.RouteSection
import io.github.nanunic9379.rideplan.route.RouteState
import io.github.nanunic9379.rideplan.ui.AppIcons
import io.github.nanunic9379.rideplan.ui.Touch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.stringResource
import kotlin.coroutines.resume

/**
 * Where the rider is, for "Use my position" (#33): asks for the permission if needed, then waits
 * for the first fix, at most [timeoutMs]. Null when refused or not found in time.
 */
suspend fun NavigationPlatform.currentPosition(timeoutMs: Long = 20_000): LatLon? {
    if (!requestLocation()) return null
    return try {
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                startLocation { fix -> if (cont.isActive) cont.resume(fix.point) }
            }
        }
    } finally {
        stopLocation()
    }
}

/**
 * Top of the map while planning (#33): back, what a tap does now, "Undo last". Place search
 * will take the middle when the offline place index exists; for now taps only.
 */
@Composable
fun PlanTopBar(draft: PlanDraft, onBack: () -> Unit, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3.dp,
        modifier = modifier.fillMaxWidth().heightIn(min = Touch.Glove),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(Touch.Glove)) {
                Icon(AppIcons.Back, contentDescription = stringResource(Res.string.plan_leave))
            }
            Text(
                stringResource(if (draft.points.isEmpty()) Res.string.plan_hint_start else Res.string.plan_hint_next),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onUndo, enabled = draft.canUndo, modifier = Modifier.size(Touch.Glove)) {
                Icon(AppIcons.Undo, contentDescription = stringResource(Res.string.plan_undo))
            }
        }
    }
}

/**
 * The sheet under the map while planning (#33): name, km, time and stops of the ride so far,
 * the route state (data to download, calculating), "Round trip" and "Done".
 */
@Composable
fun PlanSheet(
    draft: PlanDraft,
    ride: Ride,
    route: RouteState,
    routeActions: RouteActions?,
    /** "Use my position" offered: the platform can locate the phone. */
    canLocate: Boolean,
    locating: Boolean,
    locateFailed: Boolean,
    onName: (String) -> Unit,
    onHere: () -> Unit,
    onRoundTrip: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = draft.name,
                onValueChange = onName,
                label = { Text(stringResource(Res.string.plan_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                if (draft.canFinish) stats(ride) else stringResource(Res.string.plan_one_more),
                style = MaterialTheme.typography.titleMedium,
                color = if (draft.canFinish) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (draft.canFinish) RouteSection(route, routeActions)
            if (canLocate && draft.points.isEmpty()) {
                FilledTonalButton(
                    onClick = onHere,
                    enabled = !locating,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Touch.Glove),
                ) {
                    if (locating) CircularProgressIndicator(Modifier.size(ButtonDefaults.IconSize), strokeWidth = 2.dp)
                    else Icon(AppIcons.MyLocation, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(if (locating) Res.string.plan_locating else Res.string.plan_here))
                }
                if (locateFailed) {
                    Text(stringResource(Res.string.plan_here_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    onClick = onRoundTrip,
                    enabled = draft.canRoundTrip,
                    modifier = Modifier.weight(1f).heightIn(min = Touch.Glove),
                ) {
                    Icon(AppIcons.Loop, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(Res.string.plan_round_trip), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Button(
                    onClick = onDone,
                    enabled = draft.canFinish,
                    modifier = Modifier.weight(1f).heightIn(min = Touch.Glove),
                ) {
                    Icon(AppIcons.Check, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(Res.string.plan_done), maxLines = 1)
                }
            }
        }
    }
}

/** Back while planning with points on the map: nothing is lost by mistake. */
@Composable
fun PlanLeaveDialog(onKeep: () -> Unit, onDiscard: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeep,
        title = { Text(stringResource(Res.string.plan_leave_title)) },
        text = { Text(stringResource(Res.string.plan_leave_text)) },
        confirmButton = {
            TextButton(onClick = onDiscard, modifier = Modifier.heightIn(min = Touch.Min)) {
                Text(stringResource(Res.string.plan_leave_discard), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onKeep, modifier = Modifier.heightIn(min = Touch.Min)) { Text(stringResource(Res.string.plan_leave_keep)) }
        },
    )
}
