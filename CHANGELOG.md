<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# stringsmith Changelog

## [Unreleased]

## [0.1.0] - 2026-06-09

### Added
- Initial scaffold created from [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template).
- Package `com.seijind.stringsmith`.
- Plugin name: **StringSmith - Android Strings Toolkit**.
- Context-aware extract action (`Ctrl+Alt+X`) with single dialog covering key, target module, replacement preview, locale rows.
- Quick extract action (`Ctrl+Alt+Shift+X`) that skips the dialog when target is unambiguous (single module, no key collision, no locale variants, no existing key for the value); falls back to the regular dialog otherwise.
- Batch extract action (`Ctrl+Alt+Shift+B`) — table-based dialog covering all extractable strings in the current file with per-row key editing, inclusion toggles, status badges (`New`/`Reuse`/`Duplicate`/`Collision`/`Invalid`), shared locale propagation, single undoable write. Batch dialog targets the file's own module (read-only label).
- IntentionAction with yellow `AllIcons.Actions.IntentionBulb`, `HIGH` priority, family `StringSmith`. Rich description popup with context-aware replacement table and Before/After example.
- Kotlin PSI detection with caret tolerance for offsets before / after the literal.
- Context classification: `COMPOSABLE`, `ANDROID_CLASS` (superclass match + name suffix heuristic for `Activity`/`Fragment`/`Service`/`Receiver`/`Provider`/`Worker`), `KOTLIN_GENERIC`, `XML_LAYOUT`.
- Auto-import for `androidx.compose.ui.res.stringResource` inside `@Composable` and `<package>.R` for non-XML targets, sorted into the correct alphabetical position via `LanguageImportStatements`.
- Multi-flavor R package resolution via file-package prefix matching and source-layout derivation.
- Duplicate value detection with `Reuse existing key` checkbox (bold blue) shown only when the same value already exists in the selected module's `strings.xml`.
- Multi-locale propagation: per-row include/value editing for every `values-*/strings.xml` sibling of the selected default.
- Module selector that prefers the nearest `strings.xml` when the edited file lives inside that module, otherwise falls back to remembered last selection. Re-detects reuse candidates when the module is changed inside the dialog (checkbox always resets to unchecked for consistency).
- `@Preview` composable exclusion: strings inside `@Preview`-annotated functions are skipped by validator at all entry points; toggle in Settings → Tools → StringSmith → Compose Previews.
- Kotlin template expression extraction: `"Hello $name"` is rewritten to `<string>Hello %1$s</string>` and the call passes the original expression as a format argument (e.g. `stringResource(R.string.hello_s, name)` / `getString(R.string.hello_s, name)`). Toggle in Settings → Tools → StringSmith → Format Strings. Literal `%` is escaped to `%%`.
- Hardcoded string inspection (opt-in, off by default). Highlights extractable literals with a `WARNING`-level squiggle and exposes an `Extract to strings.xml` quick-fix (via `IntentionWrapper`) sharing the intention's description.
- Duplicate value inspection (default on, XML-scoped). Reports two or more `<string>` entries in the same `strings.xml` with identical trimmed text under different keys.
- Unused string resource inspection (default on, XML-scoped). Text-based search for `R.string.key` / `@string/key` across the project scope; flags keys with no references using `LIKE_UNUSED_SYMBOL` highlight.
- Settings panel groups: Key Generation, Replacement Style, Locale Files, Target Module, Compose Previews, Format Strings, Inspection, strings.xml Behavior, Exclusion Patterns. Live key preview, regex validation via `DocumentListener`, tooltips on every option.
- `PersistentStateComponent` with: `keyPrefix`, `namingConvention`, `maxKeyLength`, `minStringLength`, `sortAfterExtract`, `openStringsXmlAfterExtract`, `addSourceComment`, `trimWhitespace`, `excludePatterns`, `activityStyle`, `composeStyle`, `autoIncludeLocales`, `excludePreviewComposables`, `inspectionEnabled`, `detectFormatArgs`, `duplicateValueInspectionEnabled`, `unusedStringInspectionEnabled`, `lastTargetModulePath`, `rememberLastModule`.
- Single `autoIncludeLocales` boolean controls the initial checkbox state of locale rows; the extract dialog is the source of truth for per-locale decisions.
- DynamicBundle-based i18n (`messages/StringSmithBundle.properties`) wired through validator, dialogs, configurable, batch UI, and inspection display names via `bundle`/`key` attributes in `plugin.xml`.
- Plugin icons (light + dark SVG).
- Unit tests (133 across 10 suites): pure JUnit4 — `StringsXmlTextTest`, `KeyGeneratorTest`, `StringSmithSettingsTest`, `AndroidModuleTextTest`; `BasePlatformTestCase` PSI fixtures — `ExtractContextTest` (`@Preview` detection, format-arg extraction in Composable / Activity contexts), `BatchScannerTest`, `ExtractValidatorTest`, `ReplacementTest`, `DuplicateStringValueInspectionTest`, `UnusedStringResourceInspectionTest`.
- Action registrations: editor popup, Refactor menu, keyboard shortcuts (`Ctrl+Alt+X`, `Ctrl+Alt+Shift+X`, `Ctrl+Alt+Shift+B`).
- K2 compatibility (`<supportsKotlinPluginMode supportsK2="true"/>`).
- `parseEntries` regex tolerates extra `<string>` attributes such as `translatable="false"`.
- `ensureImport` uses a text-based `KtPsiFactory.createFile("import …")` instead of the deprecated `createImportDirective(ImportPath)`.

[Unreleased]: https://github.com/SeijinD/stringsmith/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/SeijinD/stringsmith/commits/v0.1.0
