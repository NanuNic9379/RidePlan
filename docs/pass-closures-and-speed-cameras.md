# Seasonal pass closures and speed cameras (#13)

What the issue asks:

1. A warning if the ride goes over roads with `access:conditional` / `seasonal` (Gavia, Stelvio,
   Nivolet...), with an alternative.
2. Fixed speed cameras from OSM (`highway=speed_camera`) on the map and along the ride.

Both must work offline, like routing.

## Data side (shipped earlier)


- `data/road_notes.py` (standard library only) and a new "Road notes" step in
  `.github/workflows/routing-data.yml`. From the full Italy extract, before it is cut down to
  roads, `osmium tags-filter` keeps `highway=speed_camera` nodes and ways with
  `motor_vehicle:conditional`, `motorcycle:conditional`, `vehicle:conditional`,
  `access:conditional`, `seasonal` or `opening_hours`; `osmium export` turns them into GeoJSON
  lines; the script keeps only motor roads with a real ban for part of the time and writes
  `road-notes.json`:

  ```json
  {"format":1,"osmDate":"...",
   "closures":[{"id":"w123","highway":"secondary","line":[[10.45,46.53],...],
                "tags":{"motor_vehicle:conditional":"no @ (Nov-May)"},
                "names":{"name":"Passo dello Stelvio","ref":"SS38"}}],
   "cameras":[{"id":"n456","lat":45.1,"lon":9.12,"maxspeed":"50","direction":"90"}]}
  ```

  A final "Publish road notes" step uploads it to the `routing-data` Release.
  Expected size for Italy: a few thousand cameras and a few hundred closed roads, well under 1 MB
  (to be measured on the first run).
- Tested locally on a hand-made sample (camera kept; Stelvio-like `no @ (Nov-May)` kept;
  `seasonal` track kept; a shop's `opening_hours`, a footway and a `destination @ (Mo-Fr)` rule
  dropped).

**Careful**: `routing-data.yml` runs on every push to main that touches it. Merging this commit
re-runs the whole Italy build (about 3-4 hours on the runner) and publishes the new file next to
the cells. That is harmless for the app (the new file is not read yet) but it uses Actions
minutes; if Actions are disabled nothing happens until it is run by hand.

## App side (shipped)

Owner decisions applied: warn about seasonal closures with "According to OpenStreetMap, Passo X
is closed from November to May: check before leaving", offer a route that avoids the closed
stretch; fixed speed cameras on the ride map and in navigation, only in Italy, setting on by
default.

### Download

