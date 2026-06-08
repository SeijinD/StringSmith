# StringSmith – Android String Extractor

![Build](https://github.com/SeijinD/stringsmith/workflows/Build/badge.svg)

IntelliJ / Android Studio plugin that extracts hardcoded strings into Android `strings.xml` resources.

## Features

- Context-aware replacement
  - Inside `@Composable` → `stringResource(R.string.key)`
  - Inside `Activity` / `Fragment` / `View` → `getString(R.string.key)`
  - Other Kotlin code → `R.string.key`
  - XML layout attribute → `@string/key`
- Auto-import for Compose `stringResource` (sorted with existing imports)
- Duplicate key detection and value reuse
- Multi-locale propagation across `values-*` folders
- Quick-fix intention on hardcoded literals
- Quick extract: skip dialog when target is unambiguous
- Batch extract: extract all strings in a file in one pass
- `@Preview` composables excluded by default (configurable)
- Settings panel for prefix, naming, replacement style, locale handling, exclusions

## Install

### From source

```
git clone https://github.com/SeijinD/stringsmith.git
cd stringsmith
./gradlew buildPlugin
```

Output: `build/distributions/stringsmith-<version>.zip`

Install via **Settings → Plugins → ⚙ → Install Plugin from Disk**.

### Run sandbox

```
./gradlew runIde                 # IntelliJ IDEA Ultimate
./gradlew runIdeCommunity        # IntelliJ IDEA Community
./gradlew runIdeAndroidStudio    # Android Studio
```

## Usage

### Single extract (with dialog)

1. Place caret inside a hardcoded string literal or XML attribute value.
2. `Ctrl+Alt+X` or right-click → **Extract to strings.xml**.
3. Pick key, module, locale rows in the dialog.
4. Entry written to `res/values/strings.xml`; literal replaced with context-appropriate reference.

### Quick extract (no dialog)

`Ctrl+Alt+Shift+X` — extracts immediately when the target is unambiguous (single module, no key collision, no locale variants, no existing key for the value). Falls back to the regular dialog otherwise.

### Batch extract (whole file)

`Ctrl+Alt+Shift+B` or **Refactor → Batch Extract Strings in File** — opens a table of all extractable strings in the current file. Edit per-row keys, toggle inclusion, pick locale propagation, write all in one undoable step.

### `@Preview` exclusion

Strings inside `@Preview` composables are skipped by default (typically dummy data). Toggle in **Settings → Tools → StringSmith → Compose Previews**.

## Shortcuts

| Action | Shortcut |
|---|---|
| Extract to strings.xml | `Ctrl+Alt+X` |
| Quick Extract (skip dialog when unambiguous) | `Ctrl+Alt+Shift+X` |
| Batch Extract Strings in File | `Ctrl+Alt+Shift+B` |

## Compatibility

- IntelliJ IDEA 2025.1+
- Android Studio (compatible IntelliJ 251+ platform)
- Kotlin plugin K1 and K2 modes

## Development

```
./gradlew runIde            # launch sandbox IDE
./gradlew verifyPlugin      # plugin verifier against recommended IDEs
./gradlew buildPlugin       # produce distributable zip
```

## License

MIT — see [LICENSE](LICENSE).
