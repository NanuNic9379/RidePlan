package io.github.nanunic9379.rideplan.profile

import io.github.nanunic9379.rideplan.ride.LatLon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SketchTest {
    private fun near(a: Float, b: Float) = assertTrue(kotlin.math.abs(a - b) < 0.01f, "$a != $b")

    @Test
    fun emptyInputGivesEmptyLines() {
        assertEquals(listOf(emptyList(), emptyList()), Sketch.project(listOf(emptyList(), emptyList()), 100f, 100f, 10f))
    }

    @Test
    fun boxSmallerThanPaddingGivesEmptyLines() {
        val line = listOf(LatLon(45.0, 9.0), LatLon(45.1, 9.1))
        assertEquals(listOf(emptyList()), Sketch.project(listOf(line), 10f, 100f, 10f))
    }

    @Test
    fun northIsUpAndEverythingStaysInTheBox() {
        val line = listOf(LatLon(45.0, 9.0), LatLon(45.5, 9.0), LatLon(45.5, 10.0))
        val pts = Sketch.project(listOf(line), 200f, 100f, 8f).single()
        // The northern point is higher (smaller y) than the southern one.
        assertTrue(pts[1].y < pts[0].y)
        // East is to the right.
        assertTrue(pts[2].x > pts[1].x)
        pts.forEach {
            assertTrue(it.x in 8f..192f && it.y in 8f..92f, "$it outside the box")
        }
    }

    @Test
    fun sameScaleOnBothAxesAndCentred() {
        // A north-south line at the equator: it fills the height and sits in the middle horizontally.
        val line = listOf(LatLon(0.0, 0.0), LatLon(1.0, 0.0))
        val pts = Sketch.project(listOf(line), 300f, 100f, 0f).single()
        near(pts[0].x, 150f)
        near(pts[1].x, 150f)
        near(pts[1].y, 0f)
        near(pts[0].y, 100f)
    }

    @Test
    fun aSinglePointDoesNotDivideByZero() {
        val pts = Sketch.project(listOf(listOf(LatLon(45.0, 9.0))), 100f, 100f, 10f).single()
        near(pts[0].x, 50f)
        near(pts[0].y, 50f)
    }
}
