package io.github.nanunic9379.rideplan.route

import io.github.nanunic9379.rideplan.ride.LatLon
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * Road notes (#13): seasonal or time-conditional closures and fixed speed cameras, from
 * OpenStreetMap. routing-data.yml publishes them as `road-notes.json` in the routing-data
 * Release (data/road_notes.py, docs/pass-closures-and-speed-cameras.md); the app downloads the
 * file with the road data and reads it offline.
 *
 * Without the file nothing is said: a missing file never means "no closures" or "no cameras".
 */
data class RoadNotes(val osmDate: String, val closures: List<Closure>, val cameras: List<SpeedCamera>) {
    companion object {
        /** The only format this app reads; a new one is ignored instead of misread. */
        const val FORMAT = 1

        /** Fixed name in the Release, replaced in place by every build. */
        const val FILE = "road-notes.json"

        /** Italy has a few hundred kB: anything this big is not a road-notes file. */
        const val MAX_BYTES = 16_000_000

        /**
         * Reads the file. A wrong format fails (the fields could mean something else); a single
         * broken closure or camera is skipped.
         */
        fun parse(json: String): RoadNotes {
            val root = Json.parseToJsonElement(json).jsonObject
            require(root["format"]?.jsonPrimitive?.int == FORMAT) { "Unknown road notes format" }
            val closures = (root["closures"] as? JsonArray).orEmpty().mapNotNull { runCatching { it.jsonObject.toClosure() }.getOrNull() }
            val cameras = (root["cameras"] as? JsonArray).orEmpty().mapNotNull { runCatching { it.jsonObject.toCamera() }.getOrNull() }
            return RoadNotes((root["osmDate"] as? JsonPrimitive)?.content.orEmpty(), closures, cameras)
        }

        private fun JsonObject.toClosure(): Closure? {
            val line = getValue("line").jsonArray.map { pt ->
                val a = pt.jsonArray
                LatLon(a[1].jsonPrimitive.double, a[0].jsonPrimitive.double)
            }
            if (line.size < 2) return null
            return Closure(
                id = getValue("id").jsonPrimitive.content,
                highway = (get("highway") as? JsonPrimitive)?.content,
                line = line,
                tags = (get("tags") as? JsonObject).strings(),
                names = (get("names") as? JsonObject).strings(),
            )
        }

        private fun JsonObject.toCamera() = SpeedCamera(
            id = getValue("id").jsonPrimitive.content,
            point = LatLon(getValue("lat").jsonPrimitive.double, getValue("lon").jsonPrimitive.double),
            maxspeedKmh = (get("maxspeed") as? JsonPrimitive)?.content?.let(SpeedCamera::parseMaxspeed),
        )

        private fun JsonObject?.strings(): Map<String, String> =
            this?.entries?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { k to it } }?.toMap().orEmpty()
    }
}

/**
 * A road closed for part of the year or of the day, as tagged on OpenStreetMap. [tags]: the
 * original closure tags (`motor_vehicle:conditional`, `seasonal`, `opening_hours`...), read by
 * [ClosureRule]. [names]: `name`, `ref`, `name:it`...
 */
data class Closure(
    val id: String,
    val highway: String?,
    val line: List<LatLon>,
    val tags: Map<String, String>,
    val names: Map<String, String> = emptyMap(),
) {
    val rule: ClosureRule by lazy { ClosureRule.of(tags) }

    /** The name in [language] ("it", "de"...), otherwise the local name, otherwise the road number. */
    fun name(language: String?): String? =
        language?.let { names["name:${it.lowercase().take(2)}"] } ?: names["name"] ?: names["ref"]
}

/** A fixed speed camera (OSM `highway=speed_camera`). [maxspeedKmh]: null when not tagged or not readable. */
data class SpeedCamera(val id: String, val point: LatLon, val maxspeedKmh: Int? = null) {
    companion object {
        /** "50" → 50, "30 mph" → 48; "IT:urban", "signals", "none" → null: never a guess. */
        fun parseMaxspeed(value: String): Int? {
            val m = Regex("""^\s*(\d{1,3})\s*(mph|km/h|kmh)?\s*$""").matchEntire(value.substringBefore(';')) ?: return null
            val n = m.groupValues[1].toInt()
            if (n <= 0) return null
            return if (m.groupValues[2] == "mph") (n * 1.609344 + 0.5).toInt() else n
        }
    }
}

/**
 * Where to find road-notes.json and how to check it, when the manifest (cells.json) lists it.
 * Builds before #13 do not list it: then the app fetches [RoadNotes.FILE] without a checksum and
 * only keeps it if it parses.
 */
data class RoadNotesFile(val file: String, val bytes: Long, val sha256: String) {
    /** True only for the exact file of the build: same size and SHA-256 (hex) as the manifest. */
    fun accepts(size: Long, sha256: String): Boolean = size == bytes && sha256.lowercase() == this.sha256

    companion object {
        val NAME = Regex("""road-notes[\w.-]*\.json""")

        internal fun of(o: JsonObject): RoadNotesFile? = RoadNotesFile(
            file = o.getValue("file").jsonPrimitive.content,
            bytes = o.getValue("bytes").jsonPrimitive.long,
            sha256 = o.getValue("sha256").jsonPrimitive.content.lowercase(),
        ).takeIf { NAME.matches(it.file) && Regex("[0-9a-f]{64}").matches(it.sha256) && it.bytes in 1..RoadNotes.MAX_BYTES }
    }
}
