package io.github.nanunic9379.rideplan.personal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.nanunic9379.rideplan.profile.Sketch
import io.github.nanunic9379.rideplan.profile.Units
import io.github.nanunic9379.rideplan.profile.civilDate
import io.github.nanunic9379.rideplan.profile.FuelUnits
import io.github.nanunic9379.rideplan.profile.Measures
import io.github.nanunic9379.rideplan.resources.Res
import io.github.nanunic9379.rideplan.resources.date_medium
import io.github.nanunic9379.rideplan.resources.months_long
import io.github.nanunic9379.rideplan.resources.months_short
import io.github.nanunic9379.rideplan.resources.nav_back
import io.github.nanunic9379.rideplan.resources.ride_km
import io.github.nanunic9379.rideplan.resources.ride_mi
import io.github.nanunic9379.rideplan.resources.unit_km
import io.github.nanunic9379.rideplan.resources.unit_mi
import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ui.AppIcons
import io.github.nanunic9379.rideplan.ui.Brand
import io.github.nanunic9379.rideplan.ui.Figure
import io.github.nanunic9379.rideplan.ui.TabularFigures
import io.github.nanunic9379.rideplan.ui.Touch
import io.github.nanunic9379.rideplan.ui.pressable
import org.jetbrains.compose.resources.stringResource

/** Km or miles, from the settings (#21). */
val LocalUnits = staticCompositionLocalOf { Units.KM }

/** Litres or gallons, km/l or mpg: from the units and the country (#15). */
val LocalFuelUnits = staticCompositionLocalOf { FuelUnits.METRIC }

/** The app language writes decimals with a comma ("12,5 km", #15). */
fun decimalComma(): Boolean = Measures.decimalComma(Locale.current.language)

/** "54 km" or "34 mi". */
@Composable
fun distanceText(km: Double): String {
    val units = LocalUnits.current
    val n = Measures.lengthNumber(km, units, decimalComma())
    return if (units == Units.MILES) stringResource(Res.string.ride_mi, n) else stringResource(Res.string.ride_km, n)
}

/** Just the number, in the chosen unit. */
@Composable
fun distanceNumber(km: Double): String = Measures.lengthNumber(km, LocalUnits.current, decimalComma())

/** "km" or "mi", next to a [distanceNumber]. */
@Composable
fun distanceUnit(): String = stringResource(if (LocalUnits.current == Units.MILES) Res.string.unit_mi else Res.string.unit_km)

@Composable
fun monthShort(month: Int): String = stringResource(Res.string.months_short).split('|').getOrElse(month - 1) { "$month" }

@Composable
fun monthLong(month: Int): String = stringResource(Res.string.months_long).split('|').getOrElse(month - 1) { "$month" }

/** "12 Mar 2026", in the order of the language. */
@Composable
fun dateText(millis: Long): String {
    val (d, m, y) = civilDate(millis)
    return stringResource(Res.string.date_medium, d, monthShort(m), y)
}

/** The colours you can give your avatar (#21): brand, orange, sky, ink, green. */
val AvatarColors = listOf(Color(0xFF9C4400), Brand.Orange, Color(0xFF00677F), Brand.Ink, Color(0xFF4E6B2E))

@Composable
fun Avatar(name: String, color: Int, size: Dp, modifier: Modifier = Modifier) {
    val bg = AvatarColors.getOrElse(color) { AvatarColors[0] }
    Box(modifier.size(size).background(bg, CircleShape), contentAlignment = Alignment.Center) {
        val initial = name.trim().firstOrNull()?.uppercase()
        val style = if (size >= 80.dp) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall
        if (initial != null) Text(initial, style = style, color = Color.White)
        else Icon(AppIcons.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(size / 2))
    }
}

/**
 * A page opened from a tab: top bar with back and its own actions, content that scrolls and
 * stays clear of the system bars and the keyboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubPage(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(Touch.Glove)) {
                        Icon(AppIcons.Back, contentDescription = stringResource(Res.string.nav_back))
                    }
                },
                actions = actions,
            )
            val body = Modifier.fillMaxSize().navigationBarsPadding().imePadding()
            Column(
                (if (scroll) body.verticalScroll(rememberScrollState()) else body).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 8.dp).semantics { heading() },
    )
}

/** Rows grouped on a card, with thin dividers between them. */
@Composable
fun RowGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) { Column(content = content) }
}

@Composable
fun GroupDivider() = HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)

