# Security

## How to report

If you find a flaw (for example a way to forge a route, get a non-official APK installed
or read someone's location), **do not open a public issue**: use
"Report a vulnerability" in the Security tab of this repository.

- Reply within **7 days**.
- Fix or fix plan within **90 days**; then the flaw is made public with
  acknowledgements, if you want them.

## Supported versions

Only the latest version published in the Releases. Fixes are not backported.

## Scope

In scope: the Android and iOS app, updates from the Releases, GPX, KML and link import,
the road data (`routing-data`) and the pipelines of this repository.
Out of scope: external services (OpenFreeMap, Google, GitHub, Nostr relays), rooted or
modified phones, attacks that require the unlocked phone in hand.

## Verifying the APK

Official APKs are signed with this certificate (CN=RidePlan, O=NanuNic9379):

```
SHA-256: B6:B2:A9:4D:4D:6F:A4:F9:D9:D9:8C:57:50:95:71:61:15:07:AB:BE:10:A1:FE:DA:F9:02:3A:54:99:B9:5C:40
```

To check: `apksigner verify --print-certs RidePlan.apk`. If the fingerprint differs, do not
install it. The app updates itself only with APKs signed with the same certificate.

## Never share the private key

When Nostr arrives: your private key (`nsec...`) must **never** be pasted into an issue,
a chat or a website. Nobody from the project will ever ask you for it.
