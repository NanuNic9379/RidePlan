# Stops, meeting points and ride sheet (#10)

## Done (first slice)

- **Stop plan** (`ride/StopPlan.kt`): any stop can have an optional plan with a
  purpose (meeting point, fuel, coffee, lunch, photo stop), a time (`08:30`), a pause
  (0–720 min) and a short note for the group. `Stop.plan` is optional with a default, so
  older data and other branches are not affected. Planning a dragged point (`VIA`) turns
  it into a real stop.
- **Timetable**: arrival and departure for every stop. The riding time comes from the
  computed route, split by leg length along the track (as the crow flies without a track).
  Without a route it falls back to 50 km/h and the sheet says so. A planned time sets the
  clock, a late arrival is flagged, and pauses push every later arrival. A planned time
  past midnight lands on the next day.
- **Ride sheet** in the ride detail (`personal/RideSheetSection.kt`): rows you can tap to
  edit a stop's plan (chips for purpose and pause, a time field and a note), the totals
  (riding time / time with stops, arrival), and **Share ride sheet**. The shared text has
  no coordinates and no link. Stops inside a private zone read "Private place".
- **The plan travels**:
  - in the link/QR as field 7 (field 8 is the version, see #11). Older apps and the web
    page `docs/r/` read only fields 0–6, so they still open the ride;
  - in the GPX: `wpt`/`rtept` get a `type`, a `cmt` and a `desc`, plus
    `<extensions><rideplan:stop time= pause=/>`. On import, other apps' `type` and `cmt`
    become the purpose and the note.
- Tests: `commonTest/.../ride/StopPlanTest.kt`, `profile/RideVersionTest.kt`.

## Left

- **Sheet numbers that need more data**: elevation gain, max altitude, % dirt, tolls,
  difficulty. They need elevation (DEM or Valhalla `height`) and surface/toll attributes
  from the router (Valhalla `trace_attributes` or the route legs). Owner decision: which
  source, and if it works offline.
- **Per-leg times from the router** (Valhalla `legs[i].summary.time`) instead of splitting
  the total by distance. This is more accurate on mixed roads.
- **Plan editing on the map** (the stop toolbar), not only in the ride detail.
- **Printable layout / PDF** of the sheet, and optionally a link in the shared sheet once
  it can be cut for privacy zones.
- The web page `docs/r/` could show the plans (field 7).
- When the private-zone cut moves the first or last stop, that stop loses its plan. This
  is intended for privacy, but worth a note in the UI.
