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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * RidePlan look (see docs/design/UX.md): asphalt and paper road maps. The chrome sits on a
 * neutral warm-grey ramp; the amber-orange of the app icon is the only rich colour and is kept
 * for the route line, the main action, the selection and the ride figures. Fixed colours, no
 * dynamic colour: the route on the map is orange and the UI must match it on every phone.
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

/**
 * Corner radii, one scale for the whole app: controls 12, cards 16, sheets 24. Nested
 * corners take the outer radius minus the padding between them.
 */
object Radius {
    /** Buttons, text fields, small surfaces on the map. */
    val Control = RoundedCornerShape(12.dp)
    /** Tags and badges inside a card. */
    val Tag = RoundedCornerShape(6.dp)
    val Card = RoundedCornerShape(16.dp)
    val Sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
}

/** Figures that line up and do not jump while they change: km, time, speed. */
val TabularFigures = TextStyle(fontFeatureSettings = "tnum")

private val Light = lightColorScheme(
    // Darker than the brand orange so white text on it and orange text on the surface reach 4.5:1.
    primary = Color(0xFF9C4400),
    onPrimary = Color.White,
    // Only for selected things (riding mode, default bike).
    primaryContainer = Color(0xFFF5E1D2),
    onPrimaryContainer = Color(0xFF3A1800),
    secondary = Color(0xFF5F5A53),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5E1DB),
    onSecondaryContainer = Color(0xFF1E1C19),
    tertiary = Color(0xFF3D6172),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDE6EB),
    onTertiaryContainer = Color(0xFF13262F),
    background = Color(0xFFF2F1ED),
    onBackground = Color(0xFF1E1C19),
    surface = Color(0xFFF2F1ED),
    onSurface = Color(0xFF1E1C19),
    surfaceVariant = Color(0xFFE5E2DD),
    onSurfaceVariant = Color(0xFF56514A),
    surfaceTint = Color(0xFF5F5A53),
    outline = Color(0xFF7E786F),
    outlineVariant = Color(0xFFD3CEC6),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFCFBF9),
    surfaceContainer = Color(0xFFEFEDE9),
    surfaceContainerHigh = Color(0xFFEAE7E2),
    surfaceContainerHighest = Color(0xFFE4E1DB),
    // The turn banner in navigation: dark on the light theme for sunlight.
    inverseSurface = Color(0xFF322F2B),
    inverseOnSurface = Color(0xFFF4F1EC),
    inversePrimary = Color(0xFFFFB68A),
)

private val Dark = darkColorScheme(
    // The brand orange itself: bright and readable on the dark surface (contrast about 7.8:1).
    primary = Brand.Orange,
    onPrimary = Color(0xFF2B1100),
    primaryContainer = Color(0xFF4A2A14),
    onPrimaryContainer = Color(0xFFFFDCC4),
    secondary = Color(0xFFCDC6BC),
    onSecondary = Color(0xFF2F2B26),
    secondaryContainer = Color(0xFF393632),
    onSecondaryContainer = Color(0xFFECE8E2),
    tertiary = Color(0xFFA6C8D8),
    onTertiary = Color(0xFF0E2A36),
    tertiaryContainer = Color(0xFF26363E),
    onTertiaryContainer = Color(0xFFD4E6EF),
    background = Color(0xFF141311),
    onBackground = Color(0xFFECE8E2),
    surface = Color(0xFF141311),
    onSurface = Color(0xFFECE8E2),
    surfaceVariant = Color(0xFF34312D),
    onSurfaceVariant = Color(0xFFB8B1A7),
    surfaceTint = Color(0xFFCDC6BC),
    outline = Color(0xFF8C867C),
    outlineVariant = Color(0xFF3E3A35),
    surfaceContainerLowest = Color(0xFF0F0E0D),
    surfaceContainerLow = Color(0xFF1B1A18),
    surfaceContainer = Color(0xFF201E1C),
    surfaceContainerHigh = Color(0xFF2A2825),
    surfaceContainerHighest = Color(0xFF34312D),
    inverseSurface = Color(0xFFECE8E2),
    inverseOnSurface = Color(0xFF322F2B),
    inversePrimary = Color(0xFF9C4400),
)

/** Material type scale; titles at weight 500: hierarchy comes from size and colour. */
private val Type = Typography().run {
    val medium = FontWeight.Medium
    copy(
        headlineLarge = headlineLarge.copy(fontWeight = medium),
        headlineMedium = headlineMedium.copy(fontWeight = medium),
        headlineSmall = headlineSmall.copy(fontWeight = medium),
        titleLarge = titleLarge.copy(fontWeight = medium),
        titleMedium = titleMedium.copy(fontWeight = medium),
        titleSmall = titleSmall.copy(fontWeight = medium),
        labelLarge = labelLarge.copy(fontWeight = medium),
    )
}

private val Shape = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    // FABs and floating panels: a control, not a pill.
    large = RoundedCornerShape(16.dp),
    // Sheets and dialogs.
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun RidePlanTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Type, shapes = Shape, content = content)
}
