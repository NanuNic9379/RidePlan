package io.github.nanunic9379.rideplan.route

import io.github.nanunic9379.rideplan.ride.LatLon
import kotlin.math.PI
import kotlin.math.cos

/**
 * Fuel stations on the phone (#7). Valhalla keeps no points of interest, so routing-data.yml
 * publishes, next to each 1-degree road cell, a small list of its stations (OpenStreetMap
 * amenity=fuel). The app downloads it with the cell, after checking its SHA-256.
 *
 * Without the list (data built before #7, a failed download, a ride outside the cells) the
 * stations are unknown: the app says so and never guesses that there are none.
 */
data class FuelFile(val file: String, val bytes: Long, val sha256: String) {
    companion object {
        /** fuel-l1-<cell>-<build>.tsv: no name can leave the download directory. */
        val NAME = Regex("""fuel-l1-\d+-[\w.]+\.tsv""")
    }
}

/** A fuel station from OpenStreetMap. [brand] and [name] are empty in many places. */
data class FuelStation(val point: LatLon, val brand: String? = null, val name: String? = null) {
    /** What to call it, as a stop of the ride: the name, otherwise the brand. */
    val label: String? get() = name ?: brand
}

object FuelData {
    /** First field of the first line of a list: format 1, columns lat, lon, brand, name. */
    private const val HEADER = "#rideplan-fuel 1"

    /**
     * Reads one cell's list (tab-separated, one station per line). A wrong header fails, because
     * the columns could mean something else; a single broken line is skipped.
     */
    fun parse(text: String): List<FuelStation> {
        val lines = text.lineSequence().iterator()
        require(lines.hasNext() && lines.next().split('\t').first().trim() == HEADER) { "Unknown fuel list" }
        val out = ArrayList<FuelStation>()
        for (line in lines) {
            if (line.isBlank()) continue
            val f = line.split('\t')
            val lat = f.getOrNull(0)?.trim()?.toDoubleOrNull() ?: continue
            val lon = f.getOrNull(1)?.trim()?.toDoubleOrNull() ?: continue
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) continue
            out += FuelStation(LatLon(lat, lon), f.getOrNull(2).orNullIfBlank(), f.getOrNull(3).orNullIfBlank())
        }
        return out
    }

    /**
     * The level-1 cells whose stations may be within [nearKm] of the [line]: those containing a
     * point of the line, grown by [nearKm]. Null when part of the line lies outside every cell.
     */
    fun cellsAlong(cells: RoutingCells, line: List<LatLon>, nearKm: Double = FuelAlong.NEAR_KM): List<RoutingPack>? {
        val level1 = cells.packs.filter { it.level == 1 }
        val chosen = LinkedHashSet<RoutingPack>()
        for (p in line) {
            if (level1.none { it.contains(p) }) return null
            val dLat = nearKm / KM_PER_DEG_LAT
            val dLon = nearKm / (KM_PER_DEG_LON * cos(p.lat * PI / 180).coerceAtLeast(0.01))
            level1.filterTo(chosen) { c ->
                p.lon >= c.bbox[0] - dLon && p.lon <= c.bbox[2] + dLon && p.lat >= c.bbox[1] - dLat && p.lat <= c.bbox[3] + dLat
            }
        }
        return chosen.toList()
    }

    /**
     * The stations that may be near the [line], from the lists on the phone. [read] gives the text
     * of a cell's list, null when the phone does not have it. Null (unknown) as soon as one cell
     * along the line has no readable list: a missing list must never look like "no stations".
     */
    fun stationsAlong(cells: RoutingCells, line: List<LatLon>, read: (packId: String) -> String?): List<FuelStation>? {
        if (line.isEmpty()) return null
        val around = cellsAlong(cells, line) ?: return null
        return around.flatMap { cell ->
            val text = read(cell.id) ?: return null
            runCatching { parse(text) }.getOrNull() ?: return null
        }
    }

    private fun String?.orNullIfBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    internal const val KM_PER_DEG_LAT = 110.57
    internal const val KM_PER_DEG_LON = 111.32
}
