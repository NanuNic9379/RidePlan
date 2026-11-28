package io.github.nanunic9379.rideplan.profile

import io.github.nanunic9379.rideplan.route.RouteOptions
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** What kind of bike: it only describes it, the rules come from [Engine]. */
@Serializable
enum class BikeType { NAKED, TOURING, ADVENTURE, SPORT, CUSTOM, SCOOTER }

/** Engine size class: it decides which roads the bike may use. */
@Serializable
enum class Engine {
    /** Up to 150 cc: in Italy (and most of Europe for 50–125 cc) motorways are forbidden. */
    UP_TO_150,
    OVER_150,
    ELECTRIC,
}

/**
 * A bike in the garage (#21).
 * [tankLitres] × [kmPerLitre] = range on a full tank; for an electric bike [electricRangeKm].
 */
@Serializable
data class Bike(
    val id: String,
    val name: String,
    val type: BikeType = BikeType.NAKED,
    val engine: Engine = Engine.OVER_150,
    val tankLitres: Double? = null,
    val kmPerLitre: Double? = null,
    val electricRangeKm: Double? = null,
) {
    /** Kilometres on a full tank (or charge); null when it is not known. */
    val rangeKm: Int?
        get() = when (engine) {
            Engine.ELECTRIC -> electricRangeKm?.takeIf { it > 0 }?.roundToInt()
            else -> {
                val t = tankLitres
                val c = kmPerLitre
                if (t != null && c != null && t > 0 && c > 0) (t * c).roundToInt() else null
            }
        }

    /** Under 150 cc motorways are always avoided (#8), whatever the route preferences say. */
    val avoidsMotorways: Boolean get() = engine == Engine.UP_TO_150
}

/**
 * The route options a ride really uses with [bike]: the bike's rules win over preferences.
 * Under 150 cc motorways are switched off and also banned outright in the engine (#8).
 */
fun RouteOptions.forBike(bike: Bike?): RouteOptions =
    if (bike?.avoidsMotorways == true) copy(avoidHighways = true, noMotorways = true) else this

/**
 * Reads a decimal number typed by hand: comma or point, spaces ignored. Null when it is not a
 * positive number.
 */
fun parseDecimal(text: String): Double? =
    text.trim().replace(" ", "").replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }

/** The settings of the app (#21). They are part of the backup. */
@Serializable
data class AppSettings(
    /** Km or miles; null = "Automatic", from the country of the phone (#15), see [unitsFor]. */
    val units: Units? = null,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Road data only on Wi-Fi: it can weigh tens of MB. */
    val wifiOnly: Boolean = true,
    /** Ask GitHub for a new version at start (GitHub build only). */
    val checkUpdates: Boolean = true,
    /** When the last backup was saved, to show it. */
    val lastBackupAt: Long? = null,
    /** Fixed speed cameras on the ride map and in navigation (#13), only where allowed (Italy). */
    val speedCameras: Boolean = true,
    /** Riding time before a break reminder, in minutes; zero turns it off. */
    val breakReminderMinutes: Int = 120,
    /** Warnings before tight hairpins during navigation. */
    val hairpinWarnings: HairpinWarningMode = HairpinWarningMode.SOUND,
    /** Periodic GitHub update notifications; requires explicit opt-in and Android notification permission. */
    val backgroundUpdateChecks: Boolean = false,
    /** Show locally saved photographer spots on the map (#254). */
    val photoSpots: Boolean = false,
)

@Serializable
enum class Units { KM, MILES }

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
enum class HairpinWarningMode { OFF, SOUND, VOICE }

/** Distance in the chosen unit. */
fun Units.of(km: Double): Double = if (this == Units.MILES) km * KM_TO_MILES else km

private const val KM_TO_MILES = 1 / MILE_KM
