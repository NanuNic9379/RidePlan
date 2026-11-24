package io.github.nanunic9379.rideplan.route

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.nanunic9379.rideplan.resources.Res
import io.github.nanunic9379.rideplan.resources.offroad_legend_no_access
import io.github.nanunic9379.rideplan.resources.offroad_legend_note
import io.github.nanunic9379.rideplan.resources.offroad_legend_unpaved
import io.github.nanunic9379.rideplan.resources.offroad_off
import io.github.nanunic9379.rideplan.resources.offroad_on
import io.github.nanunic9379.rideplan.resources.offroad_toggle
import io.github.nanunic9379.rideplan.ui.AppIcons
import io.github.nanunic9379.rideplan.ui.Radius
import io.github.nanunic9379.rideplan.ui.Touch
import org.jetbrains.compose.resources.stringResource
import org.maplibre.compose.expressions.dsl.all
import org.maplibre.compose.expressions.dsl.any
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.eq
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.neq
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.sources.VectorSource
import org.maplibre.compose.sources.getBaseSource

/**
 * The two colours of the off-road overlay (#9), taken from the theme by the caller: the map's
 * layer block may not see the app's theme.
 */
data class OffroadColors(val unpaved: Color, val noAccess: Color)

/** The overlay's colours in the current theme: muted blue-grey for unpaved, the error colour for closed. */
@Composable
fun offroadColors(): OffroadColors = OffroadColors(
    unpaved = MaterialTheme.colorScheme.tertiary,
    noAccess = MaterialTheme.colorScheme.error,
)

/**
 * Unpaved roads (dashed) and roads closed to traffic (dotted) over the base map (#9), drawn from
 * the base style's own tiles ([OffroadRules]): no new data, works wherever the map works.
 * Thin and half transparent, so the map stays readable.
 */
@Composable
fun OffroadLayers(colors: OffroadColors) {
    val source = getBaseSource<VectorSource>(OffroadRules.SOURCE) ?: return
    val road = any(*OffroadRules.ROAD_CLASSES.map { feature["class"] eq const(it) }.toTypedArray())
    val closed = feature["access"] eq const("no")
    val open = feature["access"] neq const("no")
    val unpaved = all(
        road,
        open,
        any(
            feature["surface"] eq const("unpaved"),
            all(feature["class"] eq const(OffroadRules.TRACK), feature["surface"] neq const("paved")),
        ),
    )
    LineLayer(
        id = "offroad-unpaved",
        source = source,
        sourceLayer = OffroadRules.LAYER,
        minZoom = OffroadRules.MIN_ZOOM,
        filter = unpaved,
        opacity = const(0.75f),
        color = const(colors.unpaved),
        dasharray = const(listOf<Number>(2, 1.5)),
        width = const(2.dp),
    )
    LineLayer(
        id = "offroad-no-access",
        source = source,
        sourceLayer = OffroadRules.LAYER,
        minZoom = OffroadRules.MIN_ZOOM,
        filter = all(road, closed),
        opacity = const(0.7f),
        color = const(colors.noAccess),
        dasharray = const(listOf<Number>(1, 1.5)),
        width = const(2.dp),
    )
}

/** The Explore map's switch for the overlay: a 56 dp button, highlighted when on. */
@Composable
fun OffroadToggle(on: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val state = stringResource(if (on) Res.string.offroad_on else Res.string.offroad_off)
    SmallFloatingActionButton(
        onClick = onToggle,
        modifier = modifier.size(Touch.Glove).semantics { role = Role.Switch; stateDescription = state },
        containerColor = if (on) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (on) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
    ) {
        Icon(AppIcons.Landscape, contentDescription = stringResource(Res.string.offroad_toggle))
    }
}

/** The small legend shown while the overlay is on. */
@Composable
fun OffroadLegend(colors: OffroadColors, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.width(220.dp),
        shape = Radius.Card,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LegendRow(colors.unpaved, dash = 6f, stringResource(Res.string.offroad_legend_unpaved))
            LegendRow(colors.noAccess, dash = 3f, stringResource(Res.string.offroad_legend_no_access))
            Text(
                stringResource(Res.string.offroad_legend_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LegendRow(color: Color, dash: Float, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Canvas(Modifier.width(28.dp).size(width = 28.dp, height = 8.dp)) {
            drawLine(
                color = color,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = 3.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash * density, dash * density * 0.75f)),
            )
        }
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** The toggle, with the legend under it while the overlay is on (top right of Explore). */
@Composable
fun OffroadControls(on: Boolean, onToggle: () -> Unit, colors: OffroadColors, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OffroadToggle(on, onToggle)
        if (on) OffroadLegend(colors)
    }
}
