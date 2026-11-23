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

When you share a ride (GPX file) you decide who to send it to. Before sharing, the app offers
to remove the first and last km, because start and finish are often your home or garage.

When sharing via Nostr arrives: what you publish on Nostr is **public** and
copied to several servers (relays), which we cannot delete. The app will tell you before publishing.

## GDPR

We do not process personal data: there is no controller that stores it. For the external services listed
above their own privacy policies apply (OpenFreeMap, Google, GitHub). For questions open an issue or
write via the Security tab of the repository.
