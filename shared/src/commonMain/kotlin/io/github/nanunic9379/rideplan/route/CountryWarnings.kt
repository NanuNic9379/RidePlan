package io.github.nanunic9379.rideplan.route

import io.github.nanunic9379.rideplan.ride.LatLon

/** One thing to know before riding in a country (#15). */
sealed interface CountryNote {
    data object Vignette : CountryNote

    /** The table does not know whether a motorcycle needs the vignette. */
    data object VignetteUnclear : CountryNote

    data object TollMotorways : CountryNote

    data object TollSome : CountryNote

    data class MotorwayMinCc(val cc: Int) : CountryNote

    data class MotorwayMinSpeed(val kmh: Int) : CountryNote
}

/** A crossed country and its notes; [rules] null = not in the table. */
data class CountryNotice(val country: String, val notes: List<CountryNote>, val rules: CountryRules?)

/** What the ride sheet says about the countries of a ride (#15, option A). */
data class CountryWarnings(
    /** Every country the ride crosses, in order. */
    val countries: List<String>,
    /** The countries with something to say, in the same order; the home country is left out. */
    val notices: List<CountryNotice>,
) {
    /** Nothing new for the rider: one country, their own, or no country found. */
    val isEmpty: Boolean get() = notices.isEmpty() && countries.size < 2

    /** Some shown rule has not been checked by a person yet. */
    val unverified: Boolean get() = notices.any { it.rules?.verified != true }

    companion object {
        val NONE = CountryWarnings(emptyList(), emptyList())

        fun notes(rules: CountryRules): List<CountryNote> = buildList {
            when (rules.vignette) {
                CountryRules.Answer.YES -> add(CountryNote.Vignette)
                CountryRules.Answer.UNCLEAR -> add(CountryNote.VignetteUnclear)
                CountryRules.Answer.NO -> {}
            }
            when (rules.tolls) {
                CountryRules.Tolls.MOTORWAYS -> add(CountryNote.TollMotorways)
                CountryRules.Tolls.SOME -> add(CountryNote.TollSome)
                CountryRules.Tolls.NONE -> {}
            }
            rules.motorwayMinCc?.let { add(CountryNote.MotorwayMinCc(it)) }
            rules.motorwayMinSpeedKmh?.let { add(CountryNote.MotorwayMinSpeed(it)) }
        }

        /**
         * Warnings for the [countries] a ride crosses. [home] (the phone's country, may be null)
         * gets no notes: its rider already knows its rules. A ride only at home says nothing.
         */
        fun of(countries: List<String>, home: String?): CountryWarnings {
            val homeCode = home?.uppercase()?.ifEmpty { null }
            if (countries.isEmpty() || (countries.size == 1 && countries[0] == homeCode)) return NONE
            val notices = countries.filter { it != homeCode }.mapNotNull { code ->
                val rules = CountryRules.of(code) ?: return@mapNotNull null
                notes(rules).takeIf { it.isNotEmpty() }?.let { CountryNotice(code, it, rules) }
            }
            return CountryWarnings(countries, notices)
        }

        /** Countries crossed by [line] and their warnings. */
        fun forLine(line: List<LatLon>, home: String?): CountryWarnings = of(Countries.crossed(line), home)
    }
}
