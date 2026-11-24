package io.github.nanunic9379.rideplan.profile

import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Units, numbers and fuel by country (#15). */
class MeasuresTest {
    private fun near(expected: Double, actual: Double, tolerance: Double = 1e-6) =
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected, got $actual")

    /** The width of the offline ride map, in the rider's units. */
    @Test
    fun roundDistance() {
        assertEquals("5 km", Measures.roundDistance(5.0, Units.KM))
        assertEquals("3 mi", Measures.roundDistance(5.0, Units.MILES))
        assertEquals("10 km", Measures.roundDistance(9.6, Units.KM))
        assertEquals("1 mi", Measures.roundDistance(1.609344, Units.MILES))
    }

    @Test
    fun milesOnlyWhereRoadSignsAreInMiles() {
        listOf("US", "GB", "gb", "IM", "JE", "GG", "PR", "LR").forEach { assertEquals(Units.MILES, Regions.units(it), it) }
        listOf("IT", "DE", "FR", "ES", "CH", "AT", "SI", "IE", "CA", "AU", "IN").forEach { assertEquals(Units.KM, Regions.units(it), it) }
        assertEquals(Units.KM, Regions.units(null))
        assertEquals(Units.KM, Regions.units(""))
    }

    @Test
    fun automaticFollowsTheCountryAChoiceWins() {
        assertEquals(Units.MILES, AppSettings().unitsFor("US"))
        assertEquals(Units.KM, AppSettings().unitsFor("IT"))
        assertEquals(Units.KM, AppSettings().unitsFor(null))
        assertEquals(Units.KM, AppSettings(units = Units.KM).unitsFor("US"))
        assertEquals(Units.MILES, AppSettings(units = Units.MILES).unitsFor("IT"))
    }

    @Test
    fun settingsSavedBeforeStayAsTheyWere() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        // Saved before #15: km was written out and stays km.
        assertEquals(Units.KM, json.decodeFromString<AppSettings>("""{"units":"KM"}""").units)
        assertEquals(Units.MILES, json.decodeFromString<AppSettings>("""{"units":"MILES"}""").units)
        // Nothing saved yet: automatic.
        assertNull(json.decodeFromString<AppSettings>("{}").units)
        assertNull(json.decodeFromString<AppSettings>(json.encodeToString(AppSettings.serializer(), AppSettings())).units)
    }

    @Test
    fun decimalSeparatorOfTheLanguage() {
        assertFalse(Measures.decimalComma("en"))
        assertFalse(Measures.decimalComma("en-GB"))
        listOf("it", "de", "es", "fr", "it-IT", "de_CH").forEach { assertTrue(Measures.decimalComma(it), it) }
    }

    @Test
    fun numbers() {
        assertEquals("12.5", Measures.number(12.46, 1, comma = false))
        assertEquals("12,5", Measures.number(12.46, 1, comma = true))
        assertEquals("3", Measures.number(2.6, 0, comma = true))
        assertEquals("0.0", Measures.number(0.0, 1, comma = false))
        assertEquals("1.05", Measures.number(1.049, 2, comma = false))
        assertEquals("-0.5", Measures.number(-0.5, 1, comma = false))
        assertEquals("–", Measures.number(Double.NaN, 1, comma = false))
    }

    @Test
    fun distances() {
        assertEquals("8.4 km", Measures.distance(8.4, Units.KM, comma = false))
        assertEquals("8,4 km", Measures.distance(8.4, Units.KM, comma = true))
        assertEquals("126 km", Measures.distance(125.6, Units.KM, comma = true))
        // 9.97 rounds to 10: whole number, not "10.0".
        assertEquals("10 km", Measures.distance(9.97, Units.KM, comma = false))
        assertEquals("62 mi", Measures.distance(100.0, Units.MILES, comma = false))
        assertEquals("5.0 mi", Measures.distance(8.04672, Units.MILES, comma = false))
        assertEquals("3,1 mi", Measures.distance(5.0, Units.MILES, comma = true))
        assertEquals("124", Measures.lengthNumber(200.0, Units.MILES, comma = false))
    }

    @Test
    fun navigationDistances() {
        assertEquals("450 m", Measures.shortDistance(447.0, Units.KM, comma = false))
        assertEquals("0 m", Measures.shortDistance(-3.0, Units.KM, comma = false))
        // 996 m would be "1000 m": it becomes km.
        assertEquals("1.0 km", Measures.shortDistance(996.0, Units.KM, comma = false))
        assertEquals("2,3 km", Measures.shortDistance(2300.0, Units.KM, comma = true))
        assertEquals("330 ft", Measures.shortDistance(100.0, Units.MILES, comma = false))
        assertEquals("0.1 mi", Measures.shortDistance(161.0, Units.MILES, comma = false))
        assertEquals("1.2 mi", Measures.shortDistance(2000.0, Units.MILES, comma = false))
        assertEquals("19 mi", Measures.shortDistance(30_000.0, Units.MILES, comma = false))
    }

    @Test
    fun spokenDistances() {
        // Metric: the same rounding as before #15.
        assertEquals("2 km", Measures.spoken(2000.0, Units.KM, comma = false))
        assertEquals("1 km", Measures.spoken(960.0, Units.KM, comma = false))
        assertEquals("300 m", Measures.spoken(310.0, Units.KM, comma = false))
        assertEquals("50 m", Measures.spoken(4.0, Units.KM, comma = false))
        // Imperial: feet, then tenths of a mile, then whole miles.
        assertEquals("100 ft", Measures.spoken(10.0, Units.MILES, comma = false))
        assertEquals("300 ft", Measures.spoken(91.0, Units.MILES, comma = false))
        assertEquals("0.5 mi", Measures.spoken(800.0, Units.MILES, comma = false))
        assertEquals("0,5 mi", Measures.spoken(800.0, Units.MILES, comma = true))
        assertEquals("1 mi", Measures.spoken(1600.0, Units.MILES, comma = false))
        assertEquals("12 mi", Measures.spoken(19_312.0, Units.MILES, comma = false))
    }

    @Test
    fun speeds() {
        assertEquals(50, Measures.speed(50.0, Units.KM))
        assertEquals(31, Measures.speed(50.0, Units.MILES))
        assertEquals(70, Measures.speed(112.65, Units.MILES))
        near(1.609344, Units.MILES.toKm(1.0))
        near(100.0, Units.MILES.toKm(Units.MILES.of(100.0)))
        assertEquals(42.0, Units.KM.toKm(42.0))
    }

    @Test
    fun fuelByCountry() {
        assertEquals(FuelUnits.METRIC, Regions.fuel(Units.KM, "US"))
        assertEquals(FuelUnits.US, Regions.fuel(Units.MILES, "US"))
        assertEquals(FuelUnits.IMPERIAL, Regions.fuel(Units.MILES, "GB"))
        // Miles chosen by hand in a metric country: miles per imperial gallon, tank in litres.
        assertEquals(FuelUnits.IMPERIAL, Regions.fuel(Units.MILES, "IT"))
        assertEquals("gal", FuelUnits.US.tankSymbol)
        assertEquals("l", FuelUnits.IMPERIAL.tankSymbol)
        assertEquals("km/l", FuelUnits.METRIC.economySymbol)
        assertEquals("mpg", FuelUnits.US.economySymbol)
    }

    @Test
    fun fuelConversions() {
        // A 4 US gallon tank is 15.14 litres; 1 litre stays 1 litre in metric and UK.
        near(15.141647136, FuelUnits.US.tankToLitres(4.0))
        near(4.0, FuelUnits.US.tankFromLitres(15.141647136))
        near(17.0, FuelUnits.IMPERIAL.tankFromLitres(17.0))
        near(17.0, FuelUnits.METRIC.tankToLitres(17.0))
        // 20 km/l = 47.04 mpg (US) = 56.50 mpg (UK).
        near(47.043, FuelUnits.US.economyFromKmPerLitre(20.0), 0.001)
        near(56.497, FuelUnits.IMPERIAL.economyFromKmPerLitre(20.0), 0.001)
        near(20.0, FuelUnits.US.economyToKmPerLitre(FuelUnits.US.economyFromKmPerLitre(20.0)))
        near(20.0, FuelUnits.IMPERIAL.economyToKmPerLitre(FuelUnits.IMPERIAL.economyFromKmPerLitre(20.0)))
        near(20.0, FuelUnits.METRIC.economyFromKmPerLitre(20.0))
        // The range does not depend on the units: 4 gal × 47.04 mpg = 188 mi = 303 km.
        val bike = Bike("b", "Bike", tankLitres = FuelUnits.US.tankToLitres(4.0), kmPerLitre = FuelUnits.US.economyToKmPerLitre(47.043))
        assertEquals(303, bike.rangeKm)
    }
}
