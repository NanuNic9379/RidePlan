package io.github.nanunic9379.rideplan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.nanunic9379.rideplan.resources.Res
import io.github.nanunic9379.rideplan.resources.map_attribution
import io.github.nanunic9379.rideplan.ui.AppIcons
import io.github.nanunic9379.rideplan.ui.Motion
import io.github.nanunic9379.rideplan.ui.Radius
import io.github.nanunic9379.rideplan.ui.Touch
import io.github.nanunic9379.rideplan.ui.riseDistance
import org.jetbrains.compose.resources.stringResource
import org.maplibre.compose.camera.CameraMoveReason
import org.maplibre.compose.map.LocalMapState
import org.maplibre.compose.overlay.AttributionLinks
import org.maplibre.compose.overlay.LocalViewportInsets
import org.maplibre.compose.overlay.DisappearingCompassButton
import org.maplibre.compose.overlay.DisappearingScaleBar
import org.maplibre.compose.overlay.MapOverlay
import org.maplibre.compose.overlay.ScaleBarMeasure
import org.maplibre.compose.overlay.ScaleBarMeasures
import io.github.nanunic9379.rideplan.personal.LocalUnits
import io.github.nanunic9379.rideplan.profile.Units
import org.maplibre.compose.overlay.MaplibreLogo
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeIn

/**
 * The map's own controls (scale bar, compass, logo, data credits) as in the library default,
 * but with the credits folded behind an info button: they open on a tap and close when the map
 * is moved, instead of a wide band across the map.
 */
val CompactMapOverlay = MapOverlay {
    val map = LocalMapState.current ?: return@MapOverlay
    // As the library places its own controls: inside the viewport insets, one spacing in.
    Box(Modifier.fillMaxSize().padding(LocalViewportInsets.current).padding(MapOverlay.Spacing)) {
        // One unit only, the one chosen in the settings (#219), instead of km and miles together.
        val measure = if (LocalUnits.current == Units.MILES) ScaleBarMeasure.FeetAndMiles else ScaleBarMeasure.Metric
        DisappearingScaleBar(
            { map.viewport?.metersPerDpAtCenter ?: 0.0 },
            { map.cameraPosition.zoom },
            Modifier.align(Alignment.TopStart),
            measures = ScaleBarMeasures(primary = measure),
        )
        DisappearingCompassButton(Modifier.align(Alignment.TopEnd))
        MaplibreLogo(Modifier.align(Alignment.BottomStart))
        CompactAttribution(Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
private fun CompactAttribution(modifier: Modifier) {
    val map = LocalMapState.current ?: return
    var open by remember { mutableStateOf(false) }
    // The credits of every source of the style, read again once the style has loaded.
    val loaded = map.style.loadState
    val credits = remember(map, loaded) {
        map.style.sources.mapNotNull { it.attributionHtml?.takeIf { html -> html.isNotBlank() } }.distinct()
    }
    LaunchedEffect(map) {
        snapshotFlow { map.isCameraMoving && map.cameraMoveReason == CameraMoveReason.Gesture }.collect { if (it) open = false }
    }
    if (credits.isEmpty()) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val rise = riseDistance()
        AnimatedVisibility(open, enter = fadeIn(Motion.enter()) + slideInVertically(Motion.enter()) { rise }, exit = Motion.fadeOut) {
            Surface(
                shape = Radius.Control,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.widthIn(max = 260.dp),
            ) {
                AttributionLinks(credits, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), MaterialTheme.typography.bodySmall)
            }
        }
        IconButton(
            onClick = { open = !open },
            modifier = Modifier.size(Touch.Min),
            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
        ) {
            Box(
                Modifier.size(28.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), Radius.Control),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Info, contentDescription = stringResource(Res.string.map_attribution), modifier = Modifier.size(20.dp))
            }
        }
    }
}

/**
 * A soft shade under the status bar, so the clock and battery stay readable over a busy map.
 * It fades out below the bar; touches go through to the map.
 */
@Composable
fun StatusBarScrim(modifier: Modifier = Modifier) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val shade = MaterialTheme.colorScheme.surface
    Box(
        modifier.fillMaxWidth().height(top + 16.dp)
            .background(Brush.verticalGradient(0f to shade.copy(alpha = 0.72f), 0.6f to shade.copy(alpha = 0.4f), 1f to Color.Transparent)),
    )
}
