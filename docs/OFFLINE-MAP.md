# Offline map of a region (#1)

Status as of November 2026. Step 1 (the map along a ride) is delivered; Step 2 (whole
regions) is still open, so #1 stays open.

## What already works

| #1 criterion | State | Evidence |
|---|---|---|
| MapLibre on Android and iOS, OSM vector tiles | Done | `maplibre-compose` 0.20.0 (`gradle/libs.versions.toml`), style `MapDefaults.STYLE` = OpenFreeMap Liberty (OpenMapTiles schema, OSM data) in `App.kt` and `NavigationScreen.kt` |
| © OpenStreetMap attribution visible | Done | MapLibre's attribution button stays on the map (`viewportInsets` in `App.kt`); the ride map section shows "Map © OpenStreetMap contributors, OpenFreeMap"; licences page lists OpenStreetMap, OpenFreeMap, MapLibre, Valhalla (`info_licences_text`) |
| Map along a ride, usable without network | **Done (Android, Step 1)** | "Download map for this ride" in the ride sheet (`RideMapUi.kt`, `RideMapDownloads.kt`), MapLibre offline packs. Checked on the emulator in airplane mode: zoom 14 detail drawn along the ride. |
| Download a whole region | **Not done (Step 2)** | Needs a file we publish ourselves, see below. |
| iOS | Not done | The shared UI is ready (`PlatformActions.rideMap`); iOS has no `RideMapActions` yet, so the section is hidden there. |

