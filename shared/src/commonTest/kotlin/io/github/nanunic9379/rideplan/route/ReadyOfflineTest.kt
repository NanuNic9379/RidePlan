package io.github.nanunic9379.rideplan.route

import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.Ride
import io.github.nanunic9379.rideplan.ride.Stop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ReadyOfflineTest {
    /** About [km] of road east from Turin, a point every 100 m. */
    private fun east(km: Int): List<LatLon> =
        (0..km * 10).map { LatLon(45.07, 7.68 + it * 0.1 / (111.32 * kotlin.math.cos(45.07 * kotlin.math.PI / 180))) }

    private val track = east(100)
    private val key = RideMap.rideKey(track)
    private val offer = RideMap.offer(track)

    private fun saved(ride: String = key, complete: Boolean = true, stale: Boolean = false) =
        SavedRideMap(RideMapInfo(ride, "Test", RideMap.FULL_ZOOM, 100, 1_000_000, version = "v1", complete = complete), 1_000_000, stale)

    private fun pack(id: String, bytes: Long = 5_000_000) =
        RoutingPack(id, 1, listOf(7.0, 45.0, 8.0, 46.0), "$id.tar.gz", bytes, bytes * 3, "0".repeat(64))

    private val cells = RoutingCells("b1", "2026-11-01", "3.5", listOf(pack("c1"), pack("c2")))

    @Test
    fun everythingOnThePhoneIsReady() {
        val r = ReadyOffline.check(ReadyOffline.mapState(key, listOf(saved())), offer, roadData = true)
        assertTrue(r.ready)
        assertFalse(r.canDownload)
        assertNull(r.mapPlan)
    }

    @Test
    fun roadDataButNoMapSaysMapMissingAndOffersIt() {
        val r = ReadyOffline.check(ReadyOffline.mapState(key, emptyList()), offer, roadData = true)
        assertEquals(listOf(OfflineNeed.MAP), r.missing)
        assertTrue(r.canDownload)
        assertNotNull(r.mapPlan)
        val get = ReadyOffline.getEverything(r, key, "Test", roadPlan = null)
        assertNull(get.road)
        assertEquals(key, get.map?.ride)
        assertEquals(r.mapPlan!!.estimate.bytes, get.bytes)
    }

    @Test
    fun aMapOfAnotherRideOrUnfinishedDoesNotCount() {
        assertEquals(MapState.MISSING, ReadyOffline.mapState(key, listOf(saved(ride = "other"))))
        assertEquals(MapState.MISSING, ReadyOffline.mapState(key, listOf(saved(complete = false))))
    }

    @Test
    fun anOldMapNeedsAnUpdateUnlessAFreshOneIsThere() {
        assertEquals(MapState.STALE, ReadyOffline.mapState(key, listOf(saved(stale = true))))
        assertEquals(MapState.SAVED, ReadyOffline.mapState(key, listOf(saved(stale = true), saved())))
        val r = ReadyOffline.check(MapState.STALE, offer, roadData = true)
        assertEquals(listOf(OfflineNeed.MAP_UPDATE), r.missing)
        assertTrue(r.mapMissing)
        assertTrue(r.canDownload)
    }

    @Test
    fun missingRoadDataComesFirstAndAddsUp() {
        val r = ReadyOffline.check(MapState.MISSING, offer, roadData = false)
        assertEquals(listOf(OfflineNeed.ROAD_DATA, OfflineNeed.MAP), r.missing)
        val road = CellsDownload(cells, cells.packs, wipe = false)
        val get = ReadyOffline.getEverything(r, key, null, road)
        assertSame(road, get.road)
        assertEquals(road.bytes + r.mapPlan!!.estimate.bytes, get.bytes)
        assertFalse(get.empty)
    }

    @Test
    fun onlyRoadDataMissingLeavesTheMapAlone() {
        val r = ReadyOffline.check(MapState.SAVED, offer, roadData = false)
        assertEquals(listOf(OfflineNeed.ROAD_DATA), r.missing)
        val get = ReadyOffline.getEverything(r, key, null, CellsDownload(cells, cells.packs, wipe = false))
        assertNull(get.map)
        assertNotNull(get.road)
    }

    @Test
    fun aRideTooLongForOneMapCanStillGetItsRoadData() {
        val tooLong = RideMap.Offer.TooLong(RideMap.Estimate(RideMap.LOWEST_ZOOM, 9_999, 90_000_000))
        val mapOnly = ReadyOffline.check(MapState.MISSING, tooLong, roadData = true)
        assertFalse(mapOnly.ready)
        assertNull(mapOnly.mapPlan)
        assertFalse(mapOnly.canDownload)
        val both = ReadyOffline.check(MapState.MISSING, tooLong, roadData = false)
        assertTrue(both.canDownload)
        assertNull(ReadyOffline.getEverything(both, key, null, null).map)
    }

    @Test
    fun aReducedMapIsStillOffered() {
        val plan = assertIs<RideMap.Offer.Full>(offer).plan
        val reduced = RideMap.Offer.Reduced(plan.estimate, plan)
        assertSame(plan, ReadyOffline.planOf(reduced))
    }

    @Test
    fun nothingLeftToDownloadIsNoDownload() {
        assertNull(ReadyOffline.toGet(null))
        assertNull(ReadyOffline.toGet(CellsDownload(cells, emptyList(), wipe = false)))
        val some = CellsDownload(cells, listOf(pack("c1")), wipe = false)
        assertSame(some, ReadyOffline.toGet(some))
        assertNull(ReadyOffline.toGet(AreasDownload(listOf(CellsDownload(cells, emptyList(), wipe = false)), emptyList())))
        val r = ReadyOffline.check(MapState.SAVED, offer, roadData = false)
        assertTrue(ReadyOffline.getEverything(r, key, null, CellsDownload(cells, emptyList(), wipe = false)).empty)
    }

    @Test
    fun roadDataIsCheckedOnTheStopsOrTheEndsOfATrack() {
        val stops = listOf(Stop("A", LatLon(45.0, 7.0)), Stop("B", LatLon(45.5, 7.5)), Stop("C", LatLon(46.0, 8.0)))
        assertEquals(stops.map { it.point }, ReadyOffline.points(Ride("r", track, stops, Ride.Source.PLANNED)))
        assertEquals(listOf(track.first(), track.last()), ReadyOffline.points(Ride("t", track, emptyList(), Ride.Source.GPX)))
        assertTrue(ReadyOffline.points(Ride("x", emptyList(), emptyList(), Ride.Source.GPX)).isEmpty())
    }
}
