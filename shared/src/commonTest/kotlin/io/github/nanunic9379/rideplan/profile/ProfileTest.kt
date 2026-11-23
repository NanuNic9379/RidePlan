package io.github.nanunic9379.rideplan.profile

import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.Ride
import io.github.nanunic9379.rideplan.ride.Stop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileTest {
    private val stops = listOf(
        Stop("Matera", LatLon(40.66665, 16.6043)),
        Stop("Grassano", LatLon(40.63, 16.28), Stop.Kind.PASS),
        Stop("Potenza", LatLon(40.64005, 15.80507)),
    )
    private val ride = Ride("Saturday ride", emptyList(), stops, Ride.Source.GOOGLE_LINK)
    private val gpx = Ride(null, listOf(LatLon(45.0, 9.0), LatLon(45.1, 9.1), LatLon(45.2, 9.0)), emptyList(), Ride.Source.GPX)

    private class Disk(var text: String? = null) { var backups = 0 }

    private fun store(disk: Disk, t: Long = 1000) =
        ProfileStore(read = { disk.text }, write = { disk.text = it }, backup = { disk.backups++ }, now = { t })

    @Test
    fun firstLaunch() {
        val disk = Disk()
        val a = store(disk)
        assertEquals(32, a.data.profile.id.length)
        assertEquals("", a.data.profile.name)
        // On the second launch it is the same person.
        assertEquals(a.data.profile.id, store(disk).data.profile.id)
        assertNotEquals(a.data.profile.id, store(Disk()).data.profile.id)
    }

    @Test
    fun saveAndReopen() {
        val disk = Disk()
        val s = store(disk)
        var id: String? = null
        s.update { d -> d.save(ride, null, 5).also { id = it.second }.first }
        s.update { it.copy(profile = it.profile.copy(name = "Nico", bike = "Tracer 9")) }
        val back = store(disk).data
        assertEquals("Nico", back.profile.name)
        assertEquals(1, back.rides.size)
        assertEquals(stops, back.rides[0].toRide().stops)
        assertEquals(Ride.Source.GOOGLE_LINK, back.rides[0].toRide().source)
        // Saving the same ride again updates it, it does not duplicate it.
        s.update { it.save(ride.copy(name = "New name"), id, 9).first }
        assertEquals(1, s.data.rides.size)
        assertEquals("New name", s.data.rides[0].name)
    }

    @Test
    fun gpxTrack() {
        val (d, id) = ProfileData.fresh(0).save(gpx, null, 1)
        val back = d.rides.first { it.id == id }.toRide()
        assertEquals(gpx.track, back.track)
        assertEquals(gpx.lengthKm, d.rides[0].km, 1e-9)
    }

    @Test
    fun statistics() {
        var d = ProfileData.fresh(0)
        val (d1, a) = d.save(ride, null, 1)
        val (d2, b) = d1.save(gpx, null, 2)
        d = d2.markRidden(a, 10).markRidden(a, 20).markRidden(b, 30)
        val s = d.stats
        assertEquals(2, s.saved)
        assertEquals(3, s.ridden)
        assertEquals(ride.lengthKm * 2 + gpx.lengthKm, s.kmRidden, 1e-9)
        assertEquals(maxOf(ride.lengthKm, gpx.lengthKm), s.longestKm!!, 1e-9)
        assertEquals(30L, s.lastRidden)
        assertEquals(1, d.undoRidden(a).rides.first { it.id == a }.ridden.size)
        assertEquals(1, d.delete(a).stats.saved)
        assertNull(ProfileData.fresh(0).stats.longestKm)
        // The last one saved is at the top.
        assertEquals(b, d.rides[0].id)
    }

    @Test
    fun corruptedFile() {
        val disk = Disk("{this is not json")
        val s = store(disk)
        assertEquals(1, disk.backups)
        assertTrue(s.data.rides.isEmpty())
        // Unknown fields (future versions) break nothing.
        val future = ProfileStore.encode(s.data).replaceFirst("{", """{"new":42,""")
        assertEquals(s.data, ProfileStore.decode(future))
    }


    @Test
    fun backupAndRestore() {
        // Old phone: a ride ridden twice, name and bike.
        val oldDisk = Disk()
        val old = store(oldDisk)
        var a = ""
        old.update { d -> d.save(ride, null, 5).also { a = it.second }.first.markRidden(a, 10).markRidden(a, 20) }
        old.update { it.copy(profile = it.profile.copy(name = "Nico", bike = "Tracer 9")) }
        val copy = old.backupText()

        // New phone: a ride of its own, then the restore, twice.
        val fresh = store(Disk())
        fresh.update { it.save(gpx, null, 7).first }
        assertEquals(1, fresh.restore(copy))
        assertEquals(0, fresh.restore(copy))
        assertEquals(2, fresh.data.rides.size)
        assertEquals(2, fresh.data.stats.ridden)
        assertEquals("Nico", fresh.data.profile.name)
        assertNotEquals(old.data.profile.id, fresh.data.profile.id)

        // Outings marked on both phones add up without duplicates.
        val newer = old.data.markRidden(a, 30)
        fresh.restore(ProfileStore.encode(newer))
        assertEquals(listOf(10L, 20L, 30L), fresh.data.rides.first { it.id == a }.ridden)

        // Any other file touches nothing.
        val before = fresh.data
        assertTrue(runCatching { fresh.restore("""{"hello":1}""") }.isFailure)
        assertEquals(before, fresh.data)
    }
    @Test
    fun date() {
        assertEquals(Triple(1, 1, 1970), civilDate(0))
        assertEquals(Triple(29, 2, 2024), civilDate(1_709_164_800_000))
        assertEquals(Triple(23, 11, 2026), civilDate(1_795_392_000_000))
        assertEquals(Triple(31, 12, 1969), civilDate(-1))
    }
}
