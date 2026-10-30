# System Architecture

## Overview
Seanime TVapp is a native Android TV application (Kotlin + Jetpack Compose + `androidx.tv`). It is a standalone client with no backend of its own and no dependency on a Seanime server:

- **Account, library and metadata** come from the AniList GraphQL API (OAuth, multiple accounts).
- **Episodes and streams** come from JavaScript provider plugins executed on-device by QuickJS; TypeScript plugins are transpiled on-device with a bundled Sucrase build.
- **Playback** uses Media3 ExoPlayer; intro/outro skip times come from AniSkip and episode thumbnails from AniZip.
- **TV integration**: a Continue Watching preview channel is published to the Android TV home screen.

## Architecture Pattern
Single-module Android app (`:app`), MVVM, organized by feature, with a shared data layer. UI state is modeled as a sealed `<Feature>UiState` held in a ViewModel `StateFlow` and consumed with `collectAsState()`. Dependencies are wired by a hand-written lazy container (`AppContainer`), not a DI framework.

## Tech Stack Summary
See `tech-stack.md` for versions. Kotlin, Jetpack Compose, `androidx.tv`, Navigation Compose, Media3, OkHttp + kotlinx.serialization, Coil, DataStore, QuickJS (`quickjs-kt`), Jsoup.

## System Structure

Root package `com.malina.seanimetvport` (`app/src/main/java/com/malina/seanimetvport/`).

### Entry points
- `SeanimeApp.kt` — `Application`; owns the lazy `AppContainer` and configures a memory-bounded Coil `ImageLoader` for low-RAM TV boxes.
- `MainActivity.kt` — hosts the Compose content and the `NavHost`, shows the splash overlay, resolves the start destination, and handles the `seanimetv://auth` (OAuth redirect) and `seanimetv://series` (TV channel) deep links.

### Navigation routes
`login` · `account_picker` · `home` · `series/{anilistId}?resumeEpisode=` · `player?server=&title=&epTitle=&episode=&mediaId=` · `settings`.
`home` hosts the library, AniList browse, search, airing schedule and extensions panels internally rather than as separate routes.

### Dependency container (`di/`)
`AppContainer` exposes process-lifetime singletons, all `by lazy`: two OkHttp clients (`streamingHttpClient` tolerates incomplete CDN TLS chains and must never carry the AniList token; `authHttpClient` is strict in release), the persistence stores, `aniListRepository`, `aniZipClient`, `aniSkipClient`, `pluginRepository`, `extensionRepository`, `tvChannelPublisher` and `isLowRamDevice`. `ViewModelFactory` builds ViewModels from it.

### Features (`feature/`)
| Package | Responsibility |
|---|---|
| `home/` | Home rows, library, AniList browse/search, airing schedule, card context menu |
| `series/` | Series detail: hero, watch panel (provider/server/sub-dub, episode grid), AniList entry editor, trailer overlay |
| `player/` | Playback screen, controls, quality/subtitle/audio sheets, AniSkip |
| `extensions/` | Installed provider/extension management |
| `pluginstore/` | Plugin marketplace browsing and install |
| `settings/` | Account, plugins, subtitle and playback preferences |
| `login/`, `account/` | AniList OAuth login and account picker |

### Shared UI (`components/`, `ui/theme/`)
Reusable focus-aware components (`FocusSurface`, `AnimeCard`, `SideNavigation`, `ActionButton`, `AmbientBackdrop`, `MediaCardRow`, motion helpers). The app is dark-theme-only; the palette is the `Seanime*` constants in `Color.kt`, with Material 3 Expressive shapes and motion in `ExpressiveShapes.kt` / `ExpressiveMotion.kt`.

### Data layer (`data/`)
| Package | Contents |
|---|---|
| `anilist/` | `AniListApiClient`, `AniListQueries`, `AccountStore` (multi-account, migrates the legacy `TokenDataStore`), `model/`, `repository/` (`AniListRepository`, `AuthRepository`) |
| `plugin/` | `PluginEngine` / `QuickJSEngine`, JS bridges (`JsHttpBridge`, `JsDomBridge`, `JsStoreBridge`), `PluginRepository`, `TsTranspiler`, `model/`, `store/` (marketplace) |
| `extension/` | `ExtensionRepository` and models |
| `metadata/` | `AniSkipClient`, `AniZipClient` |
| `net/` | `StreamingHttpClient`, user-facing error messages |
| `history/`, `settings/`, `player/`, `search/` | `WatchHistoryStore`, `AnimeSettingsStore`, `SubtitleStylePrefs`, `SearchHistoryStore` |
| `tvchannel/` | `TvChannelPublisher` (Android TV preview channel) |
| `model/` | Shared models such as `AnimeEntry` |

Repositories are interfaces whose suspend functions return `Result<T>`, with `*Impl` classes doing error mapping. Domain packages (`data/<domain>/`) replace a single `data/api` + `data/repository` split.

## TV Navigation — Key Principle
All screens must be fully navigable with a D-pad. Touch is not an input. Use `androidx.tv` components or custom focusables with a visible focus state (see `standards/frontend/tv-navigation.md`). `@OptIn(ExperimentalTvMaterial3Api::class)` is required wherever `androidx.tv.material3` is used.

## Data Flow
```
D-pad → Focus engine → Composable (emits events)
                          ↓
                 ViewModel (StateFlow<UiState>)
                          ↓
            Repository interface (suspend → Result<T>)
        ┌─────────────────┼─────────────────────┐
   AniList GraphQL   Plugin engine (QuickJS)   AniZip / AniSkip
   (OkHttp, OAuth)   → provider site → stream  (OkHttp, anonymous)
                          ↓
              ExoPlayer (Media3) → PlayerScreen
```

## External Integrations
- **AniList** — GraphQL; OAuth through the `seanimetv://auth` redirect; tokens encrypted with AES-256-GCM and an Android Keystore key in DataStore via `AccountStore`. The client secret is injected as `BuildConfig.ANILIST_CLIENT_SECRET` from `local.properties`; the client id is read from `BuildConfig.ANILIST_CLIENT_ID`, optionally configured in `local.properties`.
- **Provider plugins** — JS/TS installed from a URL or the plugin store, run in QuickJS with HTTP, DOM (Jsoup-backed) and key-value store bridges.
- **AniSkip / AniZip** — intro/outro times and episode thumbnails.
- **YouTube (nocookie embed)** — trailers, shown in a WebView overlay.
- **Android TV home screen** — preview channel through `androidx.tvprovider` (`WRITE_EPG_DATA`).

## Configuration
- Secrets: `local.properties` (git-ignored) → `BuildConfig`.
- Account credentials: encrypted DataStore values; plaintext entries migrate on first access. Authentication stores are excluded from cloud backup and device transfer because their Keystore key is local to the installation.
- Variants: debug and release only. Release has R8 enabled with keep rules in `app/proguard-rules.pro` and is signed with the debug key for sideloading.

## Deployment
- **Build**: `./gradlew assembleRelease` (use the release APK for performance testing).
- **Distribution**: sideloaded over ADB onto the target Android TV device (tested on Chromecast with Google TV HD).
- **Package**: `com.malina.seanimetvport`.

---
*Updated: 2026-10-29 — rewritten to match the implemented code (AniList + QuickJS plugins). The earlier version described a Seanime-server client.*
