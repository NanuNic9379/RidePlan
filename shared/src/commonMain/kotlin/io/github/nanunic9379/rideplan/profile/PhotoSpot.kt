package io.github.nanunic9379.rideplan.profile

import io.github.nanunic9379.rideplan.ride.LatLon
import kotlinx.serialization.Serializable

/** A locally saved place where a photographer usually takes pictures. */
@Serializable
data class PhotoSpot(
    val id: String,
    val name: String,
    val details: String = "",
    val lat: Double,
    val lon: Double,
    val photoUrl: String? = null,
    /** Disabled spots remain saved and can be enabled again on another day. */
    val enabled: Boolean = true,
    /** Public fields are opt-in individually; coordinates are always shared when publishing. */
    val shareName: Boolean = false,
    val shareDetails: Boolean = false,
    val sharePhotoUrl: Boolean = false,
    /** Set only for a spot received from a public Nostr event. */
    val sourceAddress: String? = null,
    val sourceAuthor: String? = null,
) {
    val point: LatLon get() = LatLon(lat, lon)
}
