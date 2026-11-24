package io.github.nanunic9379.rideplan.profile

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Units by country (#15). The setting "Automatic" ([AppSettings.units] = null) follows the
 * country of the phone: miles where road signs are in miles, km everywhere else.
 */
object Regions {
    /**
     * Countries (ISO 3166 alpha-2) whose road signs and speed limits are in miles: the United
     * States and their territories, the United Kingdom and the Crown Dependencies, Liberia,
     * Myanmar and some Caribbean countries.
     */
    val MILES: Set<String> = setOf(
        "US", "PR", "GU", "VI", "AS", "MP", "UM",
        "GB", "IM", "JE", "GG",
        "LR", "MM",
        "BS", "BZ", "KY", "VG", "AG", "DM", "GD", "KN", "LC", "VC", "AI", "MS", "TC", "FK",
    )

    /** Countries where fuel is sold in US gallons; the rest of the miles countries use litres. */
    val US_GALLONS: Set<String> = setOf("US", "PR", "GU", "VI", "AS", "MP", "UM", "LR")

    /** Units for a country code ("US", "gb"); km when it is unknown or empty. */
    fun units(region: String?): Units =
        if (region?.uppercase() in MILES) Units.MILES else Units.KM

    /** How fuel is measured with [units] in [region]. */
    fun fuel(units: Units, region: String?): FuelUnits = when {
        units == Units.KM -> FuelUnits.METRIC
        region?.uppercase() in US_GALLONS -> FuelUnits.US
        else -> FuelUnits.IMPERIAL
    }
}

/** The units the app really uses: the chosen ones, or those of [region] on "Automatic". */
fun AppSettings.unitsFor(region: String?): Units = units ?: Regions.units(region)

/**
 * Tank and consumption on screen (#15). The garage always stores litres and km per litre:
 * only what is shown and typed changes.
 * - METRIC: litres, km/l.
 * - IMPERIAL (United Kingdom): fuel is sold in litres, consumption in miles per imperial gallon.
 * - US: US gallons, miles per US gallon.
 */
enum class FuelUnits(
    private val tankLitres: Double,
    private val economyGallonLitres: Double,
    private val economyKm: Double,
    val tankSymbol: String,
    val economySymbol: String,
) {
    METRIC(1.0, 1.0, 1.0, "l", "km/l"),
    IMPERIAL(1.0, IMPERIAL_GALLON_L, MILE_KM, "l", "mpg"),
    US(US_GALLON_L, US_GALLON_L, MILE_KM, "gal", "mpg"),
    ;

    fun tankFromLitres(litres: Double): Double = litres / tankLitres
    fun tankToLitres(shown: Double): Double = shown * tankLitres
    fun economyFromKmPerLitre(kmPerLitre: Double): Double = kmPerLitre * economyGallonLitres / economyKm
    fun economyToKmPerLitre(shown: Double): Double = shown * economyKm / economyGallonLitres
}

/**
 * The one place where distances and speeds become text (#15). Everything shown or spoken goes
 * through here, so km/miles and the decimal separator of the language are always the same.
 * [comma]: the language writes decimals with a comma ("12,5 km"), see [decimalComma].
 */
object Measures {
    /** Languages that write decimals with a point; the others (it, de, es, fr...) use a comma. */
    private val POINT_LANGUAGES = setOf("en", "ja", "zh", "ko", "th", "he", "hi", "ms", "ga", "mt")

    fun decimalComma(language: String?): Boolean =
        language.isNullOrEmpty() || language.lowercase().substringBefore('-').substringBefore('_') !in POINT_LANGUAGES

    /** [x] with [decimals] digits after the separator ("12.5", "12,5", "3"). */
    fun number(x: Double, decimals: Int, comma: Boolean): String {
        if (!x.isFinite()) return "–"
        var scale = 1L
        repeat(decimals) { scale *= 10 }
        val n = (x * scale).roundToLong()
        val sign = if (n < 0) "-" else ""
        val whole = abs(n) / scale
        if (decimals == 0) return "$sign$whole"
        val frac = (abs(n) % scale).toString().padStart(decimals, '0')
        return "$sign$whole${if (comma) ',' else '.'}$frac"
    }

    /** A length in the chosen unit: one decimal below 10, whole number above ("8.4", "126"). */
    fun lengthNumber(km: Double, units: Units, comma: Boolean): String {
        val v = units.of(km)
        return if (abs(v) < 9.95) number(v, 1, comma) else number(v, 0, comma)
    }

    /** "126 km" or "78 mi". */
    fun distance(km: Double, units: Units, comma: Boolean): String =
        "${lengthNumber(km, units, comma)} ${units.symbol}"

    /**
     * A distance during navigation, [m] metres: tens of metres below 1 km ("450 m"), feet
     * below a tenth of a mile ("300 ft"), then [distance].
     */
    fun shortDistance(m: Double, units: Units, comma: Boolean): String {
        val safe = m.coerceAtLeast(0.0)
        return when (units) {
            Units.KM -> {
                val tens = (safe / 10).roundToLong() * 10
                if (tens < 1000) "$tens m" else distance(safe / 1000, units, comma)
            }
            Units.MILES -> {
                val feet = safe / FOOT_M
                if (feet < FEET_PER_TENTH_MILE) "${(feet / 10).roundToLong() * 10} ft"
                else distance(safe / 1000, units, comma)
            }
        }
    }

    /**
     * A distance for the voice, rounded the way people say it: whole km from 950 m, otherwise
     * steps of 50 m (never 0); in miles whole miles from 0.95 mi, tenths of a mile from 0.1 mi,
     * otherwise steps of 100 ft (never 0).
     */
    fun spoken(m: Double, units: Units, comma: Boolean): String = when (units) {
        Units.KM -> if (m >= 950) "${(m / 1000).roundToLong()} km" else "${maxOf(50L, (m / 50).roundToLong() * 50)} m"
        Units.MILES -> {
            val miles = m / 1000 * KM_TO_MILES_FACTOR
            when {
                miles >= 0.95 -> "${miles.roundToLong()} mi"
                miles >= 0.1 -> "${number(miles, 1, comma)} mi"
                else -> "${maxOf(100L, (m / FOOT_M / 100).roundToLong() * 100)} ft"
            }
        }
    }

    /** A speed in km/h as shown in the chosen unit (km/h or mph), whole. */
    fun speed(kmh: Double, units: Units): Int = units.of(kmh).roundToLong().toInt()
}

/** Back to km from a length typed in the chosen unit. */
fun Units.toKm(shown: Double): Double = if (this == Units.MILES) shown * MILE_KM else shown

/** "km" or "mi". */
val Units.symbol: String get() = if (this == Units.MILES) "mi" else "km"

/** Exact definitions: 1 mile = 1.609344 km, 1 foot = 0.3048 m. */
internal const val MILE_KM = 1.609344
private const val KM_TO_MILES_FACTOR = 1 / MILE_KM
private const val FOOT_M = 0.3048
private const val FEET_PER_TENTH_MILE = 528.0
private const val US_GALLON_L = 3.785411784
private const val IMPERIAL_GALLON_L = 4.54609