The **road data for routing and navigation** (#3, #45) is offline as before: Valhalla tiles
in 1-degree cells (`RoutingCells.kt`, `RouteService.kt`, `.github/workflows/routing-data.yml`).

**#1 is not done.** It must stay open for Step 2.

## Constraints found

* **OpenFreeMap terms of service** (openfreemap.org/tos, checked 2026-11-24): an app may let
  users download areas for offline use if each download is **at most 3,000 tiles and 50 MB**,
  with **at most 4 parallel requests** and a **User-Agent identifying the app**. Larger areas
  or long routes must be split into **several downloads, each started by the user**.
* A whole region does not fit that: Italy at zoom 0-13 is about 129,000 tiles, at zoom 0-14
  about 515,000 tiles (computed with `MapTiles.count`, see `MapTilesTest`). A region map
  therefore needs a file we publish ourselves.
* `maplibre-compose` 0.20.0 ships the offline API (`org.maplibre.compose.offline`). No new
  dependency was needed.
* Recent MapLibre Native versions read `pmtiles://` sources, so a single PMTiles file per
  area can back the same Liberty style once downloaded. To be verified on the version
  bundled by maplibre-compose 0.20.0.

## Step 1: the map along the ride (delivered)

### What the rider sees

In the sheet of a saved ride (road calculated, not straight lines) a section **Map without
signal** shows, before anything is downloaded:

* "The map along the road, 5 km on each side: N tiles, about X MB", and a 56 dp button
  **Download map for this ride**.
* If full detail is over the limit: the full size, and the same corridor with less detail
  (some small streets missing when zoomed in close), button **Download with less detail**.
* If even the lowest detail is over the limit: the ride is too long for one download,
  split it into stages.
* On mobile data with "Wi-Fi only" on (#44): the same question as for road data, with the
  size; "wait for Wi-Fi" cancels.
* While downloading: progress bar, tiles done of total, MB, **Cancel** (cancel removes the
  partial download).
* Once saved: "Map saved, it works without signal. X MB on the phone" and **Delete map**.
  An interrupted download offers **Resume**; an outdated one (see below) **Update map**.
* The saved maps are also listed, with size, in *Downloaded map data* (#21) and removed by
  "delete all data on this device".

### How it works

* `route/RideMap.kt` (pure, tested in `RideMapTest`): the corridor is the set of zoom 14
  tiles within 5 km of the track, plus all their parents down to zoom 5. It estimates bytes
  per zoom and picks the highest max zoom (14, else 13, 12, 11) that stays within 3,000
  tiles and 50 MB. The corridor is turned into rectangles (`areas`) that become one
  MultiPolygon for `OfflinePackDefinition.Shape`.
* `androidApp/.../RideMapDownloads.kt`: creates/resumes/deletes the MapLibre offline pack,
  stores a small JSON (`RideMapInfo`) as pack metadata, watches progress and stops (and
  deletes) a pack that would pass the limit while downloading.
* `RideMapNetwork` replaces MapLibre's network for `tiles.openfreemap.org` during a ride map
  download: **at most 4 requests at a time**, `User-Agent: RidePlan/<version> (+repo url)`,
  ETag / Range support. Normal map browsing is untouched.
* The map database moved from the cache folder (Android may empty it) to the app files
  folder (`maplibre.db`); the old cache file is removed once. Offline packs live in that
  same database, so the normal map uses them without network.

### Measured sizes (emulator, 25 km ride east of Turin)

| Part | Size |
|---|---|
| Tiles z5-14 (222, estimate 223) | 11.5 MB |
| Fonts (Latin, Greek, Cyrillic, etc.) | 4.8 MB |
| Style, sprites | 0.2 MB |
| **Total** | **about 17 MB** |

Average tile size by zoom: z5-8 about 250 KB (they cover large areas, relief included),
z9-10 about 150 KB, z11-12 about 60 KB, z13-14 about 35-40 KB. The old guess "300 km = 15-50
MB at zoom 14" was too optimistic. With the measured numbers:

* up to about **150 km**: full detail (zoom 14), one download;
* about **200-300 km**: zoom 13 (country lanes still drawn, a few tiny streets missing);
* a multi-day tour of about **1,200 km**: zoom 11;
* longer tours: too long for one download, split into stages.

### Decisions taken while building

* **Fonts:** the style asks for every Unicode block of each font; downloading them all is
  over 44 MB. During a ride map download, font ranges from U+3000 up (CJK) are answered
  "no content, already expired", so they are fetched again the next time the phone is
  online. Latin, Greek, Cyrillic, Arabic etc. are kept.
* **Weekly data:** OpenFreeMap publishes new tiles every week under a new address
  (`.../planet/20261122_124456_pt/...`). The app remembers the newest version it has seen;
  a saved map from an older version is marked outdated with **Update map**, because the
  style then points to tiles that are not stored. Until the rider updates, an older map can
  still show where the old style is cached, but this is not guaranteed.
* **Size check during the download:** the estimate is calibrated on one ride; dense cities
  can be heavier. The download is stopped and deleted if it passes 3,000 tiles or 50 MB.

### Limits of this slice

* No automatic split into several parts: rides over about 1,200 km must be saved as stages.
* Android only (iOS needs its own `RideMapActions`).
* The estimate is calibrated on one rural ride; dense cities can come out larger (the
  runtime check protects the limit).

## Step 2 (needs a hosting decision): whole regions

* Source: OpenFreeMap's weekly planet PMTiles (same OpenMapTiles schema as the Liberty style).
* A workflow cuts each area with `pmtiles extract <planet url> <area>.pmtiles --bbox=... --maxzoom=14`;
  this uses HTTP range requests, so the runner downloads only the area, not the planet.
* Italy at zoom 14 is expected around 1.5-2.5 GB, too close to the 2 GiB Release limit:
  publish it in **4-degree blocks** (the same blocks as the main-road packs, about 12 for
  Italy, typically 50-300 MB each) so the rider downloads only the blocks wanted.
* The app shows each block with its size, downloads it, checks the SHA-256 and adds it as a
  `pmtiles://` source under the online style.
* Details, costs and the worldwide extension: see `WORLDWIDE-COVERAGE.md`.

## Owner decisions

1. **OpenFreeMap** is used under its offline terms (free, compliant, no server). It lives on
   donations: consider sponsoring it if usage grows.
2. **Very long tours:** add a split into several parts (one button per part, each started by
   the rider, as the terms require), or keep "split the ride into stages".
3. **Step 2 hosting**: GitHub Releases (free) or a mirror (see `WORLDWIDE-COVERAGE.md`).
