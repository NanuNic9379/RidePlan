package io.github.nanunic9379.rideplan.route

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.nanunic9379.rideplan.resources.Res
import io.github.nanunic9379.rideplan.resources.route_avoid_unpaved
import io.github.nanunic9379.rideplan.resources.route_curves_lowered
import io.github.nanunic9379.rideplan.resources.route_unpaved_allow
import io.github.nanunic9379.rideplan.resources.route_unpaved_allow_desc
import io.github.nanunic9379.rideplan.resources.route_unpaved_avoid
import io.github.nanunic9379.rideplan.resources.route_unpaved_avoid_desc
import io.github.nanunic9379.rideplan.resources.route_unpaved_prefer
import io.github.nanunic9379.rideplan.resources.route_unpaved_prefer_desc
import io.github.nanunic9379.rideplan.ui.AppIcons
import io.github.nanunic9379.rideplan.ui.Touch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val Unpaved.label: StringResource
    get() = when (this) {
        Unpaved.AVOID -> Res.string.route_unpaved_avoid
        Unpaved.ALLOW -> Res.string.route_unpaved_allow
        Unpaved.PREFER -> Res.string.route_unpaved_prefer
    }

private val Unpaved.description: StringResource
    get() = when (this) {
        Unpaved.AVOID -> Res.string.route_unpaved_avoid_desc
        Unpaved.ALLOW -> Res.string.route_unpaved_allow_desc
        Unpaved.PREFER -> Res.string.route_unpaved_prefer_desc
    }

/** The three-way unpaved choice of the route settings (#9): avoid, allow, prefer. */
@Composable
fun UnpavedChoice(selected: Unpaved, onPick: (Unpaved) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(Res.string.route_avoid_unpaved),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Unpaved.entries.forEachIndexed { i, choice ->
                SegmentedButton(
                    selected = selected == choice,
                    onClick = { onPick(choice) },
                    shape = SegmentedButtonDefaults.itemShape(i, Unpaved.entries.size),
                    icon = {},
                    modifier = Modifier.heightIn(min = Touch.Min),
                ) { Text(stringResource(choice.label), maxLines = 1) }
            }
        }
        Text(
            stringResource(selected.description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Tells the rider the route was computed one curves level lower than asked (#9): at the top level
 * the curvy route kept going through unpaved roads.
 */
@Composable
fun CurvesLoweredNote(info: RouteInfo) {
    if (info.loweredFrom == null) return
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(AppIcons.Info, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stringResource(Res.string.route_curves_lowered),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
