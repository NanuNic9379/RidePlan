package io.github.nanunic9379.rideplan.nav

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import io.github.nanunic9379.rideplan.route.TurnSign
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The arrow of a maneuver, drawn by hand: no icon library, readable at a glance with
 * a thick stroke. Coordinates are fractions of the square.
 */
@Composable
fun TurnArrow(sign: TurnSign, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.minDimension
        val stroke = Stroke(width = w * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Double, y: Double) = Offset((x * w).toFloat(), (y * w).toFloat())
        when (sign) {
            TurnSign.ARRIVE -> {
                drawCircle(color, radius = w * 0.3f, style = stroke)
                drawCircle(color, radius = w * 0.13f)
            }
            TurnSign.ROUNDABOUT -> {
                drawCircle(color, radius = w * 0.17f, center = p(0.5, 0.45), style = stroke)
                drawLine(color, p(0.5, 0.95), p(0.5, 0.62), strokeWidth = stroke.width, cap = StrokeCap.Round)
                arrow(color, p(0.62, 0.33), 45.0, w, stroke, length = 0.2)
            }
            TurnSign.UTURN_LEFT, TurnSign.UTURN_RIGHT -> {
                val right = sign == TurnSign.UTURN_RIGHT
                val (a, b) = if (right) 0.32 to 0.68 else 0.68 to 0.32
                val path = Path().apply {
                    moveTo(p(a, 0.92).x, p(a, 0.92).y)
                    lineTo(p(a, 0.4).x, p(a, 0.4).y)
                    cubicTo(p(a, 0.12).x, p(a, 0.12).y, p(b, 0.12).x, p(b, 0.12).y, p(b, 0.4).x, p(b, 0.4).y)
                    lineTo(p(b, 0.55).x, p(b, 0.55).y)
                }
                drawPath(path, color, style = stroke)
                head(color, p(b, 0.72), 180.0, w)
            }
            else -> {
                val angle = sign.angle.toDouble()
                if (angle == 0.0) {
                    drawLine(color, p(0.5, 0.92), p(0.5, 0.3), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    head(color, p(0.5, 0.2), 0.0, w)
                } else {
                    drawLine(color, p(0.5, 0.92), p(0.5, 0.52), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    arrow(color, p(0.5, 0.52), angle, w, stroke, length = 0.3)
                }
            }
        }
    }
}

/** A shaft from [from] in direction [angle] (degrees, 0 = up, clockwise), with its head. */
private fun DrawScope.arrow(color: Color, from: Offset, angle: Double, w: Float, stroke: Stroke, length: Double) {
    val r = angle * PI / 180
    val dx = sin(r).toFloat()
    val dy = -cos(r).toFloat()
    val end = Offset(from.x + dx * (length * w).toFloat(), from.y + dy * (length * w).toFloat())
    drawLine(color, from, end, strokeWidth = stroke.width, cap = StrokeCap.Round)
    head(color, Offset(end.x + dx * w * 0.08f, end.y + dy * w * 0.08f), angle, w)
}

/** Filled triangle with its tip at [tip], pointing towards [angle]. */
private fun DrawScope.head(color: Color, tip: Offset, angle: Double, w: Float) {
    val r = angle * PI / 180
    val dx = sin(r).toFloat()
    val dy = -cos(r).toFloat()
    val len = w * 0.24f
    val half = w * 0.17f
    val base = Offset(tip.x - dx * len, tip.y - dy * len)
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(base.x - dy * half, base.y + dx * half)
        lineTo(base.x + dy * half, base.y - dx * half)
        close()
    }
    drawPath(path, color)
}
