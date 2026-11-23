# Privacy

*English below.*

## In breve

RidePlan **non raccoglie dati**. Non ha account, pubblicità, statistiche d'uso o segnalazioni
automatiche di errori. I giri, le tappe e le impostazioni restano sul telefono.

L'app usa internet solo per le cose qui sotto, e in quei casi il server contattato vede, come per
ogni pagina web, l'indirizzo IP del telefono e l'ora della richiesta.

| Quando | Chi viene contattato | Cosa riceve |
|---|---|---|
| Mostri la mappa | OpenFreeMap (`tiles.openfreemap.org`) | La zona di mappa da disegnare |
| Importi un link di Google Maps | Google (`maps.app.goo.gl`, `google.com`) | Il link che hai incollato o condiviso |
| Scarichi i dati stradali di una zona | GitHub (Release `routing-data` di questo repository) | Il nome del file della zona |
| Controllo degli aggiornamenti (solo versione da GitHub) | GitHub (`api.github.com`) | Nessun dato oltre alla richiesta dell'ultima versione |

Il calcolo del percorso avviene **sul telefono**: le tappe non vengono inviate a nessun server.

## Permessi

Solo `INTERNET`. L'app non legge la posizione, i contatti o le foto. I file (GPX, KML) li apri tu
dalla finestra di sistema e l'app vede solo quelli.

## Condivisione

Quando condividi un giro (file GPX) decidi tu a chi mandarlo. Prima di condividere, l'app propone
di togliere il primo e l'ultimo km, perché partenza e arrivo sono spesso casa o il garage.

Quando arriverà la condivisione tramite Nostr: quello che pubblichi su Nostr è **pubblico** e
copiato su più server (relay), che non possiamo cancellare. L'app te lo dirà prima di pubblicare.

## GDPR

Non trattiamo dati personali: non c'è un titolare che li conservi. Per i servizi esterni elencati
sopra valgono le loro informative (OpenFreeMap, Google, GitHub). Per domande apri una issue o
scrivi tramite la scheda Security del repository.

---

## English

RidePlan **collects no data**: no accounts, ads, analytics or crash reporting. Rides, stops and
settings stay on the phone. Internet is used only to: load map tiles from OpenFreeMap; resolve a
Google Maps link you import; download the road data of an area from this repository's
`routing-data` Release on GitHub; and (GitHub build only) check GitHub for a newer version. Those
servers see the phone's IP address and the time, like any web request. Routes are computed **on the
phone**: stops are never sent to a server. The only permission is `INTERNET`. When you share a GPX
the app offers to remove the first and last km, since they often reveal your home. Anything
published on Nostr (when it lands) is public and replicated on relays outside our control.
