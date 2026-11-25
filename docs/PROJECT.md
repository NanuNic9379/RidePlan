# RidePlan — project

## Who it is for
For people who ride and plan the ride: Sunday in the hills, the alpine pass, the multi-day trip.
Every choice is judged from the rider's point of view: gloves, sun on the screen, no signal,
phone battery, the group waiting at the bar.

## Principles
1. **From rider to rider (c2c).** No servers of ours, no accounts. Routes go
   from one phone to another or over open networks that anyone can run.
2. **Offline first.** Map and route calculation work without a network.
3. **Open formats.** The route is a GPX: it goes in and out of any navigator.
4. **No data collected.** No usage statistics, no ads.
5. **Open source** GPL-3.0: whoever improves it gives it back to everyone.
6. **Global.** It works all over the world, not just in Italy: data for any country,
   UI in several languages (English as the base, Italian from day one, translations from the
   community), km or miles, per-country rules (e.g. engine size limits on motorways).

## Architecture
| Part | Choice | Why |
|---|---|---|
| App | Kotlin Multiplatform + Compose Multiplatform | one codebase for Android and iPhone |
| Map | MapLibre Native, OpenStreetMap data, offline vector tiles | open source, no per-map cost |
| Route calculation | Valhalla on the phone, motorcycle profile | offline, twisty roads, avoids motorways |
| Route format | GPX 1.1 | every navigator reads it |
| Identity | key pair created on the phone | no registration; signs who made the route |
| Nearby sharing | QR code, link, GPX file | at the bar, on WhatsApp/Telegram, without mobile data |
| Network sharing | Nostr (signed events on public relays) | no servers of ours; anyone can run a relay |

### Why not a phone-to-phone-only network
On mobile networks phones cannot reach each other directly (carrier NAT) and the systems block
apps in the background: a "pure" network does not hold up. Nostr relays are servers, but third-party,
interchangeable and without accounts: the route is signed by the publisher's key, so nobody can
modify it without breaking the signature. The signature, however, tells *which key* signed it, not *who*
the person is: anyone can republish a copy with their own key.

### Known limits
- **iPhone:** it builds only on a Mac with Xcode (or with an automated build on macOS).
- **iPhone on a real phone:** installation, TestFlight and the App Store require the Apple Developer Program ($99/year).
- **Offline calculation:** road data is heavy (the whole of Italy is about 2.4 GB): it is downloaded per area
  (Geofabrik regions all over the world), from packages built with GitHub Actions and published in the
  Releases, with a configurable mirror. The map (PMTiles) can be cut per area directly from the
  public world builds of Protomaps, without hosting anything.
- **Nostr:** there is no standard format for routes; we have to define it (replaceable event).

## Milestones
Planned releases and the v1.0 criteria: [RELEASES.md](RELEASES.md).

Status on 24 November 2026 (beta 0.1.142).

1. Kotlin Multiplatform app skeleton (Android + iPhone) with a map screen. **Done.**
2. Offline OpenStreetMap map of an area, anywhere in the world. **Partly done:** online map with an offline map per ride; routing data offline for Italy, more areas in #14.
3. Route between two points with a motorcycle profile. **Done on Android** (Valhalla); iPhone engine still to wire.
4. Draggable waypoints, prefer curves / avoid motorways. **Done** (#2); field check against Kurviger still open (#4).
5. GPX export and import. **Done.**
6. Sharing with QR code and link. **Done**, with private zones (#6).
7. Key identity and publishing / searching routes on Nostr. **In progress** (#3), external signer first.

The initial analysis ([ANALYSIS.md](ANALYSIS.md)) brought more milestones, all as issues:
curve check, following the ride in the saddle, private zone, range and fuel stations,
vehicle profile (engine size), dirt and surface, stops and meeting points, versioned update
of the ride, multi-day trip, seasonal pass closures. Done so far: navigation (#5), range and fuel (#7),
vehicle profile (#8); stops, versions, stages, surface and country warnings are in the app with
follow-ups still open in their issues.

## Decision: route engine and curves (#4)
Valhalla stays. Since 3.9 its motorcycle profile has a real curvature cost (straight and urban
roads get expensive), it is the only engine of the three that runs offline on Android and
iPhone, and the app already ships it. Its limits: the twistiness is a coarse per-road score,
and unpaved roads are only expensive, not banned, so at "Max" a curvy dirt road can beat a
straight paved one. Every route therefore carries fixed safety settings (no trails, no tracks,
residents-only roads 30 minutes away, restrictions never ignored), and under 150 cc motorways
are a hard ban. GraphHopper (custom model) and BRouter remain the fallback, Android only, if the
field check against Kurviger fails. Details, numbers and the field-check protocol:
[ROUTING.md](ROUTING.md).
