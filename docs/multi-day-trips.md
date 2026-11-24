# Multi-day trips (#12)

## Done

- **Days** (`ride/Stages.kt`, `ride/DaysUi.kt`): a daily cap in km (or miles) or riding hours,
  stops marked as overnights, balanced days that prefer to end at a real stop, one GPX per day.
- **Day ends on the map** (`ride/DayMarkers.kt`): a small numbered marker where each day ends
  (the number is the day that ends there). The last day ends at the finish, which has its own
  marker. Before the road is computed only nights at a stop are shown: an "on the road" end would
  sit on a straight line that the map does not draw.
- **Days travel with the ride**:
  - link and QR (`ride/RideLink.kt`): field 9 holds one digit per stop ("1" = overnight, trailing
    zeros dropped), field 10 the cap ("K300" = 300 km a day, "H6.5" = 6.5 riding hours a day).
    Older versions of the app read only the fields they know, so new links still open there
    (without the days). Empty optional fields at the end are left out, so a link without days
    is not longer than before.
  - GPX (`ride/GpxExport.kt`, `ride/RideImport.kt`): `overnight="1"` on the
    `<rideplan:stop>` extension of `wpt` and `rtept`, the cap as `<rideplan:days cap="K300"/>`
    in the metadata extensions. Other apps ignore both.
- **Moving a stop** by dragging it on the map keeps its overnight mark and its plan (#10).
  An unnamed overnight still shows in the overnight chips ("Stop 3") so it can be unmarked.

## Left: short and long variants with a rejoin point

### What riders ask for

A group trip often has a day with two options: the "long" one over the passes for those who want
to ride, the "short" one along the valley for those who are tired, have a passenger or have bad
weather. The two split at a point and meet again at a **rejoin point** (usually the hotel or a
bar), where the group is together again.

### Model (proposal)

```
Ride
  stops            main line, as today
  variants: List<Variant>

Variant
  id               short stable id ("a1"), for links and files
  label            "Short", "Long", or the leader's text ("Valley road")
  fromStop: Int    index in the main stops where it leaves the main line
  toStop: Int      index where it rejoins (toStop > fromStop)
  stops            the stops in between (at least one, otherwise it is the same road)
  track/route      its own road, computed like the main one and never saved (as today)
```

- A variant always **replaces** the main stretch `fromStop..toStop`; it never adds a new end.
  This keeps days simple: a day that contains the split has two lengths (main and variant),
  and the day ends at the same place.
- Overnights stay on the main line. A night inside a variant is not allowed: the group sleeps
  in one place, otherwise it is two trips.
- At most a few variants per ride (3) and no variants of variants: the QR must stay readable,
  and the screen must stay usable with gloves.

### Days

`Stages.split` works on the main line as today. For each day that overlaps a variant the day row
shows both: "Day 2 · 210 km (short: 160 km) · ~4 h". The day boundaries are computed on the main
line; if the cap is in hours and the long variant goes over it, the row says so instead of moving
the night.

### Map

The variant drawn with the same colour, dashed (or thinner), with a small label at its middle
("Short"). The rejoin point gets the normal stop marker. During navigation the rider picks the
variant when the split point is near (one big button for each, 56 dp); the turn-by-turn engine
then follows the chosen line until the rejoin point.

### Sharing

- Link/QR: a new optional field (11), one entry per variant:
  `from<SUB>to<SUB>label<SUB>polyline of the stops<SUB>names`. Older apps ignore it and show the
  main line, which is still a complete ride. The QR is the tight part: a variant with two or
  three stops adds about 30-60 characters, fine; a recorded GPX track for a variant is not sent
  (it is recalculated from its stops, like the main line).
- GPX: one extra `<rte>` per variant, named "Ride name - Short", with a
  `<rideplan:variant from="3" to="5"/>` extension. Other apps show it as a second route, which is
  what a Garmin rider expects. On import the extension puts it back as a variant; a GPX with two
  plain routes from another app stays two separate routes (the first one wins, as today).

### Effort and order

1. Model, save/restore, link/GPX round trip, tests (pure Kotlin, about a day).
2. Days with two lengths and map drawing.
3. Editing: "Add a variant" on a stop in the ride sheet, then tap the rejoin stop and the stops
   in between on the map (reuses the stop editor).
4. Navigation choice at the split point (needs the navigation owner: touches `nav/`).

### Decision for the owner

- Is step 4 (choice during navigation) needed for the first release of variants, or is a variant
  shown on the map and in the days enough at first (the rider follows the main line or opens the
  variant's own GPX in another navigator)? Recommendation: ship 1-3 first, without step 4.
