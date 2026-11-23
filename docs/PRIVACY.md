# Privacy

## In short

RidePlan **collects no data**. It has no accounts, ads, usage statistics or automatic
crash reporting. Rides, stops and settings stay on the phone.

The app uses the internet only for the things below, and in those cases the server contacted sees,
as with any web page, the phone's IP address and the time of the request.

| When | Who is contacted | What it receives |
|---|---|---|
| You show the map | OpenFreeMap (`tiles.openfreemap.org`) | The map area to draw |
| You import a Google Maps link | Google (`maps.app.goo.gl`, `google.com`) | The link you pasted or shared |
| You download the road data of an area | GitHub (`routing-data` Release of this repository) | The file name of the area |
| Update check (GitHub version only) | GitHub (`api.github.com`) | No data beyond the request for the latest version |

The route is computed **on the phone**: stops are not sent to any server.

## Permissions

Only `INTERNET`. The app does not read your location, contacts or photos. You open files (GPX, KML)
yourself from the system picker and the app sees only those.

## Sharing

When you share a ride (GPX file, or QR and link) you decide who to send it to. Start and finish
are often your home or garage, so before anything leaves the phone the app can blur them:

- **Private zones.** In the profile you can mark places such as home or the garage as private
  zones (500 m, 750 m or 1 km). They are applied to every way of sharing (GPX file, QR, link, and
  Nostr when it arrives), on by default: track points inside a zone are dropped, a start or finish
  inside a zone is moved to the first point of the ride outside every zone and loses its name, and
  other stops inside a zone are removed. A ride calculated on the phone is then shared as the cut
  track, so whoever opens it does not recalculate the road from your door.
- **The circle does not point at your door.** When you create a zone, its center is moved by a
  random distance (up to half the radius) in a random direction, and only that moved center is
  stored. Your exact place is not saved anywhere, and fitting a circle on many shared rides finds
  the moved center, not your house.
- **First and last km.** You can also remove the first and last km of the ride, counted from the
  edge of the private zones.
- **Preview.** The share dialog shows what goes out: length, number of stops, how far start and
  finish moved and how many stops were hidden. If the whole ride is inside your zones, nothing
  can be shared.
- **No times.** The GPX file contains no timestamps, so it does not tell when you left home.

Private zones are stored only in the profile file on the phone (and in the backup file, if you
make one); they are never shared.

When sharing via Nostr arrives: what you publish on Nostr is **public** and
copied to several servers (relays), which we cannot delete. The app will tell you before publishing.

## GDPR

We do not process personal data: there is no controller that stores it. For the external services listed
above their own privacy policies apply (OpenFreeMap, Google, GitHub). For questions open an issue or
write via the Security tab of the repository.
