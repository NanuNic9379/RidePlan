package io.github.nanunic9379.rideplan.route

/** A day of the year, without the year. Feb 29 is a valid day. */
data class MonthDay(val month: Int, val day: Int) {
    init {
        require(month in 1..12 && day in 1..DAYS[month - 1]) { "Invalid day $day/$month" }
    }

    /** 0..365, counting Feb 29: the same number every year for the same day. */
    val index: Int get() = START[month - 1] + day - 1

    val isFirstOfMonth: Boolean get() = day == 1
    val isLastOfMonth: Boolean get() = day == DAYS[month - 1]

    companion object {
        /** Days in each month, Feb with 29. */
        internal val DAYS = intArrayOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        private val START = IntArray(12).also { s -> for (i in 1 until 12) s[i] = s[i - 1] + DAYS[i - 1] }
        const val YEAR_DAYS = 366

        fun ofIndex(i: Int): MonthDay {
            var m = 0
            while (m < 11 && i >= START[m + 1]) m++
            return MonthDay(m + 1, i - START[m] + 1)
        }

        fun lastOf(month: Int) = MonthDay(month, DAYS[month - 1])
    }
}

/** From [from] to [to], both included; wraps over New Year when [to] comes before [from] (Nov-May). */
data class DayRange(val from: MonthDay, val to: MonthDay) {
    operator fun contains(d: MonthDay): Boolean =
        if (from.index <= to.index) d.index in from.index..to.index else d.index >= from.index || d.index <= to.index

    /** Whole months, as "from November to May" rather than "from 1 November to 31 May". */
    val wholeMonths: Boolean get() = from.isFirstOfMonth && to.isLastOfMonth

    val wholeYear: Boolean get() = (to.index - from.index + MonthDay.YEAR_DAYS) % MonthDay.YEAR_DAYS == MonthDay.YEAR_DAYS - 1

    internal fun mark(days: BooleanArray) {
        var i = from.index
        while (true) {
            days[i] = true
            if (i == to.index) break
            i = (i + 1) % MonthDay.YEAR_DAYS
        }
    }

    companion object {
        val YEAR = DayRange(MonthDay(1, 1), MonthDay(12, 31))

        /** The marked days as ranges, a run over New Year as one range (Nov-May, not Jan-May + Nov-Dec). */
        internal fun runs(days: BooleanArray): List<DayRange> {
            val n = days.size
            if (days.all { it }) return listOf(YEAR)
            if (days.none { it }) return emptyList()
            // Start right after a free day, so a run over New Year is not cut in two.
            val start = (days.indices.first { !days[it] } + 1) % n
            val out = ArrayList<DayRange>()
            var runStart = -1
            for (k in 0 until n) {
                val i = (start + k) % n
                if (days[i] && runStart < 0) runStart = i
                if (!days[i] && runStart >= 0) {
                    out += DayRange(MonthDay.ofIndex(runStart), MonthDay.ofIndex((i - 1 + n) % n))
                    runStart = -1
                }
            }
            if (runStart >= 0) out += DayRange(MonthDay.ofIndex(runStart), MonthDay.ofIndex((start - 1 + n) % n))
            return out.sortedBy { it.from.index }
        }
    }
}

/** What a closure means on one day. Never "open": OpenStreetMap does not know the snow. */
enum class ClosedOn {
    /** The day falls in the period the road is closed. */
    CLOSED,

    /** Closed at some hours (or weekdays) in a period that includes the day. */
    PART_OF_DAY,

    /** The day is outside every closed period that could be read. */
    OUTSIDE,

    /** The rule cannot be read (snow, sunrise, free text): closed at some time, nobody knows when. */
    UNKNOWN,
}

/**
 * When a road is closed, from its OpenStreetMap tags (#13). Only month and day ranges, weekdays
 * and hours are understood; anything else (snow, sunrise, "wet", years, vehicle weights) makes
 * the rule [Unreadable] or adds [Seasonal.other], and the app says "closed part of the year"
 * instead of guessing.
 */
sealed interface ClosureRule {
    fun on(day: MonthDay): ClosedOn

