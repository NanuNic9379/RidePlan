package io.github.nanunic9379.rideplan.ride

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrivateEndsTest {
    // Una linea verso nord: 0,01° di latitudine ≈ 1,11 km.
    private val line = (0..10).map { LatLon(40.0 + it * 0.01, 16.0) }
    private val home = line.first()
    private val garage = line.last()

    private fun near(expectedKm: Double, actualKm: Double) = assertTrue(abs(expectedKm - actualKm) < 0.01, "$actualKm invece di $expectedKm")

    @Test
    fun tracciaSenzaCasa() {
        val ride = Ride("giro", line, listOf(Stop("Casa", home), Stop("Bar", line[5]), Stop("Garage", garage)), Ride.Source.GPX)
        val out = PrivateEnds.trim(ride)
        near(1.0, distanceKm(home, out.track.first()))
        near(1.0, distanceKm(garage, out.track.last()))
        assertTrue(out.track.none { distanceKm(it, home) < 0.99 || distanceKm(it, garage) < 0.99 })
        assertEquals(listOf(null, "Bar", null), out.stops.map { it.name })
        assertEquals(out.track.first(), out.stops.first().point)
    }

    @Test
    fun soloTappe() {
        val ride = Ride(null, emptyList(), listOf(Stop("Casa", home), Stop("Vicino", line[0].copy(lat = 40.005)), Stop("Garage", garage)), Ride.Source.GOOGLE_LINK)
        val out = PrivateEnds.trim(ride)
        assertTrue(out.straightLines)
        // La tappa a 550 m da casa sparisce.
        assertEquals(2, out.stops.size)
        near(1.0, distanceKm(home, out.stops.first().point))
    }

    @Test
    fun giroCorto() {
        val short = listOf(home, LatLon(40.02, 16.0)) // 2,2 km
        val out = PrivateEnds.trim(Ride(null, short, emptyList(), Ride.Source.GPX))
        // Al massimo un quarto per parte: resta metà giro.
        near(distanceKm(home, short.last()) / 2, distanceKm(out.track.first(), out.track.last()))
        assertNull(out.route)
    }
}
