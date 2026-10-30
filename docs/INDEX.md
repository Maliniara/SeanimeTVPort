# Documentation Index

**IMPORTANT**: Read this file at the beginning of any development task to understand available documentation and standards.

---

## Project Documentation

Located in `docs/project/`

### Vision (`project/vision.md`)
Seanime TVapp is a standalone native Android TV anime app: AniList for account/library, on-device JS provider plugins for streams, no server required. Covers the problem (anime tooling is built for browsers and phones, not the couch), target user (solo anime enthusiast), key features (AniList library and discovery, series detail, plugin-based streaming, Media3 playback, D-pad navigation, TV home-screen channel), success criteria (including smooth playback on low-end TV boxes), and differentiators (native Android TV, standalone, low-RAM tuning).

### Roadmap (`project/roadmap.md`)
Three-phase plan with status as of 2026-10-29. Phase 1 MVP (done): bootstrap, AniList + plugin data sources, home, library, series detail, playback, D-pad navigation. Phase 2 (mostly done): AniList progress sync, search, settings, playback controls, plugin store, provider auto-fallback; error-handling audit open. Phase 3: metadata, continue watching, airing schedule, trailers done; filters/sorting unverified. Open items: release signing and client-secret handling. Future: Fire TV, downloads. Includes effort sizing (S/M/L scale).

### Tech Stack (`project/tech-stack.md`)
Kotlin 2.2.10, Jetpack Compose (BOM 2025.06.01), `androidx.tv:tv-foundation` + `tv-material` (1.0.0-alpha07), Navigation Compose, Media3 ExoPlayer, OkHttp + kotlinx.serialization, Coil, DataStore, quickjs-kt, Jsoup, tvprovider. Gradle KTS with centralized version catalog (`libs.versions.toml`), AGP 9.2.1, minSdk 32, compileSdk 36, Java 11 bytecode compatibility. All current dependencies with versions listed.

### Architecture (`project/architecture.md`)
Single-module Android app organized by feature (`feature/home`, `series`, `player`, `extensions`, `pluginstore`, `settings`, `login`, `account`), each owning screens and ViewModels. Data layer grouped by domain (`data/anilist`, `data/plugin`, `data/extension`, `data/metadata`, `data/net`, stores) with a hand-written lazy `AppContainer` for DI. UI state flows via `StateFlow` / `collectAsState()`. Includes navigation routes, data flow diagram, external integrations (AniList, QuickJS plugins, AniSkip/AniZip, YouTube trailers, TV channel), and deployment (ADB sideload of the release APK).

---

### Account-data review (`project/security-audit.md`)
Documents the local secret scan, encrypted token storage and migration, backup exclusions, OAuth client-secret limitations, and logging decisions.

---

## Technical Standards

### Global Standards

Located in `docs/standards/global/`

#### Coding Style (`standards/global/coding-style.md`)
Naming consistency, automatic formatting enforcement, descriptive names, focused single-purpose functions, uniform indentation, no dead code, no backward-compatibility code paths unless required, DRY principle, explicit single-class imports only (no wildcard `import x.y.*` anywhere, including large Compose layout import blocks).

#### Commenting (`standards/global/commenting.md`)
Self-documenting code through structure and naming, comments only for non-obvious logic, no changelog-style change comments in source.

#### Development Conventions (`standards/global/conventions.md`)
Predictable file/directory structure, up-to-date documentation, clean version control (clear commits, feature branches), minimal dependencies, consistent code review process, defined test coverage requirements, feature flags for incomplete features, changelog maintenance, no speculative code. Secrets are never committed — they are read from the git-ignored `local.properties` at build time and injected as `BuildConfig` fields with empty-string defaults. The app requests `INTERNET` and `WRITE_EPG_DATA` (TV home-screen channel only); any new permission must be justified by a concrete feature.

