# Analisi iniziale (23 novembre 2026)

Otto analisi indipendenti sulle prime tappe (#1-#7): sei motociclisti
(sportivo, tourer, adventure, custom, pendolare, capogruppo di motoclub),
un'analisi tecnica e una di privacy e sicurezza. Gli agenti sono in
`.claude/agents/`. Qui c'è la sintesi; le decisioni finiscono nelle issue.

## Cosa chiedono tutti

| Tema | Chi lo chiede | Dove finisce |
|---|---|---|
| Mappa subito, senza tutorial, chiavi o permessi all'avvio (< 2-3 s, offline) | tutti | #1 |
| Testi e comandi grandi, usabili con guanti e pioggia, leggibili al sole | tutti | #1, #4 |
| Peso mostrato prima del download, solo Wi-Fi, avviso "il giro esce dalla zona scaricata" | tutti | #2 |
| Download lungo un corridoio del giro, non solo per regione | sportivo, tourer, adventure, capogruppo | #2 |
| GPX con traccia **e** rotta con punti di forma, provato su Garmin Zumo e TomTom | tutti tranne il pendolare | #5 |
| Una traccia importata resta com'è: mai ricalcolata o "agganciata" in silenzio | adventure, capogruppo, pendolare | #5 |
| QR/link che si aprono anche senza l'app (anteprima + GPX) e danno lo stesso giro a tutti | tutti | #6 |
| Partenza e arrivo sfumati per non rivelare casa | tutti + privacy | #5, #6, #7 |
| **Seguire il giro in sella** (posizione sulla traccia, fuori percorso, schermo acceso) | tutti | nuova issue |
| Autonomia e distributori lungo il giro | sportivo, tourer, adventure, custom, capogruppo | nuova issue |

## Il rischio tecnico principale: le curve

Sportivo e custom lo dicono in modo opposto e complementare: "più curve"
deve scegliere provinciali tortuose vere, ma **mai** sterrato, mulattiere o
stradine a una corsia. Il profilo moto di Valhalla ha `use_curvature` (sopra 0,5
preferisce le strade con curve), oltre a `use_highways`, `use_tolls`,
`use_trails`, `use_ferry`. "Evita sterrato" però è una preferenza
(`use_trails=0`), non un divieto. Va verificato sul campo **prima** di
costruirci sopra (#10), confrontando 3-5 giri noti con Kurviger.
GraphHopper e BRouter sono solo Java, quindi niente iPhone: restano un
ripiego.

## Per persona

**Sportivo** - curve vere, dislivello e quota massima, passi chiusi
(`access:conditional`), autovelox fissi da OSM, punti "solo forma" che non
fanno girare in tondo il Garmin.

**Tourer** - viaggi a tappe di più giorni con tetto di km/ore, più paesi
offline, evita pedaggi/vignetta (CH, AT) e traghetti, export un file per
tappa, GPX grandi in import, batteria.

**Adventure** - sterrato a tre stati (evita/ammetti/preferisci), mappa che
mostra `track`, `tracktype`, `surface` e divieti, linea libera dove il
calcolo non ha dati, registrazione traccia, modifica di tracce importate,
avviso se il QR semplifica la traccia (scarto in metri).

**Custom** - "evita sterrato" acceso di default, livello di curve a gradini,
evita pendenze forti e strade strette (300 kg + passeggera), soste con
durata nel tempo di arrivo, giro ad anello "150 km da casa".

**Pendolare** - profilo veicolo con cilindrata: sotto i 150 cc niente
autostrade e tangenziali (Valhalla `motor_scooter`), percorso più rapido,
preferiti e widget, parcheggi moto e divieti da OSM.

**Capogruppo** - stesso giro identico per 40 persone: versione dei dati e
opzioni fissate nel percorso, punti di ritrovo e soste con orario,
aggiornamento del giro con versione ("v3, frana sulla SP12"), scheda del
giro per decidere se venire, varianti corto/lungo, profili di export
Garmin/TomTom/solo traccia.

## Privacy e sicurezza

1. Zona privata attiva di default su GPX, QR e Nostr, con anteprima di cosa esce.
2. Parser GPX sicuro (niente DTD/entità esterne, limiti su dimensione e punti, test fuzz); QR/link versionati con limite dopo la decompressione; deep link che apre sempre un'anteprima.
3. Chiave in Keystore/Keychain, backup NIP-49, firmatario esterno (NIP-55/NIP-46), geohash grossolani (4-5 caratteri), nessun orario reale negli eventi.
4. Nessuna telemetria, `allowBackup` escluso per la chiave, verification metadata di Gradle.
5. Correzione: la firma dimostra **quale chiave** ha firmato, non chi è la persona; chiunque può ripubblicare un giro con la sua chiave.

## Analisi tecnica

Lo stack regge; cambia la parte dei dati offline.

| Pezzo | Scelta | Licenza | Nota |
|---|---|---|---|
| Linguaggio e UI | Kotlin 2.4.21, Compose Multiplatform 1.12.2, AGP 9 | Apache-2.0 | modulo `androidApp` separato da `shared` (obbligo AGP 9) |
| Mappa | maplibre-compose 0.20.0 su MapLibre Native | BSD | API ancora 0.x: versione fissata, aggiornata a mano |
| Tessere offline | un file PMTiles per regione (build Protomaps, zoom max 14) | ODbL | da provare in maplibre-compose (#2); ripiego MBTiles |
| Calcolo percorso | valhalla-mobile 0.6.6 (Valhalla 3.9.2) | MIT | iOS in Swift: interfaccia `RouteEngine` implementata in `iosApp` |
| Dati percorso | costruiti con GitHub Actions da Geofabrik, per macro-regione e area alpina, nelle Release | ODbL | Italia ~2,4 GB, non centinaia di MB; mirror configurabile |
| GPX | xmlutil 1.0.2.1 o parser nostro | Apache-2.0 | `rte` + `trk` |
| QR | qrose 1.3.1; dati in polyline + deflate + Base45 | MIT | il QR contiene un link `https://…/r#dati` letto dalla fotocamera di sistema, niente ML Kit |
| Nostr (#7) | Quartz 2.22.1, evento sostituibile nostro (kind 3xxxx), GPX su Blossom | MIT | nessun NIP per i percorsi; Quartz si scarica con un plugin non Maven |

**Rischi aperti**
- **iPhone:** CI su macOS gratuita per compilare il simulatore, ma installare su
  un iPhone vero, TestFlight, App Store e Universal Links richiedono l'Apple
  Developer Program (99 $/anno). Decisione del responsabile del progetto.
- **maplibre-compose 0.x:** può rompere le API a ogni versione.
- **Dati di confine:** i passi alpini richiedono pacchetti costruiti per area,
  non per nazione.

## Nuove issue aperte

Vedi la sezione "Prime tappe" di `PROGETTO.md` e le issue con etichetta
`analisi`.
