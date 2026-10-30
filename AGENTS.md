# Project instructions

Read [docs/INDEX.md](docs/INDEX.md), then the project documentation and standards relevant to the task before changing code. See [PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md) for the source layout.

- This is an unofficial, standalone Android TV adaptation of Seanime Denshi. It uses AniList and on-device provider plugins, without a Seanime server.
- Application ID and Kotlin root package: `com.malina.seanimetvport`.
- Use Kotlin, Jetpack Compose for TV, feature-owned ViewModels, and the shared `AppContainer`. Keep D-pad navigation and visible focus working.
- Use explicit imports, the existing version catalog, and the committed Gradle wrapper with JDK 21. Avoid unrelated refactors and preserve existing user changes.
- AniList credentials belong in ignored `local.properties`. Never publish secrets, account tokens, signing keys, private logs, or local tool settings.
- Account tokens and stored account JSON must remain encrypted through `TokenCipher` and Android Keystore. Preserve migrations and authentication-store backup exclusions.
- Preserve GPLv3 licensing, upstream attribution, and third-party notices.
- Verify code changes with `./gradlew testDebugUnitTest assembleDebug`. Verify release changes with `./gradlew assembleRelease`. Instrumented tests require a device or emulator.
- Keep documentation accurate. Do not claim a device test was performed when only compilation or JVM tests ran.

Project instructions are shared by Codex and Claude. No particular AI tool or workflow plugin is required to build or maintain the application.
