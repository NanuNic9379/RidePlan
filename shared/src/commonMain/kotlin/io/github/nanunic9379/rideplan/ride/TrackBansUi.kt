package io.github.nanunic9379.rideplan.ride

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.nanunic9379.rideplan.personal.distanceText
import io.github.nanunic9379.rideplan.resources.Res
import io.github.nanunic9379.rideplan.resources.track_bans_motorway
import io.github.nanunic9379.rideplan.resources.track_bans_no_access
import io.github.nanunic9379.rideplan.resources.track_bans_partial
import io.github.nanunic9379.rideplan.resources.track_bans_roads
import io.github.nanunic9379.rideplan.resources.track_bans_title
import io.github.nanunic9379.rideplan.resources.track_bans_unchecked
import io.github.nanunic9379.rideplan.route.BanKind
import io.github.nanunic9379.rideplan.route.TrackBans
import io.github.nanunic9379.rideplan.route.unroutedTrack
import io.github.nanunic9379.rideplan.ui.AppIcons
import org.jetbrains.compose.resources.stringResource

/** Where the ban check of a track stands (#9). */
private sealed interface BanCheck {
    data object Running : BanCheck

    /** No road data on the phone for this track, or no part of it could be matched. */
    data object Unchecked : BanCheck

    data class Done(val bans: TrackBans) : BanCheck
}

/**
 * The ban check of an imported track drawn as it is (#9): roads closed to motorcycles and, for a
 * bike under 150 cc ([noMotorways]), motorways. [check] runs on the phone's road data (null = the
 * platform cannot check). Says nothing for a computed route: the engine already kept to the rules.
 */
@Composable
fun TrackBansSection(
    ride: Ride,
    noMotorways: Boolean,
    check: (suspend (track: List<LatLon>, noMotorways: Boolean) -> TrackBans?)?,
) {
    if (!ride.unroutedTrack || ride.track.size < 2) return
    val track = ride.track
    var state by remember(track, noMotorways) { mutableStateOf<BanCheck>(BanCheck.Running) }
    LaunchedEffect(track, noMotorways, check) {
        state = BanCheck.Running
        val result = check?.let { runCatching { it(track, noMotorways) }.getOrNull() }
        state = if (result == null) BanCheck.Unchecked else BanCheck.Done(result)
    }
    TrackBansContent(state)
}

@Composable
private fun TrackBansContent(state: BanCheck) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when (state) {
        BanCheck.Running -> Unit
        BanCheck.Unchecked -> Text(stringResource(Res.string.track_bans_unchecked), style = MaterialTheme.typography.bodySmall, color = muted)
        is BanCheck.Done -> {
            val bans = state.bans
            if (bans.isEmpty) {
                if (bans.partial) Text(stringResource(Res.string.track_bans_partial), style = MaterialTheme.typography.bodySmall, color = muted)
                return
            }
            Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(Res.string.track_bans_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { heading() },
                )
                for (kind in BanKind.entries) {
                    val metres = bans.metres(kind)
                    if (metres <= 0) continue
                    val length = distanceText(metres / 1000)
                    val text = when (kind) {
                        BanKind.MOTORWAY -> stringResource(Res.string.track_bans_motorway, length)
                        BanKind.NO_ACCESS -> stringResource(Res.string.track_bans_no_access, length)
                    }
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(AppIcons.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(text, style = MaterialTheme.typography.bodyMedium)
                            val names = bans.names(kind)
                            if (names.isNotEmpty()) {
                                Text(stringResource(Res.string.track_bans_roads, names.joinToString(", ")), style = MaterialTheme.typography.bodySmall, color = muted)
                            }
                        }
                    }
                }
                if (bans.partial) Text(stringResource(Res.string.track_bans_partial), style = MaterialTheme.typography.bodySmall, color = muted)
            }
        }
    }
}
