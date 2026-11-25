package io.github.nanunic9379.rideplan.route

/** What the off-road overlay of the Explore map marks on a road (#9). */
enum class OffroadKind { UNPAVED, NO_ACCESS }

/**
 * Which roads the off-road overlay marks (#9), from the base map's own vector tiles: OpenFreeMap
 * "liberty" ([io.github.nanunic9379.rideplan.MapDefaults.STYLE]), OpenMapTiles schema, source
 * [SOURCE], source layer [LAYER]. Its fields (checked in the tiles' TileJSON): `class`,
 * `surface` = `paved` | `unpaved` (from the OSM `surface` tag) and `access` = `no` (the only value
 * kept). Not in the tiles: `tracktype` and `motorcycle=no`.
 *
 * Kept as plain data so the rules are tested here and the map expression is built from the same
 * lists ([OffroadLayers]).
 */
object OffroadRules {
    const val SOURCE = "openmaptiles"
    const val LAYER = "transportation"

    /** Below this zoom the tiles carry no surface detail and the lines would be clutter. */
    const val MIN_ZOOM = 11f

    /** The overlay is hidden at [zoom] because the map is zoomed out too far (#222). */
    fun hiddenAt(zoom: Double): Boolean = zoom < MIN_ZOOM

    /** Road classes a motorcycle could use: paths, footways, rail and ferries are left out. */
    val ROAD_CLASSES = listOf(
        "motorway", "trunk", "primary", "secondary", "tertiary", "minor", "service", "track", "raceway", "busway",
    )

    /** Tracks are unpaved unless the tiles say otherwise (`surface` is often missing on them). */
    const val TRACK = "track"

    /** What the overlay marks on a road with these tile fields; null = nothing. Access wins. */
    fun kindOf(roadClass: String?, surface: String?, access: String?): OffroadKind? {
        if (roadClass !in ROAD_CLASSES) return null
        return when {
            access == "no" -> OffroadKind.NO_ACCESS
            surface == "unpaved" -> OffroadKind.UNPAVED
            roadClass == TRACK && surface != "paved" -> OffroadKind.UNPAVED
            else -> null
        }
    }
}
