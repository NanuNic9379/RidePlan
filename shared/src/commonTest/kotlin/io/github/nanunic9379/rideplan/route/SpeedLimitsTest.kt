package io.github.nanunic9379.rideplan.route

import io.github.nanunic9379.rideplan.nav.Fix
import io.github.nanunic9379.rideplan.nav.NavRoute
import io.github.nanunic9379.rideplan.nav.Navigator
import io.github.nanunic9379.rideplan.nav.speedShown
import io.github.nanunic9379.rideplan.profile.Units
import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.Ride
import io.github.nanunic9379.rideplan.ride.Stop
import io.github.nanunic9379.rideplan.ride.distanceKm
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Speed limits along the route (#31): from Valhalla's edges to metres along the line. */
class SpeedLimitsTest {
    private val mPerDegLat = 6371008.8 * PI / 180
    private val mPerDegLon = mPerDegLat * cos(45.0 * PI / 180)

    private fun at(north: Double) = LatLon(45.0 + north / mPerDegLat, 9.0)

    /** 3 km north, a point every 100 m. */
    private val line = (0..30).map { at(it * 100.0) }

    private fun near(expected: Double, actual: Double, tolerance: Double = 2.0) =
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected, got $actual")

    /** A trace_attributes response over [shape]; each edge is (begin, end, speed_limit JSON or null). */
    private fun response(shape: List<LatLon>, vararg edges: Triple<Int, Int, String?>): String {
        val list = edges.joinToString(",") { (b, e, limit) ->
            val l = limit?.let { "\"speed_limit\":$it," } ?: ""
            "{${l}\"begin_shape_index\":$b,\"end_shape_index\":$e}"
        }
        val encoded = Polyline.encode(shape, 6).replace("\\", "\\\\")
        return """{"edges":[$list],"shape":"$encoded","units":"kilometers"}"""
    }

    @Test
    fun edgesBecomeStretchesInMetres() {
        val json = response(line, Triple(0, 10, "50"), Triple(10, 20, null), Triple(20, 30, "90"))
        val stretches = ValhallaSpeedLimits.parse(json, offsetM = 0.0)
        assertEquals(listOf(50, 90), stretches.map { it.kmh })
        near(0.0, stretches[0].fromM)
        near(1000.0, stretches[0].toM)
        near(2000.0, stretches[1].fromM)
        near(3000.0, stretches[1].toM)
    }

    @Test
    fun unknownAndUnlimitedAreNeverGuessed() {
        val json = response(line, Triple(0, 10, null), Triple(10, 20, "\"unlimited\""), Triple(20, 30, "255"), Triple(29, 30, "0"))
        assertTrue(ValhallaSpeedLimits.parse(json, 0.0).isEmpty())
        // No edges at all (or none in the response): nothing.
        assertTrue(ValhallaSpeedLimits.parse("""{"shape":"${Polyline.encode(line, 6).replace("\\", "\\\\")}"}""", 0.0).isEmpty())
    }

    @Test
    fun offsetMovesThePieceAlongTheRoute() {
        val json = response(line.subList(0, 11), Triple(0, 10, "70"))
        val s = ValhallaSpeedLimits.parse(json, offsetM = 5000.0).single()
        near(5000.0, s.fromM)
        near(6000.0, s.toM)
    }

    @Test
    fun lookupFindsTheLimitAtADistance() {
        val limits = SpeedLimits.of(
            listOf(SpeedLimit(1000.0, 2000.0, 90), SpeedLimit(0.0, 1000.0, 50), SpeedLimit(2500.0, 3000.0, 70)),
        )
        assertEquals(50, limits.at(0.0))
        assertEquals(50, limits.at(999.9))
        assertEquals(90, limits.at(1000.0))
        assertEquals(90, limits.at(1999.0))
        assertNull(limits.at(2200.0)) // gap: unknown
        assertEquals(70, limits.at(3000.0)) // the end of the route
        assertNull(limits.at(3000.1))
        assertNull(limits.at(-1.0))
        assertNull(SpeedLimits.NONE.at(10.0))
    }

    @Test
    fun touchingStretchesWithTheSameLimitAreJoined() {
        val limits = SpeedLimits.of(
            listOf(SpeedLimit(0.0, 100.0, 50), SpeedLimit(100.5, 300.0, 50), SpeedLimit(290.0, 400.0, 70), SpeedLimit(350.0, 380.0, 90)),
        )
        assertEquals(listOf(SpeedLimit(0.0, 300.0, 50), SpeedLimit(300.0, 400.0, 70)), limits.stretches)
    }

    @Test
    fun longRoutesAreCutIntoPiecesThatShareTheirEnds() {
        // 250 km with a point every 100 m.
        val along = DoubleArray(2501) { it * 100.0 }
        val pieces = ValhallaSpeedLimits.pieces(along)
        assertEquals(0, pieces.first().first)
        assertEquals(along.lastIndex, pieces.last().last)
        pieces.zipWithNext { a, b -> assertEquals(a.last, b.first) }
        pieces.forEach {
            assertTrue(along[it.last] - along[it.first] <= ValhallaSpeedLimits.PIECE_M)
            assertTrue(it.last - it.first + 1 <= ValhallaSpeedLimits.PIECE_POINTS)
        }
        assertEquals(3, pieces.size)
        // Many close points: the point limit cuts first.
        assertTrue(ValhallaSpeedLimits.pieces(DoubleArray(10_000) { it.toDouble() }).size >= 3)
        assertTrue(ValhallaSpeedLimits.pieces(DoubleArray(1)).isEmpty())
    }

    @Test
    fun computeWalksEachPieceAndPlacesItOnTheWholeRoute() {
        // 150 km north: two pieces. The fake engine says 90 on every edge of the first piece, 50 on the second.
        val long = (0..1500).map { at(it * 100.0) }
        val requests = ArrayList<String>()
        val limits = ValhallaSpeedLimits.compute(long) { req ->
            requests += req
            val shape = Polyline.decode(Json.parseToJsonElement(req).jsonObject.getValue("encoded_polyline").jsonPrimitive.content, 6)
            response(shape, Triple(0, shape.lastIndex, if (requests.size == 1) "90" else "50"))
        }
        assertEquals(2, requests.size)
        val first = Json.parseToJsonElement(requests[0]).jsonObject
        assertEquals("edge_walk", first.getValue("shape_match").jsonPrimitive.content)
        assertTrue(first.getValue("filters").jsonObject.getValue("attributes").jsonArray.any { it.jsonPrimitive.content == "edge.speed_limit" })
        assertEquals(2, limits.stretches.size)
        assertEquals(90, limits.at(50_000.0))
        assertEquals(50, limits.at(140_000.0))
        near(limits.stretches[0].toM, limits.stretches[1].fromM, 1.0)
        assertTrue(limits.stretches[1].fromM in 99_000.0..100_000.0)
    }

    @Test
    fun aFailingPieceIsTriedInHalvesAndOtherwiseLeftOut() {
        var calls = 0
        // The engine fails on anything longer than 1 km.
        val limits = ValhallaSpeedLimits.compute(line) { req ->
            calls++
            val shape = Polyline.decode(Json.parseToJsonElement(req).jsonObject.getValue("encoded_polyline").jsonPrimitive.content, 6)
            val m = shape.zipWithNext { a, b -> distanceKm(a, b) * 1000 }.sum()
            if (m > 1000) error("no path") else response(shape, Triple(0, shape.lastIndex, "30"))
        }
        // 30 segments are too few to split: given up, no limits, no exception.
        assertTrue(limits.isEmpty)
        assertEquals(1, calls)

        val long = (0..80).map { at(it * 25.0) } // 2 km, 81 points
        val split = ValhallaSpeedLimits.compute(long) { req ->
            val shape = Polyline.decode(Json.parseToJsonElement(req).jsonObject.getValue("encoded_polyline").jsonPrimitive.content, 6)
            val m = shape.zipWithNext { a, b -> distanceKm(a, b) * 1000 }.sum()
            if (m > 1001) error("no path") else response(shape, Triple(0, shape.lastIndex, "30"))
        }
        assertEquals(30, split.at(500.0))
        assertEquals(30, split.at(1500.0))
        assertEquals(1, split.stretches.size)
    }

    @Test
    fun navigatorTellsTheLimitOnlyOnTheRoad() {
        val region = RoutingRegion("test", listOf(8.0, 44.0, 10.0, 46.0), "test.tar.gz", 1, 1, "ab", "2026-11-22T21:00:00Z", "3.9.2")
        val limits = SpeedLimits.of(listOf(SpeedLimit(0.0, 1000.0, 50), SpeedLimit(2000.0, 3000.0, 90)))
        val ride = Ride("test", emptyList(), listOf(Stop("A", line.first()), Stop("B", line.last())), Ride.Source.GOOGLE_LINK)
            .routed(ValhallaRoute.Result(line, 3.0, 300.0, speedLimits = limits), region)
        assertEquals(limits, ride.speedLimits)
        assertTrue(ride.unrouted().speedLimits.isEmpty)

        val nav = Navigator(NavRoute(ride))
        fun fix(p: LatLon, s: Long) = Fix(p, s * 1000, speedMps = 20.0, accuracyM = 5.0)
        assertEquals(50, nav.update(fix(at(500.0), 0)).speedLimitKmh)
        assertNull(nav.update(fix(at(1500.0), 1)).speedLimitKmh) // no maxspeed here
        assertEquals(90, nav.update(fix(at(2500.0), 2)).speedLimitKmh)
        // Far from the road: off route, the limit of the road is not shown.
        var off = nav.update(fix(LatLon(45.03, 9.01), 3))
        for (s in 4L..12L) off = nav.update(fix(LatLon(45.03, 9.01), s))
        assertTrue(off.offRoute)
        assertNull(off.speedLimitKmh)
    }

    @Test
    fun speedAndLimitFollowTheUnits() {
        val kmh = speedShown(55 / 3.6, 50, Units.KM)
        assertEquals(55, kmh.speed)
        assertEquals(50, kmh.limit)
        assertTrue(kmh.over)

        // Just at the limit on screen: not over.
        assertFalse(speedShown(50.4 / 3.6, 50, Units.KM).over)

        // 30 mph roads are stored as 48 km/h: shown again as 30.
        val mph = speedShown(29.0 / 2.236936, 48, Units.MILES)
        assertEquals(29, mph.speed)
        assertEquals(30, mph.limit)
        assertFalse(mph.over)

        val unknown = speedShown(40.0, null, Units.KM)
        assertNull(unknown.limit)
        assertFalse(unknown.over)
        assertNull(speedShown(null, 50, Units.KM).speed)
        assertFalse(speedShown(null, 50, Units.KM).over)
    }
}
