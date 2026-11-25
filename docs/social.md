# Social: identity, published rides, friends on the map, community

Design for issues #3 (key-based identity and route publishing on Nostr), #16 (friends' positions
on the map during the ride) and #32 (community: shared rides with ratings, comments and photos).
Status: **direction decided, details open** (open decisions at the end). Only the first slice is built:
encoding a shared ride into an unsigned Nostr event, with no keys and no network
(`shared/.../social/RideEvent.kt`).

The project principles (docs/PROJECT.md) set the frame: no servers of ours, no accounts, offline
first, open formats, no data collected. The owner also asked to keep **peer-to-peer (P2P) as the
first-class option for every feature** and to say honestly where it stops working. So each feature
below starts from the most P2P design and ends with a verdict:

- **Pure P2P**: phone to phone, no third party in between.
- **P2P + someone else's encrypted relay**: phones talk end-to-end encrypted through servers
  run by third parties (Nostr relays), which cannot read the data and can be swapped. No server of ours.
- **Needs a server**: something must stay online and store data for others. "Server" here can still
  be someone else's (public Nostr relays, Blossom media servers), but it is no longer P2P.

## Decided direction (owner, November 2026): "P2P with encrypted relay"

- **P2P first**: direct and nearby transfer, QR, link and file sharing are the main way rides move.
- **Where P2P cannot work** (live positions at a distance, discovery of strangers' rides, availability
  when the author is offline) RidePlan uses **third-party relays the project does not run**, such as
  public Nostr relays. Private data (live positions, group data) is **end-to-end encrypted**, so relay
  operators cannot read content or locations. Content that is public by choice (a published ride,
  a rating, a comment) is signed, not encrypted, and the UI says it is public.
- **No servers run by the project.** Not for relays, not for media, not for search.

## Summary

