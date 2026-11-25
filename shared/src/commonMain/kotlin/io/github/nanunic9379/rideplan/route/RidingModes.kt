package io.github.nanunic9379.rideplan.route

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * A riding mode (#30): one tap sets every route switch. The rider can still change a switch by
 * hand afterwards; the ride then shows "Custom" but keeps the mode's softer preferences
 * ([highwayBias]).
 */
@Serializable
enum class RidingMode {
    /** Quickest route: motorways and tolls allowed. */
    FAST,

    /** More bends than normal, tolls avoided, motorways only when they save a lot of time. */
    BALANCED,

    /** As many bends as possible; motorways, tolls and towns avoided where possible. */
    CURVY,
    ;

    /** The switches this mode sets. */
    val options: RouteOptions
        get() = when (this) {
            FAST -> RouteOptions(mode = FAST)
            BALANCED -> RouteOptions(curves = Curves.SOME, avoidTolls = true, mode = BALANCED)
            CURVY -> RouteOptions(curves = Curves.MAX, avoidHighways = true, avoidTolls = true, avoidTowns = true, mode = CURVY)
        }

    /**
     * Valhalla's `use_highways` when motorways are not avoided outright; null = engine default
     * (1.0). Balanced takes a motorway only when it saves a lot of time.
     */
    val highwayBias: Double?
        get() = when (this) {
            FAST -> null
            BALANCED -> 0.25
            CURVY -> 0.1
        }

    companion object {
        /** For a rider who never chose: the issue's default. */
        val DEFAULT = BALANCED
    }
}

/**
 * Modes shown in the grid but not selectable yet (#30): they need routing data that is not
 * verified (scenic road tags, road width and surface, the ride log matched to roads).
 */
enum class ComingMode { SCENIC, TOURING, NEW_ROADS, DIRT }

/**
 * True when the switches no longer match the chosen mode (or no mode was ever chosen). The unpaved
 * choice (#9) belongs to the rider, not to the mode: it never makes a mode "Custom".
 */
val RouteOptions.custom: Boolean get() = mode == null || copy(unpaved = Unpaved.DEFAULT) != mode.options

/**
 * Picks the mode whose switches are exactly these, so preferences saved before #30 (or received
 * with a shared ride) light up the right tile. Nothing matches: no mode, shown as "Custom".
 */
fun RouteOptions.withInferredMode(): RouteOptions {
    if (mode != null) return this
    val match = RidingMode.entries.firstOrNull { it.options == copy(mode = it, unpaved = Unpaved.DEFAULT) } ?: return this
    return copy(mode = match)
}

/** A combination of switches the rider saved with a name ("Saturday on the passes"). */
@Serializable
data class RoutePreset(val id: String, val name: String, val options: RouteOptions)

/**
 * The route settings kept on the phone: the current choice and the presets.
 * Read with [RouteSettings.decode], which never fails: an unreadable file gives the defaults.
 */
@Serializable
data class RouteSettings(
    val options: RouteOptions = RidingMode.DEFAULT.options,
    val presets: List<RoutePreset> = emptyList(),
) {
    fun withPreset(name: String, options: RouteOptions, id: String): RouteSettings {
        val clean = name.trim().take(MAX_NAME)
        if (clean.isEmpty()) return this
        // Same name: the preset is updated, not doubled.
        val others = presets.filterNot { it.name.equals(clean, ignoreCase = true) }
        return copy(presets = (others + RoutePreset(id, clean, options)).takeLast(MAX_PRESETS))
    }

    fun withoutPreset(id: String): RouteSettings = copy(presets = presets.filterNot { it.id == id })

    fun encode(): String = json.encodeToString(this)

    companion object {
        const val MAX_PRESETS = 12
        const val MAX_NAME = 40

        private val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            encodeDefaults = true
        }

        fun decode(text: String?): RouteSettings? {
            if (text.isNullOrBlank()) return null
            return runCatching { json.decodeFromString<RouteSettings>(text) }.getOrNull()?.let { s ->
                s.copy(options = s.options.withInferredMode(), presets = s.presets.filter { it.name.isNotBlank() })
            }
        }

        /**
         * The preferences as saved before #30 (four separate values). Null [curves] means
         * nothing was ever saved: a new rider starts on [RidingMode.DEFAULT].
         */
        fun legacy(curves: String?, avoidHighways: Boolean, avoidTolls: Boolean, avoidFerries: Boolean): RouteSettings {
            if (curves == null) return RouteSettings()
            val options = RouteOptions(
                curves = Curves.entries.firstOrNull { it.name == curves } ?: Curves.NORMAL,
                avoidHighways = avoidHighways,
                avoidTolls = avoidTolls,
                avoidFerries = avoidFerries,
            )
            return RouteSettings(options.withInferredMode())
        }
    }
}