/**
 * A list row: 64 dp tall, a plain icon in its own column, title, optional line below and
 * something at the end (a value, a chevron, a switch).
 */
@Composable
fun NavRow(
    icon: ImageVector?,
    title: String,
    subtitle: String? = null,
    trailing: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)?,
    end: (@Composable () -> Unit)? = null,
) {
    val alpha = if (enabled) 1f else 0.5f
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (icon != null) RowIcon(icon, alpha)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
            }
        }
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        when {
            end != null -> end()
            onClick != null && enabled -> Icon(AppIcons.Chevron, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The icon of a row: one size, one colour, so the icons of a group line up in a column. */
@Composable
fun RowIcon(icon: ImageVector, alpha: Float = 1f) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha), modifier = Modifier.size(24.dp))
}

/** A row with a switch: the whole row toggles it. */
@Composable
fun SwitchRow(icon: ImageVector?, title: String, subtitle: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val alpha = if (enabled) 1f else 0.5f
        if (icon != null) RowIcon(icon, alpha)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * A number and what it counts, read at a glance like a dashboard: the figure big and tabular,
 * its [unit] smaller beside it, the label in sentence case below.
 */
@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, unit: String? = null, onClick: (() -> Unit)? = null) {
    Card(
        modifier.then(if (onClick != null) Modifier.pressable(onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Figure(
                    value,
                    style = MaterialTheme.typography.headlineSmall.merge(TabularFigures),
                    color = MaterialTheme.colorScheme.primary,
                )
                if (unit != null) {
                    Text(
                        unit,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

/** A card with an icon on the left: notes, warnings, privacy promises. */
@Composable
fun NoteCard(icon: ImageVector, text: String, container: Color, content: Color, modifier: Modifier = Modifier, title: String? = null) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = container, contentColor = content)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (title != null) Text(title, style = MaterialTheme.typography.titleSmall)
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * The shape of rides without a map: drawn on the phone, nothing downloaded. The route looks
 * as it does on the map (orange line on a dark casing, white start, dark finish) on a quiet
 * paper-like ground; a [grid] spacing above 0 adds a faint grid like a road map.
 * [progress] (read only while drawing) trims the line from 0 to 1 for the one-time draw-on.
 */
@Composable
fun RideSketch(
    lines: List<List<LatLon>>,
    modifier: Modifier,
    strokeWidth: Dp = 4.dp,
    background: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    grid: Dp = 0.dp,
    progress: () -> Float = { 1f },
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val dark = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.background(background, MaterialTheme.shapes.medium)) {
        if (grid > 0.dp) {
            val step = grid.toPx()
            val hair = 1.dp.toPx()
            var x = step
            while (x < size.width) { drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), hair); x += step }
            var y = step
            while (y < size.height) { drawLine(gridColor, Offset(0f, y), Offset(size.width, y), hair); y += step }
        }
        val pad = 16.dp.toPx()
        val shapes = Sketch.project(lines.filter { it.size >= 2 }, size.width, size.height, pad)
        val w = strokeWidth.toPx()
        val single = shapes.size == 1
        val p = progress().coerceIn(0f, 1f)
        val alpha = if (single) 1f else 0.75f
        for (pts in shapes) {
            if (pts.size < 2) continue
            var path = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (pt in pts.drop(1)) lineTo(pt.x, pt.y)
            }
            if (p < 1f) {
                val measure = PathMeasure().apply { setPath(path, false) }
                path = Path().also { measure.getSegment(0f, measure.length * p, it, true) }
            }
            drawPath(path, RouteCasing.copy(alpha = alpha), style = Stroke(width = w * 1.75f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, Brand.Orange.copy(alpha = alpha), style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (single && shapes[0].size >= 2) {
            val r = (w * 1.25f).coerceAtLeast(3.dp.toPx())
            val ring = (r * 0.4f).coerceAtLeast(1.dp.toPx())
            val start = shapes[0].first().let { Offset(it.x, it.y) }
            val end = shapes[0].last().let { Offset(it.x, it.y) }
            drawCircle(Color.White, r, start)
            drawCircle(dark, r, start, style = Stroke(ring))
            if (p >= 1f) {
                drawCircle(dark, r, end)
                drawCircle(Color.White, r, end, style = Stroke(ring))
            }
        }
    }
}

/** The casing under the orange route, as on the map. */
private val RouteCasing = Color(0xFF7A3300)

internal val ListPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp)
