# Seasonal pass closures and speed cameras (#13)

What the issue asks:

1. A warning if the ride goes over roads with `access:conditional` / `seasonal` (Gavia, Stelvio,
   Nivolet...), with an alternative.
2. Fixed speed cameras from OSM (`highway=speed_camera`) on the map and along the ride.

Both must work offline, like routing.

## What is shipped (first slice)

Only the **data side**, committed but not run:

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

## What is left, and how

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

1. OK to publish `road-notes.json` from the routing-data workflow (and to run the build when this
   is merged)?
2. Seasonal closures: OK with "warn + offer a route without the closed pass" as the behaviour, with
   the "according to OpenStreetMap, check before you go" wording?
3. Speed cameras: OK with "Italy only, off elsewhere" for the first version? Who checks the legal
   table before other countries are added?