#### Error Handling (`standards/global/error-handling.md`)
Helpful user-facing messages without internal detail leakage, fail-fast input validation, typed exceptions, centralized error boundaries, graceful degradation on partial failures, exponential backoff for transient errors, resource cleanup in finally blocks. Fallible data-layer suspend functions return `kotlin.Result<T>` instead of throwing: producers wrap work in `runCatching` (always rethrowing `CancellationException`), consumers unwrap with `fold`/`onSuccess`/`onFailure`/`getOrElse` and map failures to the `Error` UiState variant with a user-facing message.

#### Minimal Implementation (`standards/global/minimal-implementation.md`)
Build only what is called, every method must have a caller or serve clear readability, remove exploration artifacts, no future stubs or placeholder methods, no speculative abstractions (factories/strategies/adapters) without immediate need, verify no dead code before committing.

#### Validation (`standards/global/validation.md`)
Server-side validation always required, client-side for UX feedback only, validate early and reject invalid data before processing, specific field-level error messages, allowlists over blocklists, type/format/range checks, input sanitization against injection attacks, business rule validation at the appropriate layer, consistent enforcement across all entry points.

---

### Android Standards

Located in `docs/standards/android/`

#### Kotlin Language (`standards/android/kotlin.md`)
Kotlin only (no Java source files in `app/src/`), Kotlin Coroutines for all async (`suspend` functions; no callbacks, AsyncTask, or RxJava). UI state modeled as a `sealed class <Feature>UiState` (`Loading`/`Success`/`Error` variants) co-located at the top of its ViewModel file, one per screen. ViewModel state held in a private `MutableStateFlow` (leading-underscore name) exposed read-only via `asStateFlow()`; composables consume it only via `collectAsState()` — never expose a `MutableStateFlow` publicly. Async work starts from `viewModelScope.launch`; `withContext(Dispatchers.IO)` is applied inside data-layer clients only (never in ViewModels or composables), with derived reactive state via `stateIn(WhileSubscribed(5_000))`. Formatting follows the official JetBrains Kotlin code style (`kotlin.code.style=official` in `gradle.properties` — the sole formatting configuration; no ktlint/detekt).

#### Build Configuration (`standards/android/build.md`)
Gradle Kotlin DSL only (`.gradle.kts` extension, no Groovy files), all dependency versions in `gradle/libs.versions.toml` via type-safe `libs.*` accessors (no hardcoded version strings in build files), repositories declared only in `settings.gradle.kts` (`FAIL_ON_PROJECT_REPOS` enforced), JDK 21 for Gradle builds, AGP 9.2.1. Compose versions managed by the Compose BOM (`platform(libs.androidx.compose.bom)`) — no version attributes on individual `androidx.compose.*` entries. Four required plugins: `com.android.application`, Kotlin Compose plugin (mandatory for Kotlin 2.x), kotlinx-serialization, and parcelize — all tracking a single `kotlin` version ref. Canonical library choices (do not substitute): OkHttp + kotlinx.serialization JSON for networking (no Retrofit/Ktor/Gson/Moshi), Coil Compose for images, Media3 ExoPlayer for video, Navigation Compose, DataStore Preferences, `androidx.tv` for TV UI, Jsoup, quickjs-kt. Gradle configuration cache enabled project-wide — custom tasks must stay compatible. Provider plugins are installed at runtime and are not built by Gradle; TypeScript plugins are transpiled on-device with the vendored `sucrase.bundle.js` asset, which is never hand-edited.

#### SDK and API Levels (`standards/android/sdk.md`)
minSdk 32 (Android 12L, supporting Android TV hardware from 12L onward); targetSdk/compileSdk 36 (Android 16). APIs introduced after API 32 require a runtime version check or `@RequiresApi` annotation — all Jetpack libraries in use are API 32+ compatible so no guards are needed for library calls. Java 11 bytecode target (`sourceCompatibility` + `targetCompatibility = VERSION_11`). Release builds have R8 optimization enabled with keep rules in `proguard-rules.pro` (QuickJS, kotlinx.serialization, OkHttp), signed with the debug key for sideloading only; performance testing uses the release APK. Current namespace/applicationId: `com.malina.seanimetvport`.

