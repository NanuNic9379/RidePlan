package io.github.nanunic9379.rideplan.route

import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.Stop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoutingAreasTest {
    /** The real outline of Geofabrik's Alps extract, as routing_areas.py encodes it (3 decimals). */
    private val alpsRing = """ihyAoy\`HvcA~OvXxDjFuCtMuJ[sDp@oClFZ`QdCzTxB|CpAbBvErD~C`KdAbIlAbV`@fDtFt@fIzKt@xQi@dFo@~P}@tR{@|Jq@fEcF~Gg@rEe@vCUvD]`F[fHgBlMyA~DL`IzHdNxD`MvG`RdGdRdPpEnStElGhA|CiDl@aJ^kGD{AGmBIcFdAmHlAmJdIqOv^fSlMzl@nl@laAJvj@_RzdAi}@wGcQpBeZ~AmMgFkXw]sQwMol@mEkAm_@oE}VcO}XuBcT}QgMgHk}@qBmEmIwCkJ}y@wOo~@\ueBgHct@tBug@qEyf@e@gF{GuZqDqXNi\RuLhDcNpAgAza@aBxBiArGkFpIrM|w@fv@hArArFdI`@vC"""

    /** Encodes a ring of lon, lat pairs for a coarse test outline. */
    private fun ring(vararg lonLat: Double): String {
        // Same as routing_areas.py: latitude first, 3 decimals.
        val sb = StringBuilder()
        var lastLat = 0L
        var lastLon = 0L
        for (i in lonLat.indices step 2) {
            val lat = kotlin.math.round(lonLat[i + 1] * 1000).toLong()
            val lon = kotlin.math.round(lonLat[i] * 1000).toLong()
            for (d in longArrayOf(lat - lastLat, lon - lastLon)) {
                var v = if (d < 0) (d shl 1).inv() else d shl 1
                while (v >= 0x20) {
                    sb.append(((0x20 or (v and 0x1F).toInt()) + 63).toChar())
                    v = v shr 5
                }
                sb.append((v.toInt() + 63).toChar())
            }
            lastLat = lat
            lastLon = lon
        }
        return sb.toString()
    }

    private fun json(s: String) = s.replace("\\", "\\\\")

    private val italyOuter = ring(6.6, 44.0, 7.0, 46.0, 10.0, 45.9, 11.0, 47.0, 13.0, 47.1, 18.5, 40.0, 15.5, 37.0, 12.0, 37.5, 12.0, 43.0)
    private val italyHole = ring(12.4, 43.9, 12.5, 43.9, 12.5, 44.0, 12.4, 44.0)

    private val index = """
        {"format": 1, "areas": [
          {"id": "it", "name": "Italy", "release": "routing-data", "bbox": [6.59, 35.25, 18.82, 47.13],
           "outline": {"precision": 3, "rings": ["${json(italyOuter)}", "${json(italyHole)}"]}},
          {"id": "alps", "name": "Alps", "release": "routing-alps", "bbox": [4.842, 42.699, 16.762, 48.401],
           "outline": {"precision": 3, "rings": ["${json(alpsRing)}"]}}
        ]}
    """.trimIndent()

    private val areas = RoutingAreas.parse(index)
    private val italy = areas.first { it.id == "it" }
    private val alps = areas.first { it.id == "alps" }

    private val bolzano = LatLon(46.50, 11.35)
    private val innsbruck = LatLon(47.27, 11.39)
    private val nice = LatLon(43.70, 7.26)
    private val geneva = LatLon(46.20, 6.14)
    private val milan = LatLon(45.46, 9.19)
    private val rome = LatLon(41.90, 12.50)
    private val munich = LatLon(48.14, 11.58)
    private val zurich = LatLon(47.37, 8.54)
    private val madrid = LatLon(40.42, -3.70)
    private val sanMarino = LatLon(43.94, 12.45)

    // ---- Index parsing ----

    @Test
    fun parsesTheIndex() {
        assertEquals(listOf("it", "alps"), areas.map { it.id })
        assertEquals("routing-alps", alps.release)
        assertEquals("Alps", alps.name)
        assertEquals(listOf(4.842, 42.699, 16.762, 48.401), alps.bbox)
        assertTrue(alps.hasOutline)
        assertEquals(2, italy.outline.size)
    }

    @Test
    fun realAlpsOutline() {
        listOf(bolzano, innsbruck, nice, geneva).forEach { assertTrue(alps.covers(it), "$it in the Alps") }
        // Inside the box but outside the extract: the cells there would hold only part of the roads.
        listOf(milan, munich, zurich).forEach {
            assertTrue(alps.inBox(it))
            assertFalse(alps.covers(it), "$it outside the Alps extract")
        }
        assertFalse(alps.covers(rome))
        assertFalse(alps.covers(madrid))
    }

    @Test
    fun holesAreOutsideTheArea() {
        assertTrue(italy.covers(rome))
        assertFalse(italy.covers(sanMarino))
    }

    @Test
    fun wrongFormatFails() {
        assertFailsWith<IllegalArgumentException> { RoutingAreas.parse("""{"format": 2, "areas": []}""") }
        assertFailsWith<IllegalArgumentException> { RoutingAreas.parse("""{"areas": []}""") }
        assertFailsWith<IllegalArgumentException> { RoutingAreas.parse("not json") }
    }

    @Test
    fun brokenAreasAreSkippedAndItalyIsAlwaysThere() {
        val parsed = RoutingAreas.parse(
            """
            {"format": 1, "future": true, "areas": [
              {"id": "../etc", "name": "Bad", "release": "routing-x", "bbox": [0, 0, 1, 1]},
              {"id": "fr", "name": "France", "release": "../../x", "bbox": [0, 0, 1, 1]},
              {"id": "es", "name": "Spain", "release": "routing-es", "bbox": [1, 0, 0, 1]},
              {"id": "pt", "name": "Portugal", "release": "routing-pt", "bbox": [0, 0, 1]},
              {"id": "de", "name": "Germany", "release": "routing-de", "bbox": [0, 0, 200, 1]},
              {"id": "ch", "release": "routing-ch", "bbox": [5.9, 45.8, 10.5, 47.8]},
              {"id": "ch", "name": "Twice", "release": "routing-ch2", "bbox": [0, 0, 1, 1]},
              {"id": "at", "name": "Austria", "release": "routing-at", "bbox": [9.5, 46.3, 17.2, 49.1], "outline": {"precision": 3, "rings": [7]}},
              {"id": "si", "name": "Slovenia", "release": "routing-si", "bbox": [13.3, 45.4, 16.6, 46.9], "outline": {"precision": 3, "rings": ["??"]}},
              "nonsense"
            ]}
            """.trimIndent(),
        )
        // Only the valid ones, first id wins, Italy added from the fallback.
        assertEquals(listOf("ch", "at", "si", "it"), parsed.map { it.id })
        assertEquals("ch", parsed[0].name) // no name: the id
        assertEquals("routing-ch", parsed[0].release)
        // A broken outline only loses the outline: the box still works.
        assertFalse(parsed[1].hasOutline)
        assertFalse(parsed[2].hasOutline)
        assertTrue(parsed[1].covers(LatLon(47.0, 12.0)))
        val it = parsed.last()
        assertEquals("routing-data", it.release)
        assertFalse(it.hasOutline)
    }

    @Test
    fun italyCannotBeMovedToAnotherRelease() {
        val parsed = RoutingAreas.parse("""{"format": 1, "areas": [{"id": "it", "name": "Italy", "release": "routing-it", "bbox": [6, 35, 19, 48]}]}""")
        assertEquals(RoutingAreas.FALLBACK.map { it.release }, parsed.map { it.release })
    }

    @Test
    fun fallbackIsTodaysItaly() {
        val it = RoutingAreas.FALLBACK.single()
        assertEquals(RoutingAreas.ITALY, it.id)
        assertEquals(RoutingAreas.ITALY_RELEASE, it.release)
        // No outline: as before #14 the box counts, and the published cells decide.
        assertTrue(it.covers(zurich))
        assertFalse(it.covers(madrid))
    }

    // ---- Area choice ----

    private fun cells(build: String, vararg lonLat: Pair<Int, Int>, bytes: Long = 10) = RoutingCells(
        build = build,
        osmDate = "d",
        valhalla = "3.9.2",
        packs = lonLat.map { (lon, lat) ->
            val id = (lat + 90) * 360 + (lon + 180)
            RoutingPack("l1-$id", 1, listOf(lon.toDouble(), lat.toDouble(), lon + 1.0, lat + 1.0), "l1-$id-$build.zip", bytes, bytes * 2, "ff")
        },
    )

    /** Italy's cells: the north (including border cells reaching into Switzerland) and Rome. */
    private val italyCells = cells("it1", 6 to 45, 7 to 45, 8 to 45, 9 to 45, 10 to 45, 11 to 45, 11 to 46, 8 to 46, 9 to 46, 12 to 41, 7 to 43, bytes = 10)
    private val alpsCells = cells("al1", 6 to 46, 7 to 43, 11 to 46, 11 to 47, 8 to 45, 9 to 45, 8 to 47, 8 to 46, bytes = 20)

    @Test
    fun candidatesPreferTheAreaThatHoldsEveryStop() {
        // Bolzano - Innsbruck: only the Alps hold both; Italy's box holds Bolzano, so it comes after.
        assertEquals(listOf("alps", "it"), RoutingAreas.candidates(areas, listOf(bolzano, innsbruck)).map { it.id })
        // Rome: only Italy.
        assertEquals(listOf("it"), RoutingAreas.candidates(areas, listOf(rome)).map { it.id })
        // Bolzano alone: both hold it fully, the smaller outline first.
        val both = RoutingAreas.candidates(areas, listOf(bolzano)).map { it.id }
        assertEquals(setOf("alps", "it"), both.toSet())
        assertEquals(if (alps.size < italy.size) "alps" else "it", both.first())
        // Madrid: no area at all; no stops: nothing.
        assertEquals(emptyList(), RoutingAreas.candidates(areas, listOf(madrid)))
        assertEquals(emptyList(), RoutingAreas.candidates(areas, emptyList()))
    }

    @Test
    fun partialCandidatesHoldingMoreStopsComeFirst() {
        // No area holds all three. The Alps hold Innsbruck and Bolzano, Italy Rome and Bolzano: a tie, smaller first.
        val c = RoutingAreas.candidates(areas, listOf(rome, innsbruck, bolzano)).map { it.id }
        assertEquals(listOf(alps, italy).sortedBy { it.size }.map { it.id }, c)
        // Rome, Bologna and Innsbruck: Italy holds two, the Alps one.
        val c2 = RoutingAreas.candidates(areas, listOf(rome, LatLon(44.49, 11.34), innsbruck)).map { it.id }
        assertEquals(listOf("it", "alps"), c2)
    }

    @Test
    fun localDataInTheAreaIsUsedWithoutDownload() {
        val local = listOf(
            LocalArea("it", italyCells, italyCells.packs.map { it.id }.toSet()),
            LocalArea("alps", alpsCells, alpsCells.packs.map { it.id }.toSet()),
        )
        // Bolzano: both areas cover it on the phone; both hold it fully: the smaller area.
        val chosen = RoutingAreas.local(areas, local, listOf(bolzano))
        assertNotNull(chosen)
        // Rome: only Italy.
        assertEquals("it", RoutingAreas.local(areas, local, listOf(rome))?.id)
        // Bolzano - Innsbruck: only the Alps.
        assertEquals("alps", RoutingAreas.local(areas, local, listOf(bolzano, innsbruck))?.id)
    }

    @Test
    fun borderCellsCountOnlyWhenNotStrict() {
        // Lugano: Italy has the cell (its border cell) but the outline says Switzerland.
        val lugano = LatLon(46.0, 8.95)
        val italyOnly = listOf(LocalArea("it", italyCells, italyCells.packs.map { it.id }.toSet()))
        val coarseItaly = areas.filter { it.id != "alps" }
        assertTrue(italy.covers(milan))
        assertFalse(italy.covers(lugano))
        assertNull(RoutingAreas.local(coarseItaly, italyOnly, listOf(milan, lugano)))
        assertEquals("it", RoutingAreas.local(coarseItaly, italyOnly, listOf(milan, lugano), strict = false)?.id)
    }

    @Test
    fun withoutIndexItalyOnThePhoneWorksAsBefore() {
        // The fallback has no outline: Italy's cells on the phone route the ride, strict or not.
        val italyOnly = listOf(LocalArea("it", italyCells, italyCells.packs.map { it.id }.toSet()))
        assertEquals("it", RoutingAreas.local(RoutingAreas.FALLBACK, italyOnly, listOf(milan, LatLon(45.5, 10.5)))?.id)
        // Missing packs on the phone: not usable.
        val partial = listOf(LocalArea("it", italyCells, setOf(italyCells.packs.first().id)))
        assertNull(RoutingAreas.local(RoutingAreas.FALLBACK, partial, listOf(milan, LatLon(45.5, 10.5))))
        // No manifest: nothing.
        assertNull(RoutingAreas.local(RoutingAreas.FALLBACK, listOf(LocalArea("it", null, emptySet())), listOf(milan)))
    }

    @Test
    fun localAreaUnknownToTheIndexCountsAsWithoutOutline() {
        val ghost = listOf(LocalArea("old", alpsCells, alpsCells.packs.map { it.id }.toSet()))
        // Not in the index: counted as having no outline, usable.
        assertEquals("old", RoutingAreas.local(areas, ghost, listOf(bolzano))?.id)
    }

    @Test
    fun planPicksTheAreaHoldingEveryStop() {
        val published = listOf(PublishedArea(italy, italyCells), PublishedArea(alps, alpsCells))
        // Bolzano - Innsbruck: only the Alps cells cover both.
        val d = RoutingAreas.plan(published, emptyList(), listOf(bolzano, innsbruck))
        assertNotNull(d)
        assertEquals("alps", d.area)
        assertEquals("routing-alps", d.release)
        assertEquals("al1", d.cells.build)
        // Rome: Italy, with its legacy Release.
        val r = RoutingAreas.plan(published, emptyList(), listOf(rome))
        assertEquals("it", r?.area)
        assertEquals("routing-data", r?.release)
        // Madrid: nothing.
        assertNull(RoutingAreas.plan(published, emptyList(), listOf(madrid)))
    }

    @Test
    fun planPrefersTheSmallerDownloadAndDataAlreadyOnThePhone() {
        val published = listOf(PublishedArea(italy, italyCells), PublishedArea(alps, alpsCells))
        // Bolzano alone: both hold it fully; Italy's cells are cheaper (10 vs 20 bytes).
        assertEquals("it", RoutingAreas.plan(published, emptyList(), listOf(bolzano))?.area)
        // With the Alps cell already on the phone, the Alps cost nothing.
        val local = listOf(LocalArea("alps", alpsCells, alpsCells.packs.map { it.id }.toSet()))
        val d = RoutingAreas.plan(published, local, listOf(bolzano))
        assertEquals("alps", d?.area)
        assertEquals(0, d?.packs?.size)
        assertFalse(d!!.wipe)
    }

    @Test
    fun planWipesOnlyTheSameAreasOldBuild() {
        val oldAlps = LocalArea("alps", alpsCells.copy(build = "al0"), alpsCells.packs.map { it.id }.toSet())
        val italyLocal = LocalArea("it", italyCells, italyCells.packs.map { it.id }.toSet())
        val d = RoutingAreas.plan(listOf(PublishedArea(alps, alpsCells)), listOf(italyLocal, oldAlps), listOf(bolzano, innsbruck))
        assertNotNull(d)
        assertTrue(d.wipe)
        assertEquals("alps", d.area)
        // A first Alps download never wipes, even with Italy on the phone.
        val first = RoutingAreas.plan(listOf(PublishedArea(alps, alpsCells)), listOf(italyLocal), listOf(bolzano, innsbruck))
        assertFalse(first!!.wipe)
    }

    @Test
    fun planFallsBackToCellsWhenNoOutlineHoldsTheRide() {
        // Milan - Lugano: neither outline holds both, but Italy's cells (with its border cell) do.
        val lugano = LatLon(46.0, 8.95)
        val published = listOf(PublishedArea(italy, italyCells))
        val d = RoutingAreas.plan(published, emptyList(), listOf(milan, lugano))
        assertEquals("it", d?.area)
    }

    @Test
    fun outsideStopsNameTheStopsOfTheBestArea() {
        val published = listOf(PublishedArea(italy, italyCells), PublishedArea(alps, alpsCells))
        val stops = listOf(Stop("Roma", rome), Stop("Innsbruck", innsbruck), Stop("Bolzano", bolzano))
        // Italy leaves out Innsbruck only; the Alps leave out Rome only: tie, first listed wins.
        assertEquals(listOf(OutsideStop(2, "Innsbruck")), outsideStops(stops, published))
        val madridStops = listOf(Stop("Roma", rome), Stop(null, madrid))
        assertEquals(listOf(OutsideStop(2, null)), outsideStops(madridStops, published))
        assertEquals(emptyList(), outsideStops(stops, emptyList<PublishedArea>()))
    }

    @Test
    fun dataIdKeepsItalyUnchanged() {
        assertEquals("cells-it1", RoutingData.Cells(italyCells).dataId)
        assertEquals("cells-it1", RoutingData.Cells(italyCells, "it").dataId)
        assertEquals("cells-alps-al1", RoutingData.Cells(alpsCells, "alps").dataId)
    }

    @Test
    fun downloadDefaultsToItaly() {
        val d = CellsDownload.plan(italyCells, null, emptySet(), listOf(rome))
        assertEquals("it", d?.area)
        assertEquals("routing-data", d?.release)
    }
}
