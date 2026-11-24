package io.github.nanunic9379.rideplan.route

import io.github.nanunic9379.rideplan.MapDefaults
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Which roads the off-road overlay of the Explore map marks (#9). */
class OffroadRulesTest {
    @Test
    fun unpavedRoadsAreMarked() {
        for (c in listOf("primary", "secondary", "tertiary", "minor", "service", "track")) {
            assertEquals(OffroadKind.UNPAVED, OffroadRules.kindOf(c, "unpaved", null), c)
        }
    }

    @Test
    fun pavedAndUnknownRoadsAreNot() {
        assertNull(OffroadRules.kindOf("secondary", "paved", null))
        assertNull(OffroadRules.kindOf("secondary", null, null))
        assertNull(OffroadRules.kindOf("minor", null, "yes"))
    }

    @Test
    fun tracksAreUnpavedUnlessTheTilesSayPaved() {
        assertEquals(OffroadKind.UNPAVED, OffroadRules.kindOf("track", null, null))
        assertNull(OffroadRules.kindOf("track", "paved", null))
    }

    @Test
    fun closedRoadsWinOverSurface() {
        assertEquals(OffroadKind.NO_ACCESS, OffroadRules.kindOf("minor", "unpaved", "no"))
        assertEquals(OffroadKind.NO_ACCESS, OffroadRules.kindOf("service", null, "no"))
        assertEquals(OffroadKind.NO_ACCESS, OffroadRules.kindOf("track", "paved", "no"))
    }

    @Test
    fun pathsRailAndFerriesAreLeftOut() {
        for (c in listOf("path", "rail", "transit", "ferry", "pier", "bridge", null)) {
            assertNull(OffroadRules.kindOf(c, "unpaved", "no"), "$c")
        }
        assertFalse("path" in OffroadRules.ROAD_CLASSES)
        assertTrue(OffroadRules.TRACK in OffroadRules.ROAD_CLASSES)
    }

    @Test
    fun drawnFromTheBaseMapTiles() {
        // The overlay reads the OpenMapTiles source of the OpenFreeMap style the app uses.
        assertTrue("openfreemap" in MapDefaults.STYLE)
        assertEquals("openmaptiles", OffroadRules.SOURCE)
        assertEquals("transportation", OffroadRules.LAYER)
        // The tiles stop at zoom 14 (TileJSON maxzoom); the overlay starts well before.
        assertTrue(OffroadRules.MIN_ZOOM in 10f..14f)
    }
}
