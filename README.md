# RidePlan

**Motorcycle routes, from rider to rider.**
Plan the ride, share it with whoever you want, no account and no company in between.

> Status: newborn beta. For now the app only shows the map.
> Android APK in the [Releases](https://github.com/NanuNic9379/RidePlan/releases): pick `arm64-v8a`.

## What it will do
- **Plan the ride** on the map: waypoints, twisty roads preferred, motorways avoided.
- **Offline maps** (OpenStreetMap): in the mountains, where there is no signal, it still works.
- **Share from rider to rider**: QR code at the bar, link or GPX file on WhatsApp/Telegram,
  or publishing on the network signed with your key (open Nostr protocol), with no servers of ours.
- **GPX in and out**: routes come and go from Garmin, TomTom, Calimoto, Kurviger.
- **Android and iPhone**, one codebase (Kotlin Multiplatform + Compose Multiplatform).

## What it will never do
- Ask for email, phone number or registration.
- Sell or collect location data.
- Ads.

## Documentation
- [Project and architecture](docs/PROJECT.md)
- [How to contribute](CONTRIBUTING.md)
- [Privacy](docs/PRIVACY.md): no data collected, what connects to the internet and why.
- [Security](SECURITY.md): how to report a flaw and verify the APK signature.

## License
GPL-3.0 (see [LICENSE](LICENSE)), with an additional permission for distribution on app stores
(see [LICENSE-STORE.md](LICENSE-STORE.md)).
Map data © OpenStreetMap contributors, ODbL license.

## Building

You need JDK 21 and the Android SDK (platform 37).

```
./gradlew :androidApp:assembleGithubDebug   # APK in androidApp/build/outputs/apk/github/debug
./gradlew :shared:testAndroidHostTest       # tests of the common code
```

Two Android flavors:
- `github`: the Release APKs. On startup it asks GitHub for the latest Release
  and, if it is newer, offers the update (#30). It is the only contact with GitHub:
  no data sent beyond the IP address of every web request. Off in debug builds.
- `fdroid`: without the in-app update and without the permission to install apps.

Releases are signed with a key that lives only in the repo secrets, never in the code.
Without those secrets the release build is signed with the debug key.

The iPhone app (`iosApp/`) builds only on macOS with Xcode; the pipeline builds it on every PR
for the simulator, unsigned.
