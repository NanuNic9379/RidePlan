package io.github.nanunic9379.rideplan.route

import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.Stop
import io.github.nanunic9379.rideplan.ride.distanceKm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CrossAreaTest {
    private val italy = AreaFixtures.italy
    private val alps = AreaFixtures.alps
    private val both = listOf(italy, alps)

    private val milan = LatLon(45.46, 9.19)
    private val lugano = LatLon(46.00, 8.95)
    private val bolzano = LatLon(46.50, 11.35)
    private val innsbruck = LatLon(47.27, 11.39)
    private val rome = LatLon(41.90, 12.50)
    private val madrid = LatLon(40.42, -3.70)

    /** An area with only a box (lon min, lat min, lon max, lat max). */
    private fun box(id: String, vararg bbox: Double) = RoutingAreas.parse(
        """{"format": 1, "areas": [{"id": "$id", "release": "routing-$id", "bbox": [${bbox.joinToString()}]}]}""",
    ).first { it.id == id }

    /** Distance in km from [p] to the straight line a-b, on a local plane. */
    private fun offLine(p: LatLon, a: LatLon, b: LatLon): Double {
        val kx = 111.32 * kotlin.math.cos(p.lat * kotlin.math.PI / 180)
        val ky = 110.57
        val ax = (a.lon - p.lon) * kx
        val ay = (a.lat - p.lat) * ky
        val bx = (b.lon - p.lon) * kx
        val by = (b.lat - p.lat) * ky
        val dx = bx - ax
        val dy = by - ay
        val t = ((-ax * dx - ay * dy) / (dx * dx + dy * dy)).coerceIn(0.0, 1.0)
        return kotlin.math.hypot(ax + t * dx, ay + t * dy)
    }

    // ---- Splitting the ride into legs ----

    @Test
    fun aRideInOneAreaIsOneLeg() {
        val legs = CrossArea.split(both, listOf(milan, rome))
        assertEquals(listOf(AreaLeg("it", 0, 1)), legs)
        // Bolzano - Innsbruck: only the Alps hold both.
        assertEquals(listOf(AreaLeg("alps", 0, 1)), CrossArea.split(both, listOf(bolzano, innsbruck)))
        // A single point too.
        assertEquals(1, CrossArea.split(both, listOf(rome))?.size)
    }

    @Test
    fun italyToSwitzerlandGetsAHiddenJunction() {
        // The owner's ride: Milan (Italy only) to Lugano (Alps only).
        assertTrue(italy.covers(milan) && !alps.covers(milan))
        assertTrue(alps.covers(lugano) && !italy.covers(lugano))
        val legs = CrossArea.split(both, listOf(milan, lugano))
        assertNotNull(legs)
        assertEquals(2, legs.size)
        val (first, second) = legs
        assertEquals("it", first.area)
        assertEquals(0 to 0, first.first to first.last)
        assertEquals("alps", second.area)
        assertEquals(1 to 1, second.first to second.last)
        val j = assertNotNull(first.after)
        assertEquals(j, second.before)
        assertNull(first.before)
        assertNull(second.after)
        // Inside both outlines, near the straight line between the stops, between them.
        assertTrue(italy.covers(j) && alps.covers(j))
        assertTrue(CrossArea.depth(italy, alps, j) >= CrossArea.MIN_DEPTH_KM)
        assertTrue(offLine(j, milan, lugano) < 1.0)
        assertTrue(distanceKm(milan, j) < distanceKm(milan, lugano))
        // Each leg is routed through its stops and the junction.
        assertEquals(listOf(milan, j), first.points(listOf(milan, lugano)))
        assertEquals(listOf(j, lugano), second.points(listOf(milan, lugano)))
    }

    @Test
    fun theWayBackIsSplitTheOtherWayRound() {
        val legs = assertNotNull(CrossArea.split(both, listOf(lugano, milan)))
        assertEquals(listOf("alps", "it"), legs.map { it.area })
        val j = assertNotNull(legs[0].after)
        assertTrue(italy.covers(j) && alps.covers(j))
    }

    @Test
    fun aStopInBothAreasIsWhereTheRideChangesArea() {
        // Milan - Bolzano - Innsbruck: Bolzano is in Italy and in the Alps: no junction needed.
        assertTrue(italy.covers(bolzano) && alps.covers(bolzano))
        val legs = CrossArea.split(both, listOf(milan, bolzano, innsbruck))
        assertEquals(listOf(AreaLeg("it", 0, 1), AreaLeg("alps", 1, 2)), legs)
    }

    @Test
    fun theAreaHoldingMoreStopsInARowGoesFirst() {
        // Bolzano - Innsbruck - Bolzano: the Alps hold all three, Italy only the first.
        assertEquals(listOf(AreaLeg("alps", 0, 2)), CrossArea.split(both, listOf(bolzano, innsbruck, bolzano)))
        // Bolzano alone: both hold it: the smaller area.
        val smaller = listOf(italy, alps).minBy { it.size }.id
        assertEquals(smaller, CrossArea.split(both, listOf(bolzano))?.single()?.area)
    }

    @Test
    fun aRideCanLeaveAnAreaAndComeBack() {
        // Milan - Lugano - Rome: Italy, the Alps, Italy again, with two junctions.
        val legs = assertNotNull(CrossArea.split(both, listOf(milan, lugano, rome)))
        assertEquals(listOf("it", "alps", "it"), legs.map { it.area })
        assertEquals(listOf(0 to 0, 1 to 1, 2 to 2), legs.map { it.first to it.last })
        assertNotNull(legs[0].after)
        assertEquals(legs[0].after, legs[1].before)
        assertNotNull(legs[1].after)
        assertEquals(legs[1].after, legs[2].before)
        for (j in listOfNotNull(legs[0].after, legs[1].after)) assertTrue(italy.covers(j) && alps.covers(j))
    }

    @Test
    fun aStopOutsideEveryAreaCannotBeSplit() {
        assertNull(CrossArea.split(both, listOf(milan, madrid)))
        assertNull(CrossArea.split(both, emptyList()))
        assertNull(CrossArea.split(emptyList(), listOf(milan)))
    }

    @Test
    fun areasThatDoNotOverlapCannotBeJoined() {
        val west = box("w", 0.0, 40.0, 5.0, 45.0)
        val east = box("e", 5.5, 40.0, 10.0, 45.0)
        val a = LatLon(42.0, 2.0)
        val b = LatLon(42.0, 8.0)
        assertNull(CrossArea.junction(west, east, a, b))
        assertNull(CrossArea.split(listOf(west, east), listOf(a, b)))
        // Each stop alone is fine.
        assertEquals(1, CrossArea.split(listOf(west, east), listOf(a))?.size)
    }

    @Test
    fun theJunctionIsTheFirstDeepPlaceFromTheFirstArea() {
        // Overlap between 4 and 6 degrees east: about 160 km wide at this latitude.
        val west = box("w", 0.0, 40.0, 6.0, 45.0)
        val east = box("e", 4.0, 40.0, 10.0, 45.0)
        val a = LatLon(42.0, 2.0)
        val b = LatLon(42.0, 8.0)
        val j = assertNotNull(CrossArea.junction(west, east, a, b))
        assertEquals(42.0, j.lat, 1e-9)
        // 10 km past the edge of the east area, within a step of 1 km.
        val pastEdgeKm = (j.lon - 4.0) * 111.32 * kotlin.math.cos(42.0 * kotlin.math.PI / 180)
        assertTrue(pastEdgeKm >= CrossArea.DEEP_KM && pastEdgeKm < CrossArea.DEEP_KM + 1.1, "$pastEdgeKm")
        // Going the other way it is near the other edge.
        val back = assertNotNull(CrossArea.junction(east, west, b, a))
        val beforeEdgeKm = (6.0 - back.lon) * 111.32 * kotlin.math.cos(42.0 * kotlin.math.PI / 180)
        assertTrue(beforeEdgeKm >= CrossArea.DEEP_KM && beforeEdgeKm < CrossArea.DEEP_KM + 1.1, "$beforeEdgeKm")
    }

    @Test
    fun aNarrowOverlapGivesItsDeepestPlace() {
        // Overlap only 0.2 degrees wide (about 16 km): nowhere 10 km deep, the middle is the deepest.
        val west = box("w", 0.0, 40.0, 5.1, 45.0)
        val east = box("e", 4.9, 40.0, 10.0, 45.0)
        val j = assertNotNull(CrossArea.junction(west, east, LatLon(42.0, 2.0), LatLon(42.0, 8.0)))
        assertEquals(5.0, j.lon, 0.02)
        assertTrue(CrossArea.depth(west, east, j) < CrossArea.DEEP_KM)
        assertTrue(CrossArea.depth(west, east, j) >= CrossArea.MIN_DEPTH_KM)
    }

    @Test
    fun whenTheLineMissesTheOverlapTheJunctionMovesToTheSide() {
        // The overlap is the corner 5-6 E, 44-47 N; the straight line passes south of it.
        val west = box("w", 0.0, 40.0, 6.0, 47.0)
        val north = box("n", 5.0, 44.0, 10.0, 50.0)
        val a = LatLon(41.0, 1.0)
        val b = LatLon(45.0, 9.0)
        assertTrue(west.covers(a) && !north.covers(a))
        assertTrue(north.covers(b) && !west.covers(b))
        val j = assertNotNull(CrossArea.junction(west, north, a, b))
        assertTrue(west.covers(j) && north.covers(j))
        assertTrue(offLine(j, a, b) > 5.0)
        assertTrue(CrossArea.depth(west, north, j) >= CrossArea.DEEP_KM)
        val legs = assertNotNull(CrossArea.split(listOf(west, north), listOf(a, b)))
        assertEquals(listOf("w", "n"), legs.map { it.area })
        // Too far to the side (more than the largest detour tried): no junction.
        val farNorth = box("f", 5.0, 47.0, 10.0, 50.0)
        val west2 = box("w2", 0.0, 40.0, 6.0, 48.0)
        assertNull(CrossArea.junction(west2, farNorth, LatLon(40.5, 1.0), LatLon(47.5, 9.5)))
    }

    @Test
    fun anotherAreaIsTriedWhenTheFirstCannotBeJoined() {
        // From A the ride can only go on into C (B does not overlap A), even if B holds more of it.
        val a = box("a", 0.0, 40.0, 5.0, 45.0)
        val b = box("b", 5.5, 40.0, 10.0, 45.0)
        val c = box("c", 4.0, 40.0, 6.0, 45.0)
        val p0 = LatLon(42.0, 2.0)
        val p1 = LatLon(42.0, 5.8)
        val p2 = LatLon(42.0, 8.0)
        val legs = assertNotNull(CrossArea.split(listOf(a, b, c), listOf(p0, p1, p2)))
        // A then C (p1 is in C) and on into B: every change happens inside an overlap.
        assertEquals(listOf("a", "c", "b"), legs.map { it.area })
        assertEquals(listOf(p0, p1, p2), legs.flatMap { leg -> (leg.first..leg.last).map { listOf(p0, p1, p2)[it] } }.distinct())
    }

    // ---- The hidden junction stays hidden ----

    @Test
    fun theJunctionIsNoStopOfTheRide() {
        val stops = listOf(Stop("Milano", milan), Stop("Lugano", lugano, Stop.Kind.PASS))
        val legs = assertNotNull(CrossArea.split(both, stops.map { it.point }))
        val first = legs[0].stops(stops)
        val second = legs[1].stops(stops)
        assertEquals(listOf("Milano", null), first.map { it.name })
        assertEquals(Stop.Kind.STOP, first[1].kind)
        assertEquals(listOf(null, "Lugano"), second.map { it.name })
        assertEquals(stops[1], second[1])
        // The ride's own stops never change.
        assertEquals(2, stops.size)
    }

    // ---- Downloading the data of each area ----

    private val published = listOf(PublishedArea(italy, AreaFixtures.italyCells), PublishedArea(alps, AreaFixtures.alpsCells))

    @Test
    fun theDownloadAsksForTheCellsOfEachArea() {
        val d = assertNotNull(CrossArea.plan(published, emptyList(), listOf(milan, lugano)))
        assertEquals(listOf("it", "alps"), d.parts.map { it.area })
        assertEquals(listOf("routing-data", "routing-alps"), d.parts.map { it.release })
        assertEquals(d.parts.sumOf { it.bytes }, d.bytes)
        assertEquals(d.parts.sumOf { it.tileBytes }, d.tileBytes)
        assertFalse(d.ready)
        // Each area only its own cells: Italy's from Italy's build, the Alps' from theirs.
        assertTrue(d.parts[0].packs.all { it.file.endsWith("-it1.zip") })
        assertTrue(d.parts[1].packs.all { it.file.endsWith("-al1.zip") })
        // The data to route with afterwards: one part per leg, each with its area's cells.
        val data = d.data
        assertEquals(listOf("it", "alps"), data.parts.map { it.data.area })
        assertEquals(d.legs, data.parts.map { it.leg })
        assertEquals("cells-it1+cells-alps-al1", data.dataId)
    }

    @Test
    fun anAreaMetTwiceIsDownloadedOnce() {
        // Milan - Lugano - Milan: Italy, the Alps, Italy.
        val d = assertNotNull(CrossArea.plan(published, emptyList(), listOf(milan, lugano, milan)))
        assertEquals(listOf("it", "alps", "it"), d.legs.map { it.area })
        assertEquals(listOf("it", "alps"), d.parts.map { it.area })
        assertEquals(d.parts[0].packs.distinctBy { it.id }, d.parts[0].packs)
        assertEquals("cells-it1+cells-alps-al1", d.data.dataId)
    }

    @Test
    fun cellsAlreadyOnThePhoneAreNotDownloadedAgain() {
        val local = listOf(
            LocalArea("it", AreaFixtures.italyCells, AreaFixtures.italyCells.packs.map { it.id }.toSet()),
            LocalArea("alps", AreaFixtures.alpsCells, AreaFixtures.alpsCells.packs.map { it.id }.toSet()),
        )
        val d = assertNotNull(CrossArea.plan(published, local, listOf(milan, lugano)))
        assertTrue(d.ready)
        assertEquals(0L, d.bytes)
    }

    @Test
    fun noDownloadWhenALegLeavesItsAreasCells() {
        // The Alps without the cell of Lugano: that leg cannot be routed.
        val alpsWithoutLugano = AreaFixtures.cells("al2", 9 to 45, 11 to 46)
        assertNull(CrossArea.plan(listOf(PublishedArea(italy, AreaFixtures.italyCells), PublishedArea(alps, alpsWithoutLugano)), emptyList(), listOf(milan, lugano)))
        // One area only: nothing to split.
        assertNull(CrossArea.plan(published, emptyList(), listOf(milan, rome)))
        // Only Italy published: Lugano is in no area.
        assertNull(CrossArea.plan(published.take(1), emptyList(), listOf(milan, lugano)))
    }

    @Test
    fun theDataOnThePhoneRoutesAcrossAreasOffline() {
        val local = listOf(
            LocalArea("it", AreaFixtures.italyCells, AreaFixtures.italyCells.packs.map { it.id }.toSet()),
            LocalArea("alps", AreaFixtures.alpsCells, AreaFixtures.alpsCells.packs.map { it.id }.toSet()),
        )
        val data = assertNotNull(CrossArea.local(both, local, listOf(milan, lugano)))
        assertEquals(listOf("it", "alps"), data.parts.map { it.data.area })
        // Re-routing from a position in the Alps starts in the Alps.
        val back = assertNotNull(CrossArea.local(both, local, listOf(lugano, milan)))
        assertEquals("alps", back.parts.first().data.area)
        // Without the Alps cells on the phone: nothing.
        assertNull(CrossArea.local(both, local.take(1), listOf(milan, lugano)))
        val partial = listOf(local[0], LocalArea("alps", AreaFixtures.alpsCells, emptySet()))
        assertNull(CrossArea.local(both, partial, listOf(milan, lugano)))
        // One area only: the single-area data is used instead.
        assertNull(CrossArea.local(both, local, listOf(milan, rome)))
    }

    // ---- Joining the routes of the legs ----

    private fun m(type: Int, begin: Int, end: Int, km: Double = 1.0) = Maneuver(type, "t$type", begin, end, km = km, seconds = km * 60)

    private val p = (0..6).map { LatLon(45.0 + it * 0.01, 9.0) }

    @Test
    fun theLegsAreJoinedIntoOneRoute() {
        val legA = AreaLeg("it", 0, 0, after = p[3])
        val legB = AreaLeg("alps", 1, 1, before = p[3])
        val a = ValhallaRoute.Result(
            shape = p.subList(0, 4), km = 3.0, seconds = 180.0,
            maneuvers = listOf(m(1, 0, 1), m(10, 1, 3, km = 2.0), m(4, 3, 3, km = 0.0)),
            speedLimits = SpeedLimits(listOf(SpeedLimit(0.0, 500.0, 50))),
            surfaces = RoadSurfaces(listOf(RoughStretch(100.0, 200.0, Rough.GRAVEL))),
        )
        val b = ValhallaRoute.Result(
            shape = p.subList(3, 7), km = 3.0, seconds = 180.0,
            maneuvers = listOf(m(2, 0, 1), m(15, 1, 3, km = 2.0), m(4, 3, 3, km = 0.0)),
            speedLimits = SpeedLimits(listOf(SpeedLimit(0.0, 300.0, 90))),
            surfaces = RoadSurfaces(listOf(RoughStretch(50.0, 80.0, Rough.DIRT))),
        )
        val joined = CrossArea.join(listOf(a, b), listOf(legA, legB))
        // The junction point appears once.
        assertEquals(p, joined.shape)
        assertEquals(6.0, joined.km)
        assertEquals(360.0, joined.seconds)
        // No arrival or departure at the junction: the maneuver before it runs on to the next turn.
        assertEquals(listOf(1, 10, 15, 4), joined.maneuvers.map { it.type })
        val through = joined.maneuvers[1]
        assertEquals(1 to 4, through.beginIndex to through.endIndex)
        assertEquals(3.0, through.km)
        assertEquals(4 to 6, joined.maneuvers[2].beginIndex to joined.maneuvers[2].endIndex)
        assertEquals(6 to 6, joined.maneuvers[3].beginIndex to joined.maneuvers[3].endIndex)
        // Limits and rough stretches move by the length of the first leg.
        val startB = (1..3).sumOf { distanceKm(p[it - 1], p[it]) } * 1000
        assertEquals(50, joined.speedLimits.at(100.0))
        assertEquals(90, joined.speedLimits.at(startB + 100))
        assertNull(joined.speedLimits.at(startB + 400))
        val dirt = joined.surfaces.stretches.single { it.kind == Rough.DIRT }
        assertEquals(startB + 50, dirt.fromM, 1e-6)
        assertEquals(Rough.GRAVEL, joined.surfaces.at(150.0)?.kind)
    }

    @Test
    fun aSharedStopKeepsItsArrivalAndDeparture() {
        // Changing area at a real stop: the rider arrives there, as at any stop.
        val a = ValhallaRoute.Result(p.subList(0, 3), 2.0, 120.0, listOf(m(1, 0, 2), m(4, 2, 2, 0.0)))
        val b = ValhallaRoute.Result(p.subList(2, 5), 2.0, 120.0, listOf(m(1, 0, 2), m(4, 2, 2, 0.0)))
        val joined = CrossArea.join(listOf(a, b), listOf(AreaLeg("it", 0, 1), AreaLeg("alps", 1, 2)))
        assertEquals(p.subList(0, 5), joined.shape)
        assertEquals(listOf(1, 4, 1, 4), joined.maneuvers.map { it.type })
        assertEquals(listOf(0, 2, 2, 4), joined.maneuvers.map { it.beginIndex })
    }

    @Test
    fun legsThatDoNotMeetExactlyAreJoinedByTheirEnds() {
        // The engines may snap the junction a few metres apart: both points stay.
        val a = ValhallaRoute.Result(p.subList(0, 3), 2.0, 120.0)
        val b = ValhallaRoute.Result(p.subList(3, 6), 2.0, 120.0, speedLimits = SpeedLimits(listOf(SpeedLimit(0.0, 10.0, 30))))
        val joined = CrossArea.join(listOf(a, b), listOf(AreaLeg("it", 0, 0, after = p[2]), AreaLeg("alps", 1, 1, before = p[2])))
        assertEquals(p.subList(0, 6), joined.shape)
        val startB = (1..3).sumOf { distanceKm(p[it - 1], p[it]) } * 1000
        assertEquals(startB, joined.speedLimits.stretches.single().fromM, 1e-6)
        assertTrue(joined.maneuvers.isEmpty())
    }

    @Test
    fun oneLegIsTheRouteAsItIs() {
        val a = ValhallaRoute.Result(p, 6.0, 360.0, listOf(m(1, 0, 6)))
        assertEquals(a, CrossArea.join(listOf(a), listOf(AreaLeg("it", 0, 1))))
    }

    // ---- Which download the app offers ----

    @Test
    fun oneAreaHoldingTheRideStillWins() {
        val d = RoutingAreas.download(published, emptyList(), listOf(bolzano, innsbruck))
        assertIs<CellsDownload>(d)
        assertEquals("alps", d.area)
    }

    @Test
    fun aRideAcrossAreasGetsBothAreas() {
        val d = RoutingAreas.download(published, emptyList(), listOf(milan, lugano))
        assertIs<AreasDownload>(d)
        assertEquals(listOf("it", "alps"), d.parts.map { it.area })
    }

    @Test
    fun withoutTheOtherAreaTheStopsOutsideAreNamed() {
        // Only Italy is published: Lugano is not in its outline. No route with Italy's border
        // cell, which only holds the Italian side of the roads: Lugano is named instead.
        val italyOnly = published.take(1)
        assertNull(RoutingAreas.download(italyOnly, emptyList(), listOf(milan, lugano)))
        val stops = listOf(Stop("Milano", milan), Stop("Lugano", lugano))
        assertEquals(listOf(OutsideStop(2, "Lugano")), outsideStops(stops, italyOnly))
    }

    @Test
    fun withoutTheIndexItalyStillNamesTheStopsAbroad() {
        // No index yet: Italy has no outline, the country decides. Lugano is in Switzerland.
        val fallback = RoutingAreas.FALLBACK.single()
        assertTrue(fallback.covers(milan))
        assertTrue(fallback.covers(rome))
        assertFalse(fallback.covers(lugano))
        assertFalse(fallback.covers(innsbruck))
        // San Marino counts as Italy's data, as before.
        assertTrue(fallback.covers(LatLon(43.94, 12.45)))
        val published = listOf(PublishedArea(fallback, AreaFixtures.italyCells))
        assertNull(RoutingAreas.download(published, emptyList(), listOf(milan, lugano)))
        val stops = listOf(Stop("Milano", milan), Stop("Lugano", lugano))
        assertEquals(listOf(OutsideStop(2, "Lugano")), outsideStops(stops, published))
        // A ride inside Italy is as before.
        assertIs<CellsDownload>(RoutingAreas.download(published, emptyList(), listOf(milan, LatLon(45.5, 10.5))))
    }

    @Test
    fun areasThatDoNotOverlapNameTheStopsOutsideTheMainArea() {
        val west = box("w", 0.0, 40.0, 5.0, 45.0)
        val east = box("e", 5.5, 40.0, 10.0, 45.0)
        val a = LatLon(42.0, 2.0)
        val a2 = LatLon(43.0, 2.0)
        val b = LatLon(42.0, 8.0)
        val westCells = AreaFixtures.cells("w1", 2 to 42, 2 to 43)
        val eastCells = AreaFixtures.cells("e1", 8 to 42)
        val pub = listOf(PublishedArea(west, westCells), PublishedArea(east, eastCells))
        assertNull(RoutingAreas.download(pub, emptyList(), listOf(a, a2, b)))
        val stops = listOf(Stop("A", a), Stop("A2", a2), Stop("B", b))
        assertEquals(listOf(OutsideStop(3, "B")), outsideStops(stops, pub))
    }

    @Test
    fun theDataIdNamesEveryAreaOnce() {
        val it1 = RoutingData.Cells(AreaFixtures.italyCells, "it")
        val al1 = RoutingData.Cells(AreaFixtures.alpsCells, "alps")
        val data = RoutingData.Across(
            listOf(
                RoutingData.Across.Part(it1, AreaLeg("it", 0, 0, after = milan)),
                RoutingData.Across.Part(al1, AreaLeg("alps", 1, 1, before = milan, after = lugano)),
                RoutingData.Across.Part(it1, AreaLeg("it", 2, 2, before = lugano)),
            ),
        )
        assertEquals("cells-it1+cells-alps-al1", data.dataId)
        assertEquals("3.9.2", data.valhalla)
    }
}
