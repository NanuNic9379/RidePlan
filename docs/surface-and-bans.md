# Dirt roads, surface and bans (#9)

What the issue asks:

1. A map style showing `highway=track/path`, `tracktype`, `surface` and bans (`motor_vehicle=no`), with a legend.
2. Three-state dirt: avoid (default) / allow / prefer (`use_trails`).
3. Never a route on a banned road without a warning.

## What is shipped (first slice)

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

## What is left, and how

### Three-state dirt (avoid / allow / prefer)

Valhalla's motorcycle costing has `use_trails` (0 = avoid, 1 = prefer). Today the request sends
`use_trails: 0.0` always ("Unpaved roads are always avoided: it is not a toggle", #4).
Proposal, to be done together with #4/#8 since they own the costing:

| State | `use_trails` | Notes |
|---|---|---|
| Avoid (default) | 0.0 | as today |
| Allow | 0.5 | Valhalla default |
| Prefer | 1.0 | for adventure bikes |

A new `RouteOptions.dirt` field (enum, default AVOID, so saved preferences still decode), one row
in the route settings (a future "Adventure" riding mode, next to Fast/Balanced/Curvy, could set it to PREFER). Even with AVOID,
Valhalla takes a dirt road when there is no other way (e.g. a farm restaurant at the end of a
gravel lane): this is exactly what the new ride-sheet note now tells the rider.

Note: Valhalla's motorcycle costing refuses edges worse than `dirt` (`path`, `impassable`) in any
case, and the data built by `routing-data.yml` contains no `highway=path` at all (not in the
`osmium tags-filter` list). `highway=track` is in the data.

### Bans (`motor_vehicle=no`, `motorcycle=no`, `access=no/private`)

The engine already never routes on these: Valhalla reads the access tags at build time and the
motorcycle costing refuses edges without motorcycle access. The only exception is a stop placed
on a banned road: Valhalla then snaps the stop to the nearest allowed road, or answers "no route".
So the third criterion ("never a route on a banned road without a warning") is met by the engine
for computed routes.

What is not covered: **imported GPX/KML tracks** (they are drawn as they are, not routed). A check
could walk them with `trace_attributes` (`shape_match: map_snap`) and report stretches where the
match fails or `edge.access` lacks motorcycle access. Recommended as a follow-up when imported
tracks get navigation guidance.

### Map style for track/surface/bans (whole map, not only the ride)

The base map is OpenFreeMap "liberty" (`MapDefaults.STYLE`, OpenMapTiles schema, online with the
MapLibre cache). Its `transportation` layer already carries what is needed: `class=track/path`,
`surface=paved|unpaved`, `access=no` (only "no"), and at zoom 14 `subclass`. Missing: the detailed
`tracktype` and the motorcycle-specific bans (`motorcycle=no`), which OpenMapTiles drops.

Proposal: an "Off-road" map overlay switch on the explore map that adds two maplibre-compose
`LineLayer`s on the base style's `openmaptiles` source (source layer `transportation`): unpaved
roads dashed brown, `access=no` roads red-dotted, plus a small legend card. No new data, no
server. Detailed `tracktype`/`motorcycle=no` would need our own vector tiles (a server or a big
offline download): not recommended for now.

## Decisions for the owner

1. Three-state dirt: OK to add "Allow" and "Prefer" (and later an "Adventure" riding mode that prefers dirt)?
2. Map overlay for unpaved/banned roads on the whole map, based on the OpenFreeMap tiles (no
   tracktype detail): OK?
