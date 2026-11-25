package io.github.nanunic9379.rideplan.route

import kotlinx.serialization.Serializable

/**
 * What the rider wants from unpaved roads (#9): a preference of the rider (or of the bike), not
 * of a riding mode, so changing mode keeps it ([optionsKeeping]).
 *
 * Mapped onto Valhalla 3.9.2's motorcycle costing (`src/sif/motorcyclecost.cc`,
 * `src/sif/dynamiccost.cc`, docs/surface-and-bans.md):
 * - `use_trails` sets a surface factor added to every edge: 0 gives the maximum penalty (compacted
 *   +0.8, dirt +1.6, gravel +4, path +8 against about 1 for a normal road), 0.5 is neutral, 1 is
 *   a slight bonus (−0.125 × the surface weight);
 * - `use_tracks` multiplies the cost of `highway=track`: 0 is ×4 plus 5 minutes to enter one,
 *   0.5 is neutral, 1 is ×0.8;
 * - `exclude_unpaved` would be a hard ban, but the motorcycle costing of 3.9.2 does not apply it:
 *   sent only with [AVOID], for a newer engine.
 *
 * Roads closed to motorcycles (`access=no`, `motorcycle=no`, ...) and `smoothness=impassable`
 * stay forbidden in every state: the engine never relaxes access with these options.
 */
@Serializable
enum class Unpaved(val useTrails: Double, val useTracks: Double, val excludeUnpaved: Boolean) {
    /** Default, as before #9: unpaved roads and tracks cost the most the engine allows. */
    AVOID(0.0, 0.0, true),

    /** Neutral: a gravel road is taken when it is the better road, no extra cost either way. */
    ALLOW(0.5, 0.5, false),

    /** Adventure: unpaved roads and tracks get a small bonus. */
    PREFER(1.0, 1.0, false),
    ;

    companion object {
        val DEFAULT = AVOID
    }
}

/** The mode's switches, keeping the rider's unpaved choice (it is not part of the mode). */
fun RidingMode.optionsKeeping(current: RouteOptions): RouteOptions = options.copy(unpaved = current.unpaved)

/**
 * "Max curves" with unpaved roads avoided can still pick a curvy dirt road over a straight paved
 * one (docs/ROUTING.md: at Max a straight road costs ×7, a curvy dirt road about 2.6). When the
 * computed route has unpaved road in the middle, it is computed again one curves level lower and
 * the rider is told (#9, follow-up 1 of docs/ROUTING.md).
 */
object CurvesFallback {
    /** Unpaved road this close to the start or the finish is ignored: the stop is on it. */
    const val ENDS_M = 500.0

    /** Below this much unpaved road in the middle nothing is computed again. */
    const val MIN_M = RoadSurfaces.WARN_M

    /** The lower level must cut at least this much unpaved road to be worth the change. */
    const val MIN_GAIN_M = 100.0

    /** The options of the second try, or null if these options never get one. */
    fun lower(options: RouteOptions): RouteOptions? {
        if (options.unpaved != Unpaved.AVOID) return null
        val top = Curves.entries.last()
        if (options.curves != top) return null
        return options.copy(curves = Curves.entries[top.ordinal - 1])
    }

    /**
     * Unpaved metres of [surfaces] between [ENDS_M] from the start and [ENDS_M] from the finish of a
     * route [totalM] long: the part a different route could avoid.
     */
    fun middleM(surfaces: RoadSurfaces, totalM: Double): Double {
        val from = ENDS_M
        val to = totalM - ENDS_M
        if (to <= from) return 0.0
        return surfaces.stretches.sumOf { s -> (minOf(s.toM, to) - maxOf(s.fromM, from)).coerceAtLeast(0.0) }
    }

    private fun middleM(result: ValhallaRoute.Result): Double = middleM(result.surfaces, result.km * 1000)

    /** True when [result], computed with [options], should be computed again one level lower. */
    fun needsRetry(options: RouteOptions, result: ValhallaRoute.Result): Boolean =
        lower(options) != null && middleM(result) >= MIN_M

    /** The route actually used, with the options it was computed with. */
    data class Outcome(
        val result: ValhallaRoute.Result,
        val options: RouteOptions,
        /** The curves level asked for, when the route was computed one level lower; else null. */
        val loweredFrom: Curves? = null,
    )

    /**
     * Computes with [options] and, if needed, once more one level lower; keeps the lower route
     * only if it really has less unpaved road. A failing second try keeps the first route.
     */
    fun route(options: RouteOptions, compute: (RouteOptions) -> ValhallaRoute.Result): Outcome {
        val first = compute(options)
        if (!needsRetry(options, first)) return Outcome(first, options)
        val lowerOptions = lower(options) ?: return Outcome(first, options)
        val second = runCatching { compute(lowerOptions) }.getOrNull() ?: return Outcome(first, options)
        return if (isBetter(first, second)) Outcome(second, lowerOptions, options.curves) else Outcome(first, options)
    }

    /** [second] avoids at least [MIN_GAIN_M] of the unpaved road of [first]. */
    fun isBetter(first: ValhallaRoute.Result, second: ValhallaRoute.Result): Boolean =
        middleM(second) <= middleM(first) - MIN_GAIN_M
}
