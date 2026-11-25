# Dirt roads, surface and bans (#9)

What the issue asks:

1. A map style showing `highway=track/path`, `tracktype`, `surface` and bans (`motor_vehicle=no`), with a legend.
2. Three-state dirt: avoid (default) / allow / prefer (`use_trails`).
3. Never a route on a banned road without a warning.

## What is shipped

### First slice: unpaved stretches of a computed ride

**Unpaved stretches of a computed ride**, offline, from the road data already on the phone.

- After a route is computed, the phone already walks it once with Valhalla's `trace_attributes`
  to read the speed limits (#31). The same request now also asks for `edge.surface` and
  `edge.unpaved`, so there is still a single walk (`ValhallaRoadAttributes`, `route/RoadSurface.kt`).
- Valhalla turns the OSM `surface`, `tracktype` and `smoothness` tags into one class per edge:
  `paved_smooth`, `paved`, `paved_rough`, `compacted`, `dirt`, `gravel`, `path`, `impassable`.
  The app keeps two kinds for the rider:
  - **gravel**: `compacted`, `gravel` (or an unknown class that Valhalla flags `unpaved`);
  - **dirt**: `dirt`, `path`, `impassable`.
  Paved and unknown surfaces are never guessed as unpaved.
- **Ride sheet**: under the route notes, e.g. "Unpaved road on this ride: 12 km, of which 3.1 km
  dirt or worse; longest stretch 4.2 km". Nothing under 300 m in total (a car park, a farm
  drive). With at least 300 m of dirt the note uses the error colour.
- **Map**: the unpaved stretches are drawn over the orange ride line, brown for gravel and dark
  brown for dirt (`RoughLayers`).
- Like the speed limits, the stretches are not saved: they come back when the ride is routed
  again. A GPX track that was not routed on the phone has none.
- No change to the routing request in `Routing.kt` (the routing agent of #4/#8 owns the costing);
  only an additive field `surfaces` in `ValhallaRoute.Result` and in `Ride`.

Tests: `shared/src/commonTest/.../route/RoadSurfaceTest.kt` (classification, metres along the
route, totals and longest stretch, cutting the map lines, one walk for limits and surfaces,
routed/unrouted).

### Three-state unpaved choice (avoid / allow / prefer)

`RouteOptions.unpaved` (`route/Unpaved.kt`), default `AVOID`, so settings saved before (and
links from older apps) read as Avoid. It is a choice of the rider, not of a riding mode: picking
Fast/Balanced/Curvy keeps it (`optionsKeeping`), and it never makes a mode "Custom".

Mapped on Valhalla 3.9.2's motorcycle costing (checked in `src/sif/motorcyclecost.cc` and
`dynamiccost.cc`):

