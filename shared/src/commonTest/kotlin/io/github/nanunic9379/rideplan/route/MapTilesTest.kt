package io.github.nanunic9379.rideplan.route

import io.github.nanunic9379.rideplan.ride.LatLon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapTilesTest {
    @Test
    fun tileNumberingLikeTheWebMap() {
        // Rome at zoom 10 is tile 547/380 (OpenStreetMap slippy-map numbering).
        assertEquals(547, MapTiles.x(12.4964, 10))
        assertEquals(380, MapTiles.y(41.9028, 10))
        val b = MapTiles.bbox(10, 547, 380)
        assertTrue(b[0] <= 12.4964 && 12.4964 <= b[2] && b[1] <= 41.9028 && 41.9028 <= b[3])
        // Edges of the world stay inside the grid.
        assertEquals(0, MapTiles.y(89.9, 3))
        assertEquals(7, MapTiles.x(180.0, 3))
    }

    @Test
    fun countWholeWorldAndRegion() {
        assertEquals(1 + 4 + 16L, MapTiles.count(listOf(-180.0, -85.0, 180.0, 85.0), 0, 2))
        // Italy at zoom 0-13: far beyond one OpenFreeMap download, so a region needs its own file.
        val italy = MapTiles.count(listOf(6.6, 35.5, 18.6, 47.1), 0, 13)
        assertTrue(italy > 40 * MapTiles.OPENFREEMAP_MAX_TILES, "Italy: $italy tiles")
    }

    /** About 300 km of road, a point every km, east from Turin. */
    private val track = (0..300).map { LatLon(45.07, 7.68 + it * 3.8 / 300) }

    @Test
    fun rideCorridorFitsOneOpenFreeMapDownload() {
        val tiles = MapTiles.corridor(track, marginKm = 5.0, minZoom = 5, maxZoom = 14)
        assertTrue(tiles in 500..MapTiles.OPENFREEMAP_MAX_TILES, "corridor: $tiles tiles")
        // A wider strip costs more; the whole bounding box much more.
        assertTrue(MapTiles.corridor(track, marginKm = 15.0, minZoom = 5, maxZoom = 14) > tiles)
        val box = MapTiles.count(listOf(7.6, 44.9, 11.6, 45.2), 5, 14)
        assertTrue(box > tiles)
    }

    @Test
    fun corridorOfASinglePointAndEmptyTrack() {
        assertEquals(0, MapTiles.corridor(emptyList(), 5.0, 5, 14))
        // One point with 1 km around it, at zoom 0: the one world tile.
        assertEquals(1, MapTiles.corridor(listOf(LatLon(45.0, 7.0)), 1.0, 0, 0))
    }
}
