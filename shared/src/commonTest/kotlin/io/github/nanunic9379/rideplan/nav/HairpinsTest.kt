package io.github.nanunic9379.rideplan.nav

import io.github.nanunic9379.rideplan.ride.LatLon
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HairpinsTest {
    @Test
    fun detectsATightUShapedBendAndIgnoresAStraightRoad() {
        val hairpin = Hairpins.find(uTurn(radiusM = 20.0))
        assertEquals(1, hairpin.size)
        assertTrue(hairpin.single().radiusM <= 25.0)
        assertTrue(Hairpins.find(listOf(point(-100.0, 0.0), point(0.0, 0.0), point(100.0, 0.0))).isEmpty())
    }

    @Test
    fun leavesWideBendsOut() {
        assertTrue(Hairpins.find(uTurn(radiusM = 40.0)).isEmpty())
    }

    @Test
    fun groupsSeveralHairpinsAcrossFiveKilometresIntoOneWarning() {
        val p = LatLon(1.0, 2.0)
        val warnings = Hairpins.group(listOf(Hairpin(500.0, 20.0, p), Hairpin(2100.0, 18.0, p), Hairpin(4300.0, 22.0, p)))
        assertEquals(listOf(HairpinWarning(500.0, 4300.0, 3, p)), warnings)
    }

    @Test
    fun keepsWidelySeparatedHairpinsSeparate() {
        val p = LatLon(1.0, 2.0)
        val warnings = Hairpins.group(listOf(Hairpin(500.0, 20.0, p), Hairpin(6001.0, 18.0, p)))
        assertEquals(listOf(HairpinWarning(500.0, 500.0, 1, p), HairpinWarning(6001.0, 6001.0, 1, p)), warnings)
    }

    @Test
    fun warningLeadDistanceScalesWithSpeedAndStaysBounded() {
        assertEquals(120.0, Hairpins.warningDistanceM(1.0))
        assertEquals(300.0, Hairpins.warningDistanceM(20.0))
        assertEquals(450.0, Hairpins.warningDistanceM(40.0))
    }

    private fun uTurn(radiusM: Double): List<LatLon> {
        val points = mutableListOf(point(-100.0, 0.0), point(0.0, 0.0))
        val steps = 24
        for (i in 1..steps) {
            val angle = -PI / 2 + PI * i / steps
            points += point(radiusM * cos(angle), radiusM + radiusM * sin(angle))
        }
        points += point(-100.0, 2 * radiusM)
        return points
    }

    private fun point(xM: Double, yM: Double) = LatLon(yM / METRES_PER_DEGREE, xM / METRES_PER_DEGREE)

    private companion object {
        const val METRES_PER_DEGREE = 111_320.0
    }
}
