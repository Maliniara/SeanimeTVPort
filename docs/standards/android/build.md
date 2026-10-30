# Build Configuration Standards

### Gradle Kotlin DSL Only
All Gradle build files must use the Kotlin DSL (`.gradle.kts` extension). Do not introduce Groovy `.gradle` files.

```
✓ app/build.gradle.kts
✓ build.gradle.kts
✓ settings.gradle.kts
✗ app/build.gradle  ← not allowed
```

### Centralized Version Catalog
All dependency versions and library coordinates must be declared in `gradle/libs.versions.toml`. Reference them via type-safe `libs.*` accessors in build scripts. Never hardcode version strings directly in build files.

```toml
# gradle/libs.versions.toml
[versions]
myLibrary = "1.2.3"

[libraries]
my-library = { group = "com.example", name = "my-library", version.ref = "myLibrary" }
```

```kotlin
// app/build.gradle.kts
implementation(libs.my.library)  // ✓
implementation("com.example:my-library:1.2.3")  // ✗ no hardcoded versions
```

### FAIL_ON_PROJECT_REPOS Repository Strategy
Dependency repositories are declared centrally in `settings.gradle.kts` only. Individual module build files must not declare their own `repositories {}` blocks — this is enforced by `RepositoriesMode.FAIL_ON_PROJECT_REPOS`.

### JVM Toolchain and AGP
- **JDK**: JDK 21 required for running Gradle builds (foojay toolchain resolver pinned to 21)
- **AGP**: 9.2.1 — use AGP 9.x Kotlin DSL syntax (e.g., `compileSdk { version = release(36) { } }`)
- **Kotlin Compose Plugin**: `org.jetbrains.kotlin.plugin.compose` must be applied — required for Kotlin 2.x + Compose

### Compose BOM Version Management
Compose library versions are managed by the Compose BOM: `implementation(platform(libs.androidx.compose.bom))` and `androidTestImplementation(platform(libs.androidx.compose.bom))`. Individual `androidx.compose.*` libraries are declared in the version catalog without version attributes.

### Required Kotlin Compiler Plugins
The app module applies four plugins via version-catalog aliases: `com.android.application`, `org.jetbrains.kotlin.plugin.compose` (mandatory for Compose with Kotlin 2.x), `kotlin.plugin.serialization`, and `kotlin.plugin.parcelize`. All Kotlin plugins track the single `kotlin` version ref so they upgrade together.

### Canonical Library Choices
Do not introduce alternatives ad hoc — the chosen stack is: networking via OkHttp directly with kotlinx.serialization JSON (not Retrofit, Ktor, Gson, or Moshi); image loading via Coil Compose; video playback via Media3 ExoPlayer (with HLS and OkHttp datasource); navigation via Navigation Compose; persistence via DataStore Preferences; TV UI via `androidx.tv` tv-foundation/tv-material alongside standard Material 3; HTML parsing via Jsoup; embedded JS via quickjs-kt.

### Gradle Configuration Cache
The Gradle configuration cache is enabled project-wide (`org.gradle.configuration-cache=true` in `gradle.properties`). All build logic, including custom tasks, must remain configuration-cache compatible.

### Provider Plugins Are Not Built by Gradle
Provider plugins are installed at runtime (from a URL or the plugin store) and run in QuickJS; `src/main/assets/plugins/` ships empty (`.gitkeep`) and no Gradle task compiles plugins. TypeScript plugins are transpiled on-device by `TsTranspiler` using the prebuilt `src/main/assets/sucrase.bundle.js`, which is a vendored asset and is never hand-edited. Do not add a build-time plugin compilation step without updating this standard.
