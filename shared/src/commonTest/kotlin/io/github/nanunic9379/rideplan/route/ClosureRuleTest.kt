package io.github.nanunic9379.rideplan.route

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ClosureRuleTest {
    private fun rule(vararg tags: Pair<String, String>) = ClosureRule.of(tags.toMap())
    private fun day(m: Int, d: Int) = MonthDay(m, d)

    @Test
    fun monthDayIndexRoundTrips() {
        assertEquals(0, day(1, 1).index)
        assertEquals(59, day(2, 29).index)
        assertEquals(365, day(12, 31).index)
        for (i in 0 until MonthDay.YEAR_DAYS) assertEquals(i, MonthDay.ofIndex(i).index)
        assertTrue(day(2, 29).isLastOfMonth)
        assertTrue(day(3, 1).isFirstOfMonth)
    }

    @Test
    fun rangeOverNewYearWraps() {
        val winter = DayRange(day(11, 1), day(5, 31))
        assertTrue(day(12, 25) in winter)
        assertTrue(day(1, 1) in winter)
        assertTrue(day(5, 31) in winter)
        assertFalse(day(6, 1) in winter)
        assertFalse(day(10, 31) in winter)
        assertTrue(winter.wholeMonths)
        assertFalse(winter.wholeYear)
        assertTrue(DayRange.YEAR.wholeYear)
    }

    @Test
    fun stelvioClosedNovemberToMay() {
        val r = assertIs<ClosureRule.Seasonal>(rule("motor_vehicle:conditional" to "no @ (Nov-May)"))
        assertEquals(listOf(DayRange(day(11, 1), day(5, 31))), r.closed)
        assertTrue(r.partly.isEmpty())
        assertFalse(r.other)
        assertEquals(ClosedOn.CLOSED, r.on(day(1, 15)))
        assertEquals(ClosedOn.CLOSED, r.on(day(11, 1)))
        assertEquals(ClosedOn.OUTSIDE, r.on(day(7, 20)))
        assertEquals(ClosedOn.OUTSIDE, r.on(day(6, 1)))
        assertEquals("motor_vehicle:conditional=no @ (Nov-May)", r.raw)
    }

    @Test
    fun daysInsideMonths() {
        val r = assertIs<ClosureRule.Seasonal>(rule("motor_vehicle:conditional" to "no @ (Nov 15-May 15)"))
        assertEquals(listOf(DayRange(day(11, 15), day(5, 15))), r.closed)
        assertFalse(r.closed.single().wholeMonths)
        assertEquals(ClosedOn.OUTSIDE, r.on(day(11, 14)))
        assertEquals(ClosedOn.CLOSED, r.on(day(11, 15)))
        assertEquals(ClosedOn.CLOSED, r.on(day(5, 15)))
        assertEquals(ClosedOn.OUTSIDE, r.on(day(5, 16)))
        // "May 15-31": days of one month.
        val may = assertIs<ClosureRule.Seasonal>(rule("vehicle:conditional" to "no @ (May 15-31)"))
        assertEquals(listOf(DayRange(day(5, 15), day(5, 31))), may.closed)
    }

    @Test
    fun severalPeriodsAndHours() {
        val r = assertIs<ClosureRule.Seasonal>(rule("motor_vehicle:conditional" to "no @ (Dec-Feb); no @ (Jul-Aug Sa,Su 10:00-16:00)"))
        assertEquals(listOf(DayRange(day(12, 1), day(2, 29))), r.closed)
        assertEquals(listOf(DayRange(day(7, 1), day(8, 31))), r.partly)
        assertEquals(ClosedOn.CLOSED, r.on(day(2, 10)))
        assertEquals(ClosedOn.PART_OF_DAY, r.on(day(8, 1)))
        assertEquals(ClosedOn.OUTSIDE, r.on(day(5, 1)))
    }

    @Test
    fun hoursWithoutMonthsArePartlyAllYear() {
        val r = assertIs<ClosureRule.Seasonal>(rule("motor_vehicle:conditional" to "no @ (22:00-06:00)"))
        assertTrue(r.closed.isEmpty())
        assertEquals(listOf(DayRange.YEAR), r.partly)
        assertEquals(ClosedOn.PART_OF_DAY, r.on(day(3, 3)))
    }

    @Test
    fun onlyBansCountAndMotorcycleFirst() {
        // "destination" or "yes" do not close the road.
        assertSame(ClosureRule.Never, rule("motor_vehicle:conditional" to "destination @ (Nov-May)"))
        // A motorcycle-specific rule wins over the general one.
        val r = assertIs<ClosureRule.Seasonal>(rule("motorcycle:conditional" to "no @ (Jan)", "vehicle:conditional" to "no @ (Nov-May)"))
        assertTrue(r.closed.any { day(1, 10) in it })
        assertTrue(r.closed.any { day(12, 1) in it }) // both bans close the road
        assertEquals(listOf("Nov-May", "Mo-Fr 08:00-10:00"), ClosureRule.banConditions("no @ (Nov-May); private @ (Mo-Fr 08:00-10:00); yes @ (Jun)"))
    }

    @Test
    fun snowAndSunriseAreNotGuessed() {
        val snow = rule("motor_vehicle:conditional" to "no @ snow")
        assertIs<ClosureRule.Unreadable>(snow)
        assertEquals(ClosedOn.UNKNOWN, snow.on(day(7, 1)))
        // One readable period and one unreadable condition: outside the period nobody knows.
        val mixed = assertIs<ClosureRule.Seasonal>(rule("motor_vehicle:conditional" to "no @ (Dec-Mar); no @ (sunset-sunrise)"))
        assertTrue(mixed.other)
        assertEquals(ClosedOn.CLOSED, mixed.on(day(1, 1)))
        assertEquals(ClosedOn.UNKNOWN, mixed.on(day(7, 1)))
        assertIs<ClosureRule.Unreadable>(rule("seasonal" to "winter"))
        assertIs<ClosureRule.Unreadable>(rule("motor_vehicle:conditional" to "no @ (2026 Nov-2027 May)"))
    }

    @Test
    fun openingHours() {
        assertSame(ClosureRule.Never, rule("opening_hours" to "24/7"))
        // Open only in summer: closed the rest of the year.
        val summer = assertIs<ClosureRule.Seasonal>(rule("opening_hours" to "Jun-Oct"))
        assertEquals(listOf(DayRange(day(11, 1), day(5, 31))), summer.closed)
        assertEquals(ClosedOn.OUTSIDE, summer.on(day(8, 15)))
        // Explicitly off in winter, open otherwise.
        val off = assertIs<ClosureRule.Seasonal>(rule("opening_hours" to "Nov-Apr off"))
        assertEquals(listOf(DayRange(day(11, 1), day(4, 30))), off.closed)
        assertEquals(ClosedOn.OUTSIDE, off.on(day(5, 1)))
        // Open some hours in summer: partly closed then, closed in winter.
        val hours = assertIs<ClosureRule.Seasonal>(rule("opening_hours" to "May-Oct 08:00-18:00"))
        assertEquals(listOf(DayRange(day(11, 1), day(4, 30))), hours.closed)
        assertEquals(listOf(DayRange(day(5, 1), day(10, 31))), hours.partly)
        assertEquals(ClosedOn.PART_OF_DAY, hours.on(day(6, 1)))
        assertEquals("opening_hours=May-Oct 08:00-18:00", hours.raw)
    }

    @Test
    fun periodListWithCommas() {
        val r = assertIs<ClosureRule.Seasonal>(rule("motor_vehicle:conditional" to "no @ (Jan,Mar)"))
        assertEquals(listOf(DayRange(day(1, 1), day(1, 31)), DayRange(day(3, 1), day(3, 31))), r.closed)
        assertEquals(ClosedOn.OUTSIDE, r.on(day(2, 10)))
    }

    @Test
    fun runsJoinOverNewYear() {
        val days = BooleanArray(MonthDay.YEAR_DAYS)
        DayRange(day(12, 1), day(12, 31)).mark(days)
        DayRange(day(1, 1), day(2, 15)).mark(days)
        assertEquals(listOf(DayRange(day(12, 1), day(2, 15))), DayRange.runs(days))
        assertEquals(listOf(DayRange.YEAR), DayRange.runs(BooleanArray(MonthDay.YEAR_DAYS) { true }))
        assertTrue(DayRange.runs(BooleanArray(MonthDay.YEAR_DAYS)).isEmpty())
    }
}
