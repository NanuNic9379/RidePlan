package io.github.nanunic9379.rideplan

import kotlin.test.Test
import kotlin.test.assertTrue

class MapDefaultsTest {
    @Test fun styleIsHttpsWithoutKey() {
        assertTrue(MapDefaults.STYLE.startsWith("https://"))
        assertTrue("key=" !in MapDefaults.STYLE)
    }

    @Test fun startIsOnEarth() {
        assertTrue(MapDefaults.START_LAT in -90.0..90.0 && MapDefaults.START_LON in -180.0..180.0)
        assertTrue(MapDefaults.START_ZOOM in 0.0..22.0)
    }
}
