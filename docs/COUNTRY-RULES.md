# Languages, units and per-country rules (#15)

Goal: a RidePlan that works for riders outside Italy. This document records what is done,
how it works, and the proposal for the per-country road rules, which still need a decision.

## Done

### Translations
- English is the base language (`values/strings.xml`); Italian, German, Spanish and French
  are complete.
- `StringsTest` (androidHostTest) parses every `strings.xml` and fails the build when a language
  misses a key, has an extra one, an empty text, or different placeholders (`%1$s`, `%2$d`) from
  English. It also checks that `locales_config.xml` (the languages Android 13+ offers in the app
  settings) lists exactly the translated languages.
- Texts that are identical to English in a translation were reviewed: they are all real
  cognates or symbols ("Naked", "Scooter", "Garage", "OK", "km/h", "mph", "Version %1$s").
- Not done: community translations (Weblate). Weblate Hosted is free for open source projects
  and reads Android-style `strings.xml`; it needs an account linked to the GitHub repository,
  which is an owner decision. The test above already protects the build from incomplete
  contributions.

### Units
- Setting "Units": **Automatic** (default), Kilometres, Miles. Automatic follows the country of
  the phone (Android system locale, not the app language): miles in the countries whose road
  signs are in miles (US and territories, UK and Crown Dependencies, Liberia, Myanmar, some
  Caribbean countries, see `Regions.MILES`), kilometres elsewhere.
- Settings saved before this change keep their explicit "KM"/"MILES"; a new install starts on
  Automatic.
- One formatting helper, `profile/Measures.kt`, turns every distance and speed into text:
  ride length, stats, detour note, share preview, length filters, bike range, the navigation
  banner (metres/feet below 1 km / 0.1 mi), the voice ("in 300 feet", "in 0.5 mi"), the speed
  and the speed-limit sign.
- Decimals follow the app language: "12,5 km" in it/de/es/fr, "12.5 km" in English.
- Fuel: the garage keeps storing litres and km per litre. On screen and when typing:
  km → litres and km/l; miles in the US → US gallons and mpg (US); miles elsewhere (UK) →
  litres for the tank and mpg (imperial gallon), as on British pumps and car brochures.
- The voice: Valhalla's own "Continue for 12 kilometers" stays as it is in km; in miles the app
  says "Continue for 7 mi" itself, so the routing engine does not change.

### Not done (small, later)
- Time of day (an ETA clock) does not exist in the app yet; durations are "1 h 20 min" in every
  language. When an ETA is added, format it with the platform 12/24 h setting.
- "Remove the first and last km" (privacy of shared rides) stays 1 km in every unit.
- Valhalla instruction texts other than "Continue for..." never contain distances.

## Per-country rules: proposal (needs a decision)

Today the only road rule is in the bike: `Engine.UP_TO_150` avoids motorways everywhere. That
is right for Italy (motorways forbidden under 150 cc), roughly right for most of Europe, and
wrong elsewhere (e.g. Germany only forbids vehicles that cannot do 60 km/h; Switzerland and
Austria use the same speed rule; the UK allows learners with L plates nowhere on motorways).

### What riders need, by importance
1. **Minimum engine size / speed on motorways** — decides the route. Wrong = an illegal route.
2. **Vignettes** (CH, AT, SI, CZ, SK, HU, BG, RO, ...): a sticker or e-vignette needed to use
   the motorway at all. A rider without one wants to avoid those motorways, or be warned
   before the border. Motorcycles have their own price and period (AT 10-day, SI 7-day, CH only
   yearly).
3. **Tolls** (IT, FR, ES, PT, HR, ...): pay at the booth; "avoid tolls" already exists.
4. **Speed-camera warnings**: illegal to use in France (zones only), Germany, Switzerland,
   Belgium, Slovakia and others; legal in Italy and the UK. Relevant only if RidePlan ever
   shows speed cameras — it does not today, and should not without this rule.
5. Others, informational: mandatory equipment (hi-vis vest in FR/BE, gloves in FR,
   breathalyser in FR), lights on by day, lane filtering legality.

### Data model (small)

```kotlin
@Serializable
data class CountryRules(
    val country: String,                 // ISO 3166 alpha-2
    /** Smallest engine allowed on motorways, cc; null = no cc rule. */
    val motorwayMinCc: Int? = null,
    /** Smallest design speed allowed on motorways, km/h (DE/AT/CH: 60); null = none. */
    val motorwayMinSpeedKmh: Int? = null,
    /** A vignette is needed on motorways, and what it costs a motorcycle. */
    val vignette: Vignette? = null,
    val tolls: Boolean = false,
    /** Speed-camera warnings are illegal: never show them. */
    val speedCameraWarningsIllegal: Boolean = false,
    /** Short notes shown before entering the country (string resource keys). */
    val notes: List<String> = emptyList(),
    /** Date the entry was last checked, so stale rules can be flagged. */
    val checked: String,
)

@Serializable
data class Vignette(val shortestDays: Int?, val electronic: Boolean, val url: String)
```

- Stored as a bundled JSON file in `composeResources/files/country-rules.json` (~40 European
  countries + US/UK/CA/AU), updated with app releases. No server, no tracking.
- The bike gets an optional `cc: Int?` (and `topSpeedKmh` for small electric/scooter cases),
  replacing the three-way `Engine` with a derived class kept for compatibility.
- Routing: the countries a route crosses are found from the route's cells (#4) or from a
  coarse country-polygon file. If a crossed country forbids motorways for the bike, use
  `use_highways = 0` (as today). Per-country (not whole-route) avoidance would need Valhalla
  `exclude_polygons` or a re-route per section: more work, decide later.
- Vignettes: a "I have the vignette for: CH, AT..." list in the bike or settings. Without it,
  the route avoids motorways in that country (same mechanism) or the ride sheet warns
  "This ride uses Swiss motorways: vignette required".

### Options
- **A. Only warnings** (recommended first step): show on the ride sheet the countries crossed
  and their notes (vignette, tolls, motorway rule). Little code, no routing risk, useful at once.
- **B. Warnings + motorway rule per country from the bike's cc**: needs the country list of the
  route and `cc` in the bike; the route changes only when a rule forbids the motorway.
- **C. Full vignette handling** (avoid or accept per country): needs per-section routing.

Recommendation: A now, B together with the vehicle profile work (#8), C only if riders ask.
The data must be checked by hand and dated (`checked`): rules change, and a wrong rule is worse
than no rule. Community corrections can go through the same Weblate/GitHub flow.

### Decisions for the owner
1. Weblate: create the project linked to the GitHub repository (free for open source)?
2. Country rules: start with option A? Who checks the data per country (owner, community)?
3. Should the bike profile ask the exact cc instead of "up to 150 / over 150 / electric"?
