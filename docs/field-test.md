# Field test on a real phone (#63)

A short checklist for the owner, to ride the beta on a real phone before calling it ready for
the Alps. Write the results as a comment on #63 and open one issue per blocker found.

## Before you go

- [ ] Note phone model, Android version, RAM. If possible repeat on a low-end phone (3-4 GB RAM)
      and on a recent one.
- [ ] Install the build from the Release; on the second run, update from the previous Release
      with the in-app updater.
- [ ] Keep `adb logcat` running at home for the first ride (crashes, ANRs).

## 1. Italy and Alps road data

- [ ] On Wi-Fi, plan a ride in Italy and one in the Alps; download the road data the sheet offers.
- [ ] Note download time and the space used (You > Downloaded map data).
- [ ] With "Download only on Wi-Fi" on and mobile data, the app asks before downloading.

## 2. Italy - Switzerland ride across areas

- [ ] Plan Milan - Lugano - Stelvio - Innsbruck. The route is calculated across the areas, with
      no stop marked as "outside the road data".
- [ ] The line on the map is continuous at the borders (no detour around a hidden junction).

## 3. Unpaved choice

- [ ] Plan a ride with an unpaved stretch: the sheet says so, the avoid / allow choice works.
- [ ] Curves "Max": when it would keep you on dirt, the sheet says the level was lowered.

## 4. Pass closures and speed cameras

- [ ] Stelvio / Gavia ride: the seasonal closure shows in the sheet and on the map.
- [ ] Fixed speed cameras show (Italy only) and the alert fires while navigating; turning them
      off in Settings hides them.

## 5. Ready without signal (#67, #68)

- [ ] A ride with road data but no saved map shows "Not ready without signal - Missing: map";
      "Get everything for this ride" downloads it; the sheet then shows "Ready without signal".
- [ ] A ride with nothing on the phone: one tap gets road data and map, with one mobile-data
      question if on mobile data.
- [ ] Tap Navigate on a ride that is not ready: the warning shows with "Download now" and
      "Navigate anyway". On a ready ride navigation starts at once.
- [ ] Airplane mode: the saved map shows along the whole ride; leave the route on purpose and
      check that a new route is found without signal.

## 6. Navigation with the screen off

- [ ] Navigate at least 1 hour. Turn the screen off and on, switch to another app and back:
      voice and position keep going.
- [ ] Note battery % per hour, phone heat, any crash or freeze (logcat).

## 7. Sharing

- [ ] Share a ride by QR and import it on a second phone: same route, private zones cut.
- [ ] Share as GPX to another app (e.g. a navigator) and open it there.
- [ ] Publish on Nostr with Amber and follow the ride from the second phone; edit the ride and
      check the update arrives.

## Report

For each section: OK / problem, with phone, Android version and a short note. Attach logcat
for crashes. Done when the report is on #63 and every blocker has its own issue.
