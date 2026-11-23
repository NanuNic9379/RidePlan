package io.github.nanunic9379.rideplan.route

import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.Ride
import io.github.nanunic9379.rideplan.ride.Stop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoutingTest {
    private fun region(id: String, vararg bbox: Double) =
        RoutingRegion(id, bbox.toList(), "$id.tar.gz", 100, 300, "ab", "2026-11-22T21:00:00Z", "3.9.2")

    private val south = region("it-sud", 13.0, 39.7, 18.6, 42.3)
    private val calabria = region("it-calabria-sicilia", 12.3, 36.6, 17.3, 40.2)
    private val center = region("it-centro", 9.7, 41.3, 14.3, 44.3)

    private val sanBasilio = LatLon(40.6894884, 16.9741293)
    private val padula = LatLon(40.3421591, 15.6580114)

    @Test
    fun googlePolyline() {
        val p = Polyline.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@", 5)
        assertEquals(listOf(LatLon(38.5, -120.2), LatLon(40.7, -120.95), LatLon(43.252, -126.453)), p)
    }

    @Test
    fun polylineSixDigits() {
        // Same points with precision 6, as Valhalla sends them.
        val p = Polyline.decode("_izlhA~rlgdF_{geC~ywl@_kwzCn`{nI", 6)
        assertEquals(LatLon(38.5, -120.2), p[0])
        assertEquals(LatLon(43.252, -126.453), p[2])
    }

    @Test
    fun smallestAreaContainingEverything() {
        assertEquals(south, RoutingRegion.bestFor(listOf(center, south, calabria), listOf(sanBasilio, padula)))
        // Cosenza (39.3) is only in Calabria-Sicily.
        val cosenza = LatLon(39.30, 16.25)
        assertEquals(calabria, RoutingRegion.bestFor(listOf(south, calabria), listOf(cosenza)))
        // From Rome to Cosenza no single area is enough.
        assertNull(RoutingRegion.bestFor(listOf(center, south, calabria), listOf(LatLon(41.9, 12.5), cosenza)))
    }

    @Test
    fun manifest() {
        val json = """
            {"format":1,"regions":[{"id":"it-sud","bbox":[13.0,39.7,18.6,42.3],"file":"it-sud.tar.gz",
            "bytes":410000000,"tarBytes":900000000,"sha256":"ff","osmDate":"2026-11-22T21:21:22Z","valhalla":"3.9.2"}]}
        """.trimIndent()
        val r = RoutingRegion.parseManifest(json).single()
        assertEquals("it-sud", r.id)
        assertEquals(900_000_000, r.tarBytes)
        assertEquals(r, RoutingRegion.fromJson(r.toJson()))
        assertFailsWith<IllegalArgumentException> { RoutingRegion.parseManifest("""{"format":2,"regions":[]}""") }
    }

    @Test
    fun motorcycleRequest() {
        val req = ValhallaRoute.request(
            listOf(Stop("San Basilio", sanBasilio), Stop(null, LatLon(40.5, 16.5), Stop.Kind.VIA), Stop("Masseria", LatLon(40.37, 16.08)), Stop("Padula", padula)),
        )
        assertTrue(""""costing":"motorcycle"""" in req)
        assertEquals(3, Regex(""""type":"break"""").findAll(req).count())
        assertEquals(1, Regex(""""type":"through"""").findAll(req).count())
    }

    @Test
    fun townsAsPassThrough() {
        // The Google link of the test ride (#29): the towns in between are wide pass-through points.
        val ride = Ride(null, emptyList(), Stop.guessKinds(
            listOf(
                Stop("San Basilio", sanBasilio), Stop("Matera, MT", LatLon(40.66, 16.6)),
                Stop("Bar Centrale, Via Roma 12, Stigliano", LatLon(40.4, 16.23)), Stop(null, LatLon(40.38, 16.1)),
                Stop("Padula", padula),
            ),
            towns = true,
        ), Ride.Source.GOOGLE_LINK)
        assertEquals(listOf(Stop.Kind.STOP, Stop.Kind.PASS, Stop.Kind.STOP, Stop.Kind.VIA, Stop.Kind.STOP), ride.stops.map { it.kind })
        val req = ValhallaRoute.request(ride.stops)
        assertEquals(1, Regex(""""radius":2500""").findAll(req).count())
        assertEquals(2, Regex(""""type":"through"""").findAll(req).count())
        // One tap: Matera becomes a stop, the route must be recalculated.
        val routed = ride.routed(ValhallaRoute.Result(listOf(sanBasilio, padula), 200.0, 9000.0), south)
        val changed = routed.toggledStop(1)
        assertEquals(Stop.Kind.STOP, changed.stops[1].kind)
        assertNull(changed.route)
        assertEquals(changed, changed.toggledStop(3)) // dragged points do not change
        assertEquals(ride.stops[0], ride.toggledStop(0).stops[0].copy(kind = Stop.Kind.STOP))
    }

    @Test
    fun whatIsATown() {
        listOf("Matera", "75100 Matera MT", "Stigliano, Provincia di Matera", "Corleto Perticara, PZ").forEach {
            assertTrue(Stop.looksLikeTown(it), it)
        }
        listOf("Via Roma 12, Matera", "Bar Centrale, Matera", "Agriturismo Il Falco, 85050 Corleto Perticara PZ", "Piazza Vittorio Veneto, 1, Matera", "Passo del Tonale").forEach {
            assertFalse(Stop.looksLikeTown(it), it)
        }
    }

    @Test
    fun preferences() {
        val stops = listOf(Stop("A", sanBasilio), Stop("B", padula))
        val base = ValhallaRoute.request(stops)
        assertTrue(""""use_trails":0.0""" in base)
        assertFalse("use_curvature" in base)
        assertFalse("use_highways" in base)
        val all = ValhallaRoute.request(stops, RouteOptions(curves = Curves.MORE, avoidHighways = true, avoidTolls = true))
        assertTrue(""""use_curvature":0.75""" in all)
        assertTrue(""""use_highways":0.0""" in all)
        assertTrue(""""use_tolls":0.0""" in all)
    }

    @Test
    fun recalculation() {
        val ride = Ride("test", emptyList(), listOf(Stop("A", sanBasilio), Stop("B", padula)), Ride.Source.GOOGLE_LINK)
        val done = ride.routed(ValhallaRoute.Result(listOf(sanBasilio, padula), 200.0, 9000.0), south, RouteOptions(curves = Curves.MAX))
        assertTrue(done.routable)
        assertEquals(RouteOptions(curves = Curves.MAX), done.route?.options)
        assertEquals(ride, done.unrouted())
        // A GPX track is not recalculated.
        assertFalse(Ride("gpx", listOf(sanBasilio, padula), listOf(Stop("A", sanBasilio), Stop("B", padula)), Ride.Source.GPX).routable)
    }

    @Test
    fun howMuchLonger() {
        val ride = Ride("test", emptyList(), listOf(Stop("A", sanBasilio), Stop("B", padula)), Ride.Source.GOOGLE_LINK)
        // Numbers from test #10: 231 km normal, 287 km with curves at maximum.
        val curvy = ValhallaRoute.Result(listOf(sanBasilio, padula), 287.0, 18780.0)
        val normal = ValhallaRoute.Result(listOf(sanBasilio, padula), 231.0, 13440.0)
        val info = ride.routed(curvy, south, RouteOptions(curves = Curves.MAX), normal).route!!
        assertEquals(24, kotlin.math.round(info.extraShare!! * 100).toInt())
        assertTrue(info.extraShare!! >= RouteInfo.LONG_DETOUR)
        assertNull(ride.routed(curvy, south).route!!.extraShare)
    }

    @Test
    fun responseWithTwoLegs() {
        val json = """
            {"trip":{"summary":{"length":231.4,"time":13440.5},"legs":[
              {"shape":"_izlhA~rlgdF_{geC~ywl@"},
              {"shape":"_ecslA~meueF_kwzCn`{nI"}
            ]}}
        """.trimIndent()
        val r = ValhallaRoute.parse(json)
        assertEquals(231.4, r.km)
        assertEquals(13440.5, r.seconds)
        // The point shared by the two legs appears only once.
        assertEquals(listOf(LatLon(38.5, -120.2), LatLon(40.7, -120.95), LatLon(43.252, -126.453)), r.shape)
    }

    @Test
    fun calculatedRide() {
        val ride = Ride("test", emptyList(), listOf(Stop("A", sanBasilio), Stop("B", padula)), Ride.Source.GOOGLE_LINK)
        assertTrue(ride.needsRoute)
        val done = ride.routed(ValhallaRoute.Result(listOf(sanBasilio, LatLon(40.5, 16.3), padula), 231.4, 13440.0), south)
        assertFalse(done.needsRoute)
        assertFalse(done.straightLines)
        assertEquals(231.4, done.lengthKm)
        assertEquals("it-sud", done.route?.regionId)
    }

    @Test
    fun sizes() {
        assertEquals("410 MB", formatBytes(410_000_000))
        assertEquals("1.2 GB", formatBytes(1_234_000_000))
    }
}