    /** The road is never closed (opening_hours=24/7, or only "yes @" rules). */
    data object Never : ClosureRule {
        override fun on(day: MonthDay) = ClosedOn.OUTSIDE
    }

    /** Closed sometimes, but when cannot be read. [raw]: the tag as written, to show it. */
    data class Unreadable(val raw: String) : ClosureRule {
        override fun on(day: MonthDay) = ClosedOn.UNKNOWN
    }

    /**
     * [closed]: whole days closed every year. [partly]: days closed only at some hours or weekdays.
     * [other]: there are also conditions that could not be read: outside the periods the answer
     * is [ClosedOn.UNKNOWN], not [ClosedOn.OUTSIDE]. [raw]: the tag as written.
     */
    data class Seasonal(
        val closed: List<DayRange>,
        val partly: List<DayRange> = emptyList(),
        val other: Boolean = false,
        val raw: String = "",
    ) : ClosureRule {
        override fun on(day: MonthDay): ClosedOn = when {
            closed.any { day in it } -> ClosedOn.CLOSED
            partly.any { day in it } -> ClosedOn.PART_OF_DAY
            other -> ClosedOn.UNKNOWN
            else -> ClosedOn.OUTSIDE
        }
    }

    companion object {
        /** Access tags that ban motorcycles at some times, most specific first. */
        val CONDITIONAL_KEYS = listOf("motorcycle:conditional", "motor_vehicle:conditional", "vehicle:conditional", "access:conditional")

        /** The rule of a closure from its tags (the `tags` object of road-notes.json). */
        fun of(tags: Map<String, String>): ClosureRule {
            val conditions = CONDITIONAL_KEYS.mapNotNull { k -> tags[k]?.let { k to it } }
            val bans = conditions.flatMap { (_, v) -> banConditions(v) }
            if (bans.isNotEmpty()) {
                val raw = conditions.joinToString("; ") { (k, v) -> "$k=$v" }
                return fromParts(bans.map { parsePart(it) }, raw, opening = false)
            }
            tags["opening_hours"]?.let { oh ->
                val raw = "opening_hours=$oh"
                if (oh.trim() == "24/7") return Never
                val rules = oh.split(';').map { it.trim() }.filter { it.isNotEmpty() }
                if (rules.isEmpty()) return Unreadable(raw)
                return fromParts(rules.map { parsePart(it) }, raw, opening = true)
            }
            tags["seasonal"]?.takeIf { it != "no" && it.isNotBlank() }?.let { return Unreadable("seasonal=$it") }
            return if (conditions.isEmpty()) Unreadable(tags.entries.joinToString("; ") { "${it.key}=${it.value}" }) else Never
        }

        /** "no @ (Nov-May); destination @ (Mo-Fr)" → ["Nov-May"]: only the parts that ban. */
        internal fun banConditions(value: String): List<String> = splitTopLevel(value).mapNotNull { part ->
            val at = part.indexOf('@')
            if (at < 0) return@mapNotNull null
            val what = part.substring(0, at).trim().lowercase()
            if (what != "no" && what != "private") return@mapNotNull null
            part.substring(at + 1).trim().removeSurrounding("(", ")").trim()
        }

        /** Splits on ';' outside parentheses: "no @ (Mo 08:00-10:00; Tu 09:00-11:00)" is one part. */
        private fun splitTopLevel(value: String): List<String> {
            val out = ArrayList<String>()
            var depth = 0
            val cur = StringBuilder()
            for (c in value) {
                when {
                    c == '(' -> { depth++; cur.append(c) }
                    c == ')' -> { depth--; cur.append(c) }
                    c == ';' && depth <= 0 -> { out += cur.toString(); cur.clear() }
                    else -> cur.append(c)
                }
            }
            out += cur.toString()
            return out.map { it.trim() }.filter { it.isNotEmpty() }
        }

        /**
         * One rule: day ranges ([Part.days], empty = all year), then [Part.timed] when hours or
         * weekdays narrow it, [Part.off] for "off"/"closed", [Part.readable] false for anything else.
         */
        internal data class Part(val days: List<DayRange>, val timed: Boolean, val off: Boolean, val readable: Boolean)

        private const val MONTH = "(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)"
        // A number followed by ':' is an hour ("May-Oct 08:00-18:00"), not a day.
        private val RANGE = Regex("""^$MONTH(?:\s+(\d{1,2})(?![\d:]))?(?:\s*-\s*(?:$MONTH(?:\s+(\d{1,2})(?![\d:]))?|(\d{1,2})(?![\d:])))?""")
        private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        private val TIME = Regex("""^\d{1,2}:\d{2}\s*-\s*\d{1,2}:\d{2}\+?$""")
        private val WEEKDAYS = Regex("""^(Mo|Tu|We|Th|Fr|Sa|Su|PH)(-(Mo|Tu|We|Th|Fr|Sa|Su))?$""")

        internal fun parsePart(text: String): Part {
            var rest = text.trim()
            val days = ArrayList<DayRange>()
            while (true) {
                val m = RANGE.find(rest) ?: break
                val range = runCatching { range(m) }.getOrNull() ?: return Part(emptyList(), false, false, readable = false)
                days += range
                rest = rest.substring(m.value.length).trim()
                if (rest.startsWith(",") && RANGE.containsMatchIn(rest.drop(1).trim().take(3))) rest = rest.drop(1).trim() else break
            }
            rest = rest.removePrefix(":").trim()
            var timed = false
            var off = false
            // Hours and weekdays, separated by spaces or commas.
            for (token in rest.split(' ', ',').map { it.trim() }.filter { it.isNotEmpty() }) {
                when {
                    token.equals("off", true) || token.equals("closed", true) -> off = true
                    TIME.matches(token) -> timed = true
                    WEEKDAYS.matches(token) -> timed = true
                    else -> return Part(days, timed, off, readable = false)
                }
            }
            // A rule with nothing at all ("") cannot be read either.
            return Part(days, timed, off, readable = days.isNotEmpty() || timed)
        }

        private fun range(m: MatchResult): DayRange {
            val g = m.groupValues
            val m1 = MONTHS.indexOf(g[1]) + 1
            val d1 = g[2].toIntOrNull()
            val from = MonthDay(m1, d1 ?: 1)
            val to = when {
                g[3].isNotEmpty() -> {
                    val m2 = MONTHS.indexOf(g[3]) + 1
                    g[4].toIntOrNull()?.let { MonthDay(m2, it) } ?: MonthDay.lastOf(m2)
                }
                g[5].isNotEmpty() -> MonthDay(m1, g[5].toInt())
                d1 != null -> from
                else -> MonthDay.lastOf(m1)
            }
            return DayRange(from, to)
        }

        /**
         * Conditional bans: each part closes its days ([Part.timed]: only partly).
         * opening_hours: parts open their days (partly closed when [Part.timed]); "off" parts and
         * the days no part opens are closed.
         */
        private fun fromParts(parts: List<Part>, raw: String, opening: Boolean): ClosureRule {
            val readable = parts.filter { it.readable }
            if (readable.isEmpty()) return Unreadable(raw)
            val other = readable.size < parts.size
            val closed = BooleanArray(MonthDay.YEAR_DAYS)
            val partly = BooleanArray(MonthDay.YEAR_DAYS)
            fun days(p: Part) = p.days.ifEmpty { listOf(DayRange.YEAR) }
            if (!opening) {
                for (p in readable) days(p).forEach { it.mark(if (p.timed) partly else closed) }
            } else {
                val open = BooleanArray(MonthDay.YEAR_DAYS)
                for (p in readable) when {
                    p.off -> days(p).forEach { it.mark(closed) }
                    p.timed -> days(p).forEach { it.mark(open); it.mark(partly) }
                    else -> days(p).forEach { it.mark(open) }
                }
                // Only "off" rules ("Nov-Apr off"): the other days are not said to be closed.
                if (readable.any { !it.off }) for (i in closed.indices) if (!open[i]) closed[i] = true
            }
            for (i in partly.indices) if (closed[i]) partly[i] = false
            val c = DayRange.runs(closed)
            val p = DayRange.runs(partly)
            if (c.isEmpty() && p.isEmpty()) return if (other) Unreadable(raw) else Never
            return Seasonal(c, p, other, raw)
        }
    }
}
