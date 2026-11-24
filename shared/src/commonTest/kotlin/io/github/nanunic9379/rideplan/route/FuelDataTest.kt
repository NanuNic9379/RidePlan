package io.github.nanunic9379.rideplan.route

import io.github.nanunic9379.rideplan.ride.LatLon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FuelDataTest {
    private val header = "#rideplan-fuel 1\tlat\tlon\tbrand\tname"

    private fun cell(lon: Int, lat: Int, fuel: Boolean = true): RoutingPack {
        val id = "l1-${(lat + 90) * 360 + (lon + 180)}"
        return RoutingPack(
            id, 1, listOf(lon.toDouble(), lat.toDouble(), lon + 1.0, lat + 1.0), "$id-7.zip", 1, 1, "ff",
            fuel = if (fuel) FuelFile("fuel-$id-7.tsv", 100, "ee") else null,
        )
    }

    /** Two cells side by side: 12-13 °E and 13-14 °E, 41-42 °N. */
    private val west = cell(12, 41)
    private val east = cell(13, 41)
    private val cells = RoutingCells("7", "d", "3.9.2", listOf(west, east))

    @Test
    fun parsesTheList() {
        val text = "$header\n41.5\t12.5\tEni\tEni Orte\n41.6\t12.6\t\t\n\n"
        assertEquals(
            listOf(FuelStation(LatLon(41.5, 12.5), "Eni", "Eni Orte"), FuelStation(LatLon(41.6, 12.6))),
            FuelData.parse(text),
        )
        assertEquals(emptyList(), FuelData.parse(header + "\n"))
    }

    @Test
    fun aBrokenLineIsSkippedAWrongHeaderFails() {
        assertEquals(1, FuelData.parse("$header\nx\t12\n41.5\t12.5\tQ8\n99\t12\n").size)
        assertFailsWith<IllegalArgumentException> { FuelData.parse("lat\tlon\n41.5\t12.5") }
        assertFailsWith<IllegalArgumentException> { FuelData.parse("#rideplan-fuel 2\tlat\n41.5\t12.5") }
        assertFailsWith<IllegalArgumentException> { FuelData.parse("") }
    }

    @Test
    fun stationsFromEveryCellAlongTheLine() {
        val line = listOf(LatLon(41.5, 12.5), LatLon(41.5, 13.5))
        val files = mapOf(
            west.id to "$header\n41.5\t12.6\tA\t\n",
            east.id to "$header\n41.5\t13.4\tB\t\n",
        )
        assertEquals(listOf("A", "B"), FuelData.stationsAlong(cells, line) { files[it] }?.map { it.brand })
    }

    @Test
    fun unknownWhenACellAlongTheLineHasNoList() {
        val line = listOf(LatLon(41.5, 12.5), LatLon(41.5, 13.5))
        assertNull(FuelData.stationsAlong(cells, line) { if (it == west.id) "$header\n" else null })
        // An unreadable list counts as missing, never as "no stations".
        assertNull(FuelData.stationsAlong(cells, line) { if (it == west.id) "$header\n" else "garbage" })
    }

    @Test
    fun unknownWhenTheLineLeavesTheCells() {
        val line = listOf(LatLon(41.5, 12.5), LatLon(41.5, 14.5))
        assertNull(FuelData.stationsAlong(cells, line) { "$header\n" })
        assertNull(FuelData.stationsAlong(cells, emptyList()) { "$header\n" })
    }

    @Test
    fun theNeighbourCellCountsWhenTheRoadRunsAlongItsEdge() {
        // 300 m west of the 13 °E edge: stations just across it may be within 1 km.
        val line = listOf(LatLon(41.3, 12.996), LatLon(41.7, 12.996))
        assertEquals(listOf(west, east), FuelData.cellsAlong(cells, line))
        // Far from the edge only its own cell.
        assertEquals(listOf(west), FuelData.cellsAlong(cells, listOf(LatLon(41.5, 12.5))))
        // The neighbour's list is needed then: without it the stations are unknown.
        assertNull(FuelData.stationsAlong(cells, line) { if (it == west.id) "$header\n" else null })
    }

    @Test
    fun manifestKeepsTheFuelListAndOldManifestsStillWork() {
        val json = """
            {"format":1,"build":"123","osmDate":"d","valhalla":"3.9.2","packs":[
             {"id":"l1-47352","level":1,"bbox":[12.0,41.0,13.0,42.0],"file":"l1-47352-123.zip","bytes":4000000,"tileBytes":12000000,"sha256":"bb",
              "fuel":{"file":"fuel-l1-47352-123.tsv","bytes":9000,"sha256":"cc"}},
             {"id":"l1-47353","level":1,"bbox":[13.0,41.0,14.0,42.0],"file":"l1-47353-123.zip","bytes":4000000,"tileBytes":12000000,"sha256":"dd"}]}
        """.trimIndent()
        val c = RoutingCells.parse(json)
        assertEquals(FuelFile("fuel-l1-47352-123.tsv", 9000, "cc"), c.packs[0].fuel)
        assertNull(c.packs[1].fuel)
        assertEquals(c, RoutingCells.parse(c.toJson()))
        // The download size includes the lists.
        assertEquals(8_009_000, CellsDownload(c, c.packs, wipe = false).bytes)
        // A file name that could leave the download directory, or a broken entry, is dropped,
        // but the road data stays usable.
        val bad = RoutingCells.parse(json.replace("fuel-l1-47352-123.tsv", "../x.tsv"))
        assertNull(bad.packs[0].fuel)
        assertNotNull(RoutingCells.parse(json.replace("\"sha256\":\"cc\"", "\"sha\":\"cc\"")).packs[0].let { assertNull(it.fuel); it })
    }
}
