package io.github.nanunic9379.rideplan.route

import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.Ride
import io.github.nanunic9379.rideplan.ride.Stop
import io.github.nanunic9379.rideplan.ride.distanceKm
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Unpaved stretches along the route (#9): from Valhalla's edges to metres and map lines. */
class RoadSurfaceTest {
    private val mPerDegLat = 6371008.8 * PI / 180

    private fun at(north: Double) = LatLon(45.0 + north / mPerDegLat, 9.0)

    /** 3 km north, a point every 100 m. */
    private val line = (0..30).map { at(it * 100.0) }

    private fun near(expected: Double, actual: Double, tolerance: Double = 2.0) =
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected, got $actual")

    /** A trace_attributes response; each edge is (begin, end, extra JSON fields). */
    private fun response(shape: List<LatLon>, vararg edges: Triple<Int, Int, String>): String {
        val list = edges.joinToString(",") { (b, e, extra) ->
            val x = if (extra.isEmpty()) "" else "$extra,"
            "{$x\"begin_shape_index\":$b,\"end_shape_index\":$e}"
        }
        val encoded = Polyline.encode(shape, 6).replace("\\", "\\\\")
        return """{"edges":[$list],"shape":"$encoded","units":"kilometers"}"""
    }

    @Test
    fun surfaceClassesBecomeGravelOrDirt() {
        assertEquals(Rough.GRAVEL, ValhallaRoadSurface.classify("gravel", true))
        assertEquals(Rough.GRAVEL, ValhallaRoadSurface.classify("compacted", true))
        assertEquals(Rough.DIRT, ValhallaRoadSurface.classify("dirt", true))
        assertEquals(Rough.DIRT, ValhallaRoadSurface.classify("path", null))
        assertNull(ValhallaRoadSurface.classify("paved_rough", false))
        assertNull(ValhallaRoadSurface.classify("paved", null))
        // Unknown class: only the engine's flag decides.
        assertEquals(Rough.GRAVEL, ValhallaRoadSurface.classify("something_new", true))
        assertNull(ValhallaRoadSurface.classify(null, null))
    }

    @Test
    fun edgesBecomeStretchesInMetres() {
        val json = response(
            line,
            Triple(0, 10, "\"surface\":\"paved_smooth\",\"unpaved\":false,\"speed_limit\":50"),
            Triple(10, 15, "\"surface\":\"gravel\",\"unpaved\":true"),
            Triple(15, 20, "\"surface\":\"dirt\",\"unpaved\":true"),
            Triple(20, 30, ""),
        )
        val s = ValhallaRoadSurface.parse(json, offsetM = 1000.0)
        assertEquals(listOf(Rough.GRAVEL, Rough.DIRT), s.map { it.kind })
        near(2000.0, s[0].fromM)
        near(2500.0, s[0].toM)
        near(3000.0, s[1].toM)
    }

    @Test
    fun totalsAndLongestStretch() {
        val surfaces = RoadSurfaces.of(
            listOf(
                RoughStretch(1000.0, 1500.0, Rough.GRAVEL),
                RoughStretch(1500.0, 2500.0, Rough.DIRT),
                RoughStretch(2500.5, 2600.0, Rough.DIRT),
                RoughStretch(5000.0, 5200.0, Rough.GRAVEL),
                RoughStretch(5100.0, 5150.0, Rough.DIRT), // inside the previous one: dropped
            ),
        )
        assertEquals(3, surfaces.stretches.size)
        near(1800.0, surfaces.unpavedM, 0.01)
        near(1100.0, surfaces.dirtM, 0.01)
        // Gravel then dirt with no gap is one stretch for the rider.
        near(1600.0, surfaces.longestM, 0.01)
        assertTrue(surfaces.worthWarning)
        assertEquals(Rough.DIRT, surfaces.at(2000.0)?.kind)
        assertNull(surfaces.at(3000.0))
    }

    @Test
    fun aFewMetresOfGravelAreNotWorthAWarning() {
        assertFalse(RoadSurfaces.of(listOf(RoughStretch(0.0, 120.0, Rough.GRAVEL))).worthWarning)
        assertFalse(RoadSurfaces.NONE.worthWarning)
        assertEquals(0.0, RoadSurfaces.NONE.longestM)
    }

    @Test
    fun linesAreCutWhereTheStretchesBeginAndEnd() {
        val surfaces = RoadSurfaces.of(listOf(RoughStretch(250.0, 1050.0, Rough.DIRT)))
        val (kind, points) = surfaces.lines(line).single()
        assertEquals(Rough.DIRT, kind)
        near(250.0, distanceKm(line[0], points.first()) * 1000)
        near(1050.0, distanceKm(line[0], points.last()) * 1000)
        // 300 m to 1000 m: the 8 points in between are kept.
        assertEquals(10, points.size)
        val json = roughJson(surfaces.lines(line))
        val feature = Json.parseToJsonElement(json).jsonObject.getValue("features").jsonArray.single().jsonObject
        assertEquals("dirt", feature.getValue("properties").jsonObject.getValue("kind").jsonPrimitive.content)
        assertTrue(RoadSurfaces.NONE.lines(line).isEmpty())
    }

    @Test
    fun kmForTheRider() {
        assertEquals("0.1", kmText(20.0))
        assertEquals("0.4", kmText(380.0))
        assertEquals("3.2", kmText(3240.0))
        assertEquals("12", kmText(12_400.0))
    }

    @Test
    fun oneWalkGivesSpeedLimitsAndSurfaces() {
        val requests = ArrayList<String>()
        val roads = ValhallaRoadAttributes.compute(line) { req ->
            requests += req
            val shape = Polyline.decode(Json.parseToJsonElement(req).jsonObject.getValue("encoded_polyline").jsonPrimitive.content, 6)
            response(
                shape,
                Triple(0, 10, "\"surface\":\"paved\",\"speed_limit\":70"),
                Triple(10, 30, "\"surface\":\"compacted\",\"unpaved\":true"),
            )
        }
        assertEquals(1, requests.size)
        val attributes = Json.parseToJsonElement(requests[0]).jsonObject.getValue("filters").jsonObject.getValue("attributes").jsonArray
            .map { it.jsonPrimitive.content }
        assertTrue("edge.surface" in attributes && "edge.unpaved" in attributes && "edge.speed_limit" in attributes)
        assertEquals(70, roads.speedLimits.at(500.0))
        near(2000.0, roads.surfaces.unpavedM)
        assertEquals(Rough.GRAVEL, roads.surfaces.stretches.single().kind)
        // An engine that always fails: nothing, no exception.
        assertEquals(RoadAttributes.NONE, ValhallaRoadAttributes.compute(line) { error("down") })
    }

    @Test
    fun surfacesFollowTheRideAndGoWhenUnrouted() {
        val region = RoutingRegion("r", listOf(8.0, 44.0, 10.0, 46.0), "r.tar.gz", 1, 3, "x", "2026-01-01", "3.9.2")
        val surfaces = RoadSurfaces.of(listOf(RoughStretch(0.0, 900.0, Rough.GRAVEL)))
        val ride = Ride("r", emptyList(), listOf(Stop(null, line.first()), Stop(null, line.last())), Ride.Source.PLANNED)
            .routed(ValhallaRoute.Result(line, 3.0, 300.0, surfaces = surfaces), region)
        assertEquals(surfaces, ride.surfaces)
        assertTrue(ride.unrouted().surfaces.isEmpty)
    }
}
