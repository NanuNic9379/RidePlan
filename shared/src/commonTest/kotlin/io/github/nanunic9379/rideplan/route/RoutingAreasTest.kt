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
    private val areas = AreaFixtures.areas
    private val italy = AreaFixtures.italy
    private val alps = AreaFixtures.alps
    private val alpsRing = AreaFixtures.alpsRing
    private val italyCells = AreaFixtures.italyCells
    private val alpsCells = AreaFixtures.alpsCells

    private fun ring(vararg lonLat: Double) = AreaFixtures.ring(*lonLat)

    private fun json(s: String) = AreaFixtures.json(s)

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
        // No outline: the box and the country count. Zurich is in the box but abroad: its roads are
        // not in Italy's data (Italy's cells near the border hold only the Italian side).
        assertFalse(it.covers(zurich))
        assertTrue(it.covers(milan))
        assertTrue(it.covers(sanMarino))
        assertFalse(it.covers(madrid))
    }

    // ---- Area choice ----

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
    fun aBorderCellIsNotEnoughWhenNoOutlineHoldsTheRide() {
        // Milan - Lugano: Italy's border cell has only the Italian side of the roads. No single
        // area holds both: the ride is split across areas instead (CrossAreaTest).
        val lugano = LatLon(46.0, 8.95)
        val published = listOf(PublishedArea(italy, italyCells))
        assertNull(RoutingAreas.plan(published, emptyList(), listOf(milan, lugano)))
        assertNull(RoutingAreas.download(published, emptyList(), listOf(milan, lugano)))
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
