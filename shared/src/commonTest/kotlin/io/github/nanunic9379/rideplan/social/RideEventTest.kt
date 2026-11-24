package io.github.nanunic9379.rideplan.social

import io.github.nanunic9379.rideplan.ride.ImportError
import io.github.nanunic9379.rideplan.ride.ImportException
import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.PrivateZone
import io.github.nanunic9379.rideplan.ride.Ride
import io.github.nanunic9379.rideplan.ride.SharePreview
import io.github.nanunic9379.rideplan.ride.Stop
import io.github.nanunic9379.rideplan.route.Curves
import io.github.nanunic9379.rideplan.route.RouteOptions
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RideEventTest {
    private val home = LatLon(45.0, 9.0)
    private val stops = listOf(
        Stop("Home", home),
        Stop("Bar Passo", LatLon(45.3, 9.4)),
        Stop("Lake", LatLon(45.6, 9.2)),
    )
    private val ride = Ride("Sunday \"loop\"", emptyList(), stops, Ride.Source.PLANNED)

    private fun shared(r: Ride = ride, zones: List<PrivateZone> = emptyList()) = SharePreview.of(r, zones, trimEnds = false)

    @Test
    fun geohashKnownValues() {
        assertEquals("u4pruydqqvj", Geohash.encode(LatLon(57.64911, 10.40744), 11))
        assertEquals("ezs42", Geohash.encode(LatLon(42.605, -5.603), 5))
        assertEquals("u0n2", Geohash.encode(LatLon(45.0, 9.0), 4))
    }

    @Test
    fun draftHasTheAgreedShape() {
        val d = RideEvent.draft(shared(), "ride-1", RouteOptions(Curves.MORE), "Coffee at the pass")
        assertEquals(RideEvent.KIND, d.kind)
        assertEquals("ride-1", d.tag("d"))
        assertEquals("Sunday \"loop\"", d.tag("title"))
        assertEquals("Coffee at the pass", d.tag("summary"))
        assertEquals(listOf("motorcycle", "rideplan"), d.tagValues("t"))
        assertTrue(d.content.startsWith("https://nanunic9379.github.io/RidePlan/r/#"))
        val g = d.tagValues("g")
        assertTrue("u0n" in g && "u0n2" in g, g.toString())
        assertTrue(g.all { it.length in 3..4 })
        // No time of the ride anywhere: only the publishing time the signer adds.
        assertNull(d.tag("start"))
        assertNull(d.tag("published_at"))
    }

    @Test
    fun roundTripThroughJson() {
        val d = RideEvent.draft(shared(), "ride-1", RouteOptions(Curves.MORE, avoidTolls = true))
        val back = RideEvent.parse(d.toUnsignedJson(1_700_000_000, "ab".repeat(32)))
        assertEquals(stops.map { it.point }, back.ride.stops.map { it.point })
        assertEquals("Sunday \"loop\"", back.ride.name)
        assertEquals(RouteOptions(Curves.MORE, avoidTolls = true), back.options)
    }

    @Test
    fun privateZonesAreAppliedBeforeEncoding() {
        val zone = PrivateZone("h", "Home", home.lat, home.lon, 1000)
        val d = RideEvent.draft(shared(zones = listOf(zone)), "ride-1", null)
        val back = RideEvent.parse(d.toUnsignedJson(0))
        assertFalse(back.ride.line.any { zone.contains(it) }, "home leaked")
        assertFalse(back.ride.stops.any { it.name == "Home" })
    }

    @Test
    fun rideInsideZonesCannotBeDrafted() {
        val zone = PrivateZone("h", "Home", home.lat, home.lon, 1000)
        val tiny = Ride(null, emptyList(), listOf(Stop(null, home), Stop(null, LatLon(45.001, 9.001))), Ride.Source.PLANNED)
        assertFailsWith<IllegalArgumentException> { RideEvent.draft(shared(tiny, listOf(zone)), "x", null) }
    }

    @Test
    fun longTrackFitsUnderRelayLimit() {
        // A wiggly 5000-point track across northern Italy (~600 km).
        val track = (0 until 5000).map { i -> LatLon(44.5 + 0.3 * sin(i / 40.0), 7.0 + i * 0.0012) }
        val d = RideEvent.draft(shared(Ride("Long", track, emptyList(), Ride.Source.GPX)), "long", null)
        assertTrue(d.toUnsignedJson(0).encodeToByteArray().size < RideEvent.MAX_BYTES)
        // Too many fine cells: only the coarse ones remain, still searchable by area.
        val g = d.tagValues("g")
        assertTrue(g.isNotEmpty() && g.size <= RideEvent.MAX_COARSE_CELLS + RideEvent.MAX_FINE_CELLS)
        assertTrue(RideEvent.parse(d.toUnsignedJson(0)).ride.track.size in 2..150)
    }

    @Test
    fun canonicalSerializationFollowsNip01() {
        val d = NostrDraft(1, listOf(listOf("t", "a\"b")), "line1\nline2\t\\ è ☕")
        assertEquals(
            "[0,\"${"0".repeat(64)}\",123,1,[[\"t\",\"a\\\"b\"]],\"line1\\nline2\\t\\\\ è ☕\"]",
            d.serializeForId("0".repeat(64), 123),
        )
        assertEquals(
            "{\"created_at\":123,\"kind\":1,\"tags\":[[\"t\",\"a\\\"b\"]],\"content\":\"line1\\nline2\\t\\\\ è ☕\"}",
            d.toUnsignedJson(123),
        )
    }

    @Test
    fun controlCharactersAreCleaned() {
        val d = RideEvent.draft(shared(ride.copy(name = "Bad\u0001name")), "x", null, "a\u0000b")
        assertEquals("Bad name", d.tag("title"))
        assertEquals("a b", d.tag("summary"))
    }

    @Test
    fun rejectsOtherEvents() {
        val d = RideEvent.draft(shared(), "x", null)
        val wrongKind = d.copy(kind = 1).toUnsignedJson(0)
        assertEquals(ImportError.LINK_BROKEN, assertFailsWith<ImportException> { RideEvent.parse(wrongKind) }.error)
        assertEquals(ImportError.LINK_BROKEN, assertFailsWith<ImportException> { RideEvent.parse("not json") }.error)
        val huge = d.copy(content = d.content + "x".repeat(RideEvent.MAX_BYTES)).toUnsignedJson(0)
        assertEquals(ImportError.FILE_TOO_BIG, assertFailsWith<ImportException> { RideEvent.parse(huge) }.error)
    }
}
