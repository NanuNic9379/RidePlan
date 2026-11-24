package io.github.nanunic9379.rideplan.route

import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.RideLink
import io.github.nanunic9379.rideplan.ride.Stop
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The unpaved choice (#9): costing per state, saving, modes, links and the curves fallback. */
class UnpavedTest {
    private val stops = listOf(Stop("A", LatLon(40.6894884, 16.9741293)), Stop("B", LatLon(40.3421591, 15.6580114)))

    private fun motorcycle(o: RouteOptions): JsonObject =
        Json.parseToJsonElement(ValhallaRoute.request(stops, o)).jsonObject
            .getValue("costing_options").jsonObject.getValue("motorcycle").jsonObject

    @Test
    fun avoidKeepsTheSafetySettings() {
        val m = motorcycle(RouteOptions(unpaved = Unpaved.AVOID))
        assertEquals(0.0, m.getValue("use_trails").jsonPrimitive.double)
        assertEquals(0.0, m.getValue("use_tracks").jsonPrimitive.double)
        assertEquals("true", m.getValue("exclude_unpaved").jsonPrimitive.content)
        assertEquals(ValhallaRoute.DESTINATION_ONLY_PENALTY.toDouble(), m.getValue("destination_only_penalty").jsonPrimitive.double)
    }

    @Test
    fun allowIsNeutral() {
        val m = motorcycle(RouteOptions(unpaved = Unpaved.ALLOW))
        assertEquals(0.5, m.getValue("use_trails").jsonPrimitive.double)
        assertEquals(0.5, m.getValue("use_tracks").jsonPrimitive.double)
        assertFalse("exclude_unpaved" in m)
        assertTrue("destination_only_penalty" in m)
    }

    @Test
    fun preferGivesTheBonus() {
        val m = motorcycle(RouteOptions(unpaved = Unpaved.PREFER))
        assertEquals(1.0, m.getValue("use_trails").jsonPrimitive.double)
        assertEquals(1.0, m.getValue("use_tracks").jsonPrimitive.double)
        assertFalse("exclude_unpaved" in m)
        assertTrue("destination_only_penalty" in m)
    }

    @Test
    fun noStateEverRelaxesAccess() {
        // Closed roads stay closed whatever the rider thinks of gravel.
        for (u in Unpaved.entries) for (c in Curves.entries) {
            val request = ValhallaRoute.request(stops, RouteOptions(curves = c, unpaved = u))
            assertFalse("ignore_" in request, "$u $c")
            assertFalse("shortest" in request, "$u $c")
        }
    }

    @Test
    fun theStateChangesOnlyTheSurfaceSettings() {
        val avoid = motorcycle(RouteOptions(curves = Curves.MAX, avoidTolls = true, unpaved = Unpaved.AVOID))
        val prefer = motorcycle(RouteOptions(curves = Curves.MAX, avoidTolls = true, unpaved = Unpaved.PREFER))
        val surface = setOf("use_trails", "use_tracks", "exclude_unpaved")
        assertEquals(avoid.filterKeys { it !in surface }, prefer.filterKeys { it !in surface })
    }

    @Test
    fun theValuesStayInsideTheEngineRange() {
        for (u in Unpaved.entries) {
            assertTrue(u.useTrails in 0.0..1.0)
            assertTrue(u.useTracks in 0.0..1.0)
        }
        assertEquals(Unpaved.AVOID, Unpaved.DEFAULT)
        assertEquals(Unpaved.AVOID, RouteOptions().unpaved)
    }

    @Test
    fun settingsSavedBeforeTheChoiceReadAsAvoid() {
        val old = """{"options":{"curves":"MAX","avoidHighways":true,"avoidTolls":true,"avoidTowns":true},"presets":[]}"""
        val s = assertNotNull(RouteSettings.decode(old))
        assertEquals(Unpaved.AVOID, s.options.unpaved)
        assertEquals(RidingMode.CURVY, s.options.mode)
    }

    @Test
    fun theChoiceIsSavedAndAnUnknownValueFallsBack() {
        val s = RouteSettings(RidingMode.CURVY.options.copy(unpaved = Unpaved.PREFER))
        assertEquals(Unpaved.PREFER, RouteSettings.decode(s.encode())!!.options.unpaved)
        val future = s.encode().replace("\"PREFER\"", "\"MUDDY\"")
        assertEquals(Unpaved.AVOID, RouteSettings.decode(future)!!.options.unpaved)
    }

    @Test
    fun theChoiceNeverMakesAModeCustom() {
        for (mode in RidingMode.entries) for (u in Unpaved.entries) {
            val o = mode.options.copy(unpaved = u)
            assertFalse(o.custom, "$mode $u")
            assertEquals(mode, o.copy(mode = null).withInferredMode().mode, "$mode $u")
            assertEquals(u, o.copy(mode = null).withInferredMode().unpaved)
        }
        assertTrue(RidingMode.CURVY.options.copy(avoidTolls = false, unpaved = Unpaved.ALLOW).custom)
    }

    @Test
    fun changingModeKeepsTheChoice() {
        val current = RidingMode.FAST.options.copy(unpaved = Unpaved.PREFER)
        val curvy = RidingMode.CURVY.optionsKeeping(current)
        assertEquals(Unpaved.PREFER, curvy.unpaved)
        assertEquals(RidingMode.CURVY.options, curvy.copy(unpaved = Unpaved.DEFAULT))
    }

    @Test
    fun linksCarryTheChoice() {
        for (u in Unpaved.entries) {
            val o = RouteOptions(curves = Curves.SOME, avoidTolls = true, unpaved = u)
            val back = assertNotNull(RideLink.parseOptions(RideLink.optionsCode(o)))
            assertEquals(u, back.unpaved)
            assertEquals(RidingMode.BALANCED, back.mode)
        }
        assertEquals("NORMAL", RideLink.optionsCode(RouteOptions()))
        assertEquals("NORMAL,u", RideLink.optionsCode(RouteOptions(unpaved = Unpaved.ALLOW)))
        assertEquals("NORMAL,d", RideLink.optionsCode(RouteOptions(unpaved = Unpaved.PREFER)))
        // Links from older apps have no letter: avoid.
        assertEquals(Unpaved.AVOID, RideLink.parseOptions("MAX,h,t")!!.unpaved)
    }

    // --- Curves fallback ---

    private fun result(km: Double, vararg unpaved: Pair<Double, Double>) = ValhallaRoute.Result(
        shape = emptyList(),
        km = km,
        seconds = km * 60,
        surfaces = RoadSurfaces(unpaved.map { (a, b) -> RoughStretch(a, b, Rough.GRAVEL) }),
    )

    private val curvyMax = RidingMode.CURVY.options

    @Test
    fun onlyAvoidAtTheTopLevelIsLowered() {
        val top = Curves.entries.last()
        assertEquals(Curves.MAX, top)
        assertEquals(curvyMax.copy(curves = Curves.entries[top.ordinal - 1]), CurvesFallback.lower(curvyMax))
        assertNull(CurvesFallback.lower(curvyMax.copy(unpaved = Unpaved.ALLOW)))
        assertNull(CurvesFallback.lower(curvyMax.copy(unpaved = Unpaved.PREFER)))
        for (c in Curves.entries.dropLast(1)) assertNull(CurvesFallback.lower(curvyMax.copy(curves = c)), c.name)
    }

    @Test
    fun unpavedNearTheStopsDoesNotCount() {
        // 20 km: dirt in the first 400 m and in the last 300 m is where the stops are.
        val ends = RoadSurfaces(listOf(RoughStretch(0.0, 400.0, Rough.DIRT), RoughStretch(19_700.0, 20_000.0, Rough.GRAVEL)))
        assertEquals(0.0, CurvesFallback.middleM(ends, 20_000.0))
        // A stretch across the 500 m line counts only past it.
        val across = RoadSurfaces(listOf(RoughStretch(300.0, 1_300.0, Rough.GRAVEL)))
        assertEquals(800.0, CurvesFallback.middleM(across, 20_000.0))
        // A route shorter than both ends has no middle.
        assertEquals(0.0, CurvesFallback.middleM(across, 900.0))
    }

    @Test
    fun retryOnlyFromTheWarningLength() {
        assertFalse(CurvesFallback.needsRetry(curvyMax, result(20.0)))
        assertFalse(CurvesFallback.needsRetry(curvyMax, result(20.0, 5_000.0 to 5_299.0)))
        assertTrue(CurvesFallback.needsRetry(curvyMax, result(20.0, 5_000.0 to 5_300.0)))
        assertFalse(CurvesFallback.needsRetry(curvyMax.copy(curves = Curves.MORE), result(20.0, 5_000.0 to 8_000.0)))
        assertFalse(CurvesFallback.needsRetry(curvyMax.copy(unpaved = Unpaved.ALLOW), result(20.0, 5_000.0 to 8_000.0)))
    }

    @Test
    fun aCleanRouteIsComputedOnce() {
        val asked = ArrayList<RouteOptions>()
        val out = CurvesFallback.route(curvyMax) { asked += it; result(30.0) }
        assertEquals(listOf(curvyMax), asked)
        assertEquals(curvyMax, out.options)
        assertNull(out.loweredFrom)
    }

    @Test
    fun aDirtyTopRouteIsComputedOneLevelLower() {
        val asked = ArrayList<RouteOptions>()
        val dirty = result(30.0, 10_000.0 to 12_000.0)
        val clean = result(28.0)
        val out = CurvesFallback.route(curvyMax) { asked += it; if (it.curves == Curves.MAX) dirty else clean }
        assertEquals(listOf(Curves.MAX, Curves.MORE), asked.map { it.curves })
        assertEquals(clean, out.result)
        assertEquals(Curves.MORE, out.options.curves)
        assertEquals(Curves.MAX, out.loweredFrom)
        // Everything but the level is kept.
        assertEquals(curvyMax, out.options.copy(curves = Curves.MAX))
    }

    @Test
    fun theLowerRouteMustReallyBeCleaner() {
        val dirty = result(30.0, 10_000.0 to 12_000.0)
        // Only 50 m less: not worth losing the curves.
        val same = result(29.0, 10_000.0 to 11_950.0)
        val out = CurvesFallback.route(curvyMax) { if (it.curves == Curves.MAX) dirty else same }
        assertEquals(dirty, out.result)
        assertEquals(curvyMax, out.options)
        assertNull(out.loweredFrom)
        assertTrue(CurvesFallback.isBetter(dirty, result(29.0, 10_000.0 to 11_900.0)))
        assertFalse(CurvesFallback.isBetter(dirty, same))
    }

    @Test
    fun aFailingSecondTryKeepsTheFirstRoute() {
        val dirty = result(30.0, 10_000.0 to 12_000.0)
        val out = CurvesFallback.route(curvyMax) { if (it.curves == Curves.MAX) dirty else error("no route") }
        assertEquals(dirty, out.result)
        assertNull(out.loweredFrom)
    }

    @Test
    fun otherStatesAreNeverLowered() {
        val dirty = result(30.0, 10_000.0 to 12_000.0)
        for (u in listOf(Unpaved.ALLOW, Unpaved.PREFER)) {
            var calls = 0
            val out = CurvesFallback.route(curvyMax.copy(unpaved = u)) { calls++; dirty }
            assertEquals(1, calls)
            assertNull(out.loweredFrom)
        }
    }
}
