package io.github.nanunic9379.rideplan.route

/**
 * Road rules that matter to a motorcyclist, per country (#15, option A: warnings only).
 *
 * Hand-written and small on purpose: a wrong rule is worse than no rule. Every entry says when
 * it was compiled and whether a person has checked it against the official source
 * ([verified]). Today no entry is verified: they were compiled from the official sites listed
 * in [source] and from general knowledge, and must be checked by the owner or a rider who knows
 * the country before they are trusted. See docs/COUNTRY-RULES.md.
 *
 * General sources for every country:
 * - European Commission, "Going abroad": https://road-safety.transport.ec.europa.eu/going-abroad_en
 * - Natural Earth (outlines only, not rules): https://www.naturalearthdata.com/
 */
data class CountryRules(
    /** ISO 3166 alpha-2. */
    val country: String,
    /** A motorway vignette (sticker or electronic) is needed by a motorcycle. */
    val vignette: Answer = Answer.NO,
    val tolls: Tolls = Tolls.NONE,
    /** Smallest engine allowed on motorways, cc; null = no cc rule beyond mopeds. */
    val motorwayMinCc: Int? = null,
    /** Smallest design speed allowed on motorways, km/h; null = no such rule known. */
    val motorwayMinSpeedKmh: Int? = null,
    /** Is it legal to warn about speed cameras (apps, not radar detectors)? */
    val speedCameraWarningsLegal: Answer = Answer.UNCLEAR,
    /** Official page with the motorway rules or the vignette shop. */
    val source: String? = null,
    /** When the entry was compiled or last checked, yyyy-MM. */
    val checked: String = COMPILED,
    /** A person checked every field against [source]. */
    val verified: Boolean = false,
) {
    enum class Answer { YES, NO, UNCLEAR }

    enum class Tolls {
        NONE,

        /** A few toll roads, bridges or tunnels. */
        SOME,

        /** Most motorways are tolled at a booth or by plate. */
        MOTORWAYS,
    }

    /** Something to tell the rider before the ride; nothing when the country has no special rule. */
    val hasWarnings: Boolean
        get() = vignette != Answer.NO || tolls != Tolls.NONE || motorwayMinCc != null || motorwayMinSpeedKmh != null

    companion object {
        const val COMPILED = "2026-11"

        private val table: List<CountryRules> = listOf(
            // Italy: motorways forbidden to motorcycles under 150 cc (Codice della strada, art. 175).
            // Tolls on most motorways. Speed-camera warnings are legal; fixed cameras must be signed.
            CountryRules(
                "IT", tolls = Tolls.MOTORWAYS, motorwayMinCc = 150, speedCameraWarningsLegal = Answer.YES,
                source = "https://www.normattiva.it/uri-res/N2Ls?urn:nir:stato:decreto.legislativo:1992-04-30;285~art175",
            ),
            // Switzerland: yearly vignette also for motorcycles (sticker or e-vignette). Motorways
            // only for vehicles faster than 80 km/h. Warning about speed cameras is forbidden (SVG art. 98a).
            CountryRules(
                "CH", vignette = Answer.YES, tolls = Tolls.SOME, motorwayMinSpeedKmh = 80,
                speedCameraWarningsLegal = Answer.NO, source = "https://via.admin.ch/shop/dashboard",
            ),
            // Austria: motorcycle vignette (10 days, 2 months, year), extra toll on some passes and
            // tunnels (Brenner, Tauern, Arlberg...). Motorways for vehicles faster than 60 km/h.
            CountryRules(
                "AT", vignette = Answer.YES, tolls = Tolls.SOME, motorwayMinSpeedKmh = 60,
                source = "https://www.asfinag.at/en/toll/vignette/",
            ),
            // Slovenia: e-vignette also for motorcycles (7 days, 6 months, year); Karawanken tunnel toll.
            CountryRules("SI", vignette = Answer.YES, tolls = Tolls.SOME, source = "https://evinjeta.dars.si/en"),
            // Hungary: e-matrica, category D1M for motorcycles.
            CountryRules("HU", vignette = Answer.YES, source = "https://ematrica.nemzetiutdij.hu/"),
            // Czechia, Slovakia, Bulgaria: motorcycles are reported exempt from the e-vignette;
            // sources disagree, so UNCLEAR until checked.
            CountryRules("CZ", vignette = Answer.UNCLEAR, source = "https://edalnice.cz/en/"),
            CountryRules("SK", vignette = Answer.UNCLEAR, speedCameraWarningsLegal = Answer.NO, source = "https://eznamka.sk/en/"),
            CountryRules("BG", vignette = Answer.UNCLEAR, source = "https://web.bgtoll.bg/"),
            // Romania: rovinieta; whether motorcycles need it is reported both ways.
            CountryRules("RO", vignette = Answer.UNCLEAR, tolls = Tolls.SOME, source = "https://www.erovinieta.ro/"),
            // Moldova: vignette for foreign-registered vehicles; motorcycles not clear.
            CountryRules("MD", vignette = Answer.UNCLEAR),
            // Germany: no motorway charge for motorcycles. Motorways for vehicles faster than
            // 60 km/h (StVO § 18). Speed-camera warning apps forbidden while driving (StVO § 23 1c).
            CountryRules(
                "DE", tolls = Tolls.SOME, motorwayMinSpeedKmh = 60, speedCameraWarningsLegal = Answer.NO,
                source = "https://www.gesetze-im-internet.de/stvo_2013/__18.html",
            ),
            // France: tolls on most autoroutes. Motorcycles under 80 cc unverified; detectors and
            // camera-position warnings forbidden (only "danger zones").
            CountryRules(
                "FR", tolls = Tolls.MOTORWAYS, motorwayMinCc = 80, speedCameraWarningsLegal = Answer.NO,
                source = "https://www.autoroutes.fr/",
            ),
            CountryRules("ES", tolls = Tolls.SOME, speedCameraWarningsLegal = Answer.YES, source = "https://www.dgt.es/"),
            CountryRules("PT", tolls = Tolls.MOTORWAYS, source = "https://www.portugaltolls.com/"),
            CountryRules("BE", speedCameraWarningsLegal = Answer.NO),
            CountryRules("NL", speedCameraWarningsLegal = Answer.YES),
            CountryRules("LU"),
            CountryRules("GB", tolls = Tolls.SOME, speedCameraWarningsLegal = Answer.YES),
            CountryRules("IE", tolls = Tolls.SOME),
            CountryRules("DK", tolls = Tolls.SOME, source = "https://storebaelt.dk/en/"),
            CountryRules("SE", tolls = Tolls.SOME),
            CountryRules("NO", tolls = Tolls.SOME, source = "https://www.autopass.no/en/"),
            CountryRules("FI"),
            CountryRules("PL", tolls = Tolls.SOME, speedCameraWarningsLegal = Answer.YES),
            CountryRules("HR", tolls = Tolls.MOTORWAYS, source = "https://www.hac.hr/en"),
            CountryRules("GR", tolls = Tolls.MOTORWAYS),
            CountryRules("RS", tolls = Tolls.MOTORWAYS),
            CountryRules("MK", tolls = Tolls.MOTORWAYS),
            CountryRules("ME", tolls = Tolls.SOME),
            CountryRules("BA", tolls = Tolls.SOME),
            CountryRules("AL", tolls = Tolls.SOME),
            CountryRules("XK"),
            CountryRules("EE"),
            CountryRules("LV"),
            CountryRules("LT"),
            CountryRules("LI"),
            CountryRules("SM"),
            CountryRules("MC"),
            CountryRules("AD"),
            CountryRules("MT"),
        )

        private val byCode: Map<String, CountryRules> = table.associateBy { it.country }

        /** Every country in the table, ISO code to rules. */
        val all: Map<String, CountryRules> get() = byCode

        /** Rules of a country ("CH", "ch"); null when it is not in the table. */
        fun of(country: String?): CountryRules? = country?.uppercase()?.let(byCode::get)

        /**
         * Owner decision (#15): RidePlan shows speed-camera warnings only in Italy, and never
         * elsewhere, even where they may be legal. A future speed-camera feature must ask this.
         */
        val SPEED_CAMERA_WARNINGS_COUNTRIES: Set<String> = setOf("IT")

        /** Whether RidePlan may show speed-camera warnings in [country]. */
        fun speedCameraWarningsAllowed(country: String?): Boolean {
            val code = country?.uppercase() ?: return false
            return code in SPEED_CAMERA_WARNINGS_COUNTRIES && of(code)?.speedCameraWarningsLegal == Answer.YES
        }
    }
}
