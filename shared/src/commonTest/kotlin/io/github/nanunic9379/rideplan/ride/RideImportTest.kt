package io.github.nanunic9379.rideplan.ride

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RideImportTest {
    /** The user's real ride, link already opened (shortened in the tracking part). */
    private val sanBasilioPadula =
        "https://www.google.com/maps/dir/San+Basilio/Matera/Stigliano/Gorgoglione/Agriturismo+Masseria+Pinto/Corleto+Perticara/Padula/" +
            "data=!4m46!4m45!1m5!1m4!1s0x1347a0e379abb20d:0x7115d107218a4616!8m2!3d40.689488399999995!4d16.974129299999998" +
            "!1m5!1m4!1s0x13477ee2482b152b:0x8f6a4ae10da9360!8m2!3d40.6663601!4d16.6044454" +
            "!1m5!1m4!1s0x1338be8ca3ee7d73:0x3789c0cf8cd8b44e!8m2!3d40.4036895!4d16.228282999999998" +
            "!1m5!1m4!1s0x1338c0bc9e53d7bb:0x34199f28c659ac96!8m2!3d40.3936625!4d16.1449143" +
            "!1m5!1m4!1s0x1338c6c35db48e29:0xc73aa3c12495f28a!8m2!3d40.374075999999995!4d16.0802497" +
            "!1m5!1m4!1s0x1338c46cdc5496df:0x94b0dda1ae6f98a5!8m2!3d40.3790556!4d16.040077" +
            "!1m5!1m4!1s0x13392f1a7c6c9def:0xb03b5b06461913b4!8m2!3d40.342159099999996!4d15.6580114!2m1!11b1!3e0?utm_source=mstt_0&g_st=aw"

    @Test
    fun googleLinkWithStops() {
        val ride = RideImport.parse(sanBasilioPadula)
        assertEquals(Ride.Source.GOOGLE_LINK, ride.source)
        assertEquals(
            listOf("San Basilio", "Matera", "Stigliano", "Gorgoglione", "Agriturismo Masseria Pinto", "Corleto Perticara", "Padula"),
            ride.stops.map { it.name },
        )
        assertEquals(40.6663601, ride.stops[1].point.lat)
        assertEquals(15.6580114, ride.stops.last().point.lon)
        assertTrue(ride.straightLines)
        // As the crow flies between the stops: about 140 km.
        assertTrue(ride.lengthKm in 120.0..160.0, "km: ${ride.lengthKm}")
    }

    @Test
    fun sharedTextWithShortLink() {
        val shared = "Motorcycle route\nhttps://maps.app.goo.gl/DsdsDrjsAFw8XhxUA?g_st=aw"
        val url = RideImport.findUrl(shared)
        assertEquals("https://maps.app.goo.gl/DsdsDrjsAFw8XhxUA?g_st=aw", url)
        assertTrue(RideImport.isShortLink(url!!))
        assertTrue(!RideImport.isShortLink(sanBasilioPadula))
    }

    @Test
    fun linkWithWrittenCoordinates() {
        val ride = RideImport.parse("https://www.google.com/maps/dir/45.1,9.2/Pavia/@45,9,10z/data=!4m2!3d45.185!4d9.158")
        assertEquals(2, ride.stops.size)
        assertEquals(null, ride.stops[0].name)
        assertEquals("Pavia", ride.stops[1].name)
    }

    @Test
    fun linkWithCurrentLocationWithoutCoordinates() {
        assertFailsWith<ImportException> {
            RideImport.parse("https://www.google.com/maps/dir/My+Location/Pavia/data=!4m2!3d45.185!4d9.158")
        }
    }

    @Test
    fun linkApi() {
        val ride = RideImport.parse(
            "https://www.google.com/maps/dir/?api=1&origin=45.0,9.0&destination=45.5,9.5&waypoints=45.1,9.1%7C45.2,9.2",
        )
        assertEquals(4, ride.stops.size)
        assertEquals(45.2, ride.stops[2].point.lat)
    }

    @Test
    fun namesWithAccents() {
        assertEquals("Forlì Città", GoogleMapsLink.urlDecode("Forl%C3%AC+Citt%C3%A0"))
    }

    @Test
    fun gpxWithTrackAndPoints() {
        val gpx = """<?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="test" xmlns="http://www.topografix.com/GPX/1/1">
              <metadata><name>Passes ride</name></metadata>
              <wpt lat="46.5" lon="11.8"><name>Pordoi</name></wpt>
              <wpt lat="46.52" lon="11.75"><name>Sella &amp; caffè</name></wpt>
              <trk><name>track</name><trkseg>
                <trkpt lat="46.50" lon="11.80"><ele>2239</ele></trkpt>
                <trkpt lon="11.78" lat="46.51"/>
                <trkpt lat="46.52" lon="11.75"></trkpt>
              </trkseg></trk>
            </gpx>"""
        val ride = RideImport.parse(gpx)
        assertEquals("Passes ride", ride.name)
        assertEquals(3, ride.track.size)
        assertEquals(46.51, ride.track[1].lat)
        assertEquals(listOf("Pordoi", "Sella & caffè"), ride.stops.map { it.name })
        assertTrue(!ride.straightLines)
    }

    @Test
    fun gpxRouteOnly() {
        val gpx = """<gpx><rte><name>Route</name>
            <rtept lat="45.0" lon="9.0"><name>A</name></rtept>
            <rtept lat="45.1" lon="9.1"><name>B</name></rtept>
            </rte></gpx>"""
        val ride = RideImport.parse(gpx)
        assertEquals("Route", ride.name)
        assertEquals(listOf("A", "B"), ride.stops.map { it.name })
        assertTrue(ride.straightLines)
    }

    @Test
    fun emptyGpx() {
        assertFailsWith<ImportException> { RideImport.parse("<gpx><trk><trkseg></trkseg></trk></gpx>") }
    }

    @Test
    fun kmlFromMyMaps() {
        val kml = """<?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2"><Document><name>Sila ride</name>
              <Placemark><name>Start</name><Point><coordinates>16.5,39.3,0</coordinates></Point></Placemark>
              <Placemark><name>Road</name><LineString><tessellate>1</tessellate><coordinates>
                16.5,39.3,0 16.55,39.32,0
                16.6,39.35,0
              </coordinates></LineString></Placemark>
              <Placemark><name>Finish</name><Point><coordinates>16.6,39.35</coordinates></Point></Placemark>
            </Document></kml>"""
        val ride = RideImport.parse(kml)
        assertEquals("Sila ride", ride.name)
        assertEquals(3, ride.track.size)
        assertEquals(39.32, ride.track[1].lat)
        assertEquals(listOf("Start", "Finish"), ride.stops.map { it.name })
    }

    @Test
    fun unknownFormat() {
        assertFailsWith<ImportException> { RideImport.parse("hello") }
        assertFailsWith<ImportException> { RideImport.parse("https://example.com/maps/dir/a/b") }
    }
}
