package io.github.nanunic9379.rideplan.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateRulesTest {
    @Test
    fun tagValido() {
        assertEquals(42, UpdateRules.versionCodeFromTag("v0.1.42"))
        assertEquals(7, UpdateRules.versionCodeFromTag(" v1.0.7 "))
    }

    @Test
    fun tagNonValido() {
        assertNull(UpdateRules.versionCodeFromTag("0.1.42"))
        assertNull(UpdateRules.versionCodeFromTag("v0.1.42-beta"))
        assertNull(UpdateRules.versionCodeFromTag("latest"))
    }

    @Test
    fun soloVersioniPiuNuove() {
        assertTrue(UpdateRules.isNewer("v0.1.11", 10))
        assertFalse(UpdateRules.isNewer("v0.1.10", 10))
        assertFalse(UpdateRules.isNewer("v0.1.9", 10))
        assertFalse(UpdateRules.isNewer("boh", 10))
    }

    @Test
    fun apkDelProcessoreGiusto() {
        val assets = listOf("RidePlan-0.1.5-armeabi-v7a.apk", "RidePlan-0.1.5-arm64-v8a.apk", "RidePlan-0.1.5-x86_64.apk")
        assertEquals("RidePlan-0.1.5-arm64-v8a.apk", UpdateRules.pickApk(assets, listOf("arm64-v8a", "armeabi-v7a")))
        assertEquals("RidePlan-0.1.5-armeabi-v7a.apk", UpdateRules.pickApk(assets, listOf("armeabi-v7a", "armeabi")))
        assertNull(UpdateRules.pickApk(assets, listOf("x86")))
    }

    @Test
    fun digest() {
        val hex = "AB".repeat(32)
        assertEquals("ab".repeat(32), UpdateRules.sha256FromDigest("sha256:$hex"))
        assertNull(UpdateRules.sha256FromDigest("sha256:123"))
        assertNull(UpdateRules.sha256FromDigest(null))
    }
}
