package io.github.nanunic9379.rideplan.profile

import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.Ride
import io.github.nanunic9379.rideplan.ride.RideLink
import io.github.nanunic9379.rideplan.ride.RideVersion
import io.github.nanunic9379.rideplan.ride.Stop
import io.github.nanunic9379.rideplan.ride.StopPlan
import io.github.nanunic9379.rideplan.ride.withStopAdded
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RideVersionTest {
    private val stops = listOf(
        Stop("Matera", LatLon(40.66665, 16.6043)),
        Stop("Grassano", LatLon(40.63, 16.28)),
        Stop("Potenza", LatLon(40.64005, 15.80507)),
    )
    private val ride = Ride("Saturday ride", emptyList(), stops, Ride.Source.PLANNED)

    private fun ProfileData.ride(id: String) = rides.first { it.id == id }

    @Test
    fun newRideStartsAtVersionOne() {
        val (d, id) = ProfileData.fresh(0).save(ride, null, 100)
        val v = assertNotNull(d.ride(id).version)
        assertEquals(1, v.number)
        assertEquals(100, v.updatedAt)
        assertEquals(12, v.rideId.length)
        // A route computed later is not a new version.
        val (d2, _) = d.save(d.ride(id).toRide(), id, 200)
        assertEquals(v, d2.ride(id).version)
    }

    @Test
    fun editsInOneSessionStayOneVersion() {
        var (d, id) = ProfileData.fresh(0).save(ride, null, 100)
        // Same session (not reopened): stops edited twice, still version 1 with a new date.
        d = d.save(ride.withStopAdded(LatLon(40.65, 16.4)), id, 200).first
        d = d.save(ride.withStopAdded(LatLon(40.64, 16.0)), id, 300).first
        assertEquals(1, d.ride(id).version!!.number)
        assertEquals(300, d.ride(id).version!!.updatedAt)
        // Opened again the next day and changed: version 2, the old note goes.
        d = d.setVersionNote(id, "first try", 310).opened(id, 1000)
        d = d.save(ride, id, 1100).first
        assertEquals(2, d.ride(id).version!!.number)
        assertEquals("", d.ride(id).version!!.note)
        assertEquals(1100, d.ride(id).version!!.updatedAt)
        // Opened without changes: still version 2.
        d = d.opened(id, 2000)
        d = d.save(d.ride(id).toRide(), id, 2100).first
        assertEquals(2, d.ride(id).version!!.number)
    }

    @Test
    fun ridesFromOlderVersionsGetOneLazily() {
        val (d0, id) = ProfileData.fresh(0).save(ride, null, 100)
        val legacy = d0.copy(rides = d0.rides.map { it.copy(version = null) })
        val d = legacy.save(legacy.ride(id).toRide(), id, 200).first
        assertEquals(1, d.ride(id).version!!.number)
    }

    @Test
    fun stopPlanIsSavedAndCountsAsAChange() {
        var (d, id) = ProfileData.fresh(0).save(ride, null, 100)
        d = d.opened(id, 500)
        val plan = StopPlan(StopPlan.Purpose.MEETING, time = 510, pauseMinutes = 15, note = "Bar Roma")
        d = d.setStopPlan(id, 0, plan, 600)
        assertEquals(plan, d.ride(id).toRide().stops[0].plan)
        assertEquals(2, d.ride(id).version!!.number)
        // The same plan again changes nothing.
        assertEquals(d, d.setStopPlan(id, 0, plan, 700))
        // Out of range: nothing happens.
        assertEquals(d, d.setStopPlan(id, 9, plan, 700))
        // Removing it.
        d = d.setStopPlan(id, 0, null, 800)
        assertNull(d.ride(id).toRide().stops[0].plan)
        // Planning a dragged point makes it a real stop.
        val (d2, id2) = ProfileData.fresh(0).save(ride.withStopAdded(LatLon(40.65, 16.4)), null, 100)
        val via = d2.ride(id2).stops.indexOfFirst { it.kind == Stop.Kind.VIA.name }
        val d3 = d2.setStopPlan(id2, via, StopPlan(StopPlan.Purpose.FUEL), 200)
        assertEquals(Stop.Kind.STOP.name, d3.ride(id2).stops[via].kind)
    }

    @Test
    fun plansAndVersionSurviveTheFile() {
        var (d, id) = ProfileData.fresh(0).save(ride, null, 100)
        d = d.setStopPlan(id, 1, StopPlan(StopPlan.Purpose.LUNCH, time = 780, pauseMinutes = 60, note = "Trattoria"), 200)
        d = d.setVersionNote(id, "lunch moved", 300)
        val back = ProfileStore.decode(ProfileStore.encode(d))
        assertEquals(d.ride(id), back.ride(id))
        // A file from before #10/#11 still opens.
        val old = """{"version":2,"profile":{"id":"p","createdAt":0},"rides":[{"id":"r","name":null,"savedAt":1,"km":10.0,
            "source":"GPX","stops":[{"name":"A","lat":1.0,"lon":1.0,"kind":"STOP"}]}]}"""
        val r = ProfileStore.decode(old).rides.single()
        assertNull(r.version)
        assertNull(r.stops[0].plan)
        // An unknown purpose from a newer app is dropped, the rest of the plan stays.
        val newer = """{"version":2,"profile":{"id":"p","createdAt":0},"rides":[{"id":"r","name":null,"savedAt":1,"km":10.0,
            "source":"GPX","stops":[{"name":"A","lat":1.0,"lon":1.0,"purpose":"hotel","time":600}]}]}"""
        assertEquals(StopPlan(time = 600), ProfileStore.decode(newer).rides.single().stops[0].plan)
    }

    @Test
    fun versionNoteIsTrimmed() {
        val (d, id) = ProfileData.fresh(0).save(ride, null, 100)
        val noted = d.setVersionNote(id, "  landslide\non the SP12 " + "x".repeat(300), 200)
        val note = noted.ride(id).version!!.note
        assertTrue(note.startsWith("landslide on the SP12"))
        assertEquals(RideVersion.MAX_NOTE, note.length)
    }

    /** The leader's phone and a rider's phone, with a link in between. */
    private fun leaderAndRider(): Triple<ProfileData, String, Pair<ProfileData, String>> {
        val (leader, lid) = ProfileData.fresh(0).save(ride, null, 100)
        val link = RideLink.encode(leader.ride(lid).toRide())
        val (rider, rid) = ProfileData.fresh(0).receive(RideLink.decode(link).ride, 150)
        return Triple(leader, lid, rider to rid)
    }

    @Test
    fun receivingANewerVersionReplacesTheOldOne() {
        var (leader, lid, riderPair) = leaderAndRider()
        var (rider, rid) = riderPair
        rider = rider.keep(rid).setNotes(rid, "bring the rain suit").markRidden(rid, 160, null).setFavourite(rid, true)
        assertEquals(1, rider.ride(rid).version!!.number)

        // The evening before: the leader reopens, moves lunch and cuts a town.
        leader = leader.opened(lid, 1000)
        val changed = ride.copy(stops = listOf(stops[0], Stop("Tricarico", LatLon(40.62, 16.14)), stops[2]))
        leader = leader.save(changed, lid, 1100).first
        leader = leader.setVersionNote(lid, "landslide on the SP12", 1200)
        assertEquals(2, leader.ride(lid).version!!.number)

        val link = RideLink.encode(leader.ride(lid).toRide())
        val (after, id) = rider.receive(RideLink.decode(link).ride, 2000)
        // No copy: the same ride, updated.
        assertEquals(rid, id)
        assertEquals(1, after.rides.size)
        val r = after.ride(id)
        assertEquals(2, r.version!!.number)
        assertEquals("Tricarico", r.stops[1].name)
        // What was yours stays.
        assertEquals("bring the rain suit", r.notes)
        assertEquals(listOf(160L), r.ridden)
        assertTrue(r.favourite)
        assertNull(r.received)
        val u = assertNotNull(r.update)
        assertEquals(1, u.from)
        assertEquals(2, u.to)
        assertEquals("landslide on the SP12", u.note)
        assertEquals(listOf("Tricarico"), u.added)
        assertEquals(listOf("Grassano"), u.removed)
        assertEquals(2000, u.at)
        // Seen: the note goes away.
        assertNull(after.dismissUpdate(id).ride(id).update)
    }

    @Test
    fun theSameOrAnOlderVersionChangesNothing() {
        val (leader, lid, riderPair) = leaderAndRider()
        val (rider, rid) = riderPair
        val link = RideLink.encode(leader.ride(lid).toRide())
        // The same link scanned twice: no duplicate.
        val (again, id) = rider.receive(RideLink.decode(link).ride, 3000)
        assertEquals(rid, id)
        assertEquals(rider, again)
        // An older version than the one you have.
        val newer = rider.copy(rides = rider.rides.map { it.copy(version = it.version!!.copy(number = 5)) })
        val (same, id2) = newer.receive(RideLink.decode(link).ride, 3000)
        assertEquals(rid, id2)
        assertEquals(newer, same)
    }

    @Test
    fun anOldLinkOnTheMapDoesNotOverwriteTheNewerRide() {
        val (leader, lid, riderPair) = leaderAndRider()
        var (rider, rid) = riderPair
        val oldRide = RideLink.decode(RideLink.encode(leader.ride(lid).toRide())).ride
        // The rider already has v3 with another road, opened since.
        val v3 = ride.copy(stops = listOf(stops[0], stops[2]), version = oldRide.version!!.copy(number = 3, updatedAt = 500))
        rider = rider.receive(v3, 600).first.opened(rid, 700)
        assertEquals(3, rider.ride(rid).version!!.number)
        // The old v1 on screen is routed and saved under the same id: v3 stays.
        val (kept, id) = rider.save(oldRide, rid, 800)
        assertEquals(rid, id)
        assertEquals(rider, kept)
    }

    @Test
    fun importingAFileOfARideYouHaveUpdatesIt() {
        val (d, id) = ProfileData.fresh(0).save(ride, null, 100)
        val v = d.ride(id).version!!
        val newer = ride.copy(stops = stops.take(2), version = v.copy(number = 2, note = "shorter"))
        val (after, same) = d.save(newer, null, 200)
        assertEquals(id, same)
        assertEquals(1, after.rides.size)
        assertEquals(2, after.ride(id).stops.size)
        assertEquals("shorter", after.ride(id).update!!.note)
        // Another ride is not touched.
        val other = ride.copy(version = RideVersion("ffffffffffff", 2))
        val (more, otherId) = after.save(other, null, 300)
        assertNotEquals(id, otherId)
        assertEquals(2, more.rides.size)
    }

    @Test
    fun backupMergeKeepsVersions() {
        val (d, id) = ProfileData.fresh(0).save(ride, null, 100)
        val noted = d.setVersionNote(id, "note", 200)
        val (merged, _) = ProfileData.fresh(0).merge(noted)
        assertEquals("note", merged.ride(id).version!!.note)
    }
}
