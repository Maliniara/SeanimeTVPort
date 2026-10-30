# Seanime TV Port

**An unofficial Android TV port/adaptation of Seanime Denshi, by Malina.**

Based on the original [Seanime project](https://github.com/5rahim/seanime) and [Seanime Denshi](https://github.com/5rahim/seanime/tree/main/seanime-denshi), created by **5rahim and contributors**. All credit for the original project belongs to its authors. This port is independently maintained and is not affiliated with, endorsed by, or officially supported by them.

**Current release: [v1.1.0](https://github.com/Maliniara/SeanimeTVPort/releases/tag/v1.1.0)**

## A personal project that escaped

This was supposed to be a private project for my own use. But, well, things happened — and here it is.

This project is **vibecoded**: it was built with substantial help from AI coding tools. I am **not a professional developer**. Expect rough edges, bugs, regressions, and things that only work on my setup. There is no promise of regular updates, support, compatibility, or fixes.

The app is provided **as is, without warranty**. Use it at your own risk. To the extent permitted by applicable law, the port author accepts no liability for bugs, failures, loss of data, or other damage resulting from its use. See [LICENSE](LICENSE), especially sections 15–17.

## Tested device

Tested on **Chromecast with Google TV (HD)** — the Google Chromecast HD with Android TV/Google TV. Other devices are not verified.

- Android 12L / API 32 or newer is required by the current build.
- Designed for a TV remote and D-pad navigation.
- Application ID: `com.malina.seanimetvport`.

## What it does

- AniList login, multiple accounts, anime lists, search, discovery, and airing schedule.
- Series details, episode selection, watch progress, and continue watching.
- On-device JavaScript/TypeScript provider extensions and an extension store.
- Media3 playback, subtitle controls, and intro/outro skipping where metadata is available.
- Android TV home-screen integration.

This is a native Kotlin/Jetpack Compose adaptation. It runs independently: it does **not** require a Seanime server and does not include the original desktop application. Feature parity with Seanime Denshi is not promised.

The app does not bundle anime videos. Provider extensions and external services are separate projects with their own terms, availability, and licenses. Use only content and services you are entitled to access.

## Build from source

Requirements: **JDK 21**, Android SDK platform **36**, and the SDK components requested by Gradle. Use the committed Gradle wrapper; Android Studio can install missing SDK components.

1. Clone this repository and open its root in Android Studio.
2. Copy `local.properties.example` to `local.properties` and set `sdk.dir` to your SDK path. Android Studio may create the SDK entry for you.
3. For AniList login, register your own OAuth application in [AniList developer settings](https://anilist.co/settings/developer). Set its redirect URL to `https://anilist.co/api/v2/oauth/pin` and put the matching client ID and client secret in `local.properties`.
4. Run the build from the repository root:

```sh
./gradlew assembleRelease
```

On Windows:

```powershell
.\gradlew.bat assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`.

The current release variant uses the local Android **debug signing key** for personal sideloading. Builds from different machines may use different keys and therefore cannot necessarily update each other. Configure your own release signing before publishing a maintained APK distribution; never commit a keystore or signing credentials.

The default client ID is retained for existing local builds. For your own build, set **both** OAuth fields to a matching pair from your AniList application. A build without a valid client secret cannot complete the current AniList login flow. Neither credential is needed to compile the project itself.

### Verification

```sh
./gradlew testDebugUnitTest assembleDebug compileDebugAndroidTestKotlin
```

This compiles instrumented tests but does not run them. Running UI tests requires a connected compatible device or emulator and `./gradlew connectedDebugAndroidTest`.

## Account data

Account tokens and the stored account JSON are encrypted using **AES-256-GCM with an Android Keystore key**. Existing plaintext entries migrate on first access. Authentication stores are excluded from cloud backup and device transfer; signing in again is required after moving to a new installation.

The current AniList Authorization Code flow embeds the OAuth **client secret in the APK**. Keeping `local.properties` out of Git does not hide that secret from someone inspecting a built APK. This is a known limitation inherited from the personal-use setup, separate from account-token encryption.

Debug logs can contain account/profile data, list contents, and provider URLs. The AniList authorization header is masked. Review logs before sharing them and never include passwords, OAuth secrets, or access tokens in issues.

## Project documentation

[PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md) maps the code. [docs/INDEX.md](docs/INDEX.md) links to architecture and coding standards. [AGENTS.md](AGENTS.md) and [CLAUDE.md](CLAUDE.md) provide project context for Codex and Claude without private tool settings.

## Bugs and contributions

Reports and pull requests are welcome, but responses and fixes are not guaranteed. Include the device model, Android version, the revision or APK version, and steps to reproduce. Remove private data from screenshots and logs. Please report port-specific problems here rather than asking the original Seanime author to support this app.

## Credits and license

- Original Seanime / Seanime Denshi: **5rahim and contributors** — [upstream repository](https://github.com/5rahim/seanime).
- Unofficial Android TV adaptation: **Malina**.
- AniList, AniSkip, AniZip, and the open-source libraries used by this app retain their respective ownership and terms.

The port is distributed under the **GNU General Public License, version 3**, in line with the [original Seanime license](https://github.com/5rahim/seanime/blob/main/LICENSE). See [LICENSE](LICENSE), [NOTICE](NOTICE), and [third-party notices](THIRD_PARTY_NOTICES.md). Third-party components keep their own licenses.

If you distribute a compiled APK, make the corresponding source code and build instructions for that exact version available under the applicable license terms. Preserve upstream attribution and license notices. This license does not imply endorsement or grant ownership of Seanime's name, branding, or third-party content.
