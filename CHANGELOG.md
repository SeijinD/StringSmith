<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# stringsmith Changelog

## [Unreleased]

### Added
- **Duplicate String Resource.** Copy an existing string resource to a new key across `res/values/strings.xml` and every `values-*` locale file in one undoable step. Available as an action (`Ctrl+Alt+D`, editor popup and **Refactor** menu) and an Alt+Enter intention.
  - Works on any reference to the resource: `R.string.key`, `Res.string.key` (Compose Multiplatform), `@string/key`, or a `<string>` entry in `strings.xml`.
  - Propagates across all locale files — a locale that already has the source key keeps its own translation, otherwise the default value is copied. Locales that already contain the new key are left untouched.
  - Optionally redirects the reference under the caret to the new key (Compose Multiplatform imports are updated automatically).
  - Ships a description and Before/After preview in **Settings → Editor → Intentions** and the Alt+Enter `…` popup.

## [0.2.0] - 2026-06-15

### Added
- **Kotlin Multiplatform / Compose Multiplatform support.** StringSmith now detects Compose Multiplatform resource targets (`src/<sourceSet>/composeResources/values/strings.xml`) alongside Android `res/values/`, and generates the correct reference per resource system:
  - `@Composable` (CMP) → `stringResource(Res.string.key)` with imports `org.jetbrains.compose.resources.stringResource`, `<pkg>.generated.resources.Res`, and `<pkg>.generated.resources.<key>`.
  - Non-composable Kotlin (CMP) → bare `Res.string.key` (a `StringResource` object), mirroring Android's `R.string.key`.
  - Format args are propagated for CMP composables: `stringResource(Res.string.key, name)`.
  - Works across single extract, Quick Extract, Batch Extract, and the Alt+Enter intention. Locale variants under `composeResources/values-*/` are detected like Android's.
- The generated `Res` package is auto-detected (gradle `packageOfResClass` → existing `*.generated.resources.Res` imports → derived from the module package), with a manual override under **Settings → Tools → StringSmith → Kotlin Multiplatform → Res package override**.
- The extract dialog's module selector tags Compose Multiplatform targets with `[CMP]`.
- Batch dialog: the **Status** column is now color- and icon-coded per state (New / Reuse / Duplicate / Collision / Invalid) for at-a-glance triage of what blocks the write.
- Batch dialog: editing a row's key updates its status, the summary, and the OK button **live** on every keystroke (single click to edit), instead of only on commit (Enter / focus loss).
- Quick Extract shows an inline `Extracted → key` hint, so the no-dialog path is no longer silent about the key it chose.
- Settings → Key Generation: an editable **Sample** field renders the generated key live as you tune prefix / naming convention / max length.
- Extract and Batch dialogs show the locale variant count in the section header, e.g. `Locales (3)`.

### Changed
- Locale variant lists in both the Extract and Batch dialogs are height-capped with a scroll pane, so projects with many locales no longer grow the dialog off-screen. The "values:" hint and the copy/select links stay outside the scroll.
- The Extract dialog now rebuilds its locale rows when you switch the target module (previously it kept the original module's variants), and selects the suggested key on open for quick retyping.

### Fixed
- Unused string resource inspection now recognizes Compose Multiplatform `Res.string.key` references (and widened the reference look-behind to fit the longer `Res.string.` prefix). Previously every entry in a `composeResources` `strings.xml` was reported as unused because only `R.string.`/`@string/` were searched.
- The Settings key preview reused a stale copy of the key-generation logic and could show a different — even invalid — key than the actual extraction (e.g. digit-leading or trailing-underscore values). It now delegates to the real generator, so preview and extraction can never diverge.
- Compose Multiplatform `Res` package resolution in multi-module projects: a file whose package matched no existing `*.generated.resources` import previously borrowed an arbitrary module's package. It now derives the package from the file's own module before falling back to any scanned import.

### Internal
- Refactor: shared `prepare()` in the extract runner (the quick path no longer recomputes detect/validate/resolve through the full flow); `BaseExtractAction` and `BaseExtractIntention` base classes; shared `ModuleRootUtil.findModuleRoot` for the gradle/manifest module-root walk (previously duplicated three times); shared `LocaleUi` for the dialog locale header and capped scroller.
- Removed dead code and folded a duplicate `Regex` build in settings regex validation.
- Added `ModuleResolverTest` and `ModuleRootUtilTest`, and expanded the CMP resolver tests; suite is now ~185 tests.

## [0.1.2] - 2026-06-15

### Fixed
- Key suggestion no longer produces an invalid resource name when the string starts with a digit. Values like `"123 items"` now suggest `key_123_items` (or `app_123_items` with a prefix) instead of `123_items`, which Android rejects and which blocked Quick Extract and disabled the dialog's OK button. `camelCase` gets the same `key`-prefix treatment (`key123Items`).
- Truncating a suggested key to the max length no longer leaves a trailing underscore (e.g. `this_is_a_` → `this_is_a`).
- Hardcoded-string inspection and XML extraction are now restricted to Android resource XML under `res/<type>/` (layout, menu, navigation, xml, …). Previously any `XmlAttributeValue` in any XML file was flagged and offered an `@string/` replacement — including `name="…"` attributes inside `strings.xml` itself and unrelated XML (build/run configs), where the replacement would be invalid. `res/values*` is excluded.

### Internal
- Bumped Kotlin JVM `2.1.20` → `2.4.0` and the Gradle wrapper `9.5.0` → `9.5.1`.
- README: added JetBrains Marketplace badges (plugin ID 32198) and an install button.
- Removed redundant null-safe calls on the non-null reuse checkbox in the extract dialog.

## [0.1.1] - 2026-06-11

### Added
- Composable context detection inside entry-point lambdas: strings inside `setContent`, `composable`, `navigation`, `dialog`, `bottomSheet`, and `composed` trailing lambdas now classify as `COMPOSABLE` (use `stringResource`) even when the enclosing function is not annotated `@Composable`. Walk-up traverses nested composable layout lambdas (e.g. `setContent { Column { … } }`) while named-argument lambdas (`onClick = { … }`) correctly fall through to class-based classification.
- Settings → Tools → StringSmith → **Custom Composable Wrappers**: project-specific wrapper function names (e.g. `screenViewComposable`) whose trailing lambda is a `@Composable` scope; strings inside them use `stringResource`.

### Fixed
- Extract dialog allowed saving a key that already existed when the value coincidentally matched an existing entry (the suggested key equalled the reuse key). The duplicate-key check now always blocks an existing key unless `Reuse existing key` is selected.
- Batch dialog now flags two rows that share the same key but hold different values as `Collision` (blocking) instead of silently writing only the first value and pointing both call sites at it. Row statuses recompute across all rows on key/include edits while preserving manual key edits.
- Hardened the extract dialog reuse path against a null existing key.

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

[Unreleased]: https://github.com/SeijinD/stringsmith/compare/0.2.0...HEAD
[0.2.0]: https://github.com/SeijinD/stringsmith/compare/0.1.2...0.2.0
[0.1.2]: https://github.com/SeijinD/stringsmith/compare/0.1.1...0.1.2
[0.1.1]: https://github.com/SeijinD/stringsmith/compare/0.1.0...0.1.1
[0.1.0]: https://github.com/SeijinD/stringsmith/commits/0.1.0
