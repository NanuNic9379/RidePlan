# Sicurezza

## Come segnalare

Se trovi una falla (per esempio un modo per falsificare un percorso, far installare un APK non
nostro o leggere la posizione di qualcuno), **non aprire una issue pubblica**: usa
"Report a vulnerability" nella scheda Security di questo repository.

- Risposta entro **7 giorni**.
- Correzione o piano di correzione entro **90 giorni**; poi la falla diventa pubblica con i
  ringraziamenti, se li vuoi.

## Versioni supportate

Solo l'ultima versione pubblicata nelle Release. Le correzioni non vengono portate indietro.

## Perimetro

Dentro: l'app Android e iOS, gli aggiornamenti dalle Release, l'import di GPX, KML e link,
i dati stradali (`routing-data`) e le pipeline di questo repository.
Fuori: i servizi esterni (OpenFreeMap, Google, GitHub, relay Nostr), i telefoni con root o
modificati, gli attacchi che richiedono il telefono sbloccato in mano.

## Verificare l'APK

Gli APK ufficiali sono firmati con questo certificato (CN=RidePlan, O=NanuNic9379):

```
SHA-256: B6:B2:A9:4D:4D:6F:A4:F9:D9:D9:8C:57:50:95:71:61:15:07:AB:BE:10:A1:FE:DA:F9:02:3A:54:99:B9:5C:40
```

Per controllare: `apksigner verify --print-certs RidePlan.apk`. Se l'impronta è diversa, non
installarlo. L'app si aggiorna da sola solo con APK firmati con lo stesso certificato.

## Mai condividere la chiave privata

Quando arriverà Nostr: la tua chiave privata (`nsec...`) non va **mai** incollata in una issue,
una chat o un sito. Nessuno del progetto te la chiederà mai.

---

*English:* report vulnerabilities privately via "Report a vulnerability" in the Security tab, not
in public issues. We reply within 7 days and fix or plan a fix within 90 days. Only the latest
Release is supported. Official APKs are signed with the certificate above (check with
`apksigner verify --print-certs`). Never paste your Nostr private key (`nsec`) anywhere.
