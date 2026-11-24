package io.github.nanunic9379.rideplan.profile

import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.PrivateZone
import io.github.nanunic9379.rideplan.ride.Ride
import io.github.nanunic9379.rideplan.ride.Stop
import io.github.nanunic9379.rideplan.route.Curves
import io.github.nanunic9379.rideplan.route.RouteOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersonalAreaTest {
    /** A profile file as written by version 0.1.1xx, before the personal area. */
    private val oldJson = """
        {"version":1,"profile":{"id":"abc","name":"Nico","bike":"Yamaha Tracer 7","welcomed":true,"createdAt":1000},
         "rides":[{"id":"r1","name":"Stelvio","savedAt":5,"km":47.0,"seconds":5100,"source":"GOOGLE_LINK",
                   "stops":[{"name":"Bormio","lat":46.47,"lon":10.37,"kind":"STOP"},{"name":"Prato","lat":46.62,"lon":10.59,"kind":"STOP"}],
                   "track":[],"ridden":[1700000000000]}],
         "privateZones":[{"id":"z","name":"Home","lat":45.0,"lon":9.0,"radiusM":500}]}
    """.trimIndent()

    private fun ride(id: String, km: Double, name: String? = id, ridden: List<Long> = emptyList(), opened: Long? = null) = SavedRide(
        id = id, name = name, savedAt = 1, km = km, source = "GPX",
        stops = listOf(SavedStop("A $id", 45.0, 9.0), SavedStop("B", 45.1, 9.1)), ridden = ridden, openedAt = opened,
    )

    private fun data(vararg rides: SavedRide) = ProfileData(version = ProfileData.CURRENT_VERSION, profile = Profile(id = "p", createdAt = 0), rides = rides.toList())

    // 1 Jan 2026 and 1 Jan 2025, UTC.
    private val y2026 = 1_767_225_600_000L
    private val y2025 = 1_735_689_600_000L
    private val day = 86_400_000L

    @Test
    fun oldFileStillOpensAndItsBikeMovesToTheGarage() {
        val d = ProfileStore.decode(oldJson)
        assertEquals(ProfileData.CURRENT_VERSION, d.version)
        assertEquals("Nico", d.profile.name)
        assertEquals(0, d.profile.color)
        assertEquals(listOf("Yamaha Tracer 7"), d.bikes.map { it.name })
        assertEquals(d.bikes.single().id, d.defaultBikeId)
        assertEquals(Engine.OVER_150, d.bikes.single().engine)
        val r = d.rides.single()
        assertFalse(r.favourite)
        assertEquals("", r.notes)
        assertNull(r.received)
        assertNull(r.openedAt)
        assertTrue(r.riddenWith.isEmpty())
        assertEquals(1, d.privateZones.size)
        assertEquals(AppSettings(), d.settings)
    }

    @Test
    fun fileWithoutVersionIsMigratedToo() {
        val d = ProfileStore.decode("""{"profile":{"id":"a","bike":"SH 125","createdAt":1}}""")
        assertEquals("SH 125", d.defaultBike?.name)
    }

    @Test
    fun storeWritesTheMigratedFileOnceSoTheBikeKeepsItsId() {
        var disk: String? = oldJson
        val first = ProfileStore(read = { disk }, write = { disk = it }, now = { 1 }).data
        val second = ProfileStore(read = { disk }, write = { disk = it }, now = { 1 }).data
        assertEquals(first.bikes, second.bikes)
    }

    @Test
    fun newFieldsSurviveARoundTrip() {
        val d = data(ride("a", 10.0)).setFavourite("a", true).setNotes("a", "Fuel in Bormio")
            .saveBike(Bike("b", "SH 125", BikeType.SCOOTER, Engine.UP_TO_150, 7.5, 35.0), default = true)
            .copy(settings = AppSettings(units = Units.MILES, theme = ThemeMode.DARK, wifiOnly = false))
        assertEquals(d, ProfileStore.decode(ProfileStore.encode(d)))
    }

    @Test
    fun savingARideKeepsWhatOnlyTheListKnows() {
        val ride = Ride("Stelvio", emptyList(), listOf(Stop("A", LatLon(46.0, 10.0)), Stop("B", LatLon(46.1, 10.1))), Ride.Source.GOOGLE_LINK)
        val (d, id) = data().save(ride, null, 1)
        val marked = d.setFavourite(id, true).setNotes(id, "early start").opened(id, 7).markRidden(id, 100, "Tracer")
        val (again, _) = marked.save(ride.copy(name = "Stelvio pass"), id, 9)
        val r = again.rides.single()
        assertTrue(r.favourite)
        assertEquals("early start", r.notes)
        assertEquals(7L, r.openedAt)
        assertEquals(mapOf(100L to "Tracer"), r.riddenWith)
        assertEquals("Stelvio pass", r.name)
    }

    @Test
    fun receivedRideWaitsUntilKeptOrRidden() {
        val ride = Ride("From Luca", emptyList(), listOf(Stop("A", LatLon(46.0, 10.0)), Stop("B", LatLon(46.1, 10.1))), Ride.Source.RIDEPLAN_LINK)
        val (d, id) = data().receive(ride, 50)
        assertEquals(50L, d.rides.single().received?.at)
        assertNull(d.keep(id).rides.single().received)
        assertNull(d.markRidden(id, 60).rides.single().received)
        // Updating it (e.g. the route arrives) does not keep it by itself.
        assertNotNull(d.save(ride, id, 70).first.rides.single().received)
    }

    @Test
    fun undoRemovesTheBikeOfThatOuting() {
        val d = data(ride("a", 10.0)).markRidden("a", 100, "Tracer").markRidden("a", 200, "SH")
        val undone = d.undoRidden("a").rides.single()
        assertEquals(listOf(100L), undone.ridden)
        assertEquals(mapOf(100L to "Tracer"), undone.riddenWith)
    }

    @Test
    fun outingUsesTheDefaultBike() {
        val d = data(ride("a", 10.0)).saveBike(Bike("b", "Tracer 7"), default = false).markRidden("a", 100)
        assertEquals("Tracer 7", d.rides.single().riddenWith[100])
    }

    @Test
    fun rangeIsTankTimesConsumption() {
        assertEquals(263, Bike("b", "SH", tankLitres = 7.5, kmPerLitre = 35.0).rangeKm)
        assertNull(Bike("b", "SH", tankLitres = 7.5).rangeKm)
        assertNull(Bike("b", "SH", tankLitres = 0.0, kmPerLitre = 20.0).rangeKm)
        assertEquals(120, Bike("b", "E", engine = Engine.ELECTRIC, tankLitres = 10.0, kmPerLitre = 10.0, electricRangeKm = 120.0).rangeKm)
        assertEquals(7.5, parseDecimal(" 7,5 "))
        assertNull(parseDecimal("abc"))
        assertNull(parseDecimal("-3"))
    }

    @Test
    fun under150ccMotorwaysAreAlwaysAvoided() {
        val small = Bike("s", "SH 125", engine = Engine.UP_TO_150)
        val big = Bike("b", "Tracer", engine = Engine.OVER_150)
        val prefs = RouteOptions(curves = Curves.MORE, avoidHighways = false, avoidTolls = true)
        assertEquals(prefs.copy(avoidHighways = true, noMotorways = true), prefs.forBike(small))
        // Already switched off by hand: the ban is still added.
        assertTrue(prefs.copy(avoidHighways = true).forBike(small).noMotorways)
        assertEquals(prefs, prefs.forBike(big))
        assertEquals(prefs, prefs.forBike(null))
    }

    @Test
    fun defaultBikeFollowsTheGarage() {
        val a = Bike("a", "Tracer")
        val b = Bike("b", "SH")
        var d = data().saveBike(a, default = false)
        assertEquals("a", d.defaultBikeId)
        assertEquals("Tracer", d.profile.bike)
        d = d.saveBike(b, default = false)
        assertEquals("a", d.defaultBikeId)
        d = d.saveBike(b, default = true)
        assertEquals("b", d.defaultBikeId)
        assertEquals("SH", d.profile.bike)
        d = d.deleteBike("b")
        assertEquals("a", d.defaultBikeId)
        assertEquals("Tracer", d.profile.bike)
        d = d.deleteBike("a")
        assertNull(d.defaultBike)
        assertEquals("", d.profile.bike)
    }

    @Test
    fun restoringAnOldBackupDoesNotDoubleTheBike() {
        val here = ProfileStore.decode(oldJson)
        val (merged, added) = here.merge(ProfileStore.decode(oldJson))
        assertEquals(0, added)
        assertEquals(1, merged.bikes.size)
        // An empty phone gets the bike of the backup as default.
        val (fresh, _) = ProfileData.fresh(1).merge(ProfileStore.decode(oldJson))
        assertEquals("Yamaha Tracer 7", fresh.defaultBike?.name)
        assertEquals("Yamaha Tracer 7", fresh.profile.bike)
    }

    @Test
    fun restoringKeepsNotesFavouritesAndKeptRides() {
        val mine = data(ride("a", 10.0).copy(notes = "mine", received = Received(1)))
        val backup = data(ride("a", 10.0).copy(savedAt = 9, favourite = true))
        val r = mine.merge(backup).first.rides.single()
        assertEquals("mine", r.notes)
        assertTrue(r.favourite)
        assertNull(r.received)
    }

    @Test
    fun chipsCountAndFilter() {
        val rides = listOf(
            ride("a", 50.0, ridden = listOf(1)).copy(favourite = true),
            ride("b", 150.0),
            ride("c", 250.0).copy(received = Received(1)),
        )
        val counts = RideFilter.counts(rides)
        assertEquals(3, counts[RideChip.ALL])
        assertEquals(2, counts[RideChip.TODO])
        assertEquals(1, counts[RideChip.DONE])
        assertEquals(1, counts[RideChip.FAVOURITES])
        assertEquals(1, counts[RideChip.RECEIVED])
        assertEquals(listOf("c"), RideFilter.apply(rides, RideQuery(chip = RideChip.RECEIVED)).map { it.id })
        assertEquals(listOf("b"), RideFilter.apply(rides, RideQuery(length = LengthFilter.FROM_100_TO_200)).map { it.id })
        assertEquals(listOf("a", "b"), RideFilter.apply(rides, RideQuery(length = LengthFilter.ANY, chip = RideChip.ALL, sort = RideSort.NAME)).take(2).map { it.id })
    }

    @Test
    fun searchLooksAtNameStopsAndNotes() {
        val rides = listOf(ride("Stelvio", 47.0).copy(notes = "benzina a Bormio"), ride("Como", 140.0))
        assertEquals(listOf("Stelvio"), RideFilter.apply(rides, RideQuery(text = "bormio")).map { it.id })
        assertEquals(listOf("Como"), RideFilter.apply(rides, RideQuery(text = "a como")).map { it.id })
        assertEquals(listOf("Como"), RideFilter.apply(rides, RideQuery(text = "  COMO ")).map { it.id })
        assertTrue(RideFilter.apply(rides, RideQuery(text = "Dolomiti")).isEmpty())
    }

    @Test
    fun sortOrders() {
        val rides = listOf(
            ride("a", 50.0, ridden = listOf(10), opened = 5),
            ride("b", 150.0, opened = 9),
            ride("c", 100.0, ridden = listOf(30), opened = 1),
        )
        assertEquals(listOf("b", "a", "c"), RideFilter.apply(rides, RideQuery(sort = RideSort.LAST_OPENED)).map { it.id })
        assertEquals(listOf("c", "a", "b"), RideFilter.apply(rides, RideQuery(sort = RideSort.LAST_DONE)).map { it.id })
        assertEquals(listOf("b", "c", "a"), RideFilter.apply(rides, RideQuery(sort = RideSort.LONGEST)).map { it.id })
    }

    @Test
    fun bikeFilterUsesTheHistory() {
        val d = data(ride("a", 50.0), ride("b", 60.0)).saveBike(Bike("x", "Tracer"), false)
            .markRidden("a", 1, "Tracer").markRidden("b", 2, "SH 125")
        assertEquals(listOf("Tracer", "SH 125"), RideFilter.bikes(d))
        assertEquals(listOf("b"), RideFilter.apply(d.rides, RideQuery(bike = "sh 125")).map { it.id })
    }

    @Test
    fun statsByPeriod() {
        val d = data(
            ride("brianza", 54.0, ridden = listOf(y2026 + 40 * day, y2026 + 100 * day, y2025 + 200 * day)),
            ride("stelvio", 186.0, ridden = listOf(y2026 + 220 * day)),
            ride("todo", 300.0),
        )
        val now = y2026 + 300 * day
        val thisYear = RideStats.of(d, Period.THIS_YEAR, now)
        assertEquals(3, thisYear.outings)
        assertEquals(54.0 * 2 + 186.0, thisYear.km, 1e-9)
        assertEquals(186.0, thisYear.longestKm)
        assertEquals((54.0 * 2 + 186.0) / 3, thisYear.averageKm!!, 1e-9)
        assertEquals(12, thisYear.bars.size)
        assertEquals(Bar(8, 186.0), thisYear.best)
        assertEquals(54.0, thisYear.bars[1].km) // February
        assertEquals(listOf("brianza"), thisYear.repeated.map { it.ride.id })
        assertEquals(2, thisYear.repeated.single().times)

        val lastYear = RideStats.of(d, Period.LAST_YEAR, now)
        assertEquals(1, lastYear.outings)
        assertTrue(lastYear.repeated.isEmpty())

        val all = RideStats.of(d, Period.ALL, now)
        assertEquals(4, all.outings)
        assertTrue(all.barsAreYears)
        assertEquals(listOf(2025, 2026), all.bars.map { it.label })
        assertEquals(3, all.repeated.single().times)

        val empty = RideStats.of(data(), Period.THIS_YEAR, now)
        assertEquals(0, empty.outings)
        assertNull(empty.averageKm)
        assertNull(empty.best)
    }

    @Test
    fun logGroupsByMonthNewestFirst() {
        val d = data(
            ride("a", 10.0, ridden = listOf(y2026 + 40 * day, y2026 + 45 * day)),
            ride("b", 20.0, ridden = listOf(y2026 + 220 * day)),
        ).let { it.copy(rides = it.rides.map { r -> r.copy(riddenWith = r.ridden.associateWith { "Tracer" }) }) }
        val log = RideStats.log(d)
        assertEquals(listOf(202608, 202602), log.map { it.first })
        assertEquals(listOf(y2026 + 45 * day, y2026 + 40 * day), log[1].second.map { it.day })
        assertEquals("Tracer", log[0].second.single().bike)
    }

    @Test
    fun deleteAllCountsThenStartsAgain() {
        val d = data(ride("a", 10.0, ridden = listOf(1, 2)).copy(notes = "x"), ride("b", 5.0))
            .saveBike(Bike("x", "Tracer"), false)
            .addZone(PrivateZone("z", "Home", 45.0, 9.0))
            .copy(profile = Profile(id = "p", name = "Nico", welcomed = true, createdAt = 0))
        assertEquals(WipeSummary(rides = 2, notes = 1, outings = 2, bikes = 1, zones = 1), WipeSummary.of(d))
        val wiped = d.wiped(99)
        assertTrue(wiped.rides.isEmpty() && wiped.bikes.isEmpty() && wiped.privateZones.isEmpty())
        assertEquals("", wiped.profile.name)
        assertFalse(wiped.profile.welcomed)
        assertTrue(wiped.profile.id != "p")

        var disk: String? = ProfileStore.encode(d)
        val store = ProfileStore(read = { disk }, write = { disk = it }, now = { 99 })
        store.wipe()
        assertEquals(0, ProfileStore.decode(disk!!).rides.size)
    }

    @Test
    fun exportAllGivesOneGpxPerRideWithUniqueNamesAndNoNotes() {
        val rides = listOf(
            ride("a", 10.0, name = "Stelvio").copy(notes = "SECRET NOTE"),
            ride("b", 10.0, name = "Stelvio"),
            ride("c", 10.0, name = "stelvio"),
            ride("d", 10.0, name = null),
        )
        val files = RideExport.files(rides) { "Ride ${it.id}" }
        assertEquals(listOf("Stelvio.gpx", "Stelvio (2).gpx", "stelvio (3).gpx", "Ride d.gpx"), files.map { it.first })
        assertTrue(files.all { it.second.startsWith("<?xml") })
        assertTrue(files.none { "SECRET" in it.second })
    }
}
