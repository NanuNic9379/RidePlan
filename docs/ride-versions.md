# Ride update with version (#11)

## Done (first slice)

- **Version** (`ride/RideVersion.kt`): every saved ride has a `RideVersion` with
  `rideId` (12 hex chars, the same on every phone that has the ride), a number, a note
  ("v3: landslide on the SP12") and an update time. Rides saved before this change get v1
  the next time they are saved.
- **When the number changes** (`ProfileData.save`):
  - if the route (stops, plans, track) did not change, the version stays the same;
  - edits in the same session (since the ride was last opened) stay one version, with a
    new date;
  - an edit after reopening the ride means number + 1, and the old note is cleared.
  The leader writes the note in the ride sheet ("Write what changed").
- **It travels** in the link/QR (field 8: `rideId`, number, update time, note) and in the
  GPX metadata (`<desc>vN: note</desc>` and `<rideplan:version id= number= updated=/>`).
- **Receiving a newer version** of a ride you already have (link, QR, GPX file) replaces
  the old one instead of making a copy. Your notes, rides done and favourite stay. The ride
  keeps a `RideUpdate`: from vN to vM, the leader's note, the stops added and removed, and
  how many plans changed. The ride detail shows it in a card ("Got it" hides it), and a
  snackbar says "Ride updated to vM".
- The same version, or an older one, changes nothing (no duplicates). An old link still
  open on the map cannot overwrite a newer version that was already received.
- Tests: `commonTest/.../profile/RideVersionTest.kt`, `ride/StopPlanTest.kt`.

## Left

- **Nostr** (criterion 3): publish the ride as a parameterized replaceable event
  (kind 3xxxx, `d` tag = `rideId`). Every new version replaces the old one on the relays.
  If the relays do not answer, the copy already received stays valid; that part already
  works, because the local ride is only replaced by a newer version. Owner decisions:
  which relays, whether rides are public or encrypted to the group, and what key the
  leader signs with.
- **Who may publish a version**: today anyone with the link can make a "newer" version
  of the same `rideId`. With Nostr the author key settles this (only events from the
  leader's pubkey count).
- An "updated" badge in the ride list, and a map view of the differences (old route in
  grey).
- **Conflicts**: if a rider changed their copy and then receives a new version, the
  leader's version wins. Their notes stay, but their own route edits are lost. A "keep
  mine" option may be needed.
- Changing a stop plan counts as a route change and bumps the version after reopening.
  The owner should confirm this is wanted (a new meeting time is news for the group).
