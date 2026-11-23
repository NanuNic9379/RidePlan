package io.github.nanunic9379.rideplan.ride

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GpxExportTest {
    private val a = LatLon(40.6894884, 16.9741293)
    private val b = LatLon(40.374076, 16.0802497)
    private val c = LatLon(40.3421591, 15.6580114)

    @Test
    fun coordinate() {
        assertEquals("40.6894884", GpxExport.coord(40.6894884))
        assertEquals("-3.5", GpxExport.coord(-3.5))
        assertEquals("0", GpxExport.coord(0.0))
        assertEquals("-0.0000001", GpxExport.coord(-0.0000001))
        assertEquals("16", GpxExport.coord(16.0))
    }

    @Test
    fun andataERitorno() {
        // Quello che esporto deve rientrare uguale.
        val ride = Ride("Giro <del> sud & \"mare\"", listOf(a, b, c), listOf(Stop("San Basilio", a), Stop(null, b), Stop("Padula", c)), Ride.Source.GOOGLE_LINK)
        val gpx = GpxExport.write(ride, "giro")
        val back = RideImport.parse(gpx)
        assertEquals(ride.name, back.name)
        assertEquals(ride.track, back.track)
        assertEquals(listOf("San Basilio", "2", "Padula"), back.stops.map { it.name })
        assertEquals(ride.stops.map { it.point }, back.stops.map { it.point })
    }

    @Test
    fun soloTappe() {
        val ride = Ride(null, emptyList(), listOf(Stop("A", a), Stop("B", c)), Ride.Source.GOOGLE_LINK)
        val gpx = GpxExport.write(ride, "A → B")
        assertFalse("<trk>" in gpx)
        assertTrue("<rte>" in gpx)
        assertTrue("<metadata><name>A → B</name></metadata>" in gpx)
        val back = RideImport.parse(gpx)
        assertTrue(back.straightLines)
        assertEquals(2, back.stops.size)
    }

    @Test
    fun nomeDelFile() {
        val ride = Ride("Passo: Giau / Falzarego?", emptyList(), emptyList(), Ride.Source.GPX)
        assertEquals("Passo Giau Falzarego.gpx", GpxExport.fileName(ride, "x"))
        assertEquals("San Basilio - Padula.gpx", GpxExport.fileName(ride.copy(name = null), "San Basilio → Padula"))
        assertEquals("RidePlan.gpx", GpxExport.fileName(ride.copy(name = "///"), "x"))
    }
}
