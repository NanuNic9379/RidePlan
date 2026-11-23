# RidePlan

**Percorsi in moto, da motociclista a motociclista.**
Progetti il giro, lo condividi con chi vuoi, senza account e senza un'azienda in mezzo.

> Stato: beta appena nata. Per ora l'app mostra solo la mappa.
> APK Android nelle [Release](https://github.com/NanuNic9379/RidePlan/releases): scegli `arm64-v8a`.

## Cosa farà
- **Progettare il giro** sulla mappa: punti di passaggio, strade con curve preferite, autostrade evitate.
- **Mappe offline** (OpenStreetMap): in montagna, dove non c'è campo, funziona lo stesso.
- **Condividere da motociclista a motociclista**: QR code al bar, link o file GPX su WhatsApp/Telegram,
  oppure pubblicazione in rete firmata con la tua chiave (protocollo aperto Nostr), senza server nostri.
- **GPX in entrata e in uscita**: i percorsi vanno e vengono da Garmin, TomTom, Calimoto, Kurviger.
- **Android e iPhone**, un solo codice (Kotlin Multiplatform + Compose Multiplatform).

## Cosa non farà mai
- Chiedere email, numero di telefono o registrazione.
- Vendere o raccogliere dati di posizione.
- Pubblicità.

## Documentazione
- [Progetto e architettura](docs/PROGETTO.md)
- [Come contribuire](CONTRIBUTING.md)

## Licenza
GPL-3.0 (vedi [LICENSE](LICENSE)), con un permesso aggiuntivo per la distribuzione sugli store
(vedi [LICENSE-STORE.md](LICENSE-STORE.md)).
I dati delle mappe sono © i contributori di OpenStreetMap, licenza ODbL.

---

*English:* RidePlan is a free, open-source motorcycle route planner for Android and iPhone.
Plan rides with offline OpenStreetMap maps, prefer twisty roads, and share routes rider-to-rider
(QR, link, GPX, or signed posts on the open Nostr network) — no accounts, no ads, no tracking.

## Compilare

Serve JDK 21 e l'SDK Android (piattaforma 37).

```
./gradlew :androidApp:assembleGithubDebug   # APK in androidApp/build/outputs/apk/github/debug
./gradlew :shared:testAndroidHostTest       # test del codice comune
```

Due varianti Android:
- `github`: gli APK delle Release. All'avvio chiede a GitHub qual e' l'ultima Release
  e, se e' piu' nuova, propone l'aggiornamento (#30). E' l'unico contatto con GitHub:
  nessun dato inviato oltre all'indirizzo IP di ogni richiesta web. Nelle build debug e' spento.
- `fdroid`: senza aggiornamento interno e senza il permesso di installare app.

Le Release sono firmate con una chiave che sta solo nei secret del repo, mai nel codice.
Senza quei secret la build release si firma con la chiave debug.

L'app per iPhone (`iosApp/`) si compila solo su macOS con Xcode; la pipeline lo fa a ogni PR
per il simulatore, senza firma.
