# RidePlan — progetto

## Per chi
Per chi va in moto e il giro lo prepara: la domenica in collina, il passo alpino, il viaggio di più giorni.
Ogni scelta si giudica dal punto di vista di chi guida: guanti, sole sullo schermo, niente campo,
batteria del telefono, il gruppo che aspetta al bar.

## Principi
1. **Da motociclista a motociclista (c2c).** Nessun server nostro, nessun account. I percorsi passano
   da un telefono all'altro o su reti aperte che chiunque può gestire.
2. **Offline prima di tutto.** Mappa e calcolo del percorso funzionano senza rete.
3. **Formati aperti.** Il percorso è un GPX: entra ed esce verso qualsiasi navigatore.
4. **Niente dati raccolti.** Nessuna statistica d'uso, nessuna pubblicità.
5. **Open source** GPL-3.0: chi lo migliora lo rende a tutti.
6. **Globale.** Funziona in tutto il mondo, non solo in Italia: dati per qualsiasi paese,
   interfaccia in più lingue (inglese di base, italiano dal primo giorno, traduzioni dalla
   comunità), unità km o miglia, regole per paese (es. limiti di cilindrata in autostrada).

## Architettura (proposta, da verificare nella prima fase)
| Parte | Scelta | Perché |
|---|---|---|
| App | Kotlin Multiplatform + Compose Multiplatform | un solo codice per Android e iPhone |
| Mappa | MapLibre Native, dati OpenStreetMap, tessere vettoriali offline | open source, nessun costo per mappa |
| Calcolo percorso | Valhalla sul telefono, profilo moto | offline, strade con curve, evita autostrade |
| Formato percorso | GPX 1.1 | lo leggono tutti i navigatori |
| Identità | coppia di chiavi creata sul telefono | niente registrazione; firma chi ha fatto il percorso |
| Condivisione vicina | QR code, link, file GPX | al bar, su WhatsApp/Telegram, senza rete dati |
| Condivisione in rete | Nostr (eventi firmati su relay pubblici) | nessun server nostro; chiunque può aprire un relay |

### Perché non una rete solo telefono-telefono
Su rete mobile i telefoni non si raggiungono direttamente (NAT degli operatori) e i sistemi bloccano
le app in sottofondo: una rete "pura" non regge. I relay Nostr sono server, ma di terzi, intercambiabili
e senza account: il percorso è firmato dalla chiave di chi lo pubblica, quindi nessuno può
modificarlo senza che la firma salti. La firma però dice *quale chiave* ha firmato, non *chi*
è la persona: chiunque può ripubblicare una copia con la propria chiave.

### Limiti noti
- **iPhone:** si compila solo su un Mac con Xcode (o con una compilazione automatica su macOS).
- **iPhone sul telefono vero:** installazione, TestFlight e App Store richiedono l'Apple Developer Program (99 $/anno).
- **Calcolo offline:** i dati stradali pesano molto (l'Italia intera circa 2,4 GB): si scaricano per zona
  (regioni Geofabrik in tutto il mondo), da pacchetti costruiti con GitHub Actions e pubblicati nelle
  Release, con mirror configurabile. La mappa (PMTiles) si può ritagliare per zona direttamente dalle
  build mondiali pubbliche di Protomaps, senza ospitare nulla.
- **Nostr:** non esiste un formato standard per i percorsi; va definito da noi (evento sostituibile).

## Prime tappe
1. Scheletro dell'app Kotlin Multiplatform (Android + iPhone) con schermata mappa.
2. Mappa OpenStreetMap offline di una zona, ovunque nel mondo.
3. Percorso tra due punti con profilo moto (verifica Valhalla su Android e iPhone).
4. Punti di passaggio trascinabili, preferenza curve / evita autostrade.
5. Esportazione e importazione GPX.
6. Condivisione con QR code e link.
7. Identità a chiave e pubblicazione / ricerca percorsi su Nostr.

Dall'analisi iniziale ([ANALISI.md](ANALISI.md)) sono arrivate altre tappe, tutte come issue:
verifica delle curve, seguire il giro in sella, zona privata, autonomia e distributori,
profilo veicolo (cilindrata), sterrato e fondo, soste e ritrovi, aggiornamento versionato
del giro, viaggio a tappe, chiusure stagionali dei passi.
