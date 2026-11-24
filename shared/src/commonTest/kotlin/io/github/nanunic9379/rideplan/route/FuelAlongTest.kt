package io.github.nanunic9379.rideplan.route

import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.Ride
import io.github.nanunic9379.rideplan.ride.Stop
import io.github.nanunic9379.rideplan.ride.distanceKm
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FuelAlongTest {
    /** Straight north along 12°E, a point every 0.01° of latitude (about 1.1 km). */
    private fun north(from: Double, to: Double): List<LatLon> {
        val n = ((to - from) / 0.01).let { kotlin.math.round(it).toInt() }
        return (0..n).map { LatLon(from + it * (to - from) / n, 12.0) }
    }

    private fun km(a: Double, b: Double) = distanceKm(LatLon(a, 12.0), LatLon(b, 12.0))

    private fun near(a: Double, b: Double, tol: Double = 0.2) = assertTrue(abs(a - b) < tol, "$a is not about $b")

    @Test
    fun stationsNearTheRoadCountAndFarOnesDoNot() {
        val line = north(41.0, 43.0)
        val close = FuelStation(LatLon(41.5, 12.005), "Eni", "Eni Orte") // about 0.4 km off
        val far = FuelStation(LatLon(42.0, 12.02)) // about 1.7 km off
        val report = FuelAlong.report(line, listOf(far, close))
        assertEquals(listOf(close), report.stations.map { it.station })
        near(report.stations[0].km, km(41.0, 41.5))
        near(report.stations[0].offKm, 0.41, 0.05)
    }

    @Test
    fun stationsComeSortedByKm() {
        val line = north(41.0, 43.0)
        val a = FuelStation(LatLon(42.5, 12.0))
        val b = FuelStation(LatLon(41.2, 12.0))
        val c = FuelStation(LatLon(41.9, 12.0))
        assertEquals(listOf(b, c, a), FuelAlong.report(line, listOf(a, b, c)).stations.map { it.station })
    }

    @Test
    fun withoutStationsTheWholeRideIsOneStretch() {
        val line = north(41.0, 43.0)
        val report = FuelAlong.report(line, emptyList())
        assertEquals(1, report.stretches.size)
        near(report.longest.km, km(41.0, 43.0))
        assertEquals(report.stretches, report.overRange(150.0))
        assertEquals(emptyList(), report.overRange(300.0))
    }

    @Test
    fun stretchesGoFromStartThroughStationsToFinish() {
        val line = north(41.0, 43.0)
        val report = FuelAlong.report(line, listOf(FuelStation(LatLon(41.5, 12.0)), FuelStation(LatLon(41.7, 12.0))))
        val s = report.stretches
        assertEquals(3, s.size)
        near(s[0].km, km(41.0, 41.5))
        near(s[1].km, km(41.5, 41.7))
        near(s[2].km, km(41.7, 43.0))
        assertEquals(s[2], report.longest)
        // Range 100 km: only the last stretch (about 144 km) is too long.
        assertEquals(listOf(s[2]), report.overRange(100.0))
        assertEquals(emptyList(), report.overRange(200.0))
    }

    @Test
    fun kmFollowTheRideLengthShown() {
        val line = north(41.0, 42.0)
        val real = km(41.0, 42.0)
        val report = FuelAlong.report(line, listOf(FuelStation(LatLon(41.5, 12.0))), totalKm = real * 1.1)
        near(report.totalKm, real * 1.1, 0.01)
        near(report.stations[0].km, real * 1.1 / 2)
    }

    @Test
    fun aStationPassedTwiceAppearsTwice() {
        val line = north(41.0, 42.0) + north(41.0, 42.0).reversed().drop(1)
        val report = FuelAlong.report(line, listOf(FuelStation(LatLon(41.2, 12.003))))
        assertEquals(2, report.stations.size)
        val leg = km(41.0, 42.0)
        near(report.stations[0].km, km(41.0, 41.2))
        near(report.stations[1].km, 2 * leg - km(41.0, 41.2))
        near(report.longest.km, 2 * leg - 2 * km(41.0, 41.2))
    }

    @Test
    fun aStationAlongALongStraightSegmentIsFound() {
        // Points 20 km apart (a motorway in a simplified track).
        val line = listOf(LatLon(41.0, 12.0), LatLon(41.18, 12.0), LatLon(41.36, 12.0))
        val report = FuelAlong.report(line, listOf(FuelStation(LatLon(41.09, 12.004))))
        assertEquals(1, report.stations.size)
        near(report.stations[0].km, km(41.0, 41.09))
    }

    @Test
    fun aRideTooShortHasNoStations() {
        val report = FuelAlong.report(listOf(LatLon(41.0, 12.0)), listOf(FuelStation(LatLon(41.0, 12.0))))
        assertEquals(emptyList(), report.stations)
    }

    private fun routed(stops: List<Stop>, track: List<LatLon>) = Ride(
        name = null,
        track = track,
        stops = stops,
        source = Ride.Source.PLANNED,
        route = RouteInfo(km = track.zipWithNext { a, b -> distanceKm(a, b) }.sum(), seconds = 3600.0, regionId = "cells", osmDate = "d", engine = "e"),
    )

    private fun stop(lat: Double, lon: Double = 12.0, name: String? = null) = Stop(name, LatLon(lat, lon))

    private fun stationAt(ride: Ride, p: LatLon, name: String? = "Q8 Viterbo"): StationOnRide =
        FuelAlong.report(ride.line, listOf(FuelStation(p, "Q8", name)), ride.lengthKm).stations.single()

    @Test
    fun refuelGoesBetweenTheRightStops() {
        val ride = routed(listOf(stop(41.0, name = "A"), stop(42.0, name = "B"), stop(43.0, name = "C")), north(41.0, 43.0))
        val late = ride.withRefuelStop(stationAt(ride, LatLon(42.5, 12.002)))
        assertEquals(listOf("A", "B", "Q8 Viterbo", "C"), late.stops.map { it.name })
        assertEquals(Stop.Kind.STOP, late.stops[2].kind)
        assertEquals(null, late.route, "the ride must be routed again")
        assertTrue(late.straightLines)
        val early = ride.withRefuelStop(stationAt(ride, LatLon(41.3, 12.0)))
        assertEquals(listOf("A", "Q8 Viterbo", "B", "C"), early.stops.map { it.name })
    }

    @Test
    fun refuelWithoutNameUsesTheBrand() {
        val ride = routed(listOf(stop(41.0), stop(42.0)), north(41.0, 42.0))
        val fresh = ride.withRefuelStop(stationAt(ride, LatLon(41.5, 12.0), name = null))
        assertEquals("Q8", fresh.stops[1].name)
    }

    @Test
    fun refuelOnALoopGoesBeforeTheFinish() {
        // Out north to B, then back to the start, which is also the finish.
        val track = north(41.0, 42.0) + north(41.0, 42.0).reversed().drop(1)
        val ride = routed(listOf(stop(41.0, name = "Home"), stop(42.0, name = "B"), stop(41.0, name = "Home")), track)
        // Passed on the way out and on the way back: each pass has its place.
        val passes = FuelAlong.report(ride.line, listOf(FuelStation(LatLon(41.2, 12.0), name = "Eni")), ride.lengthKm).stations
        assertEquals(2, passes.size)
        assertEquals(listOf("Home", "Eni", "B", "Home"), ride.withRefuelStop(passes[0]).stops.map { it.name })
        assertEquals(listOf("Home", "B", "Eni", "Home"), ride.withRefuelStop(passes[1]).stops.map { it.name })
    }

    @Test
    fun refuelNeverBeforeStartOrAfterFinish() {
        val ride = routed(listOf(stop(41.1, name = "A"), stop(41.9, name = "B")), north(41.0, 42.0))
        assertEquals(listOf("A", "Q8 Viterbo", "B"), ride.withRefuelStop(stationAt(ride, LatLon(41.02, 12.0))).stops.map { it.name })
        assertEquals(listOf("A", "Q8 Viterbo", "B"), ride.withRefuelStop(stationAt(ride, LatLon(41.98, 12.0))).stops.map { it.name })
    }

    @Test
    fun refuelChangesNothingWhenNotEditableOrAlreadyAStop() {
        val gpx = Ride(null, north(41.0, 42.0), listOf(stop(41.0), stop(42.0)), Ride.Source.GPX)
        assertSame(gpx, gpx.withRefuelStop(stationAt(gpx, LatLon(41.5, 12.0))))
        val ride = routed(listOf(stop(41.0), stop(41.5, name = "Eni"), stop(42.0)), north(41.0, 42.0))
        assertSame(ride, ride.withRefuelStop(stationAt(ride, LatLon(41.5, 12.0))))
    }

    @Test
    fun stopKmsFollowTheOrderOfTheRide() {
        val track = north(41.0, 42.0) + north(41.0, 42.0).reversed().drop(1)
        val kms = FuelAlong.stopKms(track, listOf(LatLon(41.0, 12.0), LatLon(41.5, 12.0), LatLon(42.0, 12.0), LatLon(41.5, 12.0), LatLon(41.0, 12.0)))
        val leg = km(41.0, 42.0)
        near(kms[0], 0.0)
        near(kms[1], leg / 2)
        near(kms[2], leg)
        near(kms[3], leg * 1.5)
        near(kms[4], leg * 2)
    }
}
