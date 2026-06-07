# StringSmith – Android String Extractor

![Build](https://github.com/SeijinD/stringsmith/workflows/Build/badge.svg)

IntelliJ / Android Studio plugin that extracts hardcoded strings into Android `strings.xml` resources.

## Features

- Context-aware replacement
  - Inside `@Composable` → `stringResource(R.string.key)`
  - Inside `Activity` / `Fragment` / `View` → `getString(R.string.key)`
  - Other Kotlin code → `R.string.key`
  - XML layout attribute → `@string/key`
- Auto-import for Compose `stringResource`
- Duplicate key detection and value reuse
- Multi-locale placeholder generation across `values-*` folders
- Quick-fix intention bulb on hardcoded literals
- Settings panel for default key prefix, target module, locale handling

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

1. Select hardcoded string in editor.
2. Right-click → **Extract to strings.xml** (or `Ctrl+Alt+S`).
3. Enter resource key.
4. String added to `res/values/strings.xml`, selection replaced with context-appropriate reference.

## Compatibility

- IntelliJ IDEA 2024.2+
- Android Studio Koala 2024.2.1+
- Kotlin plugin K1 and K2 modes

## Development

```
./gradlew runIde            # launch sandbox IDE
./gradlew verifyPlugin      # plugin verifier against recommended IDEs
./gradlew buildPlugin       # produce distributable zip
```

## License

MIT — see [LICENSE](LICENSE).
