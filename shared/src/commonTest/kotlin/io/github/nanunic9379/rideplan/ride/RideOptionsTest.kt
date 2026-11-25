package io.github.nanunic9379.rideplan.ride

import io.github.nanunic9379.rideplan.profile.ProfileData
import io.github.nanunic9379.rideplan.profile.ProfileStore
import io.github.nanunic9379.rideplan.profile.SavedRide
import io.github.nanunic9379.rideplan.route.Curves
import io.github.nanunic9379.rideplan.route.RidingMode
import io.github.nanunic9379.rideplan.route.RouteInfo
import io.github.nanunic9379.rideplan.route.ownOptions
import io.github.nanunic9379.rideplan.route.unrouted
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** #64: a ride is shared, published and reopened with its own route options, not the global ones. */
class RideOptionsTest {
    private val curvy = RidingMode.CURVY.options
    private val fast = RidingMode.FAST.options

    private val stops = listOf(Stop("A", LatLon(45.0, 9.0)), Stop("B", LatLon(45.5, 9.5)))

    private fun curvyRide(): Ride = Ride(
        "Lakes", listOf(LatLon(45.0, 9.0), LatLon(45.2, 9.2), LatLon(45.5, 9.5)), stops, Ride.Source.PLANNED,
        route = RouteInfo(80.0, 5000.0, "cells", "2026-11-01", "valhalla 3", curvy),
        sharedOptions = curvy,
    )

    /** The issue's case: routed Curvy, global set to Fast, the link carries Curvy. */
    @Test
    fun linkCarriesTheRideOptionsNotTheGlobalOnes() {
        val global = fast
        val saved = SavedRide.from(curvyRide(), "id", 1)
        val reopened = saved.toRide()
        val link = RideLink.encode(reopened, reopened.ownOptions ?: global)
        val shared = RideLink.decode(link).options
        assertEquals(curvy, shared)
    }

    @Test
    fun savedRideKeepsItsOptionsThroughTheProfileFile() {
        val (data, id) = ProfileData.fresh(0).save(curvyRide(), null, 1)
        val back = ProfileStore.decode(ProfileStore.encode(data))
        val ride = back.rides.first { it.id == id }.toRide()
        assertEquals(RidingMode.CURVY, ride.ownOptions?.mode)
        assertEquals(curvy.curves, ride.ownOptions?.curves)
        assertEquals(curvy.avoidTowns, ride.ownOptions?.avoidTowns)
    }

    /** Moving a stop drops the road, not the riding mode: it is routed again the same way. */
    @Test
    fun editedRideKeepsItsMode() {
        val edited = curvyRide().unrouted().copy(stops = stops.reversed())
        assertEquals(curvy, edited.ownOptions)
    }

    /** A route computed one curves level lower (#9) shares the level the rider asked for. */
    @Test
    fun loweredRouteSharesTheLevelAsked() {
        val ride = curvyRide().copy(
            sharedOptions = null,
            route = RouteInfo(80.0, 5000.0, "cells", "2026-11-01", "valhalla 3", curvy.copy(curves = Curves.MORE), loweredFrom = Curves.MAX),
        )
        assertEquals(Curves.MAX, ride.ownOptions?.curves)
    }

    /** Rides saved before #64 or never routed have none: the caller falls back to the global options. */
    @Test
    fun noOptionsFallsBackToGlobal() {
        val gpx = Ride("Track", listOf(LatLon(45.0, 9.0), LatLon(45.1, 9.1)), emptyList(), Ride.Source.GPX)
        assertNull(gpx.ownOptions)
        val (data, id) = ProfileData.fresh(0).save(gpx, null, 1)
        assertNull(data.rides.first { it.id == id }.options)
        assertNull(data.rides.first { it.id == id }.toRide().ownOptions)
    }

    /** A new plan starts from the global options, not from the ride opened before. */
    @Test
    fun newPlanStartsFromGlobalOptions() {
        val s = RideScreenState()
        s.defaultOptions = fast
        s.options = curvy
        s.startPlan()
        assertEquals(fast, s.options)
    }
}
