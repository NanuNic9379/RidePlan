package io.github.nanunic9379.rideplan.route

import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.countryNames
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CountryRulesTest {
    @Test
    fun tableIsWellFormed() {
        val codes = CountryRules.all.keys
        assertTrue(codes.size >= 40)
        for ((code, r) in CountryRules.all) {
            assertEquals(code, r.country)
            assertTrue(Regex("[A-Z]{2}").matches(code), code)
            assertTrue(Regex("""\d{4}-\d{2}""").matches(r.checked), "$code ${r.checked}")
            r.motorwayMinCc?.let { assertTrue(it in 50..500, "$code $it") }
            r.motorwayMinSpeedKmh?.let { assertTrue(it in 30..130, "$code $it") }
            r.source?.let { assertTrue(it.startsWith("https://"), "$code $it") }
        }
    }

    @Test
    fun nothingIsMarkedVerifiedYet() {
        // Flip an entry to verified only after a person checked it against its source.
        assertTrue(CountryRules.all.values.none { it.verified })
    }

    @Test
    fun knownRules() {
        assertEquals(150, CountryRules.of("IT")?.motorwayMinCc)
        assertEquals(CountryRules.Tolls.MOTORWAYS, CountryRules.of("IT")?.tolls)
        assertEquals(CountryRules.Answer.YES, CountryRules.of("CH")?.vignette)
        assertEquals(CountryRules.Answer.YES, CountryRules.of("AT")?.vignette)
        assertEquals(CountryRules.Answer.YES, CountryRules.of("SI")?.vignette)
        assertEquals(CountryRules.Answer.YES, CountryRules.of("HU")?.vignette)
        assertEquals(CountryRules.Answer.NO, CountryRules.of("DE")?.vignette)
        assertEquals(CountryRules.Answer.NO, CountryRules.of("FR")?.vignette)
        assertEquals(CountryRules.Tolls.MOTORWAYS, CountryRules.of("FR")?.tolls)
        assertEquals(60, CountryRules.of("DE")?.motorwayMinSpeedKmh)
    }

    @Test
    fun lookupIgnoresCase() {
        assertEquals("CH", CountryRules.of("ch")?.country)
        assertNull(CountryRules.of(null))
        assertNull(CountryRules.of("US"))
        assertNull(CountryRules.of(""))
    }

    @Test
    fun speedCameraWarningsOnlyInItaly() {
        // Owner decision (#15): Italy only, off everywhere else, even where it may be legal.
        assertTrue(CountryRules.speedCameraWarningsAllowed("IT"))
        assertTrue(CountryRules.speedCameraWarningsAllowed("it"))
        for (code in CountryRules.all.keys - "IT") assertFalse(CountryRules.speedCameraWarningsAllowed(code), code)
        assertFalse(CountryRules.speedCameraWarningsAllowed("GB")) // legal there, still off
        assertFalse(CountryRules.speedCameraWarningsAllowed("US"))
        assertFalse(CountryRules.speedCameraWarningsAllowed(null))
        assertEquals(setOf("IT"), CountryRules.SPEED_CAMERA_WARNINGS_COUNTRIES)
    }

    @Test
    fun speedCameraWarningsAreIllegalWhereTheDocSaysSo() {
        for (code in listOf("FR", "DE", "CH", "BE", "SK")) {
            assertEquals(CountryRules.Answer.NO, CountryRules.of(code)?.speedCameraWarningsLegal, code)
        }
    }

    @Test
    fun everyCountryHasANameInTheSheet() {
        assertEquals(Countries.known, countryNames.keys)
    }

    @Test
    fun notesFollowTheRules() {
        assertEquals(
            listOf(CountryNote.Vignette, CountryNote.TollSome, CountryNote.MotorwayMinSpeed(80)),
            CountryWarnings.notes(CountryRules.of("CH")!!),
        )
        assertEquals(listOf(CountryNote.TollMotorways, CountryNote.MotorwayMinCc(150)), CountryWarnings.notes(CountryRules.of("IT")!!))
        assertEquals(listOf(CountryNote.VignetteUnclear), CountryWarnings.notes(CountryRules.of("CZ")!!))
        assertEquals(emptyList(), CountryWarnings.notes(CountryRules.of("LU")!!))
        assertFalse(CountryRules.of("LU")!!.hasWarnings)
        assertTrue(CountryRules.of("CH")!!.hasWarnings)
    }

    @Test
    fun warningsLeaveOutTheHomeCountry() {
        val w = CountryWarnings.of(listOf("IT", "CH"), home = "IT")
        assertEquals(listOf("IT", "CH"), w.countries)
        assertEquals(listOf("CH"), w.notices.map { it.country })
        assertTrue(CountryNote.Vignette in w.notices.single().notes)
        assertFalse(w.isEmpty)
        assertTrue(w.unverified)
    }

    @Test
    fun rideAtHomeSaysNothing() {
        assertTrue(CountryWarnings.of(listOf("IT"), "IT").isEmpty)
        assertTrue(CountryWarnings.of(listOf("IT"), "it").isEmpty)
        assertTrue(CountryWarnings.of(emptyList(), "IT").isEmpty)
    }

    @Test
    fun rideAbroadInOneCountry() {
        val w = CountryWarnings.of(listOf("AT"), "IT")
        assertEquals(listOf("AT"), w.notices.map { it.country })
        // A German rider in Luxembourg: no rule to show, nothing to say.
        assertTrue(CountryWarnings.of(listOf("LU"), "DE").isEmpty)
    }

    @Test
    fun unknownHomeShowsEveryCountry() {
        val w = CountryWarnings.of(listOf("IT", "CH"), null)
        assertEquals(listOf("IT", "CH"), w.notices.map { it.country })
        assertEquals(listOf("IT", "CH"), CountryWarnings.of(listOf("IT", "CH"), "").notices.map { it.country })
    }

    @Test
    fun twoCountriesWithoutRulesStillListed() {
        val w = CountryWarnings.of(listOf("LU", "BE"), "NL")
        assertTrue(w.notices.isEmpty())
        assertFalse(w.isEmpty)
        assertEquals(listOf("LU", "BE"), w.countries)
    }

    @Test
    fun fromTheRideLine() {
        val milan = LatLon(45.4642, 9.1900)
        val zurich = LatLon(47.3769, 8.5417)
        val w = CountryWarnings.forLine(listOf(milan, zurich), "IT")
        assertEquals(listOf("IT", "CH"), w.countries)
        assertEquals(listOf("CH"), w.notices.map { it.country })
        assertTrue(CountryWarnings.forLine(listOf(milan, LatLon(45.8081, 9.0852)), "IT").isEmpty)
    }
}
