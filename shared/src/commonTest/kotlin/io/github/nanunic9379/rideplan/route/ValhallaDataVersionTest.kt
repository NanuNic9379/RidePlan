package io.github.nanunic9379.rideplan.route

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValhallaDataVersionTest {
    @Test
    fun patchVersionsWithinSameMajorMinorAreCompatible() {
        assertTrue(ValhallaDataVersion.compatible("3.9.0", "3.9.2"))
    }

    @Test
    fun differentMajorOrMinorVersionsAreRejected() {
        assertFalse(ValhallaDataVersion.compatible("3.8.9", "3.9.2"))
        assertFalse(ValhallaDataVersion.compatible("4.9.2", "3.9.2"))
    }

    @Test
    fun malformedVersionsAreRejected() {
        assertFalse(ValhallaDataVersion.compatible("unknown", "3.9.2"))
        assertFalse(ValhallaDataVersion.compatible("3.9.2", "3.x.2"))
    }
}
