# Contributing to RidePlan

- Every change starts from an issue, then a branch and a pull request.
- Write from the point of view of the person riding the bike: what changes for them on the road.
- No libraries that collect data or require accounts with external services.
- Dependencies must be compatible with GPL-3.0.
- Code, comments and documentation in English; app texts in English and Italian.

By contributing you agree that your code is distributed under the GPL-3.0 license and the
additional store permission described in [LICENSE-STORE.md](LICENSE-STORE.md).

## Translations

The app is designed for several languages. The base language is English; today there are also Italian, German, French and Spanish.

- Texts live in `shared/src/commonMain/composeResources/values/strings.xml` (English) and in one `values-xx` folder per language (ISO 639-1 code, e.g. `values-pt`, or with the region, e.g. `values-pt-rBR`). They are shared between Android and iOS.
- No hand-written sentences in the code: only `stringResource(Res.string.key)`. Errors from the shared code are codes (`ImportError`, `UpdateError`) and the UI picks the text.
- **Adding a language:** copy `values/strings.xml` to `values-xx/strings.xml`, translate the texts keeping the key names and placeholders (`%1$s`, `%1$d`) unchanged, and add `xx` to `androidApp/src/main/res/xml/locales_config.xml`. For plurals use the language's categories (`one`, `few`, `many`, `other`).
- Typographic apostrophe (’) instead of `'`, the language's quotation marks (“ ”, « », „ “) instead of `"`.
- `./gradlew :shared:testAndroidHostTest` checks that every language has all the keys and the same placeholders.
