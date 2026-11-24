package io.github.nanunic9379.rideplan.route

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * One turn instruction of a route computed on the phone (#5), as Valhalla returns it.
 * Texts are already in the language of the request.
 *
 * [beginIndex]/[endIndex]: points of the ride's track where the maneuver starts and ends
 * (indices into [io.github.nanunic9379.rideplan.ride.Ride.track], over all legs).
 * [km]/[seconds]: length and time of the stretch that follows the maneuver.
 */
data class Maneuver(
    val type: Int,
    val instruction: String,
    val beginIndex: Int,
    val endIndex: Int,
    val km: Double = 0.0,
    val seconds: Double = 0.0,
    /** Said ahead of the maneuver ("Turn right onto Via Roma."). */
    val verbalAlert: String? = null,
    /** Said just before the maneuver. */
    val verbalPre: String? = null,
    /** Said right after the maneuver ("Continue for 12 kilometers."). */
    val verbalPost: String? = null,
    val streets: List<String> = emptyList(),
    /** Exit to take in a roundabout, when known. */
    val roundaboutExit: Int? = null,
) {
    val sign: TurnSign get() = TurnSign.of(type)

    /** End of a leg: arrival at a stop or at the finish. */
    val isArrival: Boolean get() = type in DESTINATIONS

    companion object {
        private val DESTINATIONS = setOf(4, 5, 6)

        /** Reads one element of `legs[].maneuvers`; [mapIndex] turns leg indices into track indices. */
        internal fun fromJson(m: JsonObject, mapIndex: (Int) -> Int): Maneuver {
            fun str(key: String) = m[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            return Maneuver(
                type = m["type"]?.jsonPrimitive?.intOrNull ?: 0,
                instruction = str("instruction").orEmpty(),
                beginIndex = mapIndex(m["begin_shape_index"]?.jsonPrimitive?.intOrNull ?: 0),
                endIndex = mapIndex(m["end_shape_index"]?.jsonPrimitive?.intOrNull ?: 0),
                km = m["length"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                seconds = m["time"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                verbalAlert = str("verbal_transition_alert_instruction"),
                verbalPre = str("verbal_pre_transition_instruction"),
                verbalPost = str("verbal_post_transition_instruction"),
                streets = (m["street_names"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
                roundaboutExit = m["roundabout_exit_count"]?.jsonPrimitive?.intOrNull,
            )
        }
    }
}

/**
 * The arrow to draw for a maneuver. [angle]: degrees of the turn, 0 = straight on,
 * positive to the right, negative to the left.
 */
enum class TurnSign(val angle: Int) {
    DEPART(0),
    STRAIGHT(0),
    SLIGHT_RIGHT(45),
    RIGHT(90),
    SHARP_RIGHT(135),
    UTURN_RIGHT(180),
    UTURN_LEFT(-180),
    SHARP_LEFT(-135),
    LEFT(-90),
    SLIGHT_LEFT(-45),
    KEEP_RIGHT(25),
    KEEP_LEFT(-25),
    ROUNDABOUT(0),
    ARRIVE(0),
    FERRY(0),
    ;

    companion object {
        /** Valhalla's maneuver types (odin `DirectionsLeg_Maneuver_Type`). */
        fun of(type: Int): TurnSign = when (type) {
            1, 2, 3 -> DEPART
            4, 5, 6 -> ARRIVE
            9 -> SLIGHT_RIGHT
            10 -> RIGHT
            11 -> SHARP_RIGHT
            12 -> UTURN_RIGHT
            13 -> UTURN_LEFT
            14 -> SHARP_LEFT
            15 -> LEFT
            16 -> SLIGHT_LEFT
            18, 20, 23, 37 -> KEEP_RIGHT
            19, 21, 24, 38 -> KEEP_LEFT
            26, 27 -> ROUNDABOUT
            28, 29 -> FERRY
            else -> STRAIGHT
        }
    }
}
