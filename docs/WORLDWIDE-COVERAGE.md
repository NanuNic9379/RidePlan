# Worldwide coverage of offline data (#14)

Status as of November 2026. Numbers marked *measured* come from the published Release and
the workflow logs; numbers marked *estimate* are scaled from them and must be checked on the
first real build.

## What is published today

**Only Italy, only road data (routing).** The map is still online (see `OFFLINE-MAP.md`, #1).

| Item | Value |
|---|---|
| Workflow | `.github/workflows/routing-data.yml`, manual (`workflow_dispatch`) or on changes to `data/routing-data.json` / `data/routing_data.py` |
| Source | `italy-latest.osm.pbf` from the openstreetmap.fr mirror (Geofabrik size: 2.1 GB) |
| Engine | Valhalla 3.9.2 in Docker, same version as valhalla-mobile 0.6.6 in the app |
| Build time | 21 min 34 s on the free `ubuntu-latest` runner, with 12 GB of swap added (*measured*, run 45261258357) |
| Output | Release `routing-data`: `cells.json` + 176 zip packs = **686 MB** (*measured*) |
| Packs | 158 cells of 1 degree (level 1) + 18 blocks of 4 degrees with main roads (level 0) |
| Cell sizes | 58 cells over 1 MB, median 9.2 MB, largest 43.8 MB; 100 tiny cells are sea with ferry lines |
| On the phone | 1.71 GB if every pack is extracted (about 2.5 times the download) |
| Downloads so far | 381 asset downloads in total (*measured*, 2026-11-24) |
| OSM data date | 2026-11-23 |

Ratios to scale from: routing packs are **about 0.33 times the PBF**, extracted tiles about
**0.8 times the PBF**.

## Limits that shape the plan

* **GitHub Releases**: each file under 2 GiB; at most 1,000 files per Release. During an
  update the new packs sit next to the old ones until the manifest is replaced, so an area
  may have at most about 499 packs. No published total-size or bandwidth cap for public
  repositories, and no bill, but GitHub can object if Releases are used as a heavy CDN.
  `routing_data.py` now fails the build *before* publishing when either limit would be hit
  (`release_problems`).
* **Free runner**: 4 vCPU, 16 GB RAM, about 14 GB free on `/` (more after the cleanup step,
  plus `/mnt`), **6 hours per job**. Italy (2.1 GB) already needs swap.
* **One build per manifest**: cells of different builds must never be mixed (road and
  junction numbering at cell edges changes with every build, see `RoutingCells`). So any
  split into several builds means several independent areas, not one seamless grid.

## How big Europe and the world are

Geofabrik PBF sizes on 2026-11-24 and the scaled outputs (*estimate*):

| Area | PBF | Routing packs | Routing on phone (all) | 1-degree cells | Releases needed (≤ 499 packs) |
|---|---|---|---|---|---|
| Italy (today) | 2.1 GB | 0.69 GB (*measured*) | 1.7 GB | 158 | 1 |
| Alps | 2.2 GB | 0.7 GB | 1.8 GB | ~130 | 1 |
| Germany | 4.6 GB | 1.5 GB | 3.7 GB | ~110 | 1 |
| France | 4.8 GB | 1.6 GB | 3.8 GB | ~150 | 1 |
| Europe | 33.1 GB | ~11 GB | ~27 GB | ~2,000 | ≥ 5 |
| Planet | 96 GB | ~32 GB | ~77 GB | ~15,000 | ≥ 35 |

Map PMTiles (zoom 0-14, OpenMapTiles schema) are of the same order as the PBF: Italy about
1.5-2.5 GB, Europe about 30 GB, planet 80-100 GB (*estimate*).

Nobody downloads a whole area: a rider downloads the cells around a ride, typically
20-80 MB of road data plus 15-50 MB of map. The table matters for building and hosting,
not for the phone.

## Plan

### Phase 1: several areas instead of one (Europe, step by step)

1. **Areas file** `data/routing-areas.json`: a list of areas, each with id, Geofabrik (or
   openstreetmap.fr) source URL(s), bounding box and Release tag, for example
   `it` (today), `alps`, `fr`, `es-pt`, `de-at-ch`, `benelux`, `uk-ie`, `balkans`,
   `pyrenees`, `scandinavia`, `east`. Areas that cross borders are built on purpose
   (Alps, Pyrenees, Balkans), as #14 asks; they overlap with the country areas.
2. **Workflow matrix**: one job per area (same steps as today), each publishing to its own
   Release `routing-<area>` with its own `cells.json`. An area that would break a limit
   fails before publishing. Monthly schedule (`cron`) once the owner agrees; today it stays
   manual. Areas fitting the free runner: anything up to about 5-6 GB of PBF (Germany,
   France, Alps). Splitting bigger ones uses `osmium extract --bbox`.
3. **Index** `areas.json` in a small Release `routing-index`: per area its bounding box,
   build, total size and manifest URL.
4. **App**:
   * reads the index, picks the **smallest area whose cells cover every stop**
     (`RoutingCells.outside` is the check, already used to name uncovered stops);
   * keeps **one tile directory per area** (`routing/cells/<area>/`), so a ride in the Alps
     does not wipe the cells of Italy;
   * if no single area covers the ride, says which stops are outside (done now for the
     single area) and, later, offers the area covering most of them.
   * a **configurable mirror** URL (Settings, advanced) replaces the GitHub base URL.
5. **Europe**: about 12-15 areas, each 0.2-2 GB of packs, all buildable for free, full
   rebuild about 5-8 runner hours per month (free for public repositories).

### Phase 2: the map for the same areas

* Step 1 of `OFFLINE-MAP.md` (map along the ride from OpenFreeMap, within their offline
  limits) works worldwide from day one, with no build at all.
* Region maps: per area, `pmtiles extract` from OpenFreeMap's weekly planet PMTiles with
  range requests (no planet download on the runner), split into 4-degree blocks to stay
  under 2 GiB, published next to the routing packs. The alternative is Protomaps' daily
  planet builds (same technique, different schema, would need a Protomaps style instead of
  Liberty): not recommended, it would change the look of the map.
* Cutting PMTiles directly on the phone: possible in principle (PMTiles is range-request
  friendly), but needs a native PMTiles writer in the app; the MapLibre offline pack over
  OpenFreeMap (Step 1) already gives the "cut on the phone" result without new code.

### Phase 3: the world

* Continents in areas of at most about 5 GB of PBF: about 40-60 areas in total; the biggest
  countries (USA, Russia, Brazil, India, China, Canada) split by state or by grid with
  `osmium extract`.
* Full monthly rebuild: about 40-80 runner hours. Free on a public repository, but slow in
  the queue (20 parallel jobs at most). Alternatives in the decisions below.
* Hosting at this size (~32 GB routing + ~90 GB maps, ~70 Releases) is where GitHub becomes
  a risk; a mirror is advisable.

## Costs

| Option | Build | Storage | Bandwidth | Notes |
|---|---|---|---|---|
| GitHub Actions free + Releases (today) | 0 € | 0 € | 0 € | Limits above; GitHub may object at world scale |
| GitHub larger runner (16 cores, 64 GB) for one seamless Europe build | ~10 € per build (~2-3 h at about 0.06 $/min, check current price) | 0 € in Releases | 0 € | Needs a paid plan with a spending limit; avoids area splitting in Europe |
| Cloudflare R2 mirror | — | ~0.015 $/GB-month: Europe (~40 GB) ~0.6 $/month, world (~130 GB) ~2 $/month | Egress free; ~0.36 $ per million downloads | Account and card needed; configurable mirror in the app |
| Owner's PC builds | electricity | — | — | Europe needs ~64 GB RAM and ~200 GB disk; not automated |

Bandwidth reference: 10,000 active riders × ~300 MB a year ≈ 3 TB a year, free on GitHub and R2.

## What was added now

* `RoutingCells.outside(points)` and `outsideStops(...)`: the ride sheet names the stops
  that fall outside the published data, instead of "no data for this area" (`Coverage.kt`,
  tests in `CoverageTest`).
* The download card also shows the space the data takes on the phone (about 2.5 times the
  download).
* `routing_data.py`: `release_problems` stops a build that would exceed 2 GiB per file or
  1,000 files per Release before anything is uploaded. Note: changing this file triggers
  the routing-data workflow on the next push to `main` (a normal Italy rebuild, ~22 min).
* `MapTiles.kt`: tile counts for areas and ride corridors, used for the numbers above.

## Areas: first slice (Alps)

Owner decisions taken: order Alps, France, Spain-Portugal, Germany-Austria-Switzerland,
Balkans; GitHub Releases while Europe-only; one free runner per area.

* **Areas file** `data/routing-areas.json`: Italy (`it`, Release `routing-data`, still built by
  `routing-data.yml`, untouched) and the Alps (`alps`, Geofabrik `europe/alps-latest.osm.pbf`,
  2.34 GB on 2026-11-24, Release `routing-alps`). Each area has the `.poly` outline of its
  extract.
* **Workflow** `routing-areas.yml`: matrix with one free runner per area given by hand
  (`gh workflow run routing-areas.yml -f areas=alps`), same steps as Italy (cells, fuel lists,
  road notes, Release-limit check, MD5 check of the Geofabrik download), then the index job.
  A push to `main` that changes the areas file only refreshes the index; it never builds an area.
  Italy can not be built from here (`routing_areas.py matrix` refuses it).
* **Index** `areas.json` in Release `routing-index`: per published area its id, name, Release,
  bounding box and outline (encoded polylines, 3 decimals; Alps 0.5 kB, Italy 2.5 kB).
* **App** (`RoutingAreas.kt`, `RouteService.kt`):
  * reads the index (keeps a copy for offline use); without it, only Italy as before;
  * prefers the area whose outline holds every stop: a 1-degree cell at the edge of an area
    exists even where only part of its roads was in the extract (Lugano in Italy's data);
  * among those, the smallest download (data already on the phone costs nothing), then the
    smaller area; if none holds every stop, any area whose cells cover them (as before);
  * one folder per area: Italy stays in `routing/cells` (no re-download after the update),
    other areas in `routing/areas/<id>`; a new build wipes only its own area;
  * "Data on the phone" lists one item per area (named only when there are several);
  * old app versions keep reading `routing-data/cells.json`, which is not touched.
* **Expected Alps build** (*estimate* from Italy): about 25-35 min on the free runner,
  about 0.75 GB of packs, about 130-150 cells, about 1.9 GB if every pack is extracted.

Not done yet: a mirror URL, a ride that needs two areas (stops are named, no route), the
monthly `cron`, the next areas (add an entry to `routing-areas.json` and run the workflow).

## Owner decisions

1. **Order of areas** after Italy: recommended Alps (cross-border, most requested by riders),
   then France, Spain-Portugal, Germany-Austria-Switzerland, Balkans.
2. **Hosting**: stay on GitHub Releases for Europe (free) and add an R2 mirror before the
   world phase (~2 $/month). Decide whether to open a Cloudflare account.
3. **Build machine for Europe**: areas on free runners (recommended, 0 €) or one seamless
   Europe build on a paid larger runner (~10 € a month).
4. **Monthly schedule**: switch on the `cron` once the area matrix exists (free).
5. **Map regions** (Phase 2): agree to publish PMTiles blocks cut from OpenFreeMap; consider
   sponsoring OpenFreeMap, whose servers the cut and the per-ride map rely on.