---

### Frontend Standards

Located in `docs/standards/frontend/`

#### Accessibility (`standards/frontend/accessibility.md`)
Semantic elements, keyboard/D-pad navigation with visible focus indicators, 4.5:1 color contrast minimum, alt text and form labels, screen reader verification, ARIA for complex components, proper heading hierarchy, focus management in dynamic content and SPAs.

#### Components (`standards/frontend/components.md`)
Single responsibility per component, reusability via configurable props across contexts, composability over monolithic components, explicit documented props with sensible defaults, encapsulation of implementation details, consistent naming, local state kept close to usage (lift only when needed), minimal props (split component if growing large), documented usage examples. Composables expose events as `on<Event>: (...) -> Unit` parameters, typically defaulted to no-op lambdas so callers wire only what they need; non-trivial call sites pass arguments by name.

#### Compose and Android TV UI (`standards/frontend/compose-tv.md`)
All UI in Jetpack Compose (no XML layouts). Use `androidx.tv` Material 3 components (`tv-foundation`, `tv-material`) over standard Material 3 where TV equivalents exist. `@OptIn(ExperimentalTvMaterial3Api::class)` required on every function using TV Material 3 APIs (all alpha07 APIs are experimental). All Compose content wrapped in `SeanimeTVTheme`. `modifier: Modifier = Modifier` as last non-content parameter on composables. `@Preview` on dedicated `*Preview` composables only (never on the real composable). Named parameters for non-trivial composable arguments. The app is dark-theme-only: the palette is defined as top-level semantic `Seanime<Role>` constants in `ui/theme/Color.kt` (e.g. `SeanimeBackground`, `SeanimePrimary`, `SeanimeOnSurfaceMuted`), which components may import directly rather than routing through `MaterialTheme.colorScheme`; new colors must follow this naming. Theme files in `ui.theme` subpackage.

#### TV Navigation (`standards/frontend/tv-navigation.md`)
Full D-pad navigation required on all screens (up/down/left/right + select/back) — touch is not the primary input. No touch-only interactions (long-press, swipe, pinch-to-zoom). Manifest declares `android.hardware.touchscreen` and `android.software.leanback` as `required="false"`, a TV banner (`android:banner="@drawable/tv_banner"`), and both `LAUNCHER` and `LEANBACK_LAUNCHER` intent categories. All focusable elements must show a visible focus state; custom focusables use a remembered `MutableInteractionSource` with `collectIsFocusedAsState()` to drive animated scale (~1.07–1.08, 120–150ms tween) and/or a 2.dp `SeanimePrimary` border, wired via `.clickable(interactionSource, indication = null)` + `.focusable(interactionSource)`. `Theme.AppCompat.DayNight.NoActionBar` theme — no action bar or XML menus; all navigation chrome implemented in Compose with `androidx.tv` layout components. `MainActivity` handles the `seanimetv://auth` deep link for the AniList OAuth redirect flow.

---

### Architecture Standards

Located in `docs/standards/architecture/`

#### MVVM (`standards/architecture/mvvm.md`)
Strict MVVM: composables display state and emit events only (no business logic), ViewModels hold `StateFlow<UiState>` and call repositories, models are data classes in the owning domain package (`data/<domain>/model/`). Feature-sliced directory structure — each feature owns its screen and ViewModel, no cross-feature imports except via shared `data/` or `components/`. Single Gradle module (`:app` only) — no multi-module split without explicit architectural planning.

