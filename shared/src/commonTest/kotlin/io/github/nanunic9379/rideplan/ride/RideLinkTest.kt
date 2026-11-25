package io.github.nanunic9379.rideplan.ride

import io.github.nanunic9379.rideplan.route.Curves
import io.github.nanunic9379.rideplan.route.Polyline
import io.github.nanunic9379.rideplan.route.RouteOptions
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RideLinkTest {
    private val stops = listOf(
        Stop("San Basilio", LatLon(40.68949, 16.97413)),
        Stop(null, LatLon(40.37408, 16.08025)),
        Stop("Padula | Certosa", LatLon(40.34216, 15.65801)),
    )

    @Test
    fun polylineRoundTrip() {
        assertEquals("_p~iF~ps|U_ulLnnqC_mqNvxq`@", Polyline.encode(listOf(LatLon(38.5, -120.2), LatLon(40.7, -120.95), LatLon(43.252, -126.453)), 5))
    }

    @Test
    fun stops() {
        val link = RideLink.encode(Ride("Saturday ride", emptyList(), stops, Ride.Source.GOOGLE_LINK), RouteOptions(Curves.MORE, avoidTolls = true))
        assertTrue(link.startsWith(RideLink.PREFIX))
        // No characters a messaging app could break.
        assertTrue(link.substringAfter('#').all { it.isLetterOrDigit() || it == '-' || it == '_' }, link)
        val back = RideLink.decode(link)
        assertEquals("Saturday ride", back.ride.name)
        assertEquals(RouteOptions(Curves.MORE, avoidTolls = true), back.options)
        assertEquals(back.options, back.ride.sharedOptions)
        assertEquals(stops, back.ride.stops)
        assertTrue(back.ride.straightLines)
        assertEquals(Ride.Source.RIDEPLAN_LINK, back.ride.source)
        // From the shared text, as it arrives from WhatsApp.
        assertEquals(stops, RideImport.parse("Check out this ride: $link").stops)
        // From the web page button.
        assertEquals(stops, RideImport.parse("rideplan://r?d=" + link.substringAfter("#")).stops)
    }

    /** The share dialog says how far the QR track can stray (#92). */
    @Test
    fun trackDeviation() {
        val wavy = (0 until 2000).map { LatLon(40.0 + it * 0.0005, 16.0 + 0.01 * sin(it / 30.0)) }
        val ride = Ride(null, wavy, emptyList(), Ride.Source.GPX)
        val m = RideLink.trackDeviationM(ride)
        assertTrue(m > 1.0 && m < 200.0, "$m m")
        val simple = RideLink.simplify(wavy)
        assertTrue(simple.size <= RideLink.MAX_TRACK_POINTS)
        // A short track goes in whole; a computed route goes as its stops.
        assertEquals(0.0, RideLink.trackDeviationM(Ride(null, wavy.take(100), emptyList(), Ride.Source.GPX)))
        assertEquals(0.0, RideLink.trackDeviationM(Ride(null, emptyList(), stops, Ride.Source.GOOGLE_LINK)))
    }

    @Test
    fun simplifiedTrack() {
        // 2000 points on a wavy line: the QR must stay readable.
        val track = (0 until 2000).map { LatLon(40.0 + it * 0.0005, 16.0 + 0.01 * sin(it / 30.0)) }
        val link = RideLink.encode(Ride(null, track, emptyList(), Ride.Source.GPX))
        val back = RideLink.decode(link).ride
        assertTrue(back.track.size in 2..RideLink.MAX_TRACK_POINTS, "${back.track.size} points")
        // 5 decimals: about one meter.
        assertTrue(distanceKm(track.first(), back.track.first()) < 0.002)
        assertTrue(distanceKm(track.last(), back.track.last()) < 0.002)
        assertTrue(abs(back.lengthKm - Ride(null, track, emptyList(), Ride.Source.GPX).lengthKm) < 2.0)
        assertTrue(link.length < 1500, "${link.length} characters")
        assertNull(back.name)
    }

    @Test
    fun brokenLink() {
        assertFailsWith<ImportException> { RideLink.decode(RideLink.PREFIX + "abc") }
        val cut = RideLink.encode(Ride(null, emptyList(), stops, Ride.Source.GPX)).dropLast(12)
        assertFailsWith<ImportException> { RideLink.decode(cut) }
    }

    /** #50: each stop's kind travels in the link; without preferences the field stays empty. */
    @Test
    fun stopKinds() {
        val kinds = listOf(stops[0], stops[1].copy(kind = Stop.Kind.VIA), stops[2].copy(kind = Stop.Kind.PASS))
        val back = RideLink.decode(RideLink.encode(Ride(null, emptyList(), kinds, Ride.Source.GOOGLE_LINK)))
        assertEquals(listOf(Stop.Kind.STOP, Stop.Kind.VIA, Stop.Kind.PASS), back.ride.stops.map { it.kind })
        assertNull(back.options)
        assertNull(back.ride.sharedOptions)
    }

    /** A link made before #50 (six fields, curves written by hand) still opens. */
    @Test
    fun linkFromBefore50() {
        val old = listOf("1", "Old", "MAX", Polyline.encode(stops.map { it.point }, 5), "A\u001fB\u001fC", "").joinToString("\n")
        val link = RideLink.PREFIX + kotlin.io.encoding.Base64.UrlSafe.withPadding(kotlin.io.encoding.Base64.PaddingOption.ABSENT).encode(old.encodeToByteArray())
        val back = RideLink.decode(link)
        assertEquals(RouteOptions(Curves.MAX), back.options)
        assertTrue(back.ride.stops.all { it.kind == Stop.Kind.STOP })
    }
}
