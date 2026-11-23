# Initial analysis (23 November 2026)

Eight independent analyses of the first milestones (#1-#7): six riders
(sport, tourer, adventure, custom, commuter, motorcycle club group leader),
one technical analysis and one on privacy and security. The agents are in
`.claude/agents/`. This is the summary; decisions end up in the issues.

## What everyone asks for

| Topic | Who asks for it | Where it goes |
|---|---|---|
| Map right away, no tutorial, keys or permissions at startup (< 2-3 s, offline) | everyone | #1 |
| Large texts and controls, usable with gloves and rain, readable in the sun | everyone | #1, #4 |
| Size shown before the download, Wi-Fi only, warning "the ride leaves the downloaded area" | everyone | #2 |
| Download along a corridor of the ride, not just per region | sport, tourer, adventure, group leader | #2 |
| GPX with track **and** route with shaping points, tested on Garmin Zumo and TomTom | everyone except the commuter | #5 |
| An imported track stays as it is: never silently recalculated or "snapped" | adventure, group leader, commuter | #5 |
| QR/links that open even without the app (preview + GPX) and give everyone the same ride | everyone | #6 |
| Fuzzed start and finish so as not to reveal home | everyone + privacy | #5, #6, #7 |
| **Following the ride in the saddle** (position on the track, off route, screen on) | everyone | new issue |
| Range and fuel stations along the ride | sport, tourer, adventure, custom, group leader | new issue |

## The main technical risk: curves

Sport and custom say it in opposite and complementary ways: "more curves"
must pick real twisty secondary roads, but **never** dirt, mule tracks or
single-lane lanes. Valhalla's motorcycle profile has `use_curvature` (above 0.5
it prefers twisty roads), as well as `use_highways`, `use_tolls`,
`use_trails`, `use_ferry`. "Avoid dirt", however, is a preference
(`use_trails=0`), not a ban. It must be verified in the field **before**
building on it (#10), comparing 3-5 known rides with Kurviger.
GraphHopper and BRouter are Java only, so no iPhone: they remain a
fallback.

## Per persona

**Sport** - real curves, elevation gain and max altitude, closed passes
(`access:conditional`), fixed speed cameras from OSM, "shaping only" points that don't
make the Garmin go round in circles.

**Tourer** - multi-day trips with a km/hours cap per day, several countries
offline, avoid tolls/vignette (CH, AT) and ferries, export one file per
stage, large GPX on import, battery.

**Adventure** - three-state dirt (avoid/allow/prefer), map that
shows `track`, `tracktype`, `surface` and bans, free line where the
calculation has no data, track recording, editing imported tracks,
warning if the QR simplifies the track (deviation in meters).

**Custom** - "avoid dirt" on by default, stepped curve level,
avoid steep gradients and narrow roads (300 kg + passenger), stops with
duration in the arrival time, loop ride "150 km from home".

**Commuter** - vehicle profile with engine size: below 150 cc no
motorways and ring roads (Valhalla `motor_scooter`), fastest route,
favorites and widget, motorcycle parking and bans from OSM.

**Group leader** - the same identical ride for 40 people: data version and
options fixed in the route, meeting points and stops with times,
versioned ride update ("v3, landslide on the SP12"), ride
sheet to decide whether to come, short/long variants, export profiles
Garmin/TomTom/track only.

## Privacy and security

1. Private zone on by default for GPX, QR and Nostr, with a preview of what goes out.
2. Safe GPX parser (no DTD/external entities, limits on size and points, fuzz tests); versioned QR/links with a limit after decompression; deep link that always opens a preview.
3. Key in Keystore/Keychain, NIP-49 backup, external signer (NIP-55/NIP-46), coarse geohashes (4-5 characters), no real times in events.
4. No telemetry, `allowBackup` excluded for the key, Gradle verification metadata.
5. Correction: the signature proves **which key** signed, not who the person is; anyone can republish a ride with their own key.

## Technical analysis

The stack holds up; the offline data part changes.

| Piece | Choice | License | Note |
|---|---|---|---|
| Language and UI | Kotlin 2.4.21, Compose Multiplatform 1.12.2, AGP 9 | Apache-2.0 | `androidApp` module separate from `shared` (required by AGP 9) |
| Map | maplibre-compose 0.20.0 on MapLibre Native | BSD | API still 0.x: version pinned, updated by hand |
| Offline tiles | one PMTiles file per region (Protomaps build, max zoom 14) | ODbL | to be tried in maplibre-compose (#2); MBTiles as fallback |
| Route calculation | valhalla-mobile 0.6.6 (Valhalla 3.9.2) | MIT | iOS in Swift: `RouteEngine` interface implemented in `iosApp` |
| Route data | built with GitHub Actions from Geofabrik, per macro-region and alpine area, in the Releases | ODbL | Italy ~2.4 GB, not hundreds of MB; configurable mirror |
| GPX | xmlutil 1.0.2.1 or our own parser | Apache-2.0 | `rte` + `trk` |
| QR | qrose 1.3.1; data as polyline + deflate + Base45 | MIT | the QR holds a `https://…/r#data` link read by the system camera, no ML Kit |
| Nostr (#7) | Quartz 2.22.1, our own replaceable event (kind 3xxxx), GPX on Blossom | MIT | no NIP for routes; Quartz is downloaded with a non-Maven plugin |

**Open risks**
- **iPhone:** free macOS CI to build for the simulator, but installing on
  a real iPhone, TestFlight, App Store and Universal Links require the Apple
  Developer Program ($99/year). Decision for the project owner.
- **maplibre-compose 0.x:** may break the API with every version.
- **Border data:** alpine passes require packages built per area,
  not per country.

## New issues opened

See the "First milestones" section of `PROJECT.md` and the issues labeled
`analysis`.
