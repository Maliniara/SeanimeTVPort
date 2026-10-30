# SDK and API Level Standards

### API Level Targets
- **minSdk**: 32 (Android 12L) — supports Android TV devices from Android 12L onward. Chromecast with Google TV runs API 29+ but the majority of active Android TV hardware is on API 32+. Use `@RequiresApi` guards when calling APIs introduced after API 32.
- **targetSdk / compileSdk**: 36 (Android 16)

```kotlin
android {
    compileSdk = 36
    defaultConfig {
        minSdk = 32
        targetSdk = 36
    }
}
```

### `@RequiresApi` and Version Guards
With minSdk = 32, APIs introduced after API 32 require a runtime check or `@RequiresApi` annotation. All Jetpack libraries in use (Compose, DataStore, Navigation Compose, TV Material) are compatible with API 32+ — no guards needed for library calls.

### Java Source/Target Compatibility
All Kotlin/Java source compiles to Java 11 bytecode. Do not use Java 12+ language features that require a higher target.

```kotlin
compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
```

### Release Build
Release builds have R8 optimization **enabled** (`optimization { enable = true }`) with keep rules in `app/proguard-rules.pro` (QuickJS JNI, kotlinx.serialization serializers, OkHttp dontwarn). Compose depends on R8 for lambda inlining and class merging — never disable it on the low-end Android TV target. The release build type is signed with the debug key for sideload testing only; replace with a real signing config before publishing. Performance testing must ALWAYS use the release APK — debug builds are 3–10× slower and unrepresentative.

### Application Namespace
The current namespace and applicationId is `com.malina.seanimetvport`. All source packages must be rooted at this namespace.
