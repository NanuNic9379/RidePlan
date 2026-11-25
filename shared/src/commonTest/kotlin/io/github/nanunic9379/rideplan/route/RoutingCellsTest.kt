package io.github.nanunic9379.rideplan.route

import io.github.nanunic9379.rideplan.ride.LatLon
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoutingCellsTest {
    /** Like routing_data.py: Valhalla tile numbering on the world grid. */
    private fun pack(level: Int, lon: Int, lat: Int): RoutingPack {
        val size = if (level == 0) 4 else 1
        val col = floor((lon + 180.0) / size).toInt()
        val row = floor((lat + 90.0) / size).toInt()
        val id = "l$level-${row * (360 / size) + col}"
        val lon0 = -180.0 + col * size
        val lat0 = -90.0 + row * size
        return RoutingPack(id, level, listOf(lon0, lat0, lon0 + size, lat0 + size), "$id-7.zip", 5_000_000, 20_000_000, "ff")
    }

    /** Southern Italy: cells from 12 to 18 east, 37 to 42 north, plus the main-road blocks. */
    private fun build(id: String) = RoutingCells(
        build = id,
        osmDate = "2026-11-22T21:00:00Z",
        valhalla = "3.9.2",
        packs = listOf(pack(0, 12, 38), pack(0, 16, 38), pack(0, 12, 42), pack(0, 16, 42)) +
            (12..17).flatMap { lon -> (37..41).map { lat -> pack(1, lon, lat) } },
    )

    private val cells = build("100")
    private fun cell(lon: Int, lat: Int) = pack(1, lon, lat).id

    private val sanBasilio = LatLon(40.6894884, 16.9741293)
    private val padula = LatLon(40.3421591, 15.6580114)

    @Test
    fun numberingLikeValhalla() {
        // Level-1 tile of Rome (41.9 N 12.5 E): row 131, column 192, 360 columns.
        assertEquals("l1-${131 * 360 + 192}", pack(1, 12, 41).id)
        assertEquals(listOf(12.0, 41.0, 13.0, 42.0), pack(1, 12, 41).bbox)
        assertEquals(listOf(12.0, 38.0, 16.0, 42.0), pack(0, 13, 40).bbox)
    }

    @Test
    fun onlyCellsAroundTheRide() {
        val ids = assertNotNull(cells.needed(listOf(sanBasilio, padula))).map { it.id }.toSet()
        // The cells of the stops and those the line crosses.
        assertTrue(cell(16, 40) in ids)
        assertTrue(cell(15, 40) in ids)
        // Far from the ride.
        assertFalse(cell(12, 37) in ids)
        assertFalse(cell(12, 41) in ids)
        // Main roads of the touched blocks, not of blocks without chosen cells.
        assertTrue(pack(0, 16, 38).id in ids)
        assertTrue(pack(0, 12, 38).id in ids)
        assertFalse(pack(0, 12, 42).id in ids)
        assertTrue(ids.size < cells.packs.size / 2)
    }

    @Test
    fun corridorNotBoundingBox() {
        // A diagonal leg: the far corners of its bounding box stay out.
        val ids = assertNotNull(cells.needed(listOf(LatLon(37.5, 12.5), LatLon(41.5, 17.5)))).map { it.id }.toSet()
        assertTrue(cell(12, 37) in ids)
        assertTrue(cell(17, 41) in ids)
        assertTrue(cell(15, 39) in ids)
        assertFalse(cell(12, 41) in ids)
        assertFalse(cell(17, 37) in ids)
    }

    @Test
    fun marginGrowsWithLeg() {
        assertEquals(Corridor.MIN_MARGIN_KM, Corridor.marginKm(0.0))
        assertTrue(Corridor.marginKm(100.0) > Corridor.marginKm(10.0))
        assertEquals(Corridor.MAX_MARGIN_KM, Corridor.marginKm(10_000.0))
        // A cell 15 km from the leg is in even with the minimum margin, one 60 km away is not.
        val box = listOf(16.0, 40.0, 17.0, 41.0)
        assertTrue(Corridor.near(LatLon(39.87, 16.2), LatLon(39.87, 16.3), box))
        assertFalse(Corridor.near(LatLon(39.45, 16.2), LatLon(39.45, 16.3), box))
    }

    @Test
    fun stopOutsideCells() {
        assertNull(cells.needed(listOf(sanBasilio, LatLon(45.46, 9.19))))
    }

    @Test
    fun onlyWhatIsMissing() {
        val needed = cells.needed(listOf(sanBasilio, padula))!!
        val have = needed.take(3).map { it.id }.toSet()

        val same = CellsDownload.plan(cells, cells, have, listOf(sanBasilio, padula))!!
        assertEquals(needed.size - 3, same.packs.size)
        assertFalse(same.wipe)
        assertEquals(same.packs.sumOf { it.bytes }, same.bytes)

        // New build: old cells do not match, everything needed is downloaded again.
        val newer = CellsDownload.plan(build("200"), cells, have, listOf(sanBasilio, padula))!!
        assertEquals(needed.size, newer.packs.size)
        assertTrue(newer.wipe)

        val first = CellsDownload.plan(cells, null, emptySet(), listOf(sanBasilio, padula))!!
        assertEquals(needed.size, first.packs.size)
        assertFalse(first.wipe)

        assertNull(CellsDownload.plan(cells, null, emptySet(), listOf(LatLon(45.46, 9.19))))
    }

    @Test
    fun alreadyOnThePhone() {
        val points = listOf(sanBasilio, padula)
        val all = cells.needed(points)!!.map { it.id }.toSet()
        assertTrue(CellsDownload.coveredLocally(cells, all, points))
        assertFalse(CellsDownload.coveredLocally(cells, all - all.first(), points))
        assertFalse(CellsDownload.coveredLocally(null, all, points))
    }

    @Test
    fun manifestFormat() {
        val json = """
            {"format":1,"build":"123","osmDate":"2026-11-22T21:21:22Z","valhalla":"3.9.2","packs":[
             {"id":"l0-2928","level":0,"bbox":[12.0,38.0,16.0,42.0],"file":"l0-2928-123.zip","bytes":9000000,"tileBytes":30000000,"sha256":"aa"},
             {"id":"l1-47352","level":1,"bbox":[12.0,41.0,13.0,42.0],"file":"l1-47352-123.zip","bytes":4000000,"tileBytes":12000000,"sha256":"bb"}]}
        """.trimIndent()
        val c = RoutingCells.parse(json)
        assertEquals("123", c.build)
        assertEquals(2, c.packs.size)
        assertEquals(12_000_000, c.packs[1].tileBytes)
        assertEquals(c, RoutingCells.parse(c.toJson()))
        assertTrue(c.packs[1].contains(LatLon(41.9, 12.5)))
        assertFailsWith<IllegalArgumentException> { RoutingCells.parse("""{"format":2,"build":"1","osmDate":"x","valhalla":"x","packs":[]}""") }
        // An odd id is rejected: it becomes a file name on the phone.
        assertFailsWith<IllegalArgumentException> { RoutingCells.parse(json.replace("l1-47352\",", "../x\",")) }
    }

    /** cells.json exactly as data/routing_data.py writes it with --notes (#61): json.dump, indent 2. */
    @Test
    fun roadNotesEntryFromThePipeline() {
        val sha = "3f".repeat(32)
        val json = """
            {
              "format": 1,
              "build": "45444361349",
              "osmDate": "2026-11-24T20:21:02Z",
              "valhalla": "3.9.2",
              "packs": [
                {
                  "id": "l1-49150",
                  "level": 1,
                  "bbox": [
                    10.0,
                    46.0,
                    11.0,
                    47.0
                  ],
                  "file": "l1-49150-45444361349.zip",
                  "bytes": 4000,
                  "tileBytes": 12000,
                  "sha256": "bb"
                }
              ],
              "roadNotes": {
                "file": "road-notes.json",
                "bytes": 52311,
                "sha256": "$sha"
              }
            }
        """.trimIndent()
        val notes = assertNotNull(RoutingCells.parse(json).roadNotes)
        assertEquals(RoadNotesFile("road-notes.json", 52311, sha), notes)
        assertTrue(notes.accepts(52311, sha.uppercase()))
        // A modified, truncated or swapped file is refused.
        assertFalse(notes.accepts(52311, "3e" + sha.drop(2)))
        assertFalse(notes.accepts(52310, sha))
    }

    @Test
    fun routingDataId() {
        assertEquals("cells-100", RoutingData.Cells(cells).dataId)
        assertEquals("2026-11-22T21:00:00Z", RoutingData.Cells(cells).osmDate)
    }
}
