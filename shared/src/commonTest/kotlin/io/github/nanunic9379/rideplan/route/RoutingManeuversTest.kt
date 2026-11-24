package io.github.nanunic9379.rideplan.route

import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.Ride
import io.github.nanunic9379.rideplan.ride.Stop
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Turn instructions from Valhalla (#5). */
class RoutingManeuversTest {
    private val a = LatLon(45.0, 9.0)
    private val b = LatLon(45.01, 9.0)
    private val c = LatLon(45.02, 9.0)
    private val d = LatLon(45.02, 9.01)
    private val e = LatLon(45.02, 9.02)
    private val stops = listOf(Stop("A", a), Stop("C", c), Stop("E", e))

    @Test
    fun instructionsInTheAppLanguage() {
        val req = Json.parseToJsonElement(ValhallaRoute.request(stops, language = "it-IT")).jsonObject
        assertEquals("instructions", req.getValue("directions_type").jsonPrimitive.content)
        val opts = req.getValue("directions_options").jsonObject
        assertEquals("it-IT", opts.getValue("language").jsonPrimitive.content)
        assertEquals("kilometers", opts.getValue("units").jsonPrimitive.content)
        assertEquals("motorcycle", req.getValue("costing").jsonPrimitive.content)
    }

    @Test
    fun noInstructionsWhenNotAsked() {
        val req = Json.parseToJsonElement(ValhallaRoute.request(stops)).jsonObject
        assertEquals("none", req.getValue("directions_type").jsonPrimitive.content)
        assertFalse("directions_options" in req)
    }

    @Test
    fun headingOnlyOnTheFirstStop() {
        val req = Json.parseToJsonElement(ValhallaRoute.request(stops, language = "en-US", startHeading = -90.0)).jsonObject
        val locations = req.getValue("locations").jsonArray.map { it.jsonObject }
        assertEquals(270, locations[0].getValue("heading").jsonPrimitive.int)
        assertTrue("heading_tolerance" in locations[0])
        assertFalse("heading" in locations[1])
        assertFalse("heading" in locations[2])
    }

    @Test
    fun languageOfTheApp() {
        assertEquals("it-IT", ValhallaRoute.language("it"))
        assertEquals("de-DE", ValhallaRoute.language("de-CH"))
        assertEquals("fr-FR", ValhallaRoute.language("fr_BE"))
        assertEquals("es-ES", ValhallaRoute.language("ES"))
        assertEquals("en-US", ValhallaRoute.language("en-GB"))
        assertEquals("en-US", ValhallaRoute.language("pt-BR"))
    }

    private val twoLegs = """
        {"trip":{"summary":{"length":4.5,"time":420.0},"legs":[
          {"shape":"${Polyline.encode(listOf(a, b, c), 6)}","maneuvers":[
            {"type":1,"instruction":"Head north.","verbal_post_transition_instruction":"Continue for 2 kilometers.",
             "street_names":["Via Roma"],"length":2.2,"time":200.0,"begin_shape_index":0,"end_shape_index":2},
            {"type":5,"instruction":"You have arrived at C.","length":0.0,"time":0.0,"begin_shape_index":2,"end_shape_index":2}
          ]},
          {"shape":"${Polyline.encode(listOf(c, d, e), 6)}","maneuvers":[
            {"type":2,"instruction":"Head east.","length":0.0,"time":0.0,"begin_shape_index":0,"end_shape_index":0},
            {"type":26,"instruction":"Enter the roundabout and take the 2nd exit.","roundabout_exit_count":2,
             "verbal_transition_alert_instruction":"Enter the roundabout.","length":1.5,"time":120.0,
             "begin_shape_index":1,"end_shape_index":2},
            {"type":4,"instruction":"You have arrived.","begin_shape_index":2,"end_shape_index":2}
          ]}
        ]}}
    """.trimIndent()

    @Test
    fun maneuversOnTheJoinedLine() {
        val r = ValhallaRoute.parse(twoLegs)
        assertEquals(listOf(a, b, c, d, e), r.shape)
        assertEquals(listOf(1, 5, 2, 26, 4), r.maneuvers.map { it.type })
        // Indices of the second leg move past the first: its point 1 is point 3 of the ride.
        assertEquals(listOf(0, 2, 2, 3, 4), r.maneuvers.map { it.beginIndex })
        val first = r.maneuvers[0]
        assertEquals("Head north.", first.instruction)
        assertEquals("Continue for 2 kilometers.", first.verbalPost)
        assertEquals(listOf("Via Roma"), first.streets)
        assertEquals(2.2, first.km)
        assertEquals(200.0, first.seconds)
        val roundabout = r.maneuvers[3]
        assertEquals(TurnSign.ROUNDABOUT, roundabout.sign)
        assertEquals(2, roundabout.roundaboutExit)
        assertEquals("Enter the roundabout.", roundabout.verbalAlert)
        assertNull(roundabout.verbalPre)
        assertTrue(r.maneuvers[1].isArrival && r.maneuvers[4].isArrival)
    }

    @Test
    fun withoutManeuvers() {
        val json = """{"trip":{"summary":{"length":1.0,"time":60.0},"legs":[{"shape":"${Polyline.encode(listOf(a, b), 6)}"}]}}"""
        assertTrue(ValhallaRoute.parse(json).maneuvers.isEmpty())
    }

    @Test
    fun routedRideKeepsTheManeuversUntilUnrouted() {
        val region = RoutingRegion("test", listOf(8.0, 44.0, 10.0, 46.0), "test.tar.gz", 1, 1, "ab", "2026-11-22T21:00:00Z", "3.9.2")
        val ride = Ride("test", emptyList(), stops, Ride.Source.GOOGLE_LINK)
        val done = ride.routed(ValhallaRoute.parse(twoLegs), region)
        assertEquals(5, done.maneuvers.size)
        val again = done.unrouted()
        assertTrue(again.maneuvers.isEmpty())
        assertTrue(again.straightLines)
        // Old rides and GPX tracks have none.
        assertTrue(ride.maneuvers.isEmpty())
    }
}
