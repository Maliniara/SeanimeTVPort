# Project structure

Seanime TV Port is a single-module Kotlin/Jetpack Compose Android TV application. Package: `com.malina.seanimetvport`.

| Path | Purpose |
|---|---|
| `app/build.gradle.kts` | Android application configuration, version, build variants, local OAuth configuration |
| `gradle/libs.versions.toml` | Dependency versions and plugin aliases |
| `app/src/main/java/com/malina/seanimetvport/MainActivity.kt` | Compose host, navigation, deep links |
| `app/src/main/java/com/malina/seanimetvport/di/` | Shared application dependencies and ViewModel factories |
| `app/src/main/java/com/malina/seanimetvport/feature/` | Home, series, player, extensions, settings, login and accounts |
| `app/src/main/java/com/malina/seanimetvport/components/` | Reusable TV UI and focus behavior |
| `app/src/main/java/com/malina/seanimetvport/ui/theme/` | Colors, typography, shapes and motion |
| `app/src/main/java/com/malina/seanimetvport/data/` | AniList, encrypted account storage, plugins, metadata, settings and TV integration |
| `app/src/main/assets/` | TypeScript transpiler and bundled license notices |
| `app/src/main/res/xml/` | Account-store backup and device-transfer exclusions |
| `app/src/test/` | JVM tests and fake repositories |
| `app/src/androidTest/` | Device/emulator UI tests |
| `docs/` | Maintained architecture, project context and coding standards |
| `AGENTS.md`, `CLAUDE.md` | Shared Codex/Claude instructions and entry points |

Data flow: screen → ViewModel → repository/client/store. Dependencies are shared through `AppContainer`. AniList provides account and library data; QuickJS plugins provide episodes and streams; Media3 handles playback.

See [README.md](README.md) for build instructions, tested hardware, project provenance and limitations. `local.properties`, build outputs, signing material and local AI settings are intentionally ignored.
