package io.github.nanunic9379.rideplan.route

import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.distanceKm
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoadNotesAlongTest {
    /** Straight north along [lon], a point every 0.01° of latitude (about 1.1 km). */
    private fun north(from: Double, to: Double, lon: Double = 10.0): List<LatLon> {
        val n = kotlin.math.round(abs(to - from) / 0.01).toInt()
        return (0..n).map { LatLon(from + it * (to - from) / n, lon) }
    }

    private fun km(a: Double, b: Double) = distanceKm(LatLon(a, 10.0), LatLon(b, 10.0))

    private fun near(a: Double, b: Double, tol: Double = 0.1) = assertTrue(abs(a - b) < tol, "$a is not about $b")

    private val winter = mapOf("motor_vehicle:conditional" to "no @ (Nov-May)")

    private fun closure(id: String, line: List<LatLon>, name: String? = "Passo", tags: Map<String, String> = winter) =
        Closure(id, "secondary", line, tags, listOfNotNull(name?.let { "name" to it }).toMap())

    @Test
    fun rideOverAClosedPassIsFound() {
        val ride = north(46.0, 46.5)
        val pass = closure("w1", north(46.2, 46.3))
        val found = RoadNotesAlong.closures(ride, listOf(pass)).single()
        near(found.fromKm, km(46.0, 46.2))
        near(found.toKm, km(46.0, 46.3))
        assertTrue(found.stretch.size > 10)
        // The block sits in the middle of the closed stretch.
        near(found.middle.lat, 46.25, 0.01)
        assertEquals("Passo", found.avoided("it").name)
        assertEquals("w1", found.avoided(null).id)
    }

    @Test
    fun closedRoadDrawnTheOtherWayIsFoundToo() {
        val found = RoadNotesAlong.closures(north(46.0, 46.5), listOf(closure("w1", north(46.3, 46.2)))).single()
        near(found.fromKm, km(46.0, 46.2))
        near(found.toKm, km(46.0, 46.3))
    }

    @Test
    fun aRoadNextToTheRideIsNotOnIt() {
        // Parallel road 100 m to the east (0.0013° of longitude at 46°N).
        val parallel = closure("w2", north(46.2, 46.3, lon = 10.0013))
        assertTrue(RoadNotesAlong.closures(north(46.0, 46.5), listOf(parallel)).isEmpty())
    }

    @Test
    fun crossingAClosedRoadDoesNotCount() {
        // A closed road going east-west, crossed once at 46.2.
        val across = closure("w3", listOf(LatLon(46.2, 9.98), LatLon(46.2, 10.02)))
        assertTrue(RoadNotesAlong.closures(north(46.0, 46.5), listOf(across)).isEmpty())
    }

    @Test
    fun onlyTouchingTheEndOfALongClosedRoadDoesNotCount() {
        // The ride rides 40 m of a closed road 10 km long, then turns away: below 200 m.
        val ride = north(46.0, 46.2) + listOf(LatLon(46.2004, 10.0), LatLon(46.2004, 10.05))
        val long = closure("w4", north(46.2, 46.29))
        assertTrue(RoadNotesAlong.closures(ride, listOf(long)).isEmpty())
    }

    @Test
    fun aShortClosedRoadCountsWhenMostOfItIsRidden() {
        // 100 m closed bridge or tunnel, fully ridden.
        val short = closure("w5", listOf(LatLon(46.2, 10.0), LatLon(46.2009, 10.0)))
        val found = RoadNotesAlong.closures(north(46.0, 46.5), listOf(short)).single()
        near(found.toKm - found.fromKm, 0.1, 0.03)
    }

    @Test
    fun piecesOfTheSamePassAreOneEntry() {
        val ride = north(46.0, 46.5)
        val a = closure("w6", north(46.2, 46.25))
        val b = closure("w7", north(46.25, 46.3))
        val other = closure("w8", north(46.4, 46.45), name = "Altro passo")
        val found = RoadNotesAlong.closures(ride, listOf(b, other, a))
        assertEquals(2, found.size)
        assertEquals("w6", found[0].closure.id)
        assertEquals(2, found[0].pieces)
        near(found[0].fromKm, km(46.0, 46.2))
        near(found[0].toKm, km(46.0, 46.3))
        assertEquals("w8", found[1].closure.id)
    }

    @Test
    fun piecesWithDifferentRulesOrNoNameStayApart() {
        val ride = north(46.0, 46.5)
        val a = closure("w6", north(46.2, 46.25))
        val b = closure("w7", north(46.25, 46.3), tags = mapOf("motor_vehicle:conditional" to "no @ (Dec-Mar)"))
        assertEquals(2, RoadNotesAlong.closures(ride, listOf(a, b)).size)
        val c = closure("w9", north(46.2, 46.25), name = null)
        val d = closure("w10", north(46.25, 46.3), name = null)
        assertEquals(2, RoadNotesAlong.closures(ride, listOf(c, d)).size)
    }

    @Test
    fun outAndBackIsTwoPasses() {
        val ride = north(46.0, 46.4) + north(46.4, 46.0).drop(1)
        val found = RoadNotesAlong.closures(ride, listOf(closure("w1", north(46.2, 46.3))))
        assertEquals(2, found.size)
        near(found[0].fromKm, km(46.0, 46.2))
        near(found[1].fromKm, km(46.0, 46.4) + km(46.4, 46.3))
    }

    @Test
    fun neverClosedAndFarAwayRoadsAreSkipped() {
        val ride = north(46.0, 46.5)
        val open = closure("w1", north(46.2, 46.3), tags = mapOf("opening_hours" to "24/7"))
        val far = closure("w2", north(45.0, 45.1))
        assertTrue(RoadNotesAlong.closures(ride, listOf(open, far)).isEmpty())
        assertTrue(RoadNotesAlong.closures(listOf(LatLon(46.0, 10.0)), listOf(far)).isEmpty())
    }

    @Test
    fun kmFollowTheRidesLength() {
        // As for fuel: the engine's length scales the positions.
        val ride = north(46.0, 46.5)
        val raw = km(46.0, 46.5)
        val found = RoadNotesAlong.closures(ride, listOf(closure("w1", north(46.2, 46.3))), totalKm = raw * 1.1).single()
        near(found.fromKm, km(46.0, 46.2) * 1.1)
    }

    @Test
    fun camerasNearTheRoadOnly() {
        val ride = north(45.0, 45.5)
        val on = SpeedCamera("n1", LatLon(45.2, 10.0002), 50) // about 15 m
        val off = SpeedCamera("n2", LatLon(45.3, 10.001), 70) // about 80 m
        val far = SpeedCamera("n3", LatLon(44.0, 10.0))
        val found = RoadNotesAlong.cameras(ride, listOf(off, far, on))
        assertEquals(listOf("n1"), found.map { it.camera.id })
        near(found.single().km, km(45.0, 45.2))
        assertTrue(found.single().offM < 20)
    }

    @Test
    fun cameraPassedTwiceAppearsTwice() {
        val ride = north(45.0, 45.3) + north(45.3, 45.0).drop(1)
        val cam = SpeedCamera("n1", LatLon(45.1, 10.0))
        val found = RoadNotesAlong.cameras(ride, listOf(cam))
        assertEquals(2, found.size)
        near(found[0].km, km(45.0, 45.1))
        near(found[1].km, km(45.0, 45.3) + km(45.3, 45.1))
    }

    @Test
    fun camerasOnlyWhereWarningsAreAllowed() {
        val ride = north(45.0, 45.5)
        val it = SpeedCamera("it", LatLon(45.1, 10.0))
        val ch = SpeedCamera("ch", LatLon(45.2, 10.0))
        val nowhere = SpeedCamera("x", LatLon(45.3, 10.0))
        val countries = mapOf(it.point to "IT", ch.point to "CH")
        val found = RoadNotesAlong.allowed(RoadNotesAlong.cameras(ride, listOf(it, ch, nowhere))) { p -> countries[p] }
        assertEquals(listOf("it"), found.map { c -> c.camera.id })
    }

    @Test
    fun camerasInItalyWithTheRealBorders() {
        val milan = SpeedCamera("mi", LatLon(45.4642, 9.19))
        val zurich = SpeedCamera("zh", LatLon(47.3769, 8.5417))
        val cams = listOf(CameraOnRide(milan, 1.0, 0.0), CameraOnRide(zurich, 2.0, 0.0))
        assertEquals(listOf("mi"), RoadNotesAlong.allowed(cams).map { it.camera.id })
    }

    @Test
    fun cameraWatchAnnouncesOnce() {
        val a = CameraOnRide(SpeedCamera("a", LatLon(0.0, 0.0), 50), km = 1.0, offM = 0.0)
        val b = CameraOnRide(SpeedCamera("b", LatLon(0.0, 0.0)), km = 3.0, offM = 0.0)
        val watch = CameraWatch(listOf(b, a))
        assertNull(watch.update(0.0)) // 1 km away: too early
        val first = watch.update(600.0)!!
        assertEquals("a", first.camera.camera.id)
        assertEquals(400.0, first.distanceM, 0.01)
        assertTrue(first.announce)
        val again = watch.update(800.0)!!
        assertEquals(false, again.announce)
        // Just passed: still shown (GPS lag), at 0 m.
        val passed = watch.update(1010.0)!!
        assertEquals(0.0, passed.distanceM)
        assertNull(watch.update(1100.0))
        val second = watch.update(2600.0)!!
        assertEquals("b", second.camera.camera.id)
        assertTrue(second.announce)
    }

    @Test
    fun cameraWatchDoesNotAnnounceWhenStartingOnTopOfIt() {
        val a = CameraOnRide(SpeedCamera("a", LatLon(0.0, 0.0)), km = 0.0, offM = 0.0)
        val watch = CameraWatch(listOf(a))
        val ahead = watch.update(5.0)!!
        assertEquals(false, ahead.announce)
    }

    @Test
    fun densifyKeepsEveryPointAndStepsEvenly() {
        val line = listOf(LatLon(46.0, 10.0), LatLon(46.001, 10.0))
        val pts = RoadNotesAlong.densify(line, 20.0)
        assertEquals(line.first(), pts.first().first)
        assertEquals(line.last(), pts.last().first)
        near(pts.last().second, km(46.0, 46.001) * 1000, 0.01)
        assertTrue(pts.zipWithNext().all { (p, q) -> q.second - p.second <= 20.0001 })
        assertEquals(1, RoadNotesAlong.densify(listOf(LatLon(46.0, 10.0)), 20.0).size)
    }
}
