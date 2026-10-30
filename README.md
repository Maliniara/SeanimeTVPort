# Seanime TV Port

**An unofficial Android TV port/adaptation of Seanime Denshi, by Malina.**

The project is developed primarily for **streaming anime on Android TV**, using provider extensions to find episodes and play streams directly on the device. This streaming workflow is the main focus of development and testing.

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

## Install the APK with ADB

You need a computer, a compatible Android TV/Google TV device, and a connection to the same local Wi-Fi network. Android Studio is not required. The steps below use **Windows PowerShell** and Chromecast with Google TV menu names; labels can vary with the device language and firmware.

### 1. Download Android Platform Tools

1. Open Google's official [SDK Platform Tools download page](https://developer.android.com/tools/releases/platform-tools) and download the ZIP for your operating system.
2. Extract the entire ZIP. For example, place the resulting `platform-tools` folder at `C:\Android\platform-tools`. Keep its files together.
3. Open PowerShell in the folder containing `adb.exe`, or navigate to it:

   ```powershell
   cd "C:\Android\platform-tools"
   .\adb.exe version
   ```

   The second command should print the installed ADB version. On macOS/Linux, open a terminal in the extracted `platform-tools` folder and use `./adb` instead of `.\adb.exe` in every command below, and use `./SeanimeTVPort-v1.1.0.apk` for the APK path in the install command.

### 2. Download the application

1. Open the [latest release](https://github.com/Maliniara/SeanimeTVPort/releases/latest).
2. Under **Assets**, download the `.apk` file, for example `SeanimeTVPort-v1.1.0.apk`. The **Source code** archives are not installable applications.
3. Copy the APK into your `platform-tools` folder. The examples below use the v1.1.0 filename; substitute the downloaded filename for later releases.

### 3. Enable debugging on the TV

1. On the Chromecast, open **Settings → System → About**.
2. Select **Android TV OS build** (or **Build number**) and press the remote's OK button seven times, until developer mode is enabled.
3. Return to **Settings → System → Developer options** and open **Wireless debugging**.
4. Turn it on and allow debugging on your local network if prompted.

These pairing steps require firmware that provides **Wireless debugging → Pair device with pairing code**. Google's [ADB guide](https://developer.android.com/tools/adb) lists wireless pairing support for Android TV 13 and newer. The app itself also supports API 32, but that does not guarantee the pairing menu exists. If it is missing, use your device's supported USB/network ADB setup instead; Android's [USB connection instructions](https://developer.android.com/tools/adb) cover the cable-based alternative.

### 4. Pair the computer and TV

1. On the TV, select **Pair device with pairing code**. Keep that dialog open.
2. Note its **IP address, pairing port, and pairing code**.
3. In PowerShell, run the following with the IP and port shown in that pairing dialog:

   ```powershell
   .\adb.exe pair 192.168.1.50:37001
   ```

4. Enter the pairing code when prompted. Wait for the successful-pairing message.

**Every IP address and port in these examples is illustrative. Replace them with the values shown on your TV.** Pairing codes and ports can change when the dialog is reopened.

### 5. Connect to the TV

1. Close the pairing-code dialog and return to the main **Wireless debugging** screen.
2. Find **IP address & Port** on that screen. This is the **connection port**, which is usually different from the pairing port.
3. Connect using those values, then list the devices:

   ```powershell
   .\adb.exe connect 192.168.1.50:39001
   .\adb.exe devices
   ```

4. Confirm that the selected TV appears with the status **device**. Approve any authorization prompt on the TV. Do not proceed while its status is `offline` or `unauthorized`.

Pairing alone may not create an active connection. If the connection drops or the TV restarts, check its current connection port and repeat this step.

### 6. Install and open the application

Use the connected TV's address from the previous step as the `-s` target. This also selects the intended TV when ADB lists multiple devices:

```powershell
.\adb.exe -s 192.168.1.50:39001 install -r ".\SeanimeTVPort-v1.1.0.apk"
```

Wait for **Success**, then open **SeanimeTV** from the TV's apps list. You can also launch it from PowerShell:

```powershell
.\adb.exe -s 192.168.1.50:39001 shell am start -n com.malina.seanimetvport/.MainActivity
```

The `-r` option updates an existing installation with the same application ID and a compatible signing key while retaining its data. Version 1.1.0 uses `com.malina.seanimetvport`; Android treats it as a separate application from v1.0.0's `com.example.seanimetv`, so the older app's data is not transferred automatically.

When finished, disconnect and turn off **Wireless debugging** on the TV if you no longer need it:

```powershell
.\adb.exe disconnect 192.168.1.50:39001
```

### If installation does not work

- **`adb.exe` is not found:** open PowerShell in the directory containing it and retain the `.\` prefix.
- **Pairing fails:** reopen the pairing dialog and use its current port and code.
- **Connection refused or timed out:** use the connection port, not the pairing port; check the shared Wi-Fi network, firewall, and guest-network/client isolation settings.
- **`more than one device/emulator`:** use `-s` with the exact target from `adb devices`, as in the installation example.
- **`INSTALL_FAILED_UPDATE_INCOMPATIBLE`:** the installed copy has a different signing key. Use a build signed with the same key, or remove the old copy through TV settings before installing. **Removing the app deletes its local data.**
- **`INSTALL_FAILED_OLDER_SDK`:** the TV is below API 32 and cannot run this build.

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
