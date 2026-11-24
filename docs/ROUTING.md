# Routing: curves, safety and vehicle bans

Decision document for #4 ("does the motorcycle profile really pick the curves?") and status of
#8 ("vehicle profile: engine size and bans"). Written from the engine source code
(Valhalla 3.9.2, the version inside valhalla-mobile 0.6.6 and used to build the road data),
not from memory or from the online docs, which in places promise more than the code does.

Files: `src/sif/motorcyclecost.cc`, `src/sif/dynamiccost.cc`, `valhalla/sif/dynamiccost.h`,
`src/mjolnir/util.cc` (`compute_curvature`), `src/mjolnir/countryaccess.cc`, `src/loki/worker.cc`,
`lua/graph.lua`, all at tag `3.9.2`.

## What RidePlan sends

Every route uses `costing: motorcycle`. The `motorcycle` costing options built by
`ValhallaRoute.request` (`shared/.../route/Routing.kt`):

| Option | When | Value | Effect in the engine |
|---|---|---|---|
| `use_trails` | always | 0 | bad surfaces cost the most the engine allows (see below) |
| `use_tracks` | always | 0 | tracks: cost ×4 and 300 s to enter one |
| `exclude_unpaved` | always | true | **ignored by the motorcycle costing in 3.9.2** (only `auto` applies it); sent for newer engines |
| `destination_only_penalty` | always | 1800 s | entering a road open only to reach a destination (default 600 s) |
| `use_curvature` | curves | Some 0.6, More 0.75, Max 1.0 | see "What drives the curves" |
| `use_highways` | avoid motorways / under 150 cc | 0 | motorway cost +8, trunk +4 (additive factors) |
| `use_highways` | Balanced / Curvy mode, motorways allowed | 0.25 / 0.1 | motorway +2 / +5.1, trunk half of that |
| `use_tolls` | avoid tolls | 0 | toll roads cost +2 |
| `use_ferry` | avoid ferries | 0 | ferries very expensive |
| `use_living_streets`, `service_penalty` | avoid towns | 0, 60 s | living streets ×3 + 500 s, service roads +60 s |
| `exclude_highways` | under 150 cc | true | **hard ban** on motorway-class roads (needs `allow_hard_exclusions`, set in `RouteService`) |
| location `search_filter.max_road_class` | under 150 cc | `trunk` | no stop is snapped onto a motorway |

Never sent: `shortest`, `ignore_restrictions`, `ignore_access`, `ignore_oneways`,
`ignore_closures`, `ignore_non_vehicular_restrictions`. The engine defaults respect access tags,
turn restrictions and one-ways. A unit test (`RoutingTest.curvesNeverLeaveTheTarmac`) builds the
request for every combination of switches and checks all of the above.

`motorcycle` costing has no `use_primary` and no `use_hills` (those belong to `motor_scooter`).

## What drives the curves

Valhalla 3.9.2 does have a twistiness weight, but a simple one.

**The data.** At build time every road edge (the stretch between two junctions) gets a
`curvature` from 0 to 15 (`compute_curvature`): for each three consecutive shape points the
radius of the circle through them is scored `1500 / radius` (0 above 1 km, capped at 25), the
scores are averaged over the edge and capped at 15. Consequences:
- an edge drawn with only 2 points is "straight" (0), whatever the real road;
- one hairpin on a long edge is averaged away; a dense sequence of bends scores high;
- the score depends on how finely OpenStreetMap mappers drew the road;
- no elevation, no road width, no "scenic" or "pass" information.

**The cost.** With `use_curvature` above 0.5 the edge cost is multiplied by a factor that
*penalises straight roads* rather than rewarding curvy ones
(`1 + 6 · strength · straightness²`, strength = ((step − 10) / 10)², step = round(u · 20)):

| Level | `use_curvature` | straight rural road | perfectly curvy road | road in a town (density ≥ 7) |
|---|---|---|---|---|
| Normal | not sent | ×1 | ×1 | ×1 |
| Some | 0.6 | ×1.24 | ×1 | ×2.5 |
| More | 0.75 | ×2.5 | ×1 | ×5 |
| Max | 1.0 | ×7 | ×1 | ×14 |

Suburban edges (density 3–6) get the same factor plus up to 10%. Only plain roads count:
ramps, roundabouts, tracks, service roads and living streets are treated as straight, so with
curves on they are penalised like the straightest road.

So "More curves" in RidePlan means: straight and urban roads become expensive, the route
drifts towards rural roads drawn with many bends. It does not know a pass from a winding
suburban lane, and it cannot prefer a famous road over an anonymous one. The owner's test ride
(San Basilio → Padula) got about +14% km at More and +24% at Max.

## Safety: dirt, tracks, unnamed roads, limited traffic zones

What the engine **guarantees** (hard rules):
- roads with `access=no` / `motor_vehicle=no` / `motorcycle=no`, footways, paths, cycleways,
  steps and `smoothness=impassable` are never used;
- turn restrictions and one-ways are respected;
- under 150 cc, motorway-class roads are never used in the middle of a route.

What is only **made expensive** (a cost, not a ban):
- **unpaved roads.** The motorcycle costing allows every surface except impassable. With
  `use_trails=0` the factor added to the edge is: compacted +0.8, dirt +1.6, gravel +4, path +8
  (a normal road is about 1). `exclude_unpaved` would make it a ban but motorcycle costing in
  3.9.2 does not check it;
- **tracks** (`highway=track`): ×4 plus 5 minutes to enter, plus "straight" for the curves;
- **residents-only / destination-only roads and private roads**: 30 minutes to enter
  (RidePlan), so they are used only to reach a stop placed on them;
- **living streets** and **service roads**: default penalties, stronger with "avoid towns".

