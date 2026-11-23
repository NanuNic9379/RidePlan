# Contribuire a RidePlan

- Ogni modifica parte da una issue, poi un branch e una pull request.
- Scrivi dal punto di vista di chi guida la moto: cosa cambia per lui in strada.
- Niente librerie che raccolgono dati o richiedono account a servizi esterni.
- Le dipendenze devono essere compatibili con la GPL-3.0.
- Testi dell'app in italiano e inglese.

Contribuendo accetti che il tuo codice sia distribuito con la licenza GPL-3.0 e il permesso
aggiuntivo per gli store descritto in [LICENSE-STORE.md](LICENSE-STORE.md).

## Traduzioni

L'app è pensata per più lingue. La lingua di base è l'inglese; oggi ci sono anche italiano, tedesco, francese e spagnolo.

- I testi stanno in `shared/src/commonMain/composeResources/values/strings.xml` (inglese) e in una cartella `values-xx` per ogni lingua (codice ISO 639-1, es. `values-pt`, oppure con la regione, es. `values-pt-rBR`). Sono condivisi fra Android e iOS.
- Nel codice niente frasi scritte a mano: solo `stringResource(Res.string.chiave)`. Gli errori del codice condiviso sono codici (`ImportError`, `UpdateError`) e il testo lo sceglie l'interfaccia.
- **Aggiungere una lingua:** copia `values/strings.xml` in `values-xx/strings.xml`, traduci i testi lasciando uguali i nomi delle chiavi e i segnaposto (`%1$s`, `%1$d`), e aggiungi `xx` in `androidApp/src/main/res/xml/locales_config.xml`. Per i plurali usa le categorie della lingua (`one`, `few`, `many`, `other`).
- Apostrofo tipografico (’) al posto di `'`, virgolette della lingua (“ ”, « », „ “) al posto di `"`.
- `./gradlew :shared:testAndroidHostTest` controlla che ogni lingua abbia tutte le chiavi e gli stessi segnaposto.
