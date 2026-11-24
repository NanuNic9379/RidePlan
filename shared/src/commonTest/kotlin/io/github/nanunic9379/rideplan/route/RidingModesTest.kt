package io.github.nanunic9379.rideplan.route

import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.profile.Bike
import io.github.nanunic9379.rideplan.profile.Engine
import io.github.nanunic9379.rideplan.profile.forBike
import io.github.nanunic9379.rideplan.ride.RideLink
import io.github.nanunic9379.rideplan.ride.Stop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RidingModesTest {
    private val stops = listOf(Stop("A", LatLon(40.37, 16.19)), Stop("B", LatLon(40.34, 15.65)))

    private fun request(o: RouteOptions) = ValhallaRoute.request(stops, o)

    @Test
    fun fastIsThePlainMotorcycleRoute() {
        // Fast must route exactly like the app before #30 with nothing chosen.
        val fast = RidingMode.FAST.options
        assertEquals(request(RouteOptions()), request(fast))
        val r = request(fast)
        assertFalse("use_curvature" in r)
        assertFalse("use_highways" in r)
        assertFalse("use_tolls" in r)
        assertFalse("use_living_streets" in r)
    }

    @Test
    fun balancedMapping() {
        val o = RidingMode.BALANCED.options
        assertEquals(Curves.SOME, o.curves)
        assertTrue(o.avoidTolls)
        assertFalse(o.avoidHighways)
        assertFalse(o.avoidTowns)
        val r = request(o)
        assertTrue(""""use_curvature":0.6""" in r)
        assertTrue(""""use_tolls":0.0""" in r)
        // Motorways only when they save a lot of time: a low preference, not a ban.
        assertTrue(""""use_highways":0.25""" in r)
        assertFalse("use_ferry" in r)
    }

    @Test
    fun curvyMapping() {
        val o = RidingMode.CURVY.options
        val r = request(o)
        assertTrue(""""use_curvature":1.0""" in r)
        assertTrue(""""use_highways":0.0""" in r)
        assertTrue(""""use_tolls":0.0""" in r)
        assertTrue(""""use_living_streets":0.0""" in r)
        assertTrue(""""service_penalty":${ValhallaRoute.TOWN_SERVICE_PENALTY}""" in r)
        assertTrue(""""use_trails":0.0""" in r)
    }

    @Test
    fun handChangesAreCustomButKeepTheModeBias() {
        val balanced = RidingMode.BALANCED.options
        assertFalse(balanced.custom)
        val tolls = balanced.copy(avoidTolls = false)
        assertTrue(tolls.custom)
        assertEquals(RidingMode.BALANCED, tolls.mode)
        assertTrue(""""use_highways":0.25""" in request(tolls))
        // Motorways switched off by hand: the switch wins over the bias.
        assertTrue(""""use_highways":0.0""" in request(balanced.copy(avoidHighways = true)))
        // Back to the mode's switches: no longer custom.
        assertFalse(tolls.copy(avoidTolls = true).custom)
        assertTrue(RouteOptions().custom)
    }

    @Test
    fun smallBikeLocksMotorways() {
        val scooter = Bike("s", "SH 125", engine = Engine.UP_TO_150)
        val o = RidingMode.FAST.options.forBike(scooter)
        assertTrue(o.avoidHighways)
        assertTrue(""""use_highways":0.0""" in request(o))
        assertEquals(RidingMode.FAST.options, RidingMode.FAST.options.forBike(Bike("t", "Tracer 7")))
    }

    @Test
    fun inferredMode() {
        assertEquals(RidingMode.FAST, RouteOptions().withInferredMode().mode)
        assertEquals(RidingMode.CURVY, RidingMode.CURVY.options.copy(mode = null).withInferredMode().mode)
        assertNull(RouteOptions(curves = Curves.MORE).withInferredMode().mode)
        // A mode already chosen is kept even when the switches changed.
        val custom = RidingMode.CURVY.options.copy(avoidTowns = false)
        assertEquals(custom, custom.withInferredMode())
    }

    @Test
    fun settingsRoundTrip() {
        val s = RouteSettings(RidingMode.CURVY.options.copy(avoidFerries = true))
            .withPreset("Saturday on the passes", RidingMode.CURVY.options, "p1")
            .withPreset("Two-up", RidingMode.BALANCED.options.copy(curves = Curves.NORMAL), "p2")
        val back = RouteSettings.decode(s.encode())
        assertEquals(s, back)
        assertEquals(listOf("Saturday on the passes", "Two-up"), back!!.presets.map { it.name })
    }

    @Test
    fun presets() {
        val s = RouteSettings()
            .withPreset("  Passes  ", RidingMode.CURVY.options, "a")
            .withPreset("passes", RidingMode.FAST.options, "b")
            .withPreset("   ", RidingMode.FAST.options, "c")
        // Same name (any case) replaces; a blank name is ignored; names are trimmed.
        assertEquals(listOf(RoutePreset("b", "passes", RidingMode.FAST.options)), s.presets)
        assertEquals(emptyList(), s.withoutPreset("b").presets)
        var many = RouteSettings()
        repeat(RouteSettings.MAX_PRESETS + 3) { many = many.withPreset("p$it", RidingMode.FAST.options, "$it") }
        assertEquals(RouteSettings.MAX_PRESETS, many.presets.size)
        assertEquals("p${RouteSettings.MAX_PRESETS + 2}", many.presets.last().name)
    }

    @Test
    fun decodeIsTolerant() {
        assertNull(RouteSettings.decode(null))
        assertNull(RouteSettings.decode(""))
        assertNull(RouteSettings.decode("not json"))
        // Fields from a newer app and unknown enum values do not break the saved settings.
        val newer = """{"options":{"curves":"WILD","avoidTolls":true,"mode":"SCENIC","future":1},"presets":[{"id":"x","name":"A","options":{}}],"extra":true}"""
        val s = RouteSettings.decode(newer)!!
        assertEquals(Curves.NORMAL, s.options.curves)
        assertTrue(s.options.avoidTolls)
        assertEquals(1, s.presets.size)
        // Missing fields take the defaults.
        assertEquals(RidingMode.DEFAULT.options, RouteSettings.decode("{}")!!.options)
    }

    @Test
    fun legacyPreferencesKeepLoading() {
        // Nothing ever saved: a new rider starts on Balanced.
        assertEquals(RouteSettings(RidingMode.BALANCED.options), RouteSettings.legacy(null, false, false, false))
        // The old defaults are exactly Fast: same routes as before the update.
        assertEquals(RidingMode.FAST.options, RouteSettings.legacy("NORMAL", false, false, false).options)
        val old = RouteSettings.legacy("MORE", avoidHighways = true, avoidTolls = false, avoidFerries = true).options
        assertEquals(RouteOptions(Curves.MORE, avoidHighways = true, avoidFerries = true), old)
        assertTrue(old.custom)
        assertEquals(Curves.NORMAL, RouteSettings.legacy("GONE", false, false, false).options.curves)
    }

    @Test
    fun sharedLinkCarriesTownsAndFindsTheMode() {
        val code = RideLink.optionsCode(RidingMode.CURVY.options)
        assertEquals("MAX,h,t,n", code)
        assertEquals(RidingMode.CURVY.options, RideLink.parseOptions(code))
        // A link from an app before #30: no mode, found again from the switches.
        assertEquals(RidingMode.FAST.options, RideLink.parseOptions("NORMAL"))
    }
}
