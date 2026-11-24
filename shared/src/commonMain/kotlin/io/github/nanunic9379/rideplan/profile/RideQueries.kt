package io.github.nanunic9379.rideplan.profile

import io.github.nanunic9379.rideplan.ride.GpxExport

/** The quick filters above the ride list (#21). */
enum class RideChip { ALL, TODO, DONE, FAVOURITES, RECEIVED }

enum class RideSort { LAST_OPENED, LAST_DONE, LONGEST, NAME }

enum class LengthFilter(val minKm: Double, val maxKm: Double) {
    ANY(0.0, Double.MAX_VALUE),
    UP_TO_100(0.0, 100.0),
    FROM_100_TO_200(100.0, 200.0),
    OVER_200(200.0, Double.MAX_VALUE),
}

/** What the ride list shows: search text, chip, order, bike and length. */
data class RideQuery(
    val text: String = "",
    val chip: RideChip = RideChip.ALL,
    val sort: RideSort = RideSort.LAST_OPENED,
    /** Only rides done with this bike (by name); null = any. */
    val bike: String? = null,
    val length: LengthFilter = LengthFilter.ANY,
)

object RideFilter {
    fun matchesChip(r: SavedRide, chip: RideChip): Boolean = when (chip) {
        RideChip.ALL -> true
        RideChip.TODO -> r.ridden.isEmpty()
        RideChip.DONE -> r.ridden.isNotEmpty()
        RideChip.FAVOURITES -> r.favourite
        RideChip.RECEIVED -> r.received != null
    }

    /** How many rides each chip would show. */
    fun counts(rides: List<SavedRide>): Map<RideChip, Int> =
        RideChip.entries.associateWith { chip -> rides.count { matchesChip(it, chip) } }

    /**
     * The rides to show, in order. [title] gives the name shown for a ride (also for unnamed
     * ones), used by search and by the alphabetical order.
     */
    fun apply(rides: List<SavedRide>, q: RideQuery, title: (SavedRide) -> String = { it.name.orEmpty() }): List<SavedRide> {
        val words = q.text.trim().lowercase().split(Regex("""\s+""")).filter { it.isNotEmpty() }
        val shown = rides.filter { r ->
            matchesChip(r, q.chip) &&
                r.km >= q.length.minKm && r.km < q.length.maxKm &&
                (q.bike == null || r.riddenWith.values.any { it.equals(q.bike, ignoreCase = true) }) &&
                (words.isEmpty() || searchText(r, title(r)).let { t -> words.all { it in t } })
        }
        return when (q.sort) {
            RideSort.LAST_OPENED -> shown.sortedByDescending { it.lastOpened }
            RideSort.LAST_DONE -> shown.sortedWith(compareByDescending<SavedRide> { it.lastRidden ?: Long.MIN_VALUE }.thenByDescending { it.lastOpened })
            RideSort.LONGEST -> shown.sortedByDescending { it.km }
            RideSort.NAME -> shown.sortedBy { title(it).lowercase() }
        }
    }

    /** The bikes you can filter by: the garage, then any other bike found in the history. */
    fun bikes(data: ProfileData): List<String> =
        (data.bikes.map { it.name } + data.rides.flatMap { it.riddenWith.values }).map { it.trim() }.filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }

    private fun searchText(r: SavedRide, title: String): String =
        (listOf(title, r.name.orEmpty(), r.notes) + r.stops.mapNotNull { it.name }).joinToString(" ").lowercase()
}

enum class Period { THIS_YEAR, LAST_YEAR, ALL }

/** One outing: a ride done on a day, maybe with a bike. */
data class Outing(val day: Long, val ride: SavedRide, val bike: String?) {
    val km: Double get() = ride.km
}

/** A bar of the chart: a month (1–12) of the period, or a year for "all time". */
data class Bar(val label: Int, val km: Double)

data class Repeated(val ride: SavedRide, val times: Int, val km: Double)

/** Statistics computed on the phone from your outings (#21). */
data class PeriodStats(
    val outings: Int,
    val km: Double,
    val longestKm: Double?,
    val averageKm: Double?,
    /** Months of the year, or years for [Period.ALL]. */
    val bars: List<Bar>,
    val barsAreYears: Boolean,
    /** The month (or year) with the most km. */
    val best: Bar?,
    /** Rides done at least twice, most repeated first. */
    val repeated: List<Repeated>,
    /** The rides done in the period, to draw where you rode. */
    val ridden: List<SavedRide>,
)

