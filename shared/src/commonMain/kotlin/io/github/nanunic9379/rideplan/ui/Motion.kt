package io.github.nanunic9379.rideplan.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * Motion tokens, one curve and three durations for the whole app. Motion only explains a change
 * (something arrives, a state switches, a number moves); nothing loops, pulses or bounces. Compose
 * follows the system animator scale, so with animations removed every tween ends at once.
 */
object Motion {
    /** Fast out, long settle: the only easing curve in the app. */
    val Emphasized = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

    /** Feedback: press, selection, colour changes. */
    const val Feedback = 150

    /** Content entering or switching. */
    const val Enter = 250

    /** The route drawing itself on the ride preview, once. */
    const val Draw = 600

    /** Delay between items of the single list stagger, capped at [StaggerMax] items. */
    const val Stagger = 40
    const val StaggerMax = 6

    /** Scale of something pressed: just enough to feel it under the finger. */
    const val Pressed = 0.98f

    fun <T> feedback(): FiniteAnimationSpec<T> = tween(Feedback, easing = Emphasized)

    fun <T> enter(delay: Int = 0): FiniteAnimationSpec<T> = tween(Enter, delay, Emphasized)

    /** Fade in with a short rise of [risePx]: content appearing in a sheet or a card. */
    fun riseIn(risePx: Int, delay: Int = 0): EnterTransition =
        fadeIn(enter(delay)) + slideInVertically(enter(delay)) { risePx }

    val fadeOut: ExitTransition get() = fadeOut(feedback())
}

/** 8 dp in pixels: how far content rises while it fades in. */
@Composable
fun riseDistance(): Int = with(LocalDensity.current) { 8.dp.roundToPx() }

/**
 * Clickable that shrinks a little while pressed. The scale lives in the graphics layer, so a
 * press redraws the item and never measures or recomposes what is inside it.
 */
fun Modifier.pressable(onClick: () -> Unit, enabled: Boolean = true, role: Role? = Role.Button): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) Motion.Pressed else 1f, Motion.feedback(), label = "press")
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(source, LocalIndication.current, enabled = enabled, role = role, onClick = onClick)
}

/**
 * A ride figure (km, time, speed) that rolls when it changes: the old value slides up and out,
 * the new one comes in from below. The first value shows at once.
 */
@Composable
fun Figure(text: String, style: TextStyle, color: Color = Color.Unspecified, modifier: Modifier = Modifier) {
    val rise = riseDistance()
    AnimatedContent(
        targetState = text,
        modifier = modifier,
        transitionSpec = {
            (fadeIn(Motion.enter()) + slideInVertically(Motion.enter()) { rise })
                .togetherWith(fadeOut(Motion.feedback()) + slideOutVertically(Motion.feedback()) { -rise })
                .using(SizeTransform(clip = false))
        },
        label = "figure",
    ) { value ->
        Text(value, style = style, color = color, maxLines = 1)
    }
}

/**
 * The single list stagger: item [index] fades in and rises [riseDistance] after the ones before
 * it, [Motion.Stagger] ms apart, for the first [Motion.StaggerMax] items only. Alpha and offset
 * live in the graphics layer: nothing is measured again while it plays.
 */
fun Modifier.staggerIn(index: Int, enabled: Boolean): Modifier =
    if (!enabled || index >= Motion.StaggerMax) this else composed {
        val shown = remember { Animatable(0f) }
        val rise = riseDistance()
        LaunchedEffect(Unit) { shown.animateTo(1f, Motion.enter(index * Motion.Stagger)) }
        graphicsLayer {
            alpha = shown.value
            translationY = (1f - shown.value) * rise
        }
    }
