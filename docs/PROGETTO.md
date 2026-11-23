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
e senza account: il percorso resta firmato da chi l'ha creato e nessuno può alterarlo.

### Limiti noti
- **iPhone:** si compila solo su un Mac con Xcode (o con una compilazione automatica su macOS).
- **Calcolo offline:** i dati stradali di una regione pesano centinaia di MB; si scaricano per zona.

## Prime tappe
1. Scheletro dell'app Kotlin Multiplatform (Android + iPhone) con schermata mappa.
2. Mappa OpenStreetMap offline di una regione.
3. Percorso tra due punti con profilo moto (verifica Valhalla su Android e iPhone).
4. Punti di passaggio trascinabili, preferenza curve / evita autostrade.
5. Esportazione e importazione GPX.
6. Condivisione con QR code e link.
7. Identità a chiave e pubblicazione / ricerca percorsi su Nostr.
