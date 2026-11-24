package io.github.nanunic9379.rideplan.update

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoCheckTest {
    private val interval = UpdateRules.AUTO_CHECK_INTERVAL_MS

    @Test
    fun firstForegroundChecks() {
        assertTrue(UpdateRules.shouldAutoCheck(nowMs = 1_000L, lastCheckMs = null))
    }

    @Test
    fun quickReturnDoesNotCheckAgain() {
        assertFalse(UpdateRules.shouldAutoCheck(nowMs = 1_000L + interval - 1, lastCheckMs = 1_000L))
    }

    @Test
    fun returnAfterIntervalChecks() {
        assertTrue(UpdateRules.shouldAutoCheck(nowMs = 1_000L + interval, lastCheckMs = 1_000L))
        assertTrue(UpdateRules.shouldAutoCheck(nowMs = 1_000L + 3 * 24 * 3600 * 1000L, lastCheckMs = 1_000L))
    }

    @Test
    fun clockGoingBackwardsChecks() {
        assertTrue(UpdateRules.shouldAutoCheck(nowMs = 500L, lastCheckMs = 1_000L))
    }

    @Test
    fun offersWhenNothingIsShown() {
        assertTrue(UpdateRules.mayOfferAutomatically("0.1.130", current = null, dismissed = null))
        assertTrue(UpdateRules.mayOfferAutomatically("0.1.130", UpdateUi.Available("0.1.129"), dismissed = null))
    }

    @Test
    fun dismissedVersionIsNotOfferedAgainButANewerOneIs() {
        assertFalse(UpdateRules.mayOfferAutomatically("0.1.130", current = null, dismissed = "0.1.130"))
        assertTrue(UpdateRules.mayOfferAutomatically("0.1.131", current = null, dismissed = "0.1.130"))
    }

    @Test
    fun neverReplacesWorkInProgress() {
        assertFalse(UpdateRules.mayOfferAutomatically("0.1.130", UpdateUi.Downloading(40), dismissed = null))
        assertFalse(UpdateRules.mayOfferAutomatically("0.1.130", UpdateUi.Installing, dismissed = null))
        assertFalse(UpdateRules.mayOfferAutomatically("0.1.130", UpdateUi.NeedsPermission, dismissed = null))
        assertFalse(UpdateRules.mayOfferAutomatically("0.1.130", UpdateUi.Failed(UpdateError.NETWORK), dismissed = null))
    }

    @Test
    fun pollingStopsWhileABannerIsShown() {
        assertFalse(UpdateRules.pollingPaused(null))
        assertTrue(UpdateRules.pollingPaused(UpdateUi.Available("0.1.130")))
        assertTrue(UpdateRules.pollingPaused(UpdateUi.Downloading(null)))
        assertTrue(UpdateRules.pollingPaused(UpdateUi.Failed(UpdateError.NETWORK)))
    }
}
