package io.github.nanunic9379.rideplan.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * RidePlan look (see docs/design/UX.md): the amber-orange of the app icon as brand color,
 * the icon's sky blue as accent, warm neutrals. Fixed colors, no dynamic color:
 * the route on the map is orange and the UI must match it on every phone.
 */
object Brand {
    /** App icon and route line. */
    val Orange = Color(0xFFFF8A1F)
    val Ink = Color(0xFF1C1B26)
    val Sky = Color(0xFF7FD3F0)
}

/** Touch sizes: 48 dp is the Material minimum, 56 dp for actions used with gloves on. */
object Touch {
    val Min = 48.dp
    val Glove = 56.dp
}

private val Light = lightColorScheme(
    primary = Color(0xFF9C4400),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBC8),
    onPrimaryContainer = Color(0xFF341100),
    secondary = Color(0xFF765848),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBCB),
    onSecondaryContainer = Color(0xFF2C160A),
    tertiary = Color(0xFF00677F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB7EAFF),
    onTertiaryContainer = Color(0xFF001F28),
    background = Color(0xFFFFF8F5),
    onBackground = Color(0xFF221A15),
    surface = Color(0xFFFFF8F5),
    onSurface = Color(0xFF221A15),
    surfaceVariant = Color(0xFFF4DED4),
    onSurfaceVariant = Color(0xFF52443C),
    outline = Color(0xFF85736B),
    outlineVariant = Color(0xFFD7C2B9),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFF1EB),
    surfaceContainer = Color(0xFFFCEAE3),
    surfaceContainerHigh = Color(0xFFF6E5DD),
    surfaceContainerHighest = Color(0xFFF0DFD7),
)

private val Dark = darkColorScheme(
    // The brand orange itself: bright and readable on the dark surface (contrast about 7.8:1).
    primary = Brand.Orange,
    onPrimary = Color(0xFF2B1100),
    primaryContainer = Color(0xFF763300),
    onPrimaryContainer = Color(0xFFFFDBC8),
    secondary = Color(0xFFE7BEAB),
    onSecondary = Color(0xFF442A1D),
    secondaryContainer = Color(0xFF5D4132),
    onSecondaryContainer = Color(0xFFFFDBCB),
    tertiary = Brand.Sky,
    onTertiary = Color(0xFF003543),
    tertiaryContainer = Color(0xFF004E60),
    onTertiaryContainer = Color(0xFFB7EAFF),
    background = Color(0xFF19120E),
    onBackground = Color(0xFFF0DFD7),
    surface = Color(0xFF19120E),
    onSurface = Color(0xFFF0DFD7),
    surfaceVariant = Color(0xFF52443C),
    onSurfaceVariant = Color(0xFFD7C2B9),
    outline = Color(0xFFA08D84),
    outlineVariant = Color(0xFF52443C),
    surfaceContainerLowest = Color(0xFF140D09),
    surfaceContainerLow = Color(0xFF221A15),
    surfaceContainer = Color(0xFF261E19),
    surfaceContainerHigh = Color(0xFF312823),
    surfaceContainerHighest = Color(0xFF3C332D),
)

/** Material type scale with heavier titles: they must be read at a glance. */
private val Type = Typography().run {
    copy(
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val Shape = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun RidePlanTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Type, shapes = Shape, content = content)
}
