package io.github.nanunic9379.rideplan.ride

import io.github.nanunic9379.rideplan.profile.SavedRide
import io.github.nanunic9379.rideplan.route.RouteInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PlanDraftTest {
    private val como = LatLon(45.81, 9.08)
    private val menaggio = LatLon(46.02, 9.24)
    private val colico = LatLon(46.13, 9.37)
    private val lecco = LatLon(45.85, 9.39)

    @Test
    fun tapsMakeStartThenStopsAtTheEnd() {
        val d = PlanDraft().withTap(como).withTap(menaggio).withTap(colico)
        assertEquals(listOf(como, menaggio, colico), d.points)
        // Start and finish are stops, the points in between exact pass-throughs.
        assertEquals(listOf(Stop.Kind.STOP, Stop.Kind.VIA, Stop.Kind.STOP), d.stops.map { it.kind })
        assertTrue(d.stops.all { it.name == null })
        // Tapping the last point again (a double tap) adds nothing.
        assertSame(d, d.withTap(colico))
    }

    @Test
    fun aRouteNeedsTwoPoints() {
        val one = PlanDraft().withTap(como)
        assertFalse(one.canFinish)
        assertFalse(one.ride().needsRoute)
        assertFalse(one.canRoundTrip)
        val two = one.withTap(menaggio)
        assertTrue(two.canFinish)
        assertTrue(two.ride().needsRoute)
        assertEquals(Ride.Source.PLANNED, two.ride().source)
    }

    @Test
    fun roundTripAddsTheStartAgain() {
        val d = PlanDraft().withTap(como).withTap(menaggio).withTap(lecco).withRoundTrip()
        assertEquals(listOf(como, menaggio, lecco, como), d.points)
        assertTrue(d.isRoundTrip)
        assertFalse(d.canRoundTrip)
        assertSame(d, d.withRoundTrip())
        assertEquals(Stop.Kind.STOP, d.stops.last().kind)
    }

    @Test
    fun undoGoesBackOneChangeAtATime() {
        val d = PlanDraft().withTap(como).withTap(menaggio).withRoundTrip().withMoved(1, colico)
        assertEquals(listOf(como, colico, como), d.points)
        val u1 = d.undone()
        assertEquals(listOf(como, menaggio, como), u1.points)
        val u2 = u1.undone()
        assertEquals(listOf(como, menaggio), u2.points)
        val empty = u2.undone().undone()
        assertEquals(emptyList(), empty.points)
        assertFalse(empty.canUndo)
        assertSame(empty, empty.undone())
        assertFalse(PlanDraft().canUndo)
    }

    @Test
    fun undoHistoryIsCapped() {
        var d = PlanDraft()
        repeat(PlanDraft.MAX_UNDO + 10) { d = d.withTap(LatLon(45.0 + it / 1000.0, 9.0)) }
        var steps = 0
        while (d.canUndo) { d = d.undone(); steps++ }
        assertEquals(PlanDraft.MAX_UNDO, steps)
        assertEquals(10, d.points.size)
    }

    @Test
    fun myPositionBecomesTheStart() {
        val empty = PlanDraft().withStartHere(como)
        assertEquals(listOf(como), empty.points)
        val later = PlanDraft().withTap(menaggio).withStartHere(como)
        assertEquals(listOf(como, menaggio), later.points)
        assertSame(later, later.withStartHere(como))
        assertEquals(listOf(menaggio), later.undone().points)
    }

    @Test
    fun editsOnTheMapReuseTheStopTools() {
        val d = PlanDraft().withTap(como).withTap(menaggio).withTap(colico)
        assertEquals(listOf(como, colico, menaggio), d.withReordered(2, 1).points)
        // While planning even the start can go.
        assertEquals(listOf(menaggio, colico), d.withRemoved(0).points)
        assertSame(d, d.withRemoved(5))
        assertSame(d, d.withMoved(1, menaggio))
        assertSame(d, d.withReordered(1, 1))
    }

    @Test
    fun nameIsAppliedOnlyWhenDone() {
        val d = PlanDraft().withTap(como).withTap(menaggio).withName("  Lake Como  ")
        assertNull(d.ride().name)
        val routed = d.ride().copy(track = listOf(como, menaggio), route = RouteInfo(30.0, 2400.0, "it", "2026-11-23", "test"))
        val done = d.finished(routed)
        assertEquals("Lake Como", done.name)
        // The route already computed stays.
        assertEquals(routed.track, done.track)
        // No name: the very same ride, so a route still being calculated lands on it.
        val plain = PlanDraft().withTap(como).withTap(menaggio)
        val ride = plain.ride()
        assertSame(ride, plain.finished(ride))
        assertEquals(PlanDraft.MAX_NAME, PlanDraft().withName("x".repeat(200)).name.length)
    }

    @Test
    fun plannedRidesKeepTheirSourceWhenSaved() {
        val ride = PlanDraft().withTap(como).withTap(menaggio).ride()
        assertEquals(Ride.Source.PLANNED, SavedRide.from(ride, "p", 1).toRide().source)
    }
}