#### Data Layer (`standards/architecture/data-layer.md`)
All external communication (AniList, plugins, AniSkip/AniZip, DataStore) goes through the shared data layer — direct API calls from composables or ViewModels are not allowed. The layer is grouped by domain (`data/anilist/`, `data/plugin/`, ...), each package owning its client, models and repository interface plus implementation that handles error mapping. Data access is a `<Name>Repository` interface whose methods are all suspend functions returning `Result<T>`, implemented by `<Name>RepositoryImpl` (or a purpose-named class); interface default methods return `Result.failure(UnsupportedOperationException())` so test fakes only implement what they need. API response models are `@Serializable` data classes grouped by domain, with nullable `= null` defaults or defaulted collections for optional fields, parsed via `Json { ignoreUnknownKeys = true }`. Two OkHttp clients: `streamingHttpClient` (lenient TLS, never carries the AniList token) and `authHttpClient` (strict TLS in release).

---

### Backend Standards

Located in `docs/standards/backend/`

*Note: This project has no backend of its own — it consumes the AniList API and provider plugins. These are general backend conventions from the project baseline.*

#### API Design (`standards/backend/api.md`)
RESTful resource-based URLs, consistent lowercase hyphenated/underscored naming, API versioning (URL path or headers), plural nouns for resources, URL nesting limited to 2-3 levels, query parameters for filtering/sorting/pagination, proper HTTP status codes (200/201/400/404/500), rate limit headers in responses.

#### Database Migrations (`standards/backend/migrations.md`)
Reversible migrations with rollback methods, single logical change per migration, zero-downtime awareness, schema changes separate from data migrations, careful indexing on large tables (concurrent options when available), descriptive migration names, migrations committed to version control and never modified after deployment.

#### Models (`standards/backend/models.md`)
Singular model names / plural table names, created/updated timestamps, database-level constraints (NOT NULL, UNIQUE, foreign keys), appropriate data types sized to purpose, indexed foreign keys and frequently queried fields, validation at both model and database layers, clear relationship definitions with cascade behaviors, balanced normalization vs. query performance.

#### Database Queries (`standards/backend/queries.md`)
Parameterized queries always (never interpolate user input into SQL), avoid N+1 via eager loading or joins, select only needed columns (no SELECT *), index columns used in WHERE/JOIN/ORDER BY, wrap related operations in transactions, query timeouts to prevent runaway queries, cache results of expensive or frequent queries.

---

### Testing Standards

Located in `docs/standards/testing/`

#### Test Writing (`standards/testing/test-writing.md`)
Test behavior not implementation (allows safe refactoring), mock external dependencies, fast unit test execution (milliseconds), risk-based prioritization, balance coverage with velocity, critical path coverage for core workflows. Android-specific: test names use camelCase segments joined by underscores (`subject_condition_expectation`, e.g. `logout_clearsToken`, `shouldShowError_whenGetViewerFails`) — no backtick function names — with a short comment stating the behavior verified. ViewModel unit tests inject hand-written `Fake<Interface>` doubles with constructor-configurable `Result` values and call-recording lists — no mocking library (Mockito/MockK) is permitted. Coroutine test harness: `@OptIn(ExperimentalCoroutinesApi::class)`, a test dispatcher field with `Dispatchers.setMain`/`resetMain` in `@Before`/`@After`, `runTest` bodies, and `advanceUntilIdle()` before asserting on `uiState.value`. Dependency stack: JUnit 4 + kotlinx-coroutines-test + OkHttp MockWebServer for unit tests (HTTP always mocked); instrumented UI tests via `androidx.compose.ui:ui-test-junit4` with `@RunWith(AndroidJUnit4::class)`, `createComposeRule()`, `setContent` wrapped in `SeanimeTVTheme`, and semantics assertions via `onNodeWithText`/`onNodeWithTag` (production code adds `Modifier.testTag` for non-text nodes).

---

## How to Use This Documentation

1. **Start Here**: Read this INDEX.md first to understand what documentation exists
2. **Project Context**: Read relevant project documentation before starting work
3. **Standards**: Open and follow the specific standard files relevant to your task — the index summaries are not a substitute for reading the full files
4. **Keep Updated**: Update documentation and regenerate INDEX.md when making significant changes
