# Offline map of a region (#1)

Status as of November 2026, written while checking what #1 still needs.

## What already works

| #1 criterion | State | Evidence |
|---|---|---|
| MapLibre on Android and iOS, OSM vector tiles | Done | `maplibre-compose` 0.20.0 (`gradle/libs.versions.toml`), style `MapDefaults.STYLE` = OpenFreeMap Liberty (OpenMapTiles schema, OSM data) in `App.kt` and `NavigationScreen.kt` |
| © OpenStreetMap attribution visible | Done | MapLibre's attribution button stays on the map (`viewportInsets` in `App.kt`); licences page lists OpenStreetMap, OpenFreeMap, MapLibre, Valhalla (`info_licences_text`) |
| Download a region and use it without network | **Not done for the map** | The map style and its tiles are always fetched online from `tiles.openfreemap.org`. Nothing in the code uses MapLibre's offline storage (`OfflinePack`, `OfflineManager`) or a local PMTiles/MBTiles file. Only MapLibre's ambient cache keeps the tiles you already looked at, with no guarantee. |

What *is* offline today is the **road data for routing and navigation** (#3, #45): Valhalla
tiles in 1-degree cells, downloaded around the ride (`RoutingCells.kt`, `RouteService.kt`,
`.github/workflows/routing-data.yml`). So in the mountains without signal the route and the
turn instructions work, but the map underneath can be blank where it was never viewed.

**#1 is not done.** It must stay open.

## Constraints found

* **OpenFreeMap terms of service** (openfreemap.org/tos, checked 2026-11-24): an app may let
  users download areas for offline use if each download is **at most 3,000 tiles and 50 MB**,
  with **at most 4 parallel requests** and a **User-Agent identifying the app**. Larger areas
  or long routes must be split into **several downloads, each started by the user**.
* A whole region does not fit that: Italy at zoom 0-13 is about 129,000 tiles, at zoom 0-14
  about 515,000 tiles (computed with `MapTiles.count`, see `MapTilesTest`). A region map
  therefore needs a file we publish ourselves.
* `maplibre-compose` 0.20.0 already ships an offline API (`org.maplibre.compose.offline`:
  `OfflinePackDefinition.TilePyramid` for a box, `OfflinePackDefinition.Shape` for any
  geometry, `DownloadProgress`, `setMaximumAmbientCacheSize`). No new dependency is needed.
* Recent MapLibre Native versions (from late 2024) read `pmtiles://` sources, so a single
  PMTiles file per area can back the same Liberty style once downloaded. To be verified on
  the version bundled by maplibre-compose 0.20.0.

## Plan

### Step 1 (no infrastructure, no cost): the map along the ride

After a route is calculated, offer "Map for this ride, X MB" next to the road data:

* Geometry: the calculated track with **5 km on each side**, zoom 5 to 14. A 300 km ride is
  about 1,400-2,100 tiles (`MapTilesTest.rideCorridorFitsOneOpenFreeMapDownload`), so **one
  download, one tap**, inside the OpenFreeMap limit.
* Size: OpenMapTiles vector tiles in rural areas average 10-25 KB, so 15-50 MB per ride.
  If the estimate exceeds 3,000 tiles or 50 MB (very long rides, dense cities), split the
  track into consecutive parts and show one button per part, as the terms require.
* Implementation: `OfflinePackDefinition.Shape` (corridor polygon) on the existing style,
  max 4 parallel requests, `User-Agent: RidePlan/<version>`; same Wi-Fi / mobile-data rules as
  the road data (#44); listed and deletable in *Downloaded map data* (#21).
* Done when: airplane mode, open the saved ride, the map is drawn along the whole route at
  every zoom used during navigation (overzoom above 14).

### Step 2 (needs a hosting decision): whole regions

* Source: OpenFreeMap's weekly planet PMTiles (same OpenMapTiles schema as the Liberty style).
* A workflow cuts each area with `pmtiles extract <planet url> <area>.pmtiles --bbox=... --maxzoom=14`;
  this uses HTTP range requests, so the runner downloads only the area, not the planet.
* Italy at zoom 14 is expected around 1.5-2.5 GB, too close to the 2 GiB Release limit:
  publish it in **4-degree blocks** (the same blocks as the main-road packs, about 12 for
  Italy, typically 50-300 MB each) so the rider downloads only the blocks wanted.
* The app shows each block with its size, downloads it, checks the SHA-256 and adds it as a
  `pmtiles://` source under the online style.
* Details, costs and the worldwide extension: see `WORLDWIDE-COVERAGE.md`.

## What was added now

* `route/MapTiles.kt`: tile numbering, tile count for a box and for a ride corridor, and the
  OpenFreeMap limits as constants. Tested in `MapTilesTest`. It is the size estimate Step 1
  will show before downloading.

## Owner decisions

1. **Step 1 with OpenFreeMap** under their offline terms: recommended (free, compliant, no
   server). OpenFreeMap lives on donations: consider sponsoring it if usage grows.
2. **Step 2 hosting**: GitHub Releases (free) or a mirror (see `WORLDWIDE-COVERAGE.md`).