object RideStats {
    fun outings(data: ProfileData): List<Outing> =
        data.rides.flatMap { r -> r.ridden.map { Outing(it, r, r.riddenWith[it]) } }.sortedByDescending { it.day }

    fun of(data: ProfileData, period: Period, now: Long): PeriodStats {
        val year = yearOf(now)
        val all = outings(data)
        val inPeriod = when (period) {
            Period.THIS_YEAR -> all.filter { yearOf(it.day) == year }
            Period.LAST_YEAR -> all.filter { yearOf(it.day) == year - 1 }
            Period.ALL -> all
        }
        val km = inPeriod.sumOf { it.km }
        val bars = if (period == Period.ALL) {
            val years = inPeriod.map { yearOf(it.day) }
            val first = years.minOrNull() ?: year
            (first..year).map { y -> Bar(y, inPeriod.filter { yearOf(it.day) == y }.sumOf { it.km }) }
        } else {
            (1..12).map { m -> Bar(m, inPeriod.filter { monthOf(it.day) == m }.sumOf { it.km }) }
        }
        val repeated = inPeriod.groupBy { it.ride.id }.values
            .filter { it.size >= 2 }
            .map { Repeated(it.first().ride, it.size, it.sumOf { o -> o.km }) }
            .sortedWith(compareByDescending<Repeated> { it.times }.thenByDescending { it.km })
        return PeriodStats(
            outings = inPeriod.size,
            km = km,
            longestKm = inPeriod.maxOfOrNull { it.km },
            averageKm = if (inPeriod.isEmpty()) null else km / inPeriod.size,
            bars = bars,
            barsAreYears = period == Period.ALL,
            best = bars.filter { it.km > 0 }.maxByOrNull { it.km },
            repeated = repeated,
            ridden = inPeriod.map { it.ride }.distinctBy { it.id },
        )
    }

    /** The ride log (#21): outings grouped by month, newest first. Key: year * 100 + month. */
    fun log(data: ProfileData): List<Pair<Int, List<Outing>>> =
        outings(data).groupBy { yearOf(it.day) * 100 + monthOf(it.day) }.toList()

    fun yearOf(millis: Long): Int = civilDate(millis).third
    fun monthOf(millis: Long): Int = civilDate(millis).second
}

/** What "delete everything" removes (#21), counted to show it before confirming. */
data class WipeSummary(val rides: Int, val notes: Int, val outings: Int, val bikes: Int, val zones: Int) {
    companion object {
        fun of(data: ProfileData) = WipeSummary(
            rides = data.rides.size,
            notes = data.rides.count { it.notes.isNotBlank() },
            outings = data.rides.sumOf { it.ridden.size },
            bikes = data.bikes.size,
            zones = data.privateZones.size,
        )
    }
}

/**
 * "Export all rides" (#21): one GPX per ride, with unique file names, for a .zip.
 * Only the ride itself goes in: never notes, history or bikes.
 */
object RideExport {
    fun files(rides: List<SavedRide>, fallbackName: (SavedRide) -> String): List<Pair<String, String>> {
        val used = mutableSetOf<String>()
        return rides.map { saved ->
            val ride = saved.toRide()
            val fallback = fallbackName(saved)
            val base = GpxExport.fileName(ride, fallback).removeSuffix(".gpx")
            var name = "$base.gpx"
            var n = 2
            while (!used.add(name.lowercase())) name = "$base ($n).gpx".also { n++ }
            name to GpxExport.write(ride, fallback)
        }
    }
}

/** Day, month and year (UTC) of an instant: no libraries for three numbers. */
fun civilDate(millis: Long): Triple<Int, Int, Int> {
    val days = millis.floorDiv(86_400_000L)
    val z = days + 719_468
    val era = z.floorDiv(146_097L)
    val doe = z - era * 146_097
    val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    val y = yoe + era * 400 + if (m <= 2) 1 else 0
    return Triple(d.toInt(), m.toInt(), y.toInt())
}
