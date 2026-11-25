# Release plan

Every merge to `main` still publishes a beta APK (`v0.1.N`). On top of that, the work is grouped
into planned releases, one GitHub milestone each, ordered by issue priority (P1 first, then P2,
then P3). A planned release is cut when its milestone has no open issues; the dates are targets,
not promises.

| Release | Target | Content |
|---|---|---|
| v0.2 Ready to ride | 2026-12-06 | All P1: data builds, background navigation, ready without signal, safe sharing and import, field test |
| v0.3 Ride day | 2027-01-17 | P2 riding without signal: navigation extras, per-ride mode and bike, stop editing, track guidance |
| v0.4 Plan and share | 2027-02-28 | P2 planning and sharing: France, Spain-Portugal, ride sheet details, web page, own Nostr key, profile with photo and social links |
| v0.5 Open project | 2027-03-31 | P2 project: accessibility, Weblate, F-Droid, App Links, supply chain, tests, licences |
| **v1.0 Official release** | **2027-04-30** | First stable Android release (criteria below) |
| v1.1 More Europe | 2027-05-31 | P3: DE-AT-CH, Balkans, region maps, more riding modes |
| v1.2 Ride tools | 2027-07-31 | P3: ride extras, battery, release channels and maintenance |
| v1.3 Community | 2027-09-30 | P3: Nostr community, friends on the map |
| v1.4 iPhone | 2027-12-31 | P3: the iPhone app |

## When RidePlan stops being a beta (v1.0)

- All P1 and P2 issues closed (milestones v0.2 to v0.5).
- A release candidate ridden for at least two weeks with no open P1 bug, following
  [field-test.md](field-test.md).
- Offline data for Italy, the Alps, France and Spain-Portugal rebuilt automatically every month.
- Stable and beta update channels (#183), release notes (#182) and the maintenance guide (#181).
- Signing key custody documented (#125) and the F-Droid submission sent (#126).

The iPhone app is not part of v1.0: it follows in v1.4.
