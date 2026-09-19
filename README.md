# TDoc

A native Android rich-text editor that reads, edits and saves **DOCX**, **ODT**
and **TXT** documents, and renders them faithfully on phone and print layouts.

Built with Kotlin + Jetpack Compose, Apache POI on the JVM for DOCX handling,
a hand-written DOM parser/writer for ODT, Room-backed recents, and Hilt for DI.

> Status: MVP. Core editing, parsing, saving and recents are implemented and
> covered by 160+ unit tests. See [CHANGELOG.md](CHANGELOG.md) for what shipped.

<div align="center">

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL%203.0-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green)]()
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-purple)]()

</div>

## Features

- Open documents from the launcher or the system document picker
  (`VIEW` / `EDIT` / `SEND` intents and `ACTION_OPEN_DOCUMENT`).
- Rich-text editing with a formatting toolbar: **bold / italic / underline /
  strikethrough**, heading levels, alignment, bullet lists, table and image
  blocks.
- Undo / redo — toolbar buttons plus `Ctrl+Z`, `Ctrl+Shift+Z`, `Ctrl+Y`.
- Two render modes: a paged **Print layout** and a fluid **Mobile layout**.
- Save in place, or "Save a copy…" via `ACTION_CREATE_DOCUMENT`.
- Recents list on the home screen (20 most recent, removable).
- Image downsampling on decode and a 7-day cache prune to keep memory in check.

## Formats

| Format | Read | Edit | Save-as | Notes |
| ------ | ---- | ---- | ------- | ----- |
| DOCX   | ✓    | ✓    | ✓       | Apache POI; spans, tables, images, shading/border colors |
| ODT    | ✓    | ✓    | ✓       | Home-grown DOM parser + deterministic writer (odfdom) |
| TXT    | ✓    | ✓    | ✓       | Streaming parse; UTF-8; flattened on save |
| Other/unknown | ✓ | ✓ | — | Falls back to plain text |

## Architecture

Clean Architecture split across the classic three layers under
`com.tosin.docprocessor`:

- **domain** — an immutable editor engine (`EditorDocument`, span ops, undo
  history, block transformers) with no Android dependencies.
- **data** — strategy-based format parsers/exporters for DOCX/ODT/TXT, the
  document + recent-files repositories, Room DAO, and exported Room schema
  under `app/schemas/`.
- **ui** — Compose screens (`HomeScreen`, `EditorScreen`) driven by `ViewModel`s
  exposing `Flow`/`StateFlow` state; Hilt wires the graph.

### Key decisions

Recorded in [DESIGN_DECISIONS.md](DESIGN_DECISIONS.md) — Room schema stays at
**v1** (exported) with no migration needed yet.

## Building & testing

Requires JDK 17 and Android SDK 37 (`compileSdk`); minimum SDK is 26.

```bash
# Debug APK
./gradlew :app:assembleDebug

# Unit tests (JVM + Robolectric)
./gradlew :app:testDebugUnitTest

# Lint
./gradlew :app:lintDebug

# Minified release APK (unsigned; R8 keep rules in app/proguard-rules.pro)
./gradlew :app:assembleRelease
```

HTML reports land in `app/build/reports/tests/`, `app/build/reports/lint-results*`
and `app/build/outputs/apk/`.

## CI

`.github/workflows/ci.yml` runs unit tests + lint on every push/PR, then an
`assembleRelease` job that uploads the unsigned APK as an artifact.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) if present, otherwise: fork, branch,
test (`./gradlew :app:testDebugUnitTest`), and open a PR. Feature requests and
bugs go in Issues.

## License

GPL-3.0 — see [LICENSE](LICENSE).