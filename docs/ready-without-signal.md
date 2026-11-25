# Ready without signal (#67, #68)

## What ships

- **Status in the ride sheet** (`route/ReadyOfflineUi.kt`): one line, either "Ready without
  signal" or "Not ready without signal - Missing: road data, map". Shown above the "Map without
  signal" section, which keeps the details (size, detail level, delete).
- **One button**, "Get everything for this ride": asks online which road data cells the ride
  needs, downloads them, then starts the ride map download. With "Download only on Wi-Fi" on
  and a metered network it asks **once**, with the total size, and the map does not ask again.
- **Navigate check** (#68): if the ride is not ready, a dialog says what is missing with
  "Download now" (with the size when only the map is missing) and "Navigate anyway". A ready
  ride starts at once; while the check is still running navigation is not held back.

## What "ready" means

Decided in `route/ReadyOffline.kt` (pure code, tested in `ReadyOfflineTest`):

- **Road data**: the routing cells on the phone cover the ride's stops (a GPX track without
  stops: its two ends), the same check the app uses to re-route on the move. The cells also
  carry fuel stations and pass closures / speed cameras, so they are not listed separately.
- **Map**: a complete saved ride map for this road. A map saved with older OpenFreeMap tiles
  counts as "map update" missing, since without signal it may not show.
- A ride too long for one map download stays "not ready"; the button still gets the road data
  and the map section explains how to split the ride.

## Left for later

- **Per-day status for multi-day rides.** Today the map is one download per ride; a long trip is
  often too long for one download. Per-day status needs one saved map per day (keyed by the day's
  part of the track) and a list of days in the status. Recommendation: do it together with
  per-day map downloads, once the stages work (#12) is merged, so the days are stable.
- The road-data size is only known online, so the Navigate dialog shows a size only when the map
  alone is missing.