| Feature | Verdict | Most P2P design that works on a bike |
|---|---|---|
| Ride between friends (#3 part, already there) | **Pure P2P** | QR at the bar, link, GPX file (done); nearby Wi-Fi Direct transfer later |
| Identity (#3) | **Pure P2P** (nothing goes online) | key pair made on the phone; or an external signer app |
| Publishing rides for strangers (#3) | **Needs a server** (public relays, not ours) | signed addressable event on 3-5 public relays chosen by the user |
| Friends on the map (#16) | **P2P + someone else's encrypted relay** | NIP-44 encrypted ephemeral events through public relays; BLE between bikes as offline fallback |
| Ratings, comments (#32) | **Needs a server** (public relays, not ours) | NIP-25 reactions and NIP-22 comments on the ride event |
| Photos (#32) | **Needs a server** (media host) | Blossom servers chosen by the user, off by default until moderation is decided |

## Why "pure P2P" stops where it stops

This is the honest part, valid for all features.

1. **Phones on mobile data cannot be reached.** Almost all mobile carriers use carrier-grade NAT
   (CGNAT): the phone has no public address and accepts no incoming connection. Two phones on mobile
   data can sometimes still connect with UDP hole punching, but only after both learn their public
   address from a STUN server, and many carrier NATs are "symmetric", where hole punching fails. Then
   only a TURN relay works, i.e. a server that forwards the traffic. **WebRTC does not remove this:**
   it needs a signaling channel to exchange offers (that could be Nostr), STUN to find addresses
   (public free STUN servers exist), and TURN as fallback (bandwidth costs money, nobody runs it free
   at scale). IPv6 helps on some carriers, but carriers firewall incoming IPv6 too.
2. **Phones sleep.** Android (Doze, app standby) and even more iOS stop background apps and their
   sockets. A phone cannot "serve" its rides to others while in a pocket. A DHT node or torrent seed
   on a phone is either killed or drains the battery and data plan.
3. **Strangers must find you.** Discovering rides from people you never met needs an index that is
   online when you search and the author is not. In P2P that index is a DHT (BitTorrent mainline,
   IPFS/libp2p Kademlia, Hypercore/Holepunch). On phones these are known to be heavy: constant
   keep-alive traffic (tens of MB per day for an active node), many wake-ups, poor reachability
   behind CGNAT (so the phone is a "client-only" node that leans on desktop nodes anyway), and content
   disappears when the few seeders go offline. Searching "rides near this area" also needs an index by
   area, which a plain DHT (exact key lookups) does not give.
4. **Nearby radio is short.** Bluetooth LE reaches ~10-100 m between bikes, Wi-Fi Direct ~50-200 m.
   Good at the bar and in a tight group, useless when the group spreads out over kilometres.

What is left, and what this design uses: **pure P2P for everything between people who are physically
together or already in a chat**, and **third-party Nostr relays as dumb, replaceable, end-to-end
encrypted pipes** for what must cross the internet. Relays are servers, but not ours, need no account,
anyone can run one, and the app can use several at once so no single operator matters.

## 1. Rides between friends (already P2P)

**Verdict: pure P2P.** Already shipped:

- QR at the bar and the `https://nanunic9379.github.io/RidePlan/r/#…` link (the data after `#`
  never reaches the GitHub Pages server), GPX/KML file through WhatsApp/Telegram/email.
- Private zones and trimmed ends applied to every way of sharing (`SharePreview`).

Possible additions, all without Google Play Services (the F-Droid build has none):

| Option | GMS-free | Range | Notes |
|---|---|---|---|
| QR (done) | yes | camera | best at the bar; ~150 track points max |
| Android share sheet (done) | yes | any chat | uses the chat app, which is a server but chosen by the user |
| Wi-Fi Direct (`WifiP2pManager`) | yes, Android API since 4.0 | 50-200 m | full GPX of any size; pairing dialog is clunky; iOS has no Wi-Fi Direct (Multipeer Connectivity, Apple-only) |
| Wi-Fi Aware (NAN) | yes, Android 8+ | ~100 m | many phones lack the hardware: only as an option |
| Bluetooth LE GATT | yes | 10-100 m | works Android-iOS; slow (~10-100 KB/s) but a ride is a few KB |
| Google Nearby Connections | **no** (needs GMS) | | excluded: breaks the F-Droid build |

Recommendation: QR and link stay the main way. If a "send the full GPX to the phone next to me"
button is wanted, use **BLE GATT** (works on both Android and iPhone, no GMS) with the ride as the
same compact payload as the link, and Wi-Fi Direct only if big GPX files must go. Low priority:
the chat apps already cover this.

## 2. Identity (#3)

**Verdict: pure P2P.** Nothing goes online to create an identity.

- **Key pair**: secp256k1, BIP-340 Schnorr signatures, as required by Nostr (NIP-01). Generated on
  the phone from the system secure random generator.
- **Shown as** `npub…` (NIP-19, bech32) for sharing the public key; the profile is a kind 0 event
  with nickname and default bike (#32 decision: "nickname plus default bike").
- **Storage on Android**: the 32-byte secret is encrypted with an AES-GCM key that lives in the
  Android Keystore (hardware-backed where present; Keystore cannot hold secp256k1 keys directly, so it
  wraps them). Ciphertext in app-private storage, excluded from `allowBackup`/cloud backup.
  iPhone: Keychain, `ThisDeviceOnly` accessibility.
- **Backup**: the user exports the key as **NIP-49 `ncryptsec`** (key encrypted with a password,
  scrypt) as text or QR, and can paste it on a new phone. Plain `nsec` export only behind a warning.
  **NIP-06 (12/24 words) is not recommended**: the NIPs list now marks it "unrecommended, prefer a
  single nsec", and words would add a BIP-39 dependency. Decision: NIP-49 only.
- **External signer, as alternative**: people who already use Nostr can keep the key in a signer
  app: **NIP-55** (Android signer apps such as Amber, via intents/content provider, no network) or
  **NIP-46** (remote signer, through a relay). Then RidePlan never sees the secret key. The first slice
  already produces the unsigned event JSON these signers take.
- **Limits to say in the UI**: the key proves *which key* signed, not who the person is; a lost key
  without backup is lost forever; a stolen key cannot be revoked (only a NIP-09 deletion request and a
  new key announced to friends).

Default: **no key until the user opens a Nostr feature**. Sharing by QR/link/GPX never needs one.

### Phase 2 key storage requirements (#116)

When RidePlan holds its own key (phase 2), the key storage must meet all of these:

- **Where**: the wrapped key goes in `filesDir/keys/` (one file per key, for example
  `keys/nostr.key`), never in `profile.json`, `nostr.json` or SharedPreferences.
- **Wrapping**: AES-256-GCM with a non-exportable key in the Android Keystore
  (`setIsStrongBoxBacked` where available, otherwise TEE), random 12-byte IV per write, the key
  file's purpose and version as associated data. The plain secret exists only in memory while
  signing and is zeroed afterwards.
- **Never leaves the phone by itself**: `allowBackup="false"`, `fullBackupContent` and
  `dataExtractionRules` (`androidApp/src/main/res/xml/`) exclude all app storage from cloud backup
  and from device-to-device transfer (Android 12+ ignores `allowBackup` for D2D). Any change to
  those rules must keep `keys/` excluded. The Keystore key does not migrate either, so a copied file
  would be useless anyway.
- **Moving to a new phone**: only through the NIP-49 `ncryptsec` export the rider makes on purpose.
- **Delete all data** removes `keys/` and the Keystore alias.
- **iPhone**: Keychain item with `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`, no iCloud sync.
- **Check**: the `adb shell bmgr` test below shows no `keys/`, `nostr.json` or `profile.json`.

Backup test with the local transport (Android 12+ emulator or phone, debug build; the D2D rules
exclude the same paths):

```
adb shell bmgr enable true
adb shell bmgr transport com.android.localtransport/.LocalTransport
adb shell bmgr backupnow --non-incremental io.github.nanunic9379.rideplan
adb shell ls /data/data/com.android.localtransport/files/
```

With the rules above the transport receives no app files.

## 3. Publishing rides for strangers (#3)

**Verdict: needs a server (public Nostr relays, not ours).** The author is offline most of the time
and strangers must find the ride by area: that needs something always online with an index.
A DHT on phones (see above) would cost battery and data on every rider's phone and still lose rides
when the author goes offline; relays do the same job for free from the rider's point of view.

### Event format (implemented as a draft)

No NIP for routes exists (checked against the NIPs list in November 2026; there are drafts for
geocaches, kind 37516, and workouts, kind 1301, neither fits). Proposal:

```json
{
  "kind": 30455,
  "tags": [
    ["d", "<ride id, stable: a new version replaces the old>"],
    ["title", "Sunday loop"],
    ["summary", "Coffee at the pass"],
    ["distance", "182.4"],
    ["t", "motorcycle"], ["t", "rideplan"],
    ["g", "u0n"], ["g", "u0n2"], ...
  ],
  "content": "https://nanunic9379.github.io/RidePlan/r/#…"
}
```

- **Addressable** kind (30000-39999): same author + same `d` = replaced, which gives the versioned
  ride update from the analysis ("v3, landslide on the SP12") for free. `30455` is unused in the NIPs
  list but **provisional**; a short NIP could be proposed later.
- **`content` is the RidePlan link** already used by QR: compact (a few KB, at most 150 track points
  or only the stops), already tested and privacy-filtered, and any Nostr client shows it as a link that
  opens the web page/app. The full GPX is not put in the event: a long GPX is hundreds of KB, while
  relays commonly limit messages to 16-128 KB. If the full track is ever needed, it goes to a Blossom
  server and the event carries its URL + SHA-256 (`imeta`, NIP-92), which brings the hosting problem of
  section 5.
- **Size limit**: the draft refuses events above 12 KB (below the 16 KiB example of NIP-11).
- **Search by area**: `g` tags with the geohash cells the ride crosses, precision 3 (~150 km) and 4
  (~40×20 km). Relays only match tags exactly, so the app asks for the cells of the visible map at one
  precision (`{"kinds":[30455],"#g":["u0n2","u0n3",…]}`). Very long trips keep only precision 3
  (max 24 + 48 cells).
- **Privacy**: built only from `SharePreview`, so private zones and trimmed ends are applied as for
  GPX/QR/link; no times of the ride (only the publishing time every event has); start and finish are
  never tagged separately. The UI must say before publishing: *public, copied to several servers,
  cannot be really deleted* (a NIP-09 deletion request is honoured by most relays, not all).

### Relays

Implemented in phase 1 (`social/Relays.kt`). The user can remove, add (up to 8, `wss://` only) and
restore them in Settings, Publish on Nostr. Defaults, checked in November 2026 through their NIP-11
documents (all free, no account, no payment, all running strfry, which keeps the newest version of an
addressable event):

| Relay | Max message | Why |
|---|---|---|
| `wss://relay.damus.io` | 1 MB | one of the largest relays, read by most clients (Damus, Amethyst, Primal) |
| `wss://nos.lol` | 128 KB | large general relay with long retention and spam filtering |
| `wss://relay.primal.net` | 1 MB | Primal's public relay, high uptime, also read by its web client |
| `wss://offchain.pub` | 128 KB | independent operator, so the defaults do not depend on the big apps only |

Left out: `nostr.mom` (same operator as `nos.lol`, so no extra safety if one closes) and
`relay.nostr.band` (did not answer when checked). A ride event is at most 12 KB, far below every limit.

- Reading a ride: the user's relays first, then the relay hints inside its `naddr` (at most 8 in all).
- Not done yet: publishing the user's relay list (NIP-65, kind 10002) and reading the author's list.
- Risk: free relays may delete old events or close. Publishing to four operators mitigates it; the
  ride also stays on the author's phone and can be published again with one tap.

### Phase 1 as built (November 2026)

- **No key in RidePlan.** Publishing goes through a signer app on the phone (NIP-55, for example
  Amber, also on F-Droid, Obtainium and zap.store). RidePlan asks it for the public key
  (`get_public_key`) and, for each publish, to sign the event (`sign_event`, with `id` and
  `current_user`). The answer (the signed event or only the signature) is **checked before anything
  is sent**: same content, same author, right id, valid BIP-340 signature. A wrong answer is refused
  (`Signing.signedEvent`).
- **Off by default.** Settings, Publish on Nostr has a switch; with it off, no Nostr part appears
  and nothing is contacted. With it on, the network is used only when the user taps **Publish** or
  checks a followed ride (when opening it, or with the button).
- **Publish** (ride page): a dialog with the same privacy options and preview as every other share
  (private zones, first and last km), an optional one-line summary, the relays, and the warning that
  a published ride is public and cannot be really deleted. The `d` tag is the ride's version id
  (`RideVersion.rideId`, #11): publishing a new version of the ride replaces the old one on the
  relays. A ride saved before versions existed gets its first version at the first publish.
- **Following**: the author taps **Send the Nostr address** (`nostr:naddr1...`, with author, `d`,
  kind and two relay hints) and sends it as they like. The rider pastes it in Settings, Publish on
  Nostr, Follow a ride. The ride is saved among the received rides; every time it is opened (or with
  **Look for a new version**) the app asks the relays for the address and applies a newer version
  with the existing #11 logic (`ProfileData.receive`, "updated from vN to vM" note).
- **Only the author can publish a new version** (`RideUpdates`): an event is used only if its kind is
  30455, its author and `d` are those of the address, its id and signature are valid, and the ride
  inside has the same version id. Among valid ones the highest version number wins, then the newest.
  Anyone else's event at the same `d` is ignored.
- **Relays down**: if no relay answers within 10 s the ride is not touched and the app says
  "No relay answered. You keep the last version received". The last accepted event is also kept in
  `nostr.json`.
- **Storage**: `nostr.json` in the app's private files (switch, relays, signer public key and app,
  published and followed rides). Not in the backup file; "Delete all data" clears it.
- **Code**: `shared/.../social/` (`NostrEvent`, `Nip19`, `Sha256`, `RelayProtocol`, `RelayPool`,
  `Relays`, `RideUpdates`, `SocialStore`, `NostrService`, `NostrUi`); Android: `AndroidNostrSigner`
  (NIP-55 intents, `<queries>` for `nostrsigner:`) and `OkHttpRelayTransport` (websockets).
- **Tests**: `NostrProtocolTest` and `RideUpdatesTest` (common: SHA-256 vectors, bech32 and NIP-19
  vectors and round trips, event id and JSON, relay messages, publish and fetch exchanges, relay
  list, version choice) and `NostrCryptoTest` (host: real signatures, signer answers, publish,
  follow, update, forged versions, relays down or silent, against fake relays).

## 4. Friends on the map during the ride (#16)

**Verdict: P2P + someone else's encrypted relay** (pure P2P only within Bluetooth range).

### Options compared

| Option | Works on mobile data across km | Battery/data | Server needed | Verdict |
|---|---|---|---|---|
| BLE advertising between bikes | no, 10-100 m | low | none | pure P2P, only for a tight group |
| Direct UDP / WebRTC data channel | partly: fails on symmetric CGNAT, needs STUN + TURN fallback + signaling | high (keep-alives, one connection per friend, broken on every cell handover) | STUN (free), TURN (costs), signaling | not usable reliably on a motorcycle |
| Mesh over phones (Briar-style, BLE/Wi-Fi hops) | no: bikes are too far apart to hop | high | none | no |
| **NIP-44 encrypted ephemeral events through public relays** | yes | low: one small message every 15-60 s over one websocket | third-party relays that see only ciphertext | **recommended** |

### Recommended design

- **Group key**: the organizer creates a ride group; the QR/link of the ride carries a random group
  secret (after the `#`, never sent to any server). Whoever scans it at the bar joins. No accounts,
  no friend list on a server.
- **Position messages**: every 15-60 s (adaptive: slower when stopped, faster on twisty bits), a
  **kind 2xxxx ephemeral event** (relays forward, do not store) whose content is the position
  encrypted with **NIP-44 v2** using a key derived from the group secret. Signed with a **throwaway
  key per ride**, not the user's identity key, so relays cannot link the ride to the public profile.
  Tags: only a short group topic hash (rotating per ride) to subscribe on. Optionally wrapped as
  **NIP-59 ephemeral gift wrap (kind 21059)** to hide even the sender key.
- **Payload**: rounded position (~10 m), speed bucket, "stopped since" flag, nickname. No history.
- **What relays see**: IP address, time, message size and the topic hash; not the position, not who.
- **Offline fallback**: BLE advertising of the same encrypted payload between nearby bikes, useful
  at the meeting point and in the mountains without signal (later phase).
- **Battery**: one websocket to 2 relays, foreground service with location while sharing, GPS already
  on for navigation. Estimated extra cost is small next to the screen and GPS; to be measured.

### Privacy defaults (from the issue, all mandatory)

- **Off by default.** Opt-in per ride, with a clear "Share my position with this group" switch.
- **Auto-stop** at the end of the ride (navigation ended or arrival) and after a maximum time
  (default 8 h). "Stop sharing" always visible on the map.
- **No position sent inside private zones** (#6).
- Background location only while sharing is active, explained in docs/PRIVACY.md; foreground service
  notification while active.
- Each friend shown with name, "2 min ago", distance; "someone stopped / you fell behind" warning.

## 5. Community: ratings, comments, photos (#32)

**Verdict: needs a server** (public relays for text, a media host for photos; none of ours).
Availability when the author is offline, and discovery of strangers, are the whole point of a
community: P2P cannot give them on phones (see "Why pure P2P stops"). A DHT/torrent design would make
every rider's phone a node: constant background traffic and wake-ups, poor reachability behind CGNAT,
and rides vanishing when few people seed them. Not usable for a motorcycle app.

- **Ratings**: **NIP-25** reaction (kind 7) pointing at the ride (`a` tag
  `30455:<pubkey>:<d>`), content a star value (`"1"`-`"5"`) in a custom form agreed in our format.
  The decision "rate only after marking the ride as done" is enforced by the app only: the
  network cannot verify it, and anyone can write raw events. Ratings are counted per key, the newest
  counts; fake keys can still vote, so show ratings from people you follow first.
- **Comments**: **NIP-22** (kind 1111) with `A`/`K` root tags pointing at the ride. Plain text.
- **Profile**: kind 0 with nickname and default bike (an extra field in its JSON).
- **Photos**: Nostr carries only links. Files go to **Blossom** servers (NIP-B7, user server list kind
  10063; NIP-96 is now "unrecommended, replaced by Blossom"), referenced with `imeta` (NIP-92) URL +
  SHA-256, so the same photo can be mirrored on several servers. Problems:
  - **Cost**: free Blossom servers exist but limit size/retention; reliable ones are paid. If RidePlan
    ran one, that is a server of ours, against principle 1, and it makes the project host photos of
    strangers.
  - **Moderation and law**: hosting user images means illegal content, notice-and-takedown
    obligations (EU Digital Services Act), copyright claims. Whoever hosts is responsible. With
    third-party Blossom servers that responsibility stays with their operators, but RidePlan still
    *shows* the images.
  - **Location in photos**: EXIF must be stripped on the phone before upload (GPS of home).
  - Recommendation: photos **off until decided**; when on, user-chosen Blossom servers, EXIF stripped,
    resized (max ~1600 px), images of unknown keys blurred until tapped.
- **Abuse**: **NIP-56** reports (kind 1984), local mute lists (NIP-51, kind 10000), hide content with
  more than N reports from people you follow; no central moderation exists or is possible. Relays
  with their own rules (e.g. relays that require NIP-42 auth or that moderate) can be chosen as
  defaults.

## 6. Legal and privacy

- **What becomes public**: every published ride, rating, comment, photo and profile is public,
  copied by relays we do not control, and cannot be fully deleted. Must be said before the first
  publish, and in docs/PRIVACY.md.
- **GDPR**: RidePlan (the project) still processes no personal data: no server of ours, no
  controller storing data. Users publishing their own content are covered by the household
  exemption for private use; relay and Blossom operators are controllers of what they store. A key
  and a route can be personal data (a route starting near home identifies a person): hence private
  zones on by default for Nostr too, no times, coarse geohashes.
- **Live location** is the most sensitive data (#16): opt-in, encrypted end to end, throwaway keys,
  auto-stop, no storage (ephemeral events). The privacy notice must name the relays contacted and that
  they see IP, time and size.
- **Right to erasure**: NIP-09 deletion requests and NIP-62 "request to vanish" are sent on request
  from the app (best effort; say so).
- **Minors / app stores**: user-generated content (comments, photos) makes Google Play and the App
  Store require reporting and blocking tools in the app (NIP-56 report + mute satisfy this) and may
  raise the content rating.

## 7. Libraries (Kotlin Multiplatform)

Checked on Maven Central, November 2026.

| Library | What | License | KMP | Size (Android AAR, all ABIs) | Notes |
|---|---|---|---|---|---|
| `fr.acinq.secp256k1:secp256k1-kmp` 0.24.0 | secp256k1 + Schnorr (BIP-340), wraps Bitcoin Core's libsecp256k1 | Apache-2.0 | Android, iOS, JVM | `-jni-android` 5.1 MB (~1 MB per ABI in a split APK) | minimal, audited core; NIP-44, NIP-19, NIP-49, relay client must be written by us |
| `com.vitorpamplona.quartz:quartz` 2.15.1 | Amethyst's Nostr library: events, NIP-19/44/49/55/59, relay client | MIT | Android, iOS, JVM | 14.6 MB AAR, pulls OkHttp, SQLite, secp256k1-kmp | now on Maven Central (the analysis said a non-Maven plugin was needed: no longer true); large, API follows Amethyst |
| `org.nostrdevkit:nostr-sdk-kmp` 0.45.2 | rust-nostr Kotlin bindings (UniFFI) | MIT | Android, iOS | 19.6 MB AAR (Rust natives) | README says "ALPHA, API will change in breaking ways"; Rust toolchain only for building it |

Recommendation: **secp256k1-kmp + our own small code** for the first phases (event, id with SHA-256,
NIP-19, NIP-49, NIP-44): RidePlan needs few NIPs, and a small dependency keeps the APK small and the
code reviewable; NIP-44 has official test vectors. Re-evaluate Quartz if the community part grows
(relay pool, outbox model). Avoid nostr-sdk-kmp while it is alpha.

**Used in phase 1:**

- `secp256k1-kmp` 0.24.0 (Apache-2.0), only to **verify** signatures (`verifySchnorr`). The common
  part is pure Kotlin; the native library comes from `secp256k1-kmp-jni-android` in the app (about
  1.4 MB of `.so` per ABI, so roughly 1.4 MB more per split APK) and `-jni-jvm` in host tests only.
  R8 keeps `fr.acinq.secp256k1.**` (it finds its native loader by reflection).
- `okhttp` 4.12.0 (Apache-2.0, pure Java/Kotlin, about 0.8 MB before R8) for websockets on Android;
  Okio was already in the app. No Google services: the F-Droid build is the same.
- SHA-256 and bech32 are our own small code, checked against the standard test vectors.

## 8. Phased plan

| Phase | What | Servers | Status |
|---|---|---|---|
| 0 | Ride → unsigned Nostr event draft (kind, tags, geohash, size limit, privacy via `SharePreview`), parse back | none | **done** (`social/RideEvent.kt`, `RideEventTest`) |
| 1 | Publish a ride through a NIP-55 signer app (Amber) to user-editable public relays, off by default; follow a ride by its `naddr` and receive its new versions (only from the author) | public relays (not ours) | **done** (see "Phase 1 as built") |
| 2 | Own key: generate, Keystore storage, NIP-49 backup, `npub` display; sign with secp256k1-kmp; NIP-65 relay list | public relays | for riders without a signer app |
| 3 | Explore tab: search by area (`g`), ride page, ratings (NIP-25) and comments (NIP-22), report/mute | public relays | needs moderation (open 2) |
| 4 | Friends on the map: group secret in the QR, NIP-44 ephemeral events, foreground service, auto-stop | public relays (ciphertext only) | needs max time and interval (open 4), privacy text |
| 5 | Photos on Blossom; BLE offline positions; BLE ride transfer | media servers (third party) | needs photo hosting (open 1) |

Phase 0 needs no network. Phase 1 talks only to the public relays in the user's list, and only when they tap Publish or check a followed ride.

## Decided (no longer open)

- Direction: P2P first, third-party encrypted relays where P2P cannot work, no project servers.
- Rides can be published publicly on Nostr, opt-in per ride, private zones applied (#3).
- Live positions go through public relays as NIP-44 encrypted ephemeral events with throwaway keys,
  BLE between nearby bikes as offline fallback; pure-internet P2P (WebRTC) is not used (#16).
- The project never runs a relay, a media server or a search index.
- Photos are postponed (#32).
- Moderation is client-side: content is hidden after 3 reports from people you follow (#32, phase 3).
- Default relays: the four above, editable; publishing is opt-in (switch off by default) and per tap.
- Phase 1 keys: signer app (NIP-55) only; RidePlan holds no private key.

## Open decisions for the owner

1. **Defaults** still open:
   - event kind: provisional 30455 and the format above, NIP proposal later (recommended: yes);
   - own key for riders without a signer app (phase 2): Keystore with NIP-49 backup, no NIP-06 words;
   - live positions: maximum sharing time (proposed 8 h) and update interval (proposed 15-60 s).
2. **Followed rides are checked only on open**: today a followed ride is checked when its page is
   opened or with the button. A periodic check in the background (for example once a day on Wi-Fi)
   would bring updates without opening the app, at the cost of contacting relays on its own.
   Recommended: keep it manual until the Explore tab exists.
