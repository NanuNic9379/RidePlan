package io.github.nanunic9379.rideplan.route

import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.Stop
import io.github.nanunic9379.rideplan.ride.distanceKm
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoadNotesTest {
    private val sample = """
        {"format":1,"osmDate":"2026-11-22T21:00:00Z",
         "closures":[
           {"id":"w123","highway":"secondary","line":[[10.45,46.52],[10.46,46.53]],
            "tags":{"motor_vehicle:conditional":"no @ (Nov-May)"},"names":{"name":"Passo dello Stelvio","ref":"SS38","name:de":"Stilfser Joch"}},
           {"id":"broken","line":[[10.0,46.0]]},
           {"id":"w9","line":"nonsense"}
         ],
         "cameras":[
           {"id":"n456","lat":45.1,"lon":9.12,"maxspeed":"50","direction":"90"},
           {"id":"n457","lat":45.2,"lon":9.13},
           {"id":"bad","lat":"x"}
         ]}
    """.trimIndent()

    @Test
    fun parsesTheFileAndSkipsBrokenEntries() {
        val notes = RoadNotes.parse(sample)
        assertEquals("2026-11-22T21:00:00Z", notes.osmDate)
        val c = notes.closures.single()
        assertEquals("w123", c.id)
        assertEquals("secondary", c.highway)
        assertEquals(listOf(LatLon(46.52, 10.45), LatLon(46.53, 10.46)), c.line)
        assertIs<ClosureRule.Seasonal>(c.rule)
        assertEquals(listOf(SpeedCamera("n456", LatLon(45.1, 9.12), 50, "90"), SpeedCamera("n457", LatLon(45.2, 9.13), null)), notes.cameras)
    }

    @Test
    fun unknownFormatIsRefused() {
        assertFailsWith<IllegalArgumentException> { RoadNotes.parse("""{"format":2,"closures":[],"cameras":[]}""") }
        assertFailsWith<IllegalArgumentException> { RoadNotes.parse("""{"closures":[]}""") }
    }

    @Test
    fun namesInTheRidersLanguage() {
        val c = RoadNotes.parse(sample).closures.single()
        assertEquals("Stilfser Joch", c.name("de"))
        assertEquals("Stilfser Joch", c.name("de-AT"))
        assertEquals("Passo dello Stelvio", c.name("it"))
        assertEquals("Passo dello Stelvio", c.name(null))
        assertEquals("SS38", c.copy(names = mapOf("ref" to "SS38")).name("it"))
        assertNull(c.copy(names = emptyMap()).name("it"))
    }

    @Test
    fun maxspeedIsNeverGuessed() {
        assertEquals(50, SpeedCamera.parseMaxspeed("50"))
        assertEquals(90, SpeedCamera.parseMaxspeed(" 90 km/h"))
        assertEquals(48, SpeedCamera.parseMaxspeed("30 mph"))
        assertEquals(70, SpeedCamera.parseMaxspeed("70;50"))
        assertNull(SpeedCamera.parseMaxspeed("IT:urban"))
        assertNull(SpeedCamera.parseMaxspeed("signals"))
        assertNull(SpeedCamera.parseMaxspeed("none"))
        assertNull(SpeedCamera.parseMaxspeed("0"))
    }

    @Test
    fun manifestEntryIsChecked() {
        val sha = "a".repeat(64)
        fun entry(file: String, bytes: Long, sha256: String) =
            Json.parseToJsonElement("""{"file":"$file","bytes":$bytes,"sha256":"$sha256"}""").jsonObject
        assertEquals(RoadNotesFile("road-notes.json", 1000, sha), RoadNotesFile.of(entry("road-notes.json", 1000, sha.uppercase())))
        assertNull(RoadNotesFile.of(entry("../cells.json", 1000, sha)))
        assertNull(RoadNotesFile.of(entry("road-notes.json", 0, sha)))
        assertNull(RoadNotesFile.of(entry("road-notes.json", 1000, "abc")))
    }

    @Test
    fun cellsManifestKeepsTheRoadNotesEntry() {
        val notes = RoadNotesFile("road-notes.json", 12345, "b".repeat(64))
        val cells = RoutingCells(build = "1", osmDate = "2026-11-22T21:00:00Z", valhalla = "3.9.2", packs = emptyList(), roadNotes = notes)
        assertEquals(notes, RoutingCells.parse(cells.toJson()).roadNotes)
        // Older manifests have no entry; a broken one is ignored, the cells still load.
        assertNull(RoutingCells.parse(cells.copy(roadNotes = null).toJson()).roadNotes)
        val broken = cells.toJson().replace("\"sha256\":\"${"b".repeat(64)}\"", "\"sha256\":\"zz\"")
        assertNull(RoutingCells.parse(broken).roadNotes)
    }

    @Test
    fun requestExcludesTheAvoidedArea() {
        val stops = listOf(Stop("A", LatLon(46.5, 10.4)), Stop("B", LatLon(46.6, 10.5)))
        val area = RoadNotesAlong.square(LatLon(46.53, 10.45), 25.0)
        val json = Json.parseToJsonElement(ValhallaRoute.request(stops, avoid = listOf(area))).jsonObject
        val ring = json.getValue("exclude_polygons").jsonArray.single().jsonArray
        assertEquals(5, ring.size)
        assertEquals(ring.first(), ring.last())
        // Valhalla wants [lon, lat].
        assertEquals(area[0].lon, ring[0].jsonArray[0].jsonPrimitive.content.toDouble())
        assertEquals(area[0].lat, ring[0].jsonArray[1].jsonPrimitive.content.toDouble())
        assertFalse("exclude_polygons" in Json.parseToJsonElement(ValhallaRoute.request(stops)).jsonObject)
    }

    @Test
    fun avoidSquareIsAboutFiftyMetresWide() {
        val p = LatLon(46.53, 10.45)
        val sq = RoadNotesAlong.square(p, 25.0)
        assertTrue(abs(distanceKm(sq[0], sq[1]) * 1000 - 50) < 1)
        assertTrue(abs(distanceKm(sq[1], sq[2]) * 1000 - 50) < 1)
    }
}
