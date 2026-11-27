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
| You download the map of a ride for use without signal | OpenFreeMap (`tiles.openfreemap.org`) | The map tiles along the ride |
| You download the road data of an area (roads, fuel stations, road notes) | GitHub (Releases of this repository) | The file name of the area |
| Update check (GitHub version only; foreground checks are automatic when enabled, background checks are optional and run about every 12 hours) | GitHub (`api.github.com`) | No data beyond the request for the latest version; GitHub sees the phone's IP address and request time |
| You tap Publish on Nostr (off by default) | The Nostr relays in your list (by default `relay.damus.io`, `nos.lol`, `relay.primal.net`, `offchain.pub`) | The ride as shared (private zones and first and last km removed), its name, your summary, your Nostr public key: **public** |
| You follow a ride on Nostr and open or check it | Your relays and the relays named in its address | The address of the ride (author key and ride id) |
| You publish a photo spot on Nostr | The Nostr relays in your list | Its exact coordinates, only the name, description and photo link you selected, and your Nostr public key: **public** |
| You import a photo spot from Nostr | Your relays and the relays named in its address | The spot address (author key and spot id) |

The route is computed **on the phone**: stops are not sent to any server. The same holds during
navigation: your position is used only on the phone.

## Permissions

- `INTERNET`, for the uses in the table above.
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`, only for turn-by-turn navigation. The app asks
  for it when you tap **Navigate** and reads your position **only while navigating**, from Navigate to
  Exit. With the screen off or another app open, navigation goes on through a foreground service with a
  visible notification ("Stop navigation" ends it); it also stops at the finish, when the app is swiped
  away, or after 45 minutes without moving. There is no background location outside navigation (no
  `ACCESS_BACKGROUND_LOCATION`). The position is used on the phone to follow the route and to recompute
  it when you leave it: it is not stored and never leaves the phone.
- `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_LOCATION`: only for the navigation service.
- On Android 13+, `POST_NOTIFICATIONS`: required for optional background update alerts and requested
  when you enable them if it has not already been granted. It is also used for the navigation service notification; refusing it does
  not stop guidance. Background update checks are scheduled only after you enable them and grant
  notifications. They use the network only, never location.
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: lets you ask Android to exempt RidePlan from battery
  restrictions that can stop turn-by-turn navigation with the screen off. RidePlan explains this
  the first time you navigate; opening the Android prompt is optional, and navigation works if you skip it.

Voice instructions are spoken by the phone's text-to-speech engine. The app does not read contacts or
photos. You open files (GPX, KML) yourself from the system picker and the app sees only those.

## Sharing

When you share a ride (GPX file, or QR and link) you decide who to send it to. Start and finish
are often your home or garage, so before anything leaves the phone the app can blur them:

- **Private zones.** In the profile you can mark places such as home or the garage as private
  zones (500 m, 750 m or 1 km). They are applied to every way of sharing (GPX file, QR, link, and
  Nostr), on by default: track points inside a zone are dropped, a start or finish
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

**Nostr (off by default).** In Settings, Publish on Nostr you can turn on public sharing. What you
publish is **public**, copied to several servers (relays) run by others, and cannot be really
deleted; the app says so before publishing. Your Nostr key stays in a separate signer app (such as
Amber): RidePlan never sees it and only stores your public key. Relays see your IP address, the time
and what you publish or ask for. RidePlan runs no relay. Your Nostr settings (relays, followed rides)
are stored in a separate file on the phone, not in the backup, and "Delete all data" removes them.
Photo spots are published separately from rides. Their exact coordinates are always public; you
choose whether to include the spot name, description and HTTPS photo link. RidePlan never uploads
or hosts the linked photos. Sharing an `naddr` lets another rider import the spot into their own map.

## GDPR

RidePlan does not operate a server or collect the request metadata listed above. External services
receive the data shown in the table and process it under their own privacy policies (OpenFreeMap,
Google, GitHub). For questions open an issue or write via the Security tab of the repository.
