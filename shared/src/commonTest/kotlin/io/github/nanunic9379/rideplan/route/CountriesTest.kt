package io.github.nanunic9379.rideplan.route

import io.github.nanunic9379.rideplan.ride.LatLon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CountriesTest {
    private val rome = LatLon(41.9028, 12.4964)
    private val milan = LatLon(45.4642, 9.1900)
    private val como = LatLon(45.8081, 9.0852)
    private val lugano = LatLon(46.0037, 8.9511)
    private val zurich = LatLon(47.3769, 8.5417)
    private val innsbruck = LatLon(47.2692, 11.4041)
    private val bolzano = LatLon(46.4983, 11.3548)
    private val trieste = LatLon(45.6495, 13.7768)
    private val ljubljana = LatLon(46.0569, 14.5058)
    private val florence = LatLon(43.7696, 11.2558)
    private val rimini = LatLon(44.0594, 12.5683)
    private val sanMarino = LatLon(43.9356, 12.4473)

    @Test
    fun capitalsAndCities() {
        val cases = mapOf(
            rome to "IT", milan to "IT", como to "IT", lugano to "CH", zurich to "CH",
            innsbruck to "AT", bolzano to "IT", ljubljana to "SI", trieste to "IT",
            LatLon(48.1351, 11.5820) to "DE", // Munich
            LatLon(48.8566, 2.3522) to "FR", // Paris
            LatLon(40.4168, -3.7038) to "ES", // Madrid
            LatLon(38.7369, -9.1427) to "PT", // Lisbon
            LatLon(51.5074, -0.1278) to "GB", // London
            LatLon(47.4979, 19.0402) to "HU", // Budapest
            LatLon(50.0755, 14.4378) to "CZ", // Prague
            LatLon(48.1486, 17.1077) to "SK", // Bratislava
            LatLon(48.2082, 16.3738) to "AT", // Vienna
            LatLon(47.1410, 9.5209) to "LI", // Vaduz
            LatLon(42.6629, 21.1655) to "XK", // Pristina
            LatLon(44.7866, 20.4489) to "RS", // Belgrade
            LatLon(45.8150, 15.9819) to "HR", // Zagreb
            LatLon(52.2297, 21.0122) to "PL", // Warsaw
            LatLon(50.8503, 4.3517) to "BE", // Brussels
            LatLon(52.3676, 4.9041) to "NL", // Amsterdam
            LatLon(49.6116, 6.1319) to "LU", // Luxembourg
            LatLon(42.5063, 1.5218) to "AD", // Andorra la Vella
            LatLon(37.9838, 23.7275) to "GR", // Athens
            LatLon(44.4268, 26.1025) to "RO", // Bucharest
            LatLon(42.6977, 23.3219) to "BG", // Sofia
            LatLon(59.3293, 18.0686) to "SE", // Stockholm
            LatLon(60.1699, 24.9384) to "FI", // Helsinki
            LatLon(55.6761, 12.5683) to "DK", // Copenhagen
            LatLon(53.3498, -6.2603) to "IE", // Dublin
            LatLon(39.2238, 9.1217) to "IT", // Cagliari, Sardinia
            LatLon(37.5079, 15.0830) to "IT", // Catania, Sicily
            LatLon(42.0396, 9.0129) to "FR", // Corsica, inland
        )
        for ((p, code) in cases) assertEquals(code, Countries.at(p), "$p")
    }

    @Test
    fun enclavesAreHolesInItaly() {
        assertEquals("SM", Countries.at(sanMarino))
        // The Vatican is far smaller than the simplification: it reads as Italy (no motorways there).
        assertEquals("IT", Countries.at(LatLon(41.9029, 12.4534)))
    }

    @Test
    fun seaAndFarAwayAreUnknown() {
        assertNull(Countries.at(LatLon(40.0, 11.0))) // Tyrrhenian Sea
        assertNull(Countries.at(LatLon(40.7128, -74.0060))) // New York
        assertNull(Countries.at(LatLon(-33.8688, 151.2093))) // Sydney
        assertNull(Countries.at(LatLon(55.7558, 37.6173))) // Moscow: not in the table
    }

    @Test
    fun overseasPartsAreDropped() {
        assertNull(Countries.at(LatLon(4.9372, -52.3260))) // Cayenne, French Guiana
        assertNull(Countries.at(LatLon(32.6669, -16.9241))) // Funchal, Madeira (Portugal, outside the box)
    }

    @Test
    fun ridesInOneCountry() {
        assertEquals(listOf("IT"), Countries.crossed(listOf(rome, florence, milan)))
        assertEquals(listOf("IT"), Countries.crossed(listOf(milan, como)))
    }

    @Test
    fun ridesAcrossBorders() {
        assertEquals(listOf("IT", "CH"), Countries.crossed(listOf(milan, lugano, zurich)))
        assertEquals(listOf("CH", "IT"), Countries.crossed(listOf(lugano, milan)))
        assertEquals(listOf("AT", "IT"), Countries.crossed(listOf(innsbruck, bolzano)))
        assertEquals(listOf("IT", "SI"), Countries.crossed(listOf(trieste, ljubljana)))
        assertEquals(listOf("IT", "SM"), Countries.crossed(listOf(rimini, sanMarino)))
        // Each country once, in the order the ride enters it.
        assertEquals(listOf("IT", "SM"), Countries.crossed(listOf(rimini, sanMarino, rimini)))
    }

    @Test
    fun straightLinesAreSampledAlongTheWay() {
        // From Milan to Munich in a straight line: over the Engadin (Switzerland) and Tyrol
        // (Austria), with no point of the line there.
        assertEquals(listOf("IT", "CH", "AT", "DE"), Countries.crossed(listOf(milan, LatLon(48.1351, 11.5820))))
        // Milan to Innsbruck passes east of Switzerland.
        assertEquals(listOf("IT", "AT"), Countries.crossed(listOf(milan, innsbruck)))
    }

    @Test
    fun kilometresAddUpToTheRideLength() {
        val km = Countries.kmByCountry(listOf(lugano, milan))
        val total = io.github.nanunic9379.rideplan.ride.distanceKm(lugano, milan)
        assertEquals(total, km.values.sum(), 0.01)
        assertTrue(km.getValue("CH") in 10.0..35.0, "$km")
        assertTrue(km.getValue("IT") in 30.0..55.0, "$km")
    }

    @Test
    fun emptyAndSinglePoints() {
        assertEquals(emptyList(), Countries.crossed(emptyList()))
        assertEquals(listOf("CH"), Countries.crossed(listOf(zurich)))
        assertEquals(emptyList(), Countries.crossed(listOf(LatLon(40.0, 11.0))))
        assertEquals(listOf("IT"), Countries.crossed(listOf(rome, rome)))
    }

    @Test
    fun shortDipsDoNotCount() {
        assertEquals(listOf("IT"), Countries.pick(linkedMapOf("IT" to 40.0, "CH" to 0.6), "IT"))
        assertEquals(listOf("IT", "CH"), Countries.pick(linkedMapOf("IT" to 40.0, "CH" to 1.0), "IT"))
        // A ride shorter than 2 km: half of it is enough.
        assertEquals(listOf("CH"), Countries.pick(linkedMapOf("IT" to 0.3, "CH" to 0.9), "IT"))
        // All near a border, nothing passes: the start country.
        assertEquals(listOf("IT"), Countries.pick(linkedMapOf("IT" to 0.5, "CH" to 0.5, "FR" to 0.5), "IT"))
        assertEquals(emptyList(), Countries.pick(emptyMap(), null))
    }

    @Test
    fun decodesGooglePolylines() {
        // The example of the Google polyline documentation, 5 decimals.
        val (lats, lons) = Countries.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@", 5)
        assertEquals(listOf(38.5, 40.7, 43.252), lats.toList())
        assertEquals(listOf(-120.2, -120.95, -126.453), lons.toList())
    }

    @Test
    fun everyCountryHasRulesAndAnOutline() {
        assertEquals(CountryRules.all.keys, Countries.known)
    }
}
