package io.github.nanunic9379.rideplan.route

import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.Ride
import io.github.nanunic9379.rideplan.ride.Stop
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The ban check of imported tracks (#9): two matches, open and legal, compared. */
class TrackBansTest {
    private val mPerDegLat = 6371008.8 * PI / 180

    private fun at(north: Double) = LatLon(45.0 + north / mPerDegLat, 9.0)

    /** 3 km north, a point every 100 m. */
    private val line = (0..30).map { at(it * 100.0) }

    private fun near(expected: Double, actual: Double, tolerance: Double = 2.0) =
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected, got $actual")

    private data class E(val begin: Int, val end: Int, val way: Long, val roadClass: String = "secondary", val name: String? = null)

    /** A trace_attributes response over [shape] with [edges]. */
    private fun response(shape: List<LatLon>, edges: List<E>): String {
        val list = edges.joinToString(",") { e ->
            val names = e.name?.let { "\"names\":[\"$it\"]," } ?: ""
            "{\"way_id\":${e.way},\"road_class\":\"${e.roadClass}\",$names\"begin_shape_index\":${e.begin},\"end_shape_index\":${e.end}}"
        }
        val encoded = Polyline.encode(shape, 6).replace("\\", "\\\\")
        return """{"edges":[$list],"shape":"$encoded","units":"kilometers"}"""
    }

    private fun shapeOf(request: String) =
        Polyline.decode(Json.parseToJsonElement(request).jsonObject.getValue("encoded_polyline").jsonPrimitive.content, 6)

    private fun isOpen(request: String) = "ignore_access" in request

    /** A primary road, then the A1 motorway, then a road closed to motorcycles (way 3). */
    private val openEdges = listOf(
        E(0, 10, 1, "primary", "SP1"),
        E(10, 20, 2, "motorway", "A1"),
        E(20, 30, 3, "residential", "Via Chiusa"),
    )

    /** The legal match goes around way 3 on way 4. */
    private val legalEdges = listOf(E(0, 10, 1), E(10, 20, 2), E(20, 30, 4))

    private fun engine(open: List<E> = openEdges, legal: List<E>? = legalEdges): (String) -> String = { req ->
        val shape = shapeOf(req)
        if (isOpen(req)) response(shape, open) else legal?.let { response(shape, it) } ?: error("no path")
    }

    @Test
    fun theOpenMatchIgnoresAccessAndTheLegalOneDoesNot() {
        val open = Json.parseToJsonElement(ValhallaTrackCheck.openRequest(line)).jsonObject
        assertEquals("map_snap", open.getValue("shape_match").jsonPrimitive.content)
        assertEquals("motorcycle", open.getValue("costing").jsonPrimitive.content)
        val m = open.getValue("costing_options").jsonObject.getValue("motorcycle").jsonObject
        for (k in listOf("ignore_access", "ignore_restrictions", "ignore_oneways", "ignore_closures")) {
            assertEquals("true", m.getValue(k).jsonPrimitive.content, k)
        }
        val attributes = open.getValue("filters").jsonObject.getValue("attributes").jsonArray.map { it.jsonPrimitive.content }
        assertTrue(attributes.containsAll(listOf("edge.way_id", "edge.road_class", "edge.names", "edge.begin_shape_index", "edge.end_shape_index", "shape")))

        val legal = ValhallaTrackCheck.legalRequest(line)
        assertFalse("ignore_" in legal)
        assertFalse("costing_options" in legal)
        assertTrue("edge.way_id" in legal)
        assertEquals(line.size, shapeOf(legal).size)
    }

    @Test
    fun edgesArePlacedInMetresAlongTheTrack() {
        val edges = ValhallaTrackCheck.parseOpen(response(line, openEdges), offsetM = 1_000.0)
        assertEquals(3, edges.size)
        near(1_000.0, edges[0].fromM)
        near(2_000.0, edges[0].toM)
        assertEquals(2L, edges[1].wayId)
        assertEquals("motorway", edges[1].roadClass)
        assertEquals("A1", edges[1].name)
    }

    @Test
    fun brokenEdgesAreSkipped() {
        val json = response(line, listOf(E(5, 5, 9), E(10, 99, 9), E(20, 10, 9), E(0, 3, 7)))
        val edges = ValhallaTrackCheck.parseOpen(json, 0.0)
        assertEquals(listOf(7L), edges.map { it.wayId })
        val noEdges = """{"shape":"${Polyline.encode(line, 6).replace("\\", "\\\\")}"}"""
        assertTrue(ValhallaTrackCheck.parseOpen(noEdges, 0.0).isEmpty())
    }

    @Test
    fun theLegalMatchGivesItsWaysAndItsExtent() {
        val legal = ValhallaTrackCheck.parseLegal(response(line, legalEdges), 500.0)
        assertEquals(setOf(1L, 2L, 4L), legal.ways)
        near(500.0, legal.fromM)
        near(3_500.0, legal.toM)
    }

    @Test
    fun motorwaysCountOnlyUnder150cc() {
        val bans = assertNotNull(ValhallaTrackCheck.check(line, noMotorways = true, engine()))
        near(1_000.0, bans.metres(BanKind.MOTORWAY))
        assertEquals(listOf("A1"), bans.names(BanKind.MOTORWAY))
        val big = assertNotNull(ValhallaTrackCheck.check(line, noMotorways = false, engine()))
        assertEquals(0.0, big.metres(BanKind.MOTORWAY))
    }

    @Test
    fun aWayMissingFromTheLegalMatchIsClosed() {
        val bans = assertNotNull(ValhallaTrackCheck.check(line, noMotorways = false, engine()))
        assertEquals(1, bans.stretches.size)
        val s = bans.stretches.single()
        assertEquals(BanKind.NO_ACCESS, s.kind)
        assertEquals("Via Chiusa", s.name)
        near(2_000.0, s.fromM)
        near(3_000.0, s.toM)
        near(3_000.0, bans.checkedM)
        assertFalse(bans.partial)
        assertFalse(bans.isEmpty)
    }

    @Test
    fun aCleanTrackHasNoBans() {
        val bans = assertNotNull(ValhallaTrackCheck.check(line, noMotorways = true, engine(open = legalEdges)))
        assertTrue(bans.isEmpty)
    }

    @Test
    fun nothingIsClaimedWhereTheLegalMatchFailed() {
        // The legal match fails everywhere (e.g. the whole track is in a closed area): only what
        // the open match proves by itself, the motorway, is said.
        val bans = assertNotNull(ValhallaTrackCheck.check(line, noMotorways = true, engine(legal = null)))
        assertEquals(listOf(BanKind.MOTORWAY), bans.stretches.map { it.kind })
    }

    @Test
    fun noMatchAtAllMeansUnchecked() {
        assertNull(ValhallaTrackCheck.check(line, noMotorways = true) { error("no road data here") })
        assertNull(ValhallaTrackCheck.check(listOf(line[0]), noMotorways = true, engine()))
    }

    @Test
    fun aPartlyMatchedTrackSaysSo() {
        // Only the first kilometre is on a known road.
        val bans = assertNotNull(ValhallaTrackCheck.check(line, noMotorways = true, engine(open = listOf(E(0, 10, 1)), legal = listOf(E(0, 10, 1)))))
        assertTrue(bans.isEmpty)
        assertTrue(bans.partial)
        near(1_000.0, bans.checkedM)
        near(3_000.0, bans.trackM)
    }

    @Test
    fun shortStretchesAreMatchingNoise() {
        val open = listOf(E(0, 10, 1), E(10, 11, 3, name = "Corner"), E(11, 30, 1))
        val bans = assertNotNull(ValhallaTrackCheck.check(line, noMotorways = false, engine(open = open, legal = listOf(E(0, 30, 1)))))
        // 100 m closed: kept. Under 50 m it would be dropped.
        assertEquals(1, bans.stretches.size)
        val tiny = ValhallaTrackCheck.classify(
            listOf(ValhallaTrackCheck.Edge(100.0, 140.0, 3, "residential", null)),
            listOf(ValhallaTrackCheck.Legal(0.0, 1_000.0, setOf(1))),
            noMotorways = false,
        )
        assertTrue(tiny.isEmpty())
    }

    @Test
    fun classifyNeedsALegalMatchOverTheEdge() {
        val edge = ValhallaTrackCheck.Edge(2_000.0, 3_000.0, 3, "residential", null)
        // Legal match covering 0..1 km only: way 3 is outside it, unknown, not closed.
        assertTrue(ValhallaTrackCheck.classify(listOf(edge), listOf(ValhallaTrackCheck.Legal(0.0, 1_000.0, setOf(1))), false).isEmpty())
        assertEquals(1, ValhallaTrackCheck.classify(listOf(edge), listOf(ValhallaTrackCheck.Legal(0.0, 3_000.0, setOf(1))), false).size)
        // An edge without a way id is never called closed.
        val anonymous = edge.copy(wayId = null)
        assertTrue(ValhallaTrackCheck.classify(listOf(anonymous), listOf(ValhallaTrackCheck.Legal(0.0, 3_000.0, setOf(1))), false).isEmpty())
        // A motorway edge is a motorway ban first.
        val motorway = edge.copy(roadClass = "motorway")
        assertEquals(BanKind.MOTORWAY, ValhallaTrackCheck.classify(listOf(motorway), emptyList(), true).single().kind)
    }

    @Test
    fun joinMergesCloseStretchesOfTheSameKind() {
        val joined = ValhallaTrackCheck.join(
            listOf(
                BanStretch(0.0, 100.0, BanKind.NO_ACCESS, null),
                BanStretch(120.0, 200.0, BanKind.NO_ACCESS, "Via"),
                BanStretch(150.0, 400.0, BanKind.MOTORWAY, "A1"),
                BanStretch(500.0, 600.0, BanKind.NO_ACCESS, null),
                BanStretch(700.0, 700.0, BanKind.NO_ACCESS, null),
            ),
        )
        assertEquals(
            listOf(
                BanStretch(0.0, 200.0, BanKind.NO_ACCESS, "Via"),
                BanStretch(150.0, 400.0, BanKind.MOTORWAY, "A1"),
                BanStretch(500.0, 600.0, BanKind.NO_ACCESS, null),
            ),
            joined,
        )
    }

    @Test
    fun coveredCountsOverlapsOnce() {
        assertEquals(0.0, ValhallaTrackCheck.covered(emptyList()))
        assertEquals(300.0, ValhallaTrackCheck.covered(listOf(0.0 to 200.0, 100.0 to 300.0)))
        assertEquals(200.0, ValhallaTrackCheck.covered(listOf(500.0 to 600.0, 0.0 to 100.0, 50.0 to 60.0)))
    }

    @Test
    fun namesAreDistinctAndLimited() {
        val bans = TrackBans(
            listOf("A", "B", "A", "C", "D").mapIndexed { i, n -> BanStretch(i * 100.0, i * 100.0 + 60, BanKind.NO_ACCESS, n) },
            checkedM = 1_000.0,
            trackM = 1_000.0,
        )
        assertEquals(listOf("A", "B", "C"), bans.names(BanKind.NO_ACCESS))
        near(300.0, bans.metres(BanKind.NO_ACCESS), 0.001)
    }

    @Test
    fun onlyTracksDrawnAsTheyAreAreChecked() {
        val stops = listOf(Stop("A", line.first()), Stop("B", line.last()))
        val gpx = Ride("gpx", line, stops, Ride.Source.GPX)
        assertTrue(gpx.unroutedTrack)
        // Stops only: routed on the phone first.
        assertFalse(Ride("plan", emptyList(), stops, Ride.Source.GPX).unroutedTrack)
        // Computed on the phone: the engine kept to the rules.
        val routed = gpx.routed(ValhallaRoute.Result(line, 3.0, 300.0), RoutingData.Region(RoutingRegion("x", listOf(0.0, 0.0, 1.0, 1.0), "x.tar.gz", 1, 1, "a", "d", "3.9.2")))
        assertFalse(routed.unroutedTrack)
    }
}
