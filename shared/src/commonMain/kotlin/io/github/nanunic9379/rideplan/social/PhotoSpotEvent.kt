package io.github.nanunic9379.rideplan.social

import io.github.nanunic9379.rideplan.profile.PhotoSpot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.encodeToString

/** Public, addressable photo spot. Only fields selected by its author are put in the event. */
object PhotoSpotEvent {
    const val KIND = 30456
    const val MAX_BYTES = 8 * 1024
    private val json = Json { encodeDefaults = true }

    fun draft(spot: PhotoSpot): NostrDraft {
        require(spot.id.isNotBlank() && spot.id.length <= 64)
        require(spot.lat.isFinite() && spot.lat in -90.0..90.0 && spot.lon.isFinite() && spot.lon in -180.0..180.0)
        require(spot.shareName || spot.shareDetails || spot.sharePhotoUrl) { "Choose at least one detail to share" }
        require(!spot.sharePhotoUrl || spot.photoUrl?.let(::validPhotoUrl) == true)
        val name = spot.name.takeIf { spot.shareName }?.let { NostrDraft.clean(it).trim().take(100).takeIf(String::isNotBlank) }
        val details = spot.details.takeIf { spot.shareDetails }?.let { NostrDraft.clean(it).trim().take(1000).takeIf(String::isNotBlank) }
        val photoUrl = spot.photoUrl?.takeIf { spot.sharePhotoUrl && validPhotoUrl(it) }
        require(name != null || details != null || photoUrl != null) { "No selected details to share" }
        val content = buildMap {
            put("lat", JsonPrimitive(spot.lat))
            put("lon", JsonPrimitive(spot.lon))
            name?.let { put("name", JsonPrimitive(it)) }
            details?.let { put("details", JsonPrimitive(it)) }
            photoUrl?.let { put("photo_url", JsonPrimitive(it)) }
            put("v", JsonPrimitive(1))
        }
        val payload = json.encodeToString(JsonObject.serializer(), JsonObject(content))
        val draft = NostrDraft(KIND, listOf(listOf("d", spot.id), listOf("t", "rideplan"), listOf("t", "photo-spot"), listOf("g", Geohash.encode(spot.point, 5))), payload)
        require(draft.toUnsignedJson(0).encodeToByteArray().size + 300 <= MAX_BYTES)
        return draft
    }

    /** Reject malformed, oversized, or unsafe payloads before they reach the map. */
    fun parse(event: NostrEvent): PhotoSpot? = runCatching {
        if (event.kind != KIND || event.content.encodeToByteArray().size > MAX_BYTES) return null
        val id = event.tag("d")?.takeIf { it.isNotBlank() && it.length <= 64 } ?: return null
        if ("photo-spot" !in event.draft.tagValues("t")) return null
        val obj = Json.parseToJsonElement(event.content) as? JsonObject ?: return null
        if (obj["v"]?.jsonPrimitive?.intOrNull != 1) return null
        val lat = obj["lat"]?.jsonPrimitive?.doubleOrNull ?: return null
        val lon = obj["lon"]?.jsonPrimitive?.doubleOrNull ?: return null
        if (!lat.isFinite() || lat !in -90.0..90.0 || !lon.isFinite() || lon !in -180.0..180.0) return null
        val name = obj["name"]?.jsonPrimitive?.content?.take(100).orEmpty()
        val details = obj["details"]?.jsonPrimitive?.content?.take(1000).orEmpty()
        val url = obj["photo_url"]?.jsonPrimitive?.content?.takeIf(::validPhotoUrl)
        if (name.isBlank() && details.isBlank() && url == null) return null
        PhotoSpot("nostr:${event.pubkey}:$id", name.ifBlank { "Photo spot" }, details, lat, lon, url, sourceAddress = event.address?.let { Nip19.naddr(it) }, sourceAuthor = event.pubkey)
    }.getOrNull()

    private fun validPhotoUrl(value: String): Boolean = value.startsWith("https://") && value.length in 9..2048 && value.none { it <= ' ' || it == '\u007F' }
}
