# Technology Stack

## Overview
Technology choices for Seanime TVapp — a standalone native Android TV anime app (AniList for account and library, on-device JS provider plugins for streams).

## Languages

### Kotlin (2.2.10)
- **Usage**: 100% of application code (plus a prebuilt JS asset, `sucrase.bundle.js`)
- **Key features used**: Coroutines and Flow, sealed classes for UI state, `kotlinx.serialization`, `@Composable`

## Frameworks

### UI — Jetpack Compose + `androidx.tv`
- **Compose BOM 2025.06.01** — all screens and components, plus Material 3 (`material3`, `material-icons-extended`) and `graphics-shapes` for Expressive shapes.
- **`androidx.tv:tv-foundation` + `tv-material` 1.0.0-alpha07** — TV focus and layout components. Many APIs are `@ExperimentalTvMaterial3Api` and need `@OptIn`.
- **Navigation Compose 2.8.9** — screen navigation and deep links.
- **Coil 2.7.0 (`coil-compose`)** — image loading, configured in `SeanimeApp`.

### Media
- **Media3 1.3.1** — `exoplayer`, `exoplayer-hls`, `ui`, `datasource-okhttp`.

### Networking and data
- **OkHttp 4.12.0** (+ logging interceptor) with **kotlinx.serialization 1.7.3** JSON — no Retrofit, Ktor, Gson or Moshi.
- **DataStore Preferences 1.1.1** — accounts, history and settings persistence.
- **Jsoup 1.17.2** — HTML parsing backing the JS DOM bridge.
- **quickjs-kt 1.0.0-alpha13** — embedded JavaScript engine for provider plugins.

### TV platform
- **`androidx.tvprovider` 1.1.0-alpha01** — Android TV home-screen preview channel.
- **`androidx.profileinstaller` 1.4.1** — baseline profile installation.

### Backend / API
No backend of its own. External services: AniList GraphQL, AniSkip, AniZip, YouTube (trailer embed), and whatever sites the installed provider plugins scrape.

### Testing
- **Unit**: JUnit 4.13.2, `kotlinx-coroutines-test` 1.8.1, OkHttp MockWebServer.
- **Instrumented UI**: `androidx.compose.ui:ui-test-junit4`, `androidx.test.ext:junit`.

## Build System

### Gradle with Kotlin DSL
- **AGP**: 9.2.1; plugins: `com.android.application`, Kotlin Compose, kotlinx-serialization, parcelize (all on Kotlin 2.2.10).
- **Version catalog**: `gradle/libs.versions.toml`, referenced through `libs.*`.
- **Configuration cache**: enabled; `FAIL_ON_PROJECT_REPOS` with repositories in `settings.gradle.kts`; foojay toolchain resolver.

### SDK levels
- **minSdk**: 32 · **targetSdk / compileSdk**: 36 · **Java**: 11 source/target.

### Release build
R8 optimization enabled with keep rules in `proguard-rules.pro`; signed with the debug key for sideloading.

## Infrastructure
Android APK sideloaded onto an Android TV device. No CI/CD. No containerization.

## Development Tools
- **Formatting**: official Kotlin code style (`kotlin.code.style=official`); no ktlint/detekt.
- **IDE**: Android Studio.

## Package Structure
- Application ID and root package: `com.malina.seanimetvport`
- Source layout: see `architecture.md`.

---
*Last Updated*: 2026-10-29 — synchronized with `app/build.gradle.kts` and `gradle/libs.versions.toml`.