- `RouteService.downloadRoadNotes` fetches `road-notes.json` from the Release of the area being
  downloaded (`routing-data` for Italy, `routing-<area>` for the areas of #14) into that area's
  folder, after the cells, never blocking routing. The notes of all areas on the phone are merged
  (each road or camera once, as areas overlap): any failure (no file yet, network, bad JSON, over 16 MB) only
  means no notes. When `cells.json` lists it (`"roadNotes": {"file", "bytes", "sha256"}`, parsed by
  `RoutingCells`, see `RoadNotesFile`), size and SHA-256 are checked; today the manifest does not
  list it, so the file is only kept when it parses as format 1.
- `RouteService.roadNotes(fetch)` reads the local copy (cached) and, once per app run, downloads it
  when it is missing or older than the cells on the phone (`osmDate`). On metered networks with
  "Download only on Wi-Fi" it does not fetch.
- **Without the file nothing is said**: no "no closures", no "no cameras".

### Closures (`route/ClosureRule.kt`, `route/RoadNotesAlong.kt`)

- `ClosureRule.of(tags)` reads `motorcycle:conditional`, `motor_vehicle:conditional`,
  `vehicle:conditional`, `access:conditional` (only `no` / `private @ (...)` parts), then
  `opening_hours`, then `seasonal`. Understood: month ranges (`Nov-May`, `Nov 15-May 15`,
  `May 15-31`, lists `Jan,Mar`), weekdays and hours (these make a period "closed at certain
  times"), `off`. Anything else (snow, sunrise, years, free text) is never guessed: the rule
  becomes "closed part of the year" (`Unreadable`), or adds "unknown" outside the readable
  periods. `24/7` = never closed. A pass is never said to be open.
- Matching: points every 20 m along the closed road; a point is on the ride when within 20 m of
  its line. The ride must follow the road for at least min(200 m, half its length) (25 m minimum)
  and the matched part must run along the ride, so crossings, bridges, parallel roads and
  touching the end of a long road do not count. Out-and-back rides give two entries. Consecutive
  pieces with the same name and rule (a pass is many OSM ways) within 2 km become one entry. km
  follow the engine's length, like the fuel stations.
- Ride sheet (`ride/RoadNotesUi.kt`, under Fuel): one entry per pass with its km, the sentence with
  the period, the original tag, and the check for the ride date: rides have no date, so it is
  today unless the rider picks another day ("Change date", today or later). Closed that day /
  closed at some hours that day / outside the period "but opening dates change with the snow".
- "Route avoiding it": the ride is routed again with a 50 m square around the middle of the
  closed stretch sent as Valhalla `exclude_polygons` (`Ride.avoid`, `ValhallaRoute.request`;
  well under Valhalla's default 10 km perimeter limit). The sheet then shows "The route avoids X"
  with "Ride over it again". `Ride.avoid` is not saved: reopening a saved ride offers the warning
  again. If no other road exists the engine fails as for any impossible route; the rider undoes.
- Map: closed stretches drawn in the theme's error colour over the ride's line.

### Speed cameras

- Cameras within 30 m of the ride line, each pass counted; only those whose position is in a
  country where `CountryRules.speedCameraWarningsAllowed` (Italy), using the offline borders
  (`Countries.at`). The `direction` tag is not used (a camera on the opposite carriageway of a dual
  road may show).
- Ride sheet: "N fixed speed cameras on this ride, from OpenStreetMap"; map: dots.
- Navigation (`nav/SpeedCameraAlerts.kt`): dots on the map, and within 500 m ahead a banner
  "Speed camera in 300 m" (with the limit when tagged); the voice says it once per camera unless
  muted. Recomputed after a reroute.
- Settings, "Speed cameras" switch (`AppSettings.speedCameras`, default on). Off = no cameras
  anywhere.

### Tests

`ClosureRuleTest` (date ranges over New Year, conditional and opening_hours rules, hours vs
days, unreadable cases), `RoadNotesTest` (file and manifest parsing, names, maxspeed,
`exclude_polygons` in the request), `RoadNotesAlongTest` (follow vs cross vs parallel vs touch,
joined pieces, out and back, km scaling, cameras near/far/twice, country filter with real borders,
`CameraWatch` announcing once).

## What is left

- Data side: add `"roadNotes": {"file", "bytes", "sha256"}` to `cells.json` in the workflow, so the
  phone verifies the file (the app already reads it). Left out here so as not to re-run the
  routing-data build.
- Ride dates: rides have no date field; the picked date lives only while the sheet is open. When
  rides get a date (trips of #12), use it.
- Use `direction` to drop cameras for the other carriageway; average-speed sections
  (`enforcement` relations) are not in the file.
- Optional: send `date_time` to Valhalla so the engine itself honours `*:conditional` (see below).
- Only Italy's data exists; other countries need the legal table below confirmed first.

## Original analysis

### Seasonal closures on the phone

1. Download `road-notes.json` together with the routing cells (same Release, same "ask on mobile
   data" rule) and keep it next to the tiles.
2. After a ride is computed, check its line against the closures: a closure counts when the ride
   follows its line for more than ~200 m (distance of route points to the closure line < 15 m).
3. Interpret the condition for the **date of the ride**: parse the month ranges in
   `no @ (Nov-May)` / `opening_hours=May 15-Oct 15`. When the date is unknown or the rule cannot
   be read (sunrise times, "snow", free text), say so: "Passo dello Stelvio is closed part of the
   year (Nov-May): check before you go". Never claim a pass is open.
4. Warning in the ride sheet (like the unpaved note of #9) and on the map (red line over the
   ride).
5. **Alternative**: route again with the closed roads excluded. Valhalla supports `exclude_polygons`
   (small polygons around the closed stretch) since 3.1; the app keeps both routes and lets the
   rider pick. This needs one additive parameter in `ValhallaRoute.request` (coordinate with
   #4/#8).

Alternative considered: let Valhalla itself apply the conditional restrictions (it stores
`*:conditional` time restrictions in the tiles and honours them when the request has a
`date_time`). Cheaper, but the rider gets a silently different route with no explanation, and
seasonal rules written as `seasonal=yes` or `opening_hours` are not used by the engine. Better
as a later addition (send `date_time` when the ride has a date) than as the only mechanism.

Caveat: OSM coverage is uneven. Stelvio, Gavia, Nivolet and most Alpine passes are tagged, many
minor passes are not, and real opening dates change with the snow every year. The warning must
say "according to OpenStreetMap" and suggest checking the official road status (ANAS / province).

### Speed cameras

Technically simple once the file is on the phone: dots on the map (ride view and navigation) and,
during navigation, a short sound/banner 500 m before a camera on the route ahead (matching with
`direction` when tagged).

**Legal side: warnings about fixed speed cameras are illegal in some countries.** The app must
decide by the country the rider is in (from the position, offline: the Valhalla tiles carry the
country of each road, readable with `trace_attributes` `admin.country_code`, to be verified; or a
small table of country outlines), not by the phone language.

| Country | Fixed-camera warnings in a navigation app | Default |
|---|---|---|
| Italy | allowed | on (user can turn off) |
| France | forbidden since 2012 (only "danger zones" allowed) | off, not offered |
| Germany | forbidden while driving (StVO §23 1c) | off, not offered |
| Switzerland | forbidden (also fixed cameras) | off, not offered |
| Austria | grey area | off, not offered |
| Any other country | not checked | off until checked |

This table is the developer's best understanding, not legal advice: the owner should confirm it
before shipping (and before adding countries). Since the road data today covers only Italy, the
first version can be **Italy only**: cameras shown in Italy, hidden everywhere else.

## Decisions for the owner

Taken: publish `road-notes.json` from the workflow; warn + offer a route without the closed pass
with the "according to OpenStreetMap, check before leaving" wording; speed cameras Italy only,
setting on by default.

Open:

1. OK to add the `roadNotes` entry (size + SHA-256) to `cells.json` in the workflow? It needs one
   more run of the routing-data build.
2. Who checks the legal table before countries other than Italy are added?
