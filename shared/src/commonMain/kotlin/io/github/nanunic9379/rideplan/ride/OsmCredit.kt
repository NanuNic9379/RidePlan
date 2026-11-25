package io.github.nanunic9379.rideplan.ride

/**
 * Credit to OpenStreetMap (ODbL, #130) on what leaves the phone: the GPX copyright element,
 * the ride sheet text and the share preview. It applies when the road was computed on the
 * phone from OpenStreetMap data: a ride with a [Ride.route], or one whose road can only come
 * from the phone (planned, from a Google or RidePlan link). An imported GPX or KML keeps the
 * track of its file, whose source is unknown.
 */
object OsmCredit {
    const val AUTHOR = "OpenStreetMap contributors"
    const val TEXT = "© OpenStreetMap contributors"
    const val LICENSE_URL = "https://opendatacommons.org/licenses/odbl/"

    fun applies(ride: Ride): Boolean =
        !ride.straightLines && (ride.route != null || (ride.source != Ride.Source.GPX && ride.source != Ride.Source.KML))
}
