package io.github.nanunic9379.rideplan.ride

import kotlin.math.roundToInt

/**
 * What the group does at a stop (#10): a meeting point at 08:30, fuel, coffee, lunch.
 * [time]: planned time at the stop, minutes after midnight (0..1439); null = whenever we get there.
 * [pauseMinutes]: how long the group stays; it moves every later arrival.
 * [note]: a short line for the group ("bar on the square, park behind").
 */
data class StopPlan(
    val purpose: Purpose? = null,
    val time: Int? = null,
    val pauseMinutes: Int = 0,
    val note: String = "",
) {
    enum class Purpose(
        /** Stable code for links and files: never rename. */
        val code: String,
    ) {
        MEETING("meeting"),
        FUEL("fuel"),
        COFFEE("coffee"),
        LUNCH("lunch"),
        PHOTO("photo"),
        ;

        companion object {
            fun of(code: String?): Purpose? = entries.firstOrNull { it.code == code?.trim()?.lowercase() }
        }
    }

    /** Nothing planned: the stop carries no plan at all. */
    val isEmpty: Boolean get() = purpose == null && time == null && pauseMinutes == 0 && note.isBlank()

    /** Within limits: valid time, pause up to a day, note short enough for a QR. */
    fun normalized(): StopPlan? = StopPlan(
        purpose = purpose,
        time = time?.takeIf { it in 0 until MINUTES_PER_DAY },
        pauseMinutes = pauseMinutes.coerceIn(0, MAX_PAUSE),
        note = note.replace('\n', ' ').trim().take(MAX_NOTE),
    ).takeUnless { it.isEmpty }

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
        const val MAX_PAUSE = 12 * 60
        const val MAX_NOTE = 120

        /** The pauses offered in the editor, in minutes. */
        val PAUSES = listOf(0, 10, 15, 30, 45, 60, 90)

        /** "08:30" or "8.30" or "830" → minutes after midnight; null if it is not a time. */
        fun parseTime(text: String): Int? {
            val t = text.trim()
            if (t.isEmpty()) return null
            val m = Regex("""^(\d{1,2})(?:[:.h]?(\d{2}))?$""").matchEntire(t) ?: return null
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].ifEmpty { "0" }.toInt()
            if (h > 23 || min > 59) return null
            return h * 60 + min
        }

        /**
         * "08:30" on a 24-hour clock, "8:30 AM" on a 12-hour one ([h24] = false, #82), as the
         * phone is set. Wraps past midnight.
         */
        fun timeText(minutes: Int, h24: Boolean = true): String {
            val m = ((minutes % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
            val h = m / 60
            val mm = (m % 60).toString().padStart(2, '0')
            if (h24) return "${h.toString().padStart(2, '0')}:$mm"
            val h12 = if (h % 12 == 0) 12 else h % 12
            return "$h12:$mm ${if (h < 12) "AM" else "PM"}"
        }
    }
}

/**
 * The timetable of a ride (#10): when the group gets to each stop and leaves it.
 * Riding time is spread along the ride by distance, so the times are estimates
 * ([Row.estimated]); a planned time ([StopPlan.time]) is taken as it is.
 */
data class Timetable(
    val rows: List<Row>,
    /** Riding time, without pauses. */
    val ridingSeconds: Double,
    val pauseMinutes: Int,
    /** True when the riding time comes from an average speed, not from a computed route. */
    val roughSpeed: Boolean,
) {
    /**
     * [arrival] and [departure]: minutes after midnight of the first day (may exceed 1440);
     * null while no time is known yet (no planned time earlier in the ride).
     * [late]: the estimated arrival is after the planned time.
     */
    data class Row(
        val index: Int,
        val arrival: Int?,
        val departure: Int?,
        val estimated: Boolean,
        val late: Boolean,
    )

    val totalSeconds: Double get() = ridingSeconds + pauseMinutes * 60.0

    /** Estimated arrival at the finish, when a time is known. */
    val finish: Int? get() = rows.lastOrNull()?.arrival

    val hasTimes: Boolean get() = rows.any { it.arrival != null }

    companion object {
        /** Average speed when no route was computed (a GPX track): a calm ride on country roads. */
        const val ROUGH_KMH = 50.0

        /**
         * [km] and [seconds]: the length and riding time of the whole ride (from the route);
         * without [seconds] the time comes from [ROUGH_KMH].
         */
        fun of(ride: Ride, km: Double = ride.lengthKm, seconds: Double? = ride.route?.seconds): Timetable {
            val stops = ride.stops
            val riding = seconds ?: (km / ROUGH_KMH * 3600)
            val legs = legKm(ride)
            val sum = legs.sum()
            val legSeconds = legs.map { if (sum > 0) riding * it / sum else 0.0 }
            val rows = ArrayList<Row>(stops.size)
            var clock: Double? = null
            stops.forEachIndexed { i, stop ->
                if (i > 0 && clock != null) clock = clock!! + legSeconds[i - 1] / 60
                val plan = stop.plan
                val estimate = clock
                val planned = plan?.time?.let { t -> anchor(t, estimate) }
                val arrival = planned ?: estimate
                val late = planned != null && estimate != null && estimate > planned + 1
                // A meeting time is when the group leaves, too: nobody waits if we arrive late.
                val leave = arrival?.let { maxOf(it, estimate ?: it) + (plan?.pauseMinutes ?: 0) }
                rows += Row(
                    index = i,
                    arrival = arrival?.roundToInt(),
                    departure = leave?.roundToInt(),
                    estimated = planned == null,
                    late = late,
                )
                clock = leave
            }
            return Timetable(
                rows = rows,
                ridingSeconds = riding,
                pauseMinutes = stops.sumOf { s -> s.plan?.pauseMinutes ?: 0 }.let { total ->
                    // The pause at the finish is not part of the ride.
                    total - (stops.lastOrNull()?.plan?.pauseMinutes ?: 0)
                },
                roughSpeed = seconds == null,
            )
        }

        /**
         * A planned time on the day of the estimate: "09:00" after an estimate of 23:40 the day
         * before is the next morning (multi-day rides).
         */
        private fun anchor(time: Int, estimate: Double?): Double {
            if (estimate == null) return time.toDouble()
            var t = time.toDouble()
            val day = StopPlan.MINUTES_PER_DAY
            while (t < estimate - day / 2) t += day
            return t
        }

        /**
         * Distance of each leg (stop i to stop i+1): along the track when there is one, as the
         * crow flies otherwise. Only the proportions matter.
         */
        internal fun legKm(ride: Ride): List<Double> {
            val stops = ride.stops.map { it.point }
            if (stops.size < 2) return emptyList()
            val track = ride.track
            if (track.size < 2) return stops.zipWithNext { a, b -> distanceKm(a, b) }
            val along = DoubleArray(track.size)
            for (i in 1 until track.size) along[i] = along[i - 1] + distanceKm(track[i - 1], track[i])
            // Each stop on the nearest track point, never going back.
            var from = 0
            val at = stops.map { p ->
                var best = from
                var bestD = Double.MAX_VALUE
                for (i in from until track.size) {
                    val d = distanceKm(track[i], p)
                    if (d < bestD) { bestD = d; best = i }
                }
                from = best
                along[best]
            }
            return at.zipWithNext { a, b -> (b - a).coerceAtLeast(0.0) }
        }
    }
}

