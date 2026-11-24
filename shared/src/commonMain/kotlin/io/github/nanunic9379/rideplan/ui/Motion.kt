package io.github.nanunic9379.rideplan.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically

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

    fun <T> feedback(): FiniteAnimationSpec<T> = tween(Feedback, easing = Emphasized)

    fun <T> enter(delay: Int = 0): FiniteAnimationSpec<T> = tween(Enter, delay, Emphasized)

    /** Fade in with a short rise: content appearing in a sheet or a card. */
    fun riseIn(delay: Int = 0, risePx: Int = 24): EnterTransition =
        fadeIn(enter(delay)) + slideInVertically(enter(delay)) { risePx }

    val fadeOut: ExitTransition get() = fadeOut(feedback())
}