**The real risk is at "Max".** A curvy unpaved road classified as a normal road (e.g. an
`unclassified` *strada bianca* with `surface=dirt`) costs about 2.6; a straight paved road at
Max costs 7. So at Max the engine can prefer the curvy dirt road when the paved alternative is
straight. At More the two are about even (2.6 against 2.5), at Some the paved road always wins.
Gravel (about 5) only beats a perfectly straight paved road at Max. Tracks never win.

What the engine **cannot see**:
- **unnamed roads**: there is no cost for a road without a name; only tracks and service roads
  are penalised by type;
- **surface not mapped**: an `unclassified` road without `surface` is taken as paved;
- **time-based limited traffic zones** (`motor_vehicle:conditional=no @ (...)`, the usual
  tagging of Italian ZTLs): conditional restrictions are evaluated only when the request has a
  departure time. RidePlan sends none (the ride date is unknown and time-dependent routing uses a
  different, slower algorithm), so these ZTLs are not enforced. ZTLs tagged as
  `destination`/`private` get the 30-minute penalty; the town penalty of More/Max (×5/×14 in
  dense areas) also keeps the route out of historic centres in practice.

## Comparison with Kurviger

Not done in this round: there are no road data tiles and no Valhalla engine on the PC used for
this analysis, and downloading them was out of scope. Protocol for the field check (to be done
on the phone, where the engine and the data are):
1. Rides: Lombard Prealps (Lecco → Valsassina → Passo San Marco), Tuscan-Emilian Apennines
   (Pistoia → Abetone → Pievepelago), Dolomites (Bolzano → Passo Sella → Passo Gardena →
   Corvara), plus one Tuscan *strade bianche* area (Siena → Montalcino) to test the dirt risk.
2. For each ride: RidePlan at Normal / More / Max and Kurviger "curvy" / "extra curvy";
   export the GPX of each.
3. Note km, time, share of the route on roads that Kurviger also uses, and any stretch on
   unpaved, track, unnamed or residents-only roads (check on the OpenStreetMap map).
4. Fail if Max uses any unpaved stretch that Kurviger avoids.

## Recommendation

**Keep Valhalla, with the tuned options above.** Reasons:
- it is the only one of the three engines that runs offline on both Android and iPhone
  (valhalla-mobile ships both); GraphHopper and BRouter are Java, so no iPhone;
- the app already ships it, with turn instructions in five languages, speed limits, and the
  road data pipeline (`routing-data.yml`);
- since 3.9 it has a real curvature cost, which was the reason for this issue.

Follow-ups, in order of value:
1. **Check the result, not only the request.** Valhalla marks every maneuver that touches an
   unpaved or rough stretch (`rough` in the maneuver; to verify on 3.9.2). If a More/Max route
   has one in the middle, compute it again one level lower and tell the rider. This turns
   "dirt is expensive" into "dirt is never shown silently". It belongs with #9 (surface).
2. **Cap "Max" or warn.** If the field check shows dirt at Max, lower Max to 0.85–0.9
   (straight factor about 4–5) or label it "may use white roads".
3. **Update valhalla-mobile** when a version applies `exclude_unpaved` to the motorcycle
   costing: RidePlan already sends it.
4. Consider a departure time for conditional ZTLs once the ride has a date (#12 multi-day).

Alternatives, if the field check fails:

| Engine | Curves | Bans on surface | Offline on phone | Against |
|---|---|---|---|---|
| Valhalla 3.9 (now) | edge curvature, penalises straight roads | cost only for motorcycle | Android + iPhone | twistiness is coarse, no hard unpaved ban |
| GraphHopper (custom model) | `curvature` encoded value, any formula | hard (`surface` → priority 0) | Android only (Java), large graph, more RAM | no iPhone; second data pipeline; Kurviger is built on it |
| BRouter | profile scripts, no native curvature | hard, per profile | Android only (Java), small segment files | no iPhone; no turn instructions of our quality; curvature must be emulated |

Moving engine would cost the iPhone version and the existing pipeline; it is justified only if
Valhalla fails the field check on curves, not for the dirt risk, which can be closed with the
result check above.

## #8: engine size and bans

| Criterion | Status | Where |
|---|---|---|
| Profiles 125 / over 150 / electric | done | `profile/Garage.kt` (`Engine`), bike page in "You" |
| 125: bans applied automatically | done: motorways are banned outright (`exclude_highways`), stops never snapped onto them, trunk roads (where Italian *superstrade* and many ring roads sit) heavily penalised; the "avoid motorways" switch is locked on | `forBike`, `ValhallaRoute.request`, `RouteSettingsUi` |
| `motor_scooter` costing "or equivalent" | equivalent: motorcycle costing plus hard ban | see below |
| "Fastest" besides "more curves" | done: riding mode Fast | `route/RidingModes.kt` |

Why not `motor_scooter`: it forbids motorways and `motorroad=yes` roads at the data level (moped
access), which is the full Italian rule; but in 3.9.2 it ignores `use_curvature` (no curves for
125 riders), caps the speed at 120 km/h with a 45 km/h default, avoids primary roads by default,
and follows `moped=no` signs that do not apply to 125 cc motorcycles.

Known gaps:
- **`motorroad=yes` trunk roads** (Italian *strade extraurbane principali*, banned under 150 cc)
  are only penalised, not banned: motorcycle costing has no way to exclude them. If riders
  report it, switch to `motor_scooter` (with `top_speed` about 110) for bikes under 150 cc and
  accept no curves for them.
- **Low-power electric bikes**: in Italy electric motorcycles under 11 kW have the same
  motorway ban. The garage has one "electric" class, which is treated like over 150 cc. A power
  class (or a "below 11 kW" switch) would close it.
- **Per-country rules** (#15): the 150 cc threshold is the Italian one.