/**
 * The ride sheet (#10): a plain text to send in the group chat or print. No coordinates:
 * stops inside a private zone (#6) lose their name.
 */
object RideSheet {
    /** The translated words of the sheet; the UI fills them. */
    data class Labels(
        val title: (version: Int?) -> String,
        val distance: (km: Double) -> String,
        val duration: (seconds: Double) -> String,
        /** "Riding 3 h 10 min, with stops 4 h 25 min". */
        val totals: (riding: String, total: String) -> String,
        val purpose: (StopPlan.Purpose) -> String,
        val pause: (minutes: Int) -> String,
        val stop: (number: Int) -> String,
        val privatePlace: String,
        val start: String,
        val finish: String,
        val approx: String,
        val late: String,
        val updated: (note: String) -> String,
        /** A clock time as the phone shows it (#82). */
        val clock: (minutes: Int) -> String = { StopPlan.timeText(it) },
    )

    fun text(
        ride: Ride,
        name: String,
        labels: Labels,
        km: Double = ride.lengthKm,
        seconds: Double? = ride.route?.seconds,
        zones: List<PrivateZone> = emptyList(),
        link: String? = null,
    ): String {
        val table = Timetable.of(ride, km, seconds)
        val sb = StringBuilder()
        val version = ride.version
        sb.append(name)
        labels.title(version?.number).takeIf { it.isNotEmpty() }?.let { sb.append(" · ").append(it) }
        sb.append('\n')
        version?.note?.takeIf { it.isNotBlank() }?.let { sb.append(labels.updated(it)).append('\n') }
        sb.append(labels.distance(km)).append(" · ")
            .append(labels.totals(labels.duration(table.ridingSeconds), labels.duration(table.totalSeconds)))
            .append('\n')
        sb.append('\n')
        ride.stops.forEachIndexed { i, stop ->
            val row = table.rows[i]
            val plan = stop.plan
            // Only what matters to the group: start, finish and the stops where we stop.
            if (i != 0 && i != ride.stops.lastIndex && plan == null && stop.kind != Stop.Kind.STOP) return@forEachIndexed
            val hidden = zones.any { it.contains(stop.point) }
            val place = when {
                hidden -> labels.privatePlace
                stop.name != null -> stop.name.substringBefore(',').trim()
                i == 0 -> labels.start
                i == ride.stops.lastIndex -> labels.finish
                else -> labels.stop(i + 1)
            }
            val time = row.arrival?.let { (if (row.estimated) labels.approx else "") + labels.clock(it) }
            sb.append(time ?: "--:--").append("  ")
            plan?.purpose?.let { sb.append(labels.purpose(it)).append(" · ") }
            sb.append(place)
            if (plan != null && plan.pauseMinutes > 0 && i != ride.stops.lastIndex) sb.append(" (").append(labels.pause(plan.pauseMinutes)).append(')')
            if (row.late) sb.append(" ").append(labels.late)
            sb.append('\n')
            if (plan != null && plan.note.isNotBlank()) sb.append("        ").append(plan.note).append('\n')
        }
        if (link != null) sb.append('\n').append(link).append('\n')
        return sb.toString().trimEnd() + "\n"
    }
}