| State | `use_trails` | `use_tracks` | `exclude_unpaved` | Effect |
|---|---|---|---|---|
| Avoid (default) | 0.0 | 0.0 | true | as before (#4/#49): compacted +0.8, dirt +1.6, gravel +4 per edge; tracks ×4 plus 300 s to enter |
| Allow | 0.5 | 0.5 | not sent | neutral: a gravel road is taken when it is simply the better road |
| Prefer ("Adventure") | 1.0 | 1.0 | not sent | small bonus: surface factor −0.125, tracks ×0.8 |

`destination_only_penalty` and every access rule stay the same in all three states: closed or
private roads are never opened by this choice. `exclude_unpaved` is still ignored by the
motorcycle costing of 3.9.2; it is sent only with Avoid, for a newer engine.

- **Route settings**: a segmented row "Unpaved roads: Avoid / Allow / Prefer" with a one-line
  explanation, under the Avoid switches (`route/UnpavedUi.kt`).
- **Links** (#50): `u` = Allow, `d` = Prefer in the options field; older apps ignore the letters.

### Curves fallback at Max

With Avoid at the top curves level (Max), the curvature cost can still beat the unpaved cost
(docs/ROUTING.md, "The real risk is at Max"). After computing the route the phone already knows
its unpaved stretches (first slice). `CurvesFallback` (`route/Unpaved.kt`):

1. counts the unpaved metres away from the stops (the first and last 500 m are ignored: if a stop
   is on a gravel lane no other route avoids it);
2. from 300 m (the same threshold as the ride-sheet note) it computes the route once more at
   More;
3. keeps the lower route only if it has at least 100 m less unpaved road, otherwise keeps the
   Max route (and the ride-sheet note tells about the gravel as before);
4. the ride sheet says "The most curvy route kept using unpaved roads, so this one uses one curves
   level lower" (`RouteInfo.loweredFrom`). The rider's setting stays Max; only this route used
   More. Navigation re-routes with the level actually used.

A failing second computation keeps the first route. Allow and Prefer are never lowered.

### Off-road overlay on the Explore map

A toggle (56 dp, landscape icon) at the top right of the Explore map, off by default, with a small
legend while it is on (`route/OffroadMapUi.kt`, rules in `route/OffroadMap.kt`).

- Data: the base map's own OpenFreeMap tiles (source `openmaptiles`, layer `transportation`,
  checked in the TileJSON at https://tiles.openfreemap.org/planet: fields `class`, `surface`
  = paved/unpaved, `access` = no). No new data, no server.
- Unpaved road: road classes a motorcycle could use (motorway … service, track, raceway, busway)
  with `surface=unpaved`, plus tracks unless `surface=paved`. Dashed, theme tertiary colour.
- Closed to traffic: the same classes with `access=no`. Dotted, theme error colour. This wins
  over unpaved.
- Thin (2 dp), 70–75% opacity, only from zoom 11 (the tiles carry no surface detail below).
- Not in the tiles: `tracktype`, `motorcycle=no`, `motor_vehicle=no`. The legend says the data may
  be incomplete.

### Ban check of imported tracks

A GPX/KML track drawn as it is never went through the engine's access rules. When such a ride is
open (`Ride.unroutedTrack`), the ride sheet checks it on the road data already on the phone
(`route/TrackBans.kt`, `ride/TrackBansUi.kt`):

1. **open match**: `trace_attributes`, `shape_match: map_snap`, motorcycle costing with
   `ignore_access`, `ignore_restrictions`, `ignore_oneways`, `ignore_closures` (available on every
   costing in 3.9.2), asking `edge.way_id`, `edge.road_class`, `edge.names`: the roads the track
   really uses;
2. **legal match**: the same without the ignore options, asking `edge.way_id`: roads closed to
   motorcycles cannot be matched, so their ways are missing.

A way of the open match missing from the legal match, where the legal match covered that part of
the track, is reported as "closed to motorcycles". A `road_class=motorway` edge is reported as
"motorway, not allowed under 150 cc" when the default bike is under 150 cc (#8). Stretches are
joined across gaps under 30 m and dropped under 50 m (matching noise at junctions). Long tracks
are walked in 100 km pieces like the speed limits (`ValhallaSpeedLimits.walk`).

The sheet shows, in the error colour, the length per kind and up to three road names. When less
than 90% of the track could be matched it adds "Only part of the track could be checked". With no
road data on the phone for the track it says "Road rules not checked": nothing is downloaded just
for this check.

Why two matches: `trace_attributes` in 3.9.2 has no per-edge access attribute for the motorcycle,
so the difference between an "ignore access" match and a normal one is the most reliable signal
the engine gives.

Tests: `route/UnpavedTest.kt` (costing JSON per state, saving and old settings, modes, links,
fallback rules), `route/TrackBansTest.kt` (requests, parsing, open/legal comparison, joining,
partial and unchecked results, which rides are checked), `route/OffroadRulesTest.kt`.

## Limits

- The track check depends on the road data on the phone: a track outside the downloaded areas is
  not checked (the sheet says so). The data has only roads (`routing-data.yml`): a track on a
  footpath is simply not matched, and shows up as "partly checked".
- Map matching can pick a parallel road (a motorway next to a service road); the 50 m minimum and
  the "legal match must cover that part" rule keep false alarms low but not at zero. Not yet tried
  on real tracks on the phone.
- Seasonal or conditional closures (`access:conditional`) are not checked: the open match ignores
  closures, the legal one has no date.
- The overlay shows only what OpenMapTiles keeps: no `motorcycle=no`, no `tracktype`.

## Decisions for the owner

1. Prefer is a preference only: there is no "Adventure" riding mode tile yet (`ComingMode.DIRT`
   stays "coming"). Turn it into a mode that sets Curvy + Prefer?
2. The fallback lowers Max to More only once. Should it also try Some, or warn instead of
   changing the route?
3. Track check: should "Recompute on roads" be offered right from the warning (turning the track
   into stops), or is the warning enough?
4. Overlay: keep it off by default and not remembered between launches (as now), or remember the
   switch?
