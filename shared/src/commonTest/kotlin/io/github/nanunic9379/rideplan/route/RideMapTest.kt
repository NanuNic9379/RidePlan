package io.github.nanunic9379.rideplan.route

import io.github.nanunic9379.rideplan.ride.LatLon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RideMapTest {
    /** About [km] of road east from Turin, a point every 100 m (like a calculated route). */
    private fun east(km: Int, lat: Double = 45.07, lon: Double = 7.68): List<LatLon> =
        (0..km * 10).map { LatLon(lat, lon + it * 0.1 / (111.32 * kotlin.math.cos(lat * kotlin.math.PI / 180))) }

    /** A long zig-zag tour: [legs] legs of 300 km, alternating east and north-east. */
    private fun tour(legs: Int): List<LatLon> {
        val out = ArrayList<LatLon>()
        var p = LatLon(44.0, 6.0)
        repeat(legs) { leg ->
            val (dLat, dLon) = if (leg % 2 == 0) 0.0 to 3.8 else 1.9 to 1.9
            for (i in 1..300) out += LatLon(p.lat + dLat * i / 300, p.lon + dLon * i / 300)
            p = out.last()
        }
        return out
    }

    @Test
    fun noRoadNoOffer() {
        assertNull(RideMap.offer(emptyList()))
        assertNull(RideMap.offer(listOf(LatLon(45.0, 7.0))))
    }

    @Test
    fun aHalfDayRideIsOneFullDetailDownload() {
        val offer = assertIs<RideMap.Offer.Full>(RideMap.offer(east(100)))
        val e = offer.plan.estimate
        assertEquals(RideMap.FULL_ZOOM, e.maxZoom)
        assertTrue(e.fits)
        assertTrue(e.tiles in 500..MapTiles.OPENFREEMAP_MAX_TILES.toLong(), "tiles: ${e.tiles}")
        assertTrue(e.bytes <= MapTiles.OPENFREEMAP_MAX_BYTES, "bytes: ${e.bytes}")
        // Same order of size as the count made for the docs (MapTiles.corridor).
        val corridor = MapTiles.corridor(east(100), RideMap.MARGIN_KM, RideMap.MIN_ZOOM, RideMap.FULL_ZOOM)
        assertTrue(e.tiles >= corridor && e.tiles < corridor * 1.2, "${e.tiles} vs $corridor")
    }

    @Test
    fun aShortRideMatchesTheMeasuredDownload() {
        // 25 km east of Turin, saved on the emulator: 222 tiles, 16.5 MB with style and fonts.
        val e = assertIs<RideMap.Offer.Full>(RideMap.offer(east(25))).plan.estimate
        assertTrue(e.tiles in 200..250, "tiles: ${e.tiles}")
        assertTrue(e.bytes in 15_000_000..19_000_000, "bytes: ${e.bytes}")
    }

    @Test
    fun aDayRideGetsOneZoomLess() {
        val offer = assertIs<RideMap.Offer.Reduced>(RideMap.offer(east(300)))
        assertEquals(13, offer.plan.estimate.maxZoom)
        assertTrue(offer.plan.estimate.fits)
    }

    @Test
    fun sizeGrowsWithDetail() {
        assertTrue(RideMap.tileBytes(5) > RideMap.tileBytes(10))
        assertTrue(RideMap.tileBytes(10) > RideMap.tileBytes(12))
        assertTrue(RideMap.tileBytes(12) > RideMap.tileBytes(14))
    }

    @Test
    fun aLongTourGetsLessDetail() {
        val offer = assertIs<RideMap.Offer.Reduced>(RideMap.offer(tour(4)))
        assertFalse(offer.full.fits)
        assertEquals(RideMap.FULL_ZOOM, offer.full.maxZoom)
        val e = offer.plan.estimate
        assertTrue(e.fits)
        assertTrue(e.maxZoom in RideMap.LOWEST_ZOOM until RideMap.FULL_ZOOM, "zoom ${e.maxZoom}")
        assertTrue(e.tiles < offer.full.tiles)
    }

    @Test
    fun aVeryLongTourMustBeSplit() {
        val offer = assertIs<RideMap.Offer.TooLong>(RideMap.offer(tour(40)))
        assertEquals(RideMap.LOWEST_ZOOM, offer.lowest.maxZoom)
        assertFalse(offer.lowest.fits)
    }

    @Test
    fun theStripIsFiveKilometresEachSide() {
        val track = east(50)
        val tiles = RideMap.tiles(track, 14)
        val mid = track[track.size / 2]
        fun tileOf(lat: Double, lon: Double) = (MapTiles.x(lon, 14).toLong() shl 32) or MapTiles.y(lat, 14).toLong()
        val kmLat = 1 / 110.57
        assertTrue(tileOf(mid.lat, mid.lon) in tiles)
        assertTrue(tileOf(mid.lat + 4.5 * kmLat, mid.lon) in tiles)
        assertTrue(tileOf(mid.lat - 4.5 * kmLat, mid.lon) in tiles)
        assertFalse(tileOf(mid.lat + 9 * kmLat, mid.lon) in tiles)
        assertFalse(tileOf(mid.lat - 9 * kmLat, mid.lon) in tiles)
        // A wider strip is bigger.
        assertTrue(RideMap.tiles(track, 14, marginKm = 10.0).size > tiles.size)
    }

    @Test
    fun lowerZoomsAreTheParents() {
        val track = tour(1)
        for (z in 10..14) {
            assertEquals(RideMap.tiles(track, z - 1), RideMap.parents(RideMap.tiles(track, z)), "zoom $z")
        }
    }

    @Test
    fun areasCoverExactlyTheTiles() {
        for (track in listOf(east(80), tour(1))) {
            val z = 13
            val tiles = RideMap.tiles(track, z)
            val areas = RideMap.areas(tiles, z)
            assertTrue(areas.size < tiles.size, "one area per run of tiles in a row")
            val covered = HashSet<Long>()
            for (a in areas) {
                assertTrue(a.west < a.east && a.south < a.north)
                val y = MapTiles.y(a.north, z)
                assertEquals(y, MapTiles.y(a.south, z), "an area is one row")
                for (x in MapTiles.x(a.west, z)..MapTiles.x(a.east, z)) {
                    assertTrue(covered.add((x.toLong() shl 32) or y.toLong()), "areas do not overlap")
                }
            }
            assertEquals(tiles, covered)
        }
        assertTrue(RideMap.areas(emptySet(), 14).isEmpty())
    }

    @Test
    fun limitsOfOneDownload() {
        assertFalse(RideMap.overLimit(3_000, 50_000_000))
        assertTrue(RideMap.overLimit(3_001, 1))
        assertTrue(RideMap.overLimit(1, 50_000_001))
        assertTrue(RideMap.Estimate(14, 3_000, 50_000_000).fits)
        assertFalse(RideMap.Estimate(14, 3_001, 1).fits)
        assertFalse(RideMap.Estimate(14, 1, 50_000_001).fits)
    }

    @Test
    fun rideKeyFollowsTheRoad() {
        val road = east(30)
        assertEquals(RideMap.rideKey(road), RideMap.rideKey(road.map { LatLon(it.lat + 1e-7, it.lon - 1e-7) }))
        assertEquals(16, RideMap.rideKey(road).length)
        assertNotEquals(RideMap.rideKey(road), RideMap.rideKey(road.map { LatLon(it.lat + 0.001, it.lon) }))
        assertNotEquals(RideMap.rideKey(road), RideMap.rideKey(road.reversed()))
        assertNotEquals(RideMap.rideKey(road), RideMap.rideKey(road.dropLast(1)))
    }

    @Test
    fun thinningKeepsTheEnds() {
        val road = east(10)
        val thin = RideMap.thin(road)
        assertEquals(road.first(), thin.first())
        assertEquals(road.last(), thin.last())
        assertTrue(thin.size in 30..50, "${thin.size}")
        assertEquals(listOf(road[0], road[1]), RideMap.thin(road.take(2)))
    }

    @Test
    fun tilesVersionFromTheAddress() {
        assertEquals("20251119_001001_pt", RideMap.tilesVersion("https://tiles.openfreemap.org/planet/20251119_001001_pt/14/8541/5875.pbf"))
        assertNull(RideMap.tilesVersion("https://tiles.openfreemap.org/planet"))
        assertNull(RideMap.tilesVersion("https://tiles.openfreemap.org/styles/liberty"))
        assertNull(RideMap.tilesVersion("https://tiles.openfreemap.org/fonts/Noto%20Sans%20Regular/0-255.pbf"))
        assertNull(RideMap.tilesVersion("https://example.org/planet/20251119_001001_pt/14/1/1.pbf"))
    }

    @Test
    fun fontRangesFromTheAddress() {
        assertEquals(0, RideMap.glyphRangeStart("https://tiles.openfreemap.org/fonts/Noto%20Sans%20Regular/0-255.pbf"))
        assertEquals(12288, RideMap.glyphRangeStart("https://tiles.openfreemap.org/fonts/Noto%20Sans%20Bold/12288-12543.pbf"))
        assertTrue(RideMap.glyphRangeStart("https://tiles.openfreemap.org/fonts/Noto%20Sans%20Bold/12288-12543.pbf")!! >= RideMap.GLYPHS_KEPT)
        assertTrue(RideMap.glyphRangeStart("https://tiles.openfreemap.org/fonts/Noto%20Sans%20Italic/1024-1279.pbf")!! < RideMap.GLYPHS_KEPT)
        assertNull(RideMap.glyphRangeStart("https://tiles.openfreemap.org/planet/20251119_001001_pt/14/8541/5875.pbf"))
        assertNull(RideMap.glyphRangeStart("https://tiles.openfreemap.org/styles/liberty"))
    }

    @Test
    fun infoSurvivesTheRoundTrip() {
        val info = RideMapInfo("abc", "Colle del Nivolet", 14, 1_800, 39_000_000, "20251119_001001_pt", 1_700_000_000_000, complete = true)
        assertEquals(info, RideMapInfo.decode(info.encode()))
        assertNull(RideMapInfo.decode(null))
        assertNull(RideMapInfo.decode("not json".encodeToByteArray()))
        assertNull(RideMapInfo.decode(byteArrayOf()))
        // A newer version may add fields: still readable.
        val newer = """{"ride":"k","maxZoom":12,"tiles":5,"bytes":9,"parts":3}""".encodeToByteArray()
        assertEquals(RideMapInfo("k", maxZoom = 12, tiles = 5, bytes = 9), RideMapInfo.decode(newer))
    }
}
