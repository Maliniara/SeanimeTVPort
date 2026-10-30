# Third-party notices

## Original project

Seanime and Seanime Denshi are created by **5rahim and contributors**:

- Source: https://github.com/5rahim/seanime
- Denshi: https://github.com/5rahim/seanime/tree/main/seanime-denshi
- License: GNU GPL version 3; the full upstream license is preserved in [LICENSE](LICENSE).

The unofficial Android TV adaptation is by Malina. Existing copyright, license, and attribution notices in upstream or vendored material must be preserved. The port's license does not replace the licenses of independent dependencies, provider extensions, artwork, or external services.

## Vendored JavaScript transpiler

`app/src/main/assets/sucrase.bundle.js` contains Sucrase and modules identified from its bundled module paths. Their license texts are included in the source tree **and in the APK** under `assets/licenses/`:

| Component | License | License text |
|---|---|---|
| Sucrase | MIT, including its upstream parser notices | [Sucrase](app/src/main/assets/licenses/sucrase-LICENSE.txt) |
| @jridgewell/gen-mapping | MIT | [gen-mapping](app/src/main/assets/licenses/jridgewell-gen-mapping-LICENSE.txt) |
| @jridgewell/resolve-uri | MIT | [resolve-uri](app/src/main/assets/licenses/jridgewell-resolve-uri-LICENSE.txt) |
| @jridgewell/sourcemap-codec | MIT | [sourcemap-codec](app/src/main/assets/licenses/jridgewell-sourcemap-codec-LICENSE.txt) |
| @jridgewell/trace-mapping | MIT | [trace-mapping](app/src/main/assets/licenses/jridgewell-trace-mapping-LICENSE.txt) |
| lines-and-columns | MIT | [lines-and-columns](app/src/main/assets/licenses/lines-and-columns-LICENSE.txt) |
| ts-interface-checker | Apache-2.0 | [ts-interface-checker](app/src/main/assets/licenses/ts-interface-checker-LICENSE.txt) |

The original lockfile used to build this vendored bundle was not retained. [License source metadata](app/src/main/assets/licenses/sources.json) records where the license texts were retrieved; those retrieval versions are not a claim about the bundle's exact dependency versions.

The Sucrase copyright notice refers to its [AUTHORS file](app/src/main/assets/licenses/sucrase-AUTHORS.txt), which is also preserved.

## Android dependencies

Versions and artifact coordinates are declared in [gradle/libs.versions.toml](gradle/libs.versions.toml). Major direct dependencies include:

| Project | Upstream license information |
|---|---|
| AndroidX / Compose / Media3 | [AndroidX Apache-2.0 license](https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt) |
| Kotlin / kotlinx libraries | [Kotlin Apache-2.0 license](https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt) |
| OkHttp | [Apache-2.0](https://github.com/square/okhttp/blob/master/LICENSE.txt) |
| Coil | [Apache-2.0](https://github.com/coil-kt/coil/blob/main/LICENSE.txt) |
| Jsoup | [MIT](https://github.com/jhy/jsoup/blob/master/LICENSE) |
| quickjs-kt | [Upstream repository and license](https://github.com/dokar3/quickjs-kt) |
| QuickJS, used by quickjs-kt | [Upstream MIT license](https://bellard.org/quickjs/) |
| Gradle Wrapper | [Apache-2.0 and Gradle notices](https://github.com/gradle/gradle/blob/master/LICENSE) |

This overview is not a complete inventory of transitive dependencies. When redistributing binaries, retain the licenses and notices shipped with the exact resolved artifacts, including native libraries. Test-only dependencies are not part of the production APK.

Provider plugins installed at runtime are separately licensed by their authors. AniList, AniSkip, AniZip, and Google TV are external services/platforms; attribution here does not imply affiliation or endorsement.
