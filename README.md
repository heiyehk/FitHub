# FitHub

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.21-blueviolet.svg)](https://kotlinlang.org)
[![AGP](https://img.shields.io/badge/AGP-9.1.0-green.svg)](https://developer.android.com/build)

> An Android client for finding and installing open-source apps shipped as GitHub Releases. It reads each release's assets and tells you which build actually fits the device in your hand, then scans your installed apps to say whether an update exists — and whether the signature would let Android install it over the top.

It does two things a generic browser tab cannot:

- **Picks the right build.** A release usually ships several archives for different ABIs and platforms. FitHub reads each one and tells you which matches the device you are holding.
- **Tells you what is already installed.** It scans your installed packages, and for anything it can match to a repository, reports whether an update exists and whether the signature would allow an in-place install.

[中文说明](README.zh-CN.md)

## Screenshots

| | |
|---|---|
| <img src="docs/screens/repo-detail.png" width="300" alt="Repository detail"><br>Repository detail — real release assets, each carrying the reason it does or does not fit | <img src="docs/screens/device-scan.png" width="300" alt="Device tab"><br>Device tab — a real <code>PackageManager</code> scan, 259 packages in 1.7 s |
| <img src="docs/screens/discover.png" width="300" alt="Discover"><br>Discover — the device profile every verdict is computed against | <img src="docs/screens/search.png" width="300" alt="Search"><br>Search — GitHub qualifiers, results passed straight through |
| <img src="docs/screens/package-lookup.png" width="300" alt="Package lookup"><br>Package lookup — candidates only, binding stays explicit | <img src="docs/screens/profile.png" width="300" alt="Profile"><br>Profile — live <code>X-RateLimit-Remaining</code>, not a hardcoded number |

## Status

Early and incomplete. Version `0.0.6`, and the following is accurate as of that release:

| Area | State |
|---|---|
| Discovery, search, user/org pages, repo detail | Working, live GitHub API |
| Search history | Working — last 10 queries, clearable, local only and never synced |
| Navigation | Working — back returns to the page you came from (search results, user page, My projects), not to the home tab |
| API rate limit display | Working, reads `X-RateLimit-Remaining` |
| Installed-app scan and device ABI/SDK detection | Working |
| Release asset classification and fit verdict | Working |
| APK parsing via `PackageManager` | Working, on user request |
| README rendering | Working, fetched on demand when the tab is opened |
| Repository code browsing | Working — a folder tree tab next to README. Directories load lazily and are cached for six hours; file bodies are not cached. Binary and oversized files fall back to "open on GitHub". Breadcrumb segments jump back up |
| Fit verdict for unnamed assets | Working — an APK with no ABI in its filename is no longer "unparseable"; it is offered with the caveat, and among equally-candidates `release` wins over `debug` / `unsigned` |
| Package-to-repository binding | Working — a bundled F-Droid index answers most lookups with no request at all; everything else is a user-confirmed candidate. FitHub's own link is fixed and cannot be unlinked or repointed |
| Subscriptions | Working — local list, import/export, manual refresh. No scheduled checks or notifications yet |
| Subscription sync over WebDAV | Working — opt-in, last-write-wins on `exportedAt`. Presets for the common providers; self-hosted needs a URL typed in |
| Home sections | Working — the four built-ins can be toggled, up to 6 custom topic sections |
| History | Working — local only, never synced. Browsing history and download/install records share one list; a record is written when the file actually lands, not when the button is tapped |
| Download and install | Working — foreground service with a progress notification, SHA-256 computed on download, handed to the system installer. The primary button toggles download/pause/resume, cancel sits beside it, and resuming picks up whatever mirror you switched to, keeping the bytes already fetched |
| Sign-in | Working — GitHub Device Flow, verified end to end on a device. `read:user` only, token in the Android Keystore |
| Update check | Working — compares version numbers properly (not string equality), says so when the local build is ahead of the latest release, and offers a button that opens this repo's own detail page to download and install it |
| Share | Working — system share sheet with the repo, its fit verdict for this device, and the Release URL. Text, not an image card |
| Preferences | Working — appearance (follow system / light / dark), include prereleases, require a SHA-256 checksum before install |

APKs ship with every release. Take [`FitHub-v0.0.6-release.apk`](https://github.com/heiyehk/FitHub/releases/tag/v0.0.6) — signed, R8-minified, and carrying all four ABIs. The `-debug` variant is there when you want logcat.

## What it does

**Fit verdicts.** Every release asset gets one of four states, shown on the asset row:

| Verdict | Meaning |
|---|---|
| Match | The asset's ABI is one this device supports |
| Degrade | Universal package — installable, but carries code for ABIs you cannot use |
| Mismatch | Wrong ABI, or a desktop build. Greyed out rather than hidden, with the reason |
| Unknown | Could not be parsed. Shown with the raw link, not guessed at |

**Device verdicts.** For an installed app matched to a repository, one of:

| Verdict | Meaning |
|---|---|
| Upgrade | Local `versionCode` is lower than the release's |
| Latest | Local `versionCode` is at or above the release's |
| Signing conflict | Both certificate fingerprints were read and differ — Android will refuse to overwrite-install |
| Version unknown | The remote APK was never parsed, so the two `versionCode` values are not comparable |

**Uncertainty is represented explicitly.** Three values in this codebase are deliberately not `Boolean`:

- `hasInstallable()` returns `null` on failure. "The request failed" and "this repo has no releases" are different states and the UI renders them differently.
- `ScanResult.complete` records whether the scan was truncated by package visibility filtering. When it is, the UI says "N apps visible" instead of "N apps installed".
- A signing conflict is only reported when both fingerprints were actually read. With one side missing, FitHub reports the version comparison and says nothing about signatures.

## Requirements

- Android 8.0 (API 26) or later
- JDK 17
- Android SDK with API 36

## Build

```bash
./gradlew assembleDebug       # debug APK
./gradlew testDebugUnitTest   # FitEngine unit tests
./gradlew installDebug
```

On Windows PowerShell, swap `./gradlew` for `.\gradlew.bat`.

Output lands in `app/build/outputs/apk/`.

### Release signing

`assembleRelease` signs the APK only when `keystore.properties` exists in the repository root **and**
the `storeFile` it names really exists. Otherwise it silently produces an unsigned APK that cannot be
installed. The script does both halves and prints the certificate fingerprint:

```bash
pwsh -File scripts/make-release-keystore.ps1
```

It prompts twice without echoing, creates `app/fithub-release.jks`, and writes `keystore.properties`.
The password reaches `keytool` through an environment variable, never as a command-line argument.
To do it by hand instead:

```bash
keytool -genkeypair -v -keystore app/fithub-release.jks -storetype PKCS12 \
  -alias fithub -keyalg RSA -keysize 4096 -sigalg SHA256withRSA -validity 10000 \
  -dname "CN=FitHub, OU=Open Source, O=FitHub, C=CN"
```

Then copy the template and fill it in:

```properties
storeFile=app/fithub-release.jks
storePassword=<你设的口令>
keyAlias=fithub
keyPassword=<同一个口令>
```

Two things that bite: `storeFile` is resolved **relative to the repository root**, and PKCS12 requires
`storePassword` and `keyPassword` to be identical. `keystore.properties` and `*.jks` are git-ignored —
do not commit them. Back the keystore up somewhere safe: losing it means you can never ship an update
to an app already published under it.

Since `v0.0.1` that published certificate is fixed, and a future keystore has to match it or Android
refuses to install over the top:

```
SHA-256: A4:49:FD:1B:31:E8:94:F8:2E:E5:F6:C6:22:6E:B7:4E:6F:0C:EF:9C:4C:1E:B6:7F:83:D3:40:0B:CE:B9:92:B4
```

`keytool -list -v -keystore app/fithub-release.jks -alias fithub` prints the same line under `SHA256:`
when the keystore in hand is the right one.

### GitHub client ID

The sign-in screen is wired up; what a **fresh clone** is missing is a client ID. The APKs on the
release page carry one, baked in at build time, so sign-in works there — it is a clone that has to
supply its own. `app/build.gradle.kts` bakes a `GITHUB_CLIENT_ID` `BuildConfig` field, and
`LoginScreen` says so in as many words when that field is blank rather than failing at the first
request. The value is read at configuration time from the first source that is set and non-blank:

| Source | Used by |
|---|---|
| `FITHUB_CLIENT_ID` environment variable | CI, and your own shell |
| `fithub.githubClientId` Gradle property | `~/.gradle/gradle.properties`, which keeps it out of the repo |
| neither | resolves to `""` and the build still succeeds |

So a fresh clone needs no configuration at all. To produce an APK that can actually sign in,
register an OAuth app under *Settings → Developer settings → OAuth Apps* with **Enable Device Flow**
ticked, then:

```bash
export FITHUB_CLIENT_ID=xxxxxxxxxxxxxxxxxxxx    # bash
./gradlew assembleDebug
```

```powershell
$env:FITHUB_CLIENT_ID = 'xxxxxxxxxxxxxxxxxxxx'  # PowerShell
.\gradlew.bat assembleDebug
```

It is not a secret — the device flow does not use a `client_secret`, and the value sits in
plaintext in every APK you ship. It is kept out of the repository so there is no ready-made material
for impersonating the app's authorization page, and so each clone can use its own. A value that
contains unexpected characters is reported as a `[fithub]` warning at build time but does not fail
the build, since a bad client ID should not be able to break an unrelated build.

### CI

`.github/workflows/android.yml` runs on every push and pull request, and on `v*` tags.

It runs the unit tests, then builds and uploads a debug APK and a release APK. On a tag it
also opens a GitHub Release and attaches the APKs. The build script is not modified for CI —
`app/build.gradle.kts` already reads `keystore.properties`, so the workflow just restores the
keystore into that file.

The release APK is **unsigned unless** you add these repository secrets under
*Settings → Secrets and variables → Actions*:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 app/fithub-release.jks` (Linux/macOS) or `certutil -encode app/fithub-release.jks keystore.b64` then strip the header/footer on Windows |
| `STORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | key alias, e.g. `fithub` |
| `KEY_PASSWORD` | key password |

Without them the release APK is still produced and published, just unsigned — the job prints a
notice rather than failing. That keeps forks (which cannot read your secrets) able to run the
whole pipeline.

One more optional **variable**, `FITHUB_CLIENT_ID`, is passed through the job's `env` and read by
`app/build.gradle.kts` like any other build-time value. It is a variable rather than a secret because
it is not a secret: Device Flow needs no `client_secret` and the value sits in plaintext in every APK
you ship anyway, while a secret would get replaced with `***` in the log — including the innocent
occurrences in messages like `Build succeeded`. Leaving it unset only means the published APKs cannot
sign in; nothing else changes, and the Gradle property fallback still gets its turn.

Note that the two are separate interfaces: `${{ vars.X }}` cannot read a secret, `${{ secrets.X }}`
cannot read a variable, and the same name in both does not fall through. Do not move
`KEYSTORE_BASE64` and the passwords to variables — those are real secrets and variables are not
masked in build output.

## Architecture

Single module, two layers, no DI framework and no navigation library.

```
data/
  FitEngine.kt        fit + device verdicts (pure functions, no Android deps)
  Env.kt              mutable process state: device profile, date, formatters
  ScanEngine.kt       PackageManager scan
  LinkEngine.kt       package name -> repository bindings
  FitRepository.kt    single async entry point for the UI, Async<T> tri-state
  HistoryStore.kt     browsing history and download records
  Prefs.kt            user preferences, backed by SharedPreferences
  TokenStore.kt       OAuth token in the Android Keystore
  remote/             Ktor client, DTOs, DTO->domain mapping, file cache
    ReleasePick.kt    which release counts as "latest" (drafts out, prereleases behind a pref)
  parse/ApkParser.kt  download + PackageManager manifest parsing
  install/            DownloadService (foreground progress) + ApkInstaller (PackageInstaller)
ui/
  FitHubApp.kt        state holder, single-Activity overlay navigation
  theme/              colors, type scale, motion tokens
  icons/Icons.kt      hand-written ImageVectors
  */                  one file per screen
```

`Env` is process-global mutable state rather than dependency injection. It is deliberate for an app this size and is the main thing to revisit if this grows.

Two decisions worth knowing before you change anything:

**The device profile is the baseline for every verdict.** `DeviceProfile.detect()` reads `Build.SUPPORTED_ABIS` and `Build.VERSION.SDK_INT`. Every ABI and version conclusion in the app is computed against it. It is populated from the real device, never from a fixture.

**The unauthenticated rate limit is 60 requests/hour.** The request budget is therefore explicit rather than incidental: one request per discovery section, two for a repo detail, one for a search, two for a user page. FitHub does not fetch releases per row in a list to fill in fit badges — a 30-row list would exhaust the budget. Fit verdicts are computed on demand in the detail view, and the README is fetched only when its tab is opened rather than alongside the repo detail.

**Cached data is labelled.** API responses carry `fromCache` and `ageMs`, and the UI says so. Without that, a user cannot tell "3 stars, just now" from "3 stars, two hours ago", which is the same class of error as reporting a failed lookup as "no releases". Stale cache is never discarded just for being past its TTL — offline, showing old data beats showing nothing.

## Localization

English and Simplified Chinese. English is the default resource, Chinese is the translation:

| System language | Shown |
|---|---|
| English | English |
| Chinese | Chinese |
| Anything else | English (falls back to `values/`) |

There is also an in-app override — *Profile → Language* — with **Follow system / 简体中文 / English**. It is
not just a preference that gets applied on next launch; the choice is visible in the system Settings app
under the app's languages on Android 13 and later.

Two things about how this is built that are worth knowing before you add a string.

**The language code is applied in two different places, and they must not both be active.** On API 33+
`AppLocale` writes to `android.app.LocaleManager.applicationLocales`, which is the platform's own
per-app language feature — the system applies it, and Settings picks it up. Below API 33 there is no
such feature, so `MainActivity.attachBaseContext` wraps the context itself and the choice is applied on
`recreate()`. If the context wrapping also ran on API 33+, a language change made from the *system*
settings would be overwritten by the stale in-app preference. `AppLocale.wrap()` returns the context
untouched when the system already has a locale set, which is what keeps the two from fighting.

`androidx.appcompat`'s `AppCompatDelegate.setApplicationLocales()` is the other obvious choice, and it
is deliberately not used: it requires the Activity to extend `AppCompatActivity` and the theme to
extend an AppCompat theme. This app's theme parent is the platform `@android:style/Theme.Material.Light.NoActionBar`,
and dragging Material Components in for a language switch is not a trade worth making. The platform API
covers the same ground with zero dependencies.

**The domain layer holds resource IDs, not strings.** `data/` has no `Context` and `FitEngine` is a pure
function that runs in JVM unit tests, so it cannot call `getString`. Instead it returns an `Explain` —
a `@StringRes` plus its format arguments — and the UI renders it:

```kotlin
Explain(R.string.reason_abi_mismatch, listOf(device.abi, abi))
```

```kotlin
Text(explainText(asset.reason!!))   // in a @Composable
context.getString(...)               // and in a plain function, e.g. the share sheet
```

This is why `Asset.reason` is an `Explain` and not a `String`, and why the fit-verdict tests assert
*which resource was chosen* rather than whether the Chinese text contains a particular character —
a test asserting on wording breaks the moment the wording is reworded or translated.

### Adding a language

1. Create `res/values-<code>/strings.xml` and give it the same keys as `res/values/strings.xml`.
2. That is the whole process. There is no per-language code to register.

### Not missing a translation

`./gradlew :app:verifyTranslations` parses both files and fails the build when the key sets differ or
when a `%1$s` placeholder is missing or renumbered in one language. It is wired into `preBuild`, so
every `assembleDebug` runs it. This replaces lint's `MissingTranslation`, which is not enforced here
because `checkReleaseBuilds` is off (see Known limitations), and it is stricter about placeholders:
a dropped `%1$s` produces `"%1$s"` at runtime and no compiler will ever complain.



```bash
./gradlew testDebugUnitTest
```

131 JVM unit tests, all in `app/src/test/java/com/heiyehk/fithub/data/`. They run on the JVM rather than a device because the only device available during development was an x86_64 emulator, on which the arm64 branches never execute. If you have an arm64 device in CI, that is a gap worth closing.

| File | Covers |
|---|---|
| `FitEngineTest.kt` | Fit and device verdicts via `GitHubMapper` — the code path that actually runs |
| `RepoCacheTest.kt` | Cache TTL behaviour, and that expired cache stays readable for the offline path |
| `ReadmeTest.kt` | base64 decoding of the readme endpoint, and the 512 KB truncation flag |
| `LocalStoreTest.kt` | filesDir persistence: corrupt data falls back to a default instead of throwing, atomic writes |
| `SubscriptionTest.kt` | Snapshot staleness, ordering, and the `Repo` → `Subscription` conversion |
| `SubscriptionTransferTest.kt` | Import/export: merge semantics, schema rejection, path-traversal rejection |
| `SubscriptionSyncTest.kt` | WebDAV: `exportedAt` parsing on corrupt/empty input, last-write-wins, provider presets, enable semantics |
| `HistoryEntryTest.kt` | History dedupe keys, ordering, and the ring-buffer cap |
| `HomeSectionTest.kt` | Home section config: built-ins immutable, id derivation, the 6-custom cap, ordering |
| `DeviceFlowTest.kt` | Device-flow sign-in: unconfigured vs failed, denial, code expiry, `slow_down`, and that the default scope stays `read:user` |
| `ReleasePickTest.kt` | "Latest release" selection — a prerelease must never be picked as latest, drafts are always out, and "only ever shipped a prerelease" is distinguishable from "shipped nothing" |

The verdict tests pin:

- an arm64 asset must not be a `Match` on an x86_64 device
- `versionCode == 0` from an unparsed release must yield `VersionUnknown`, never `Latest`
- a signing conflict requires both fingerprints; one-sided data must not report one
- checksum files (`SHA256SUMS.txt`, `*_sha256sums`, `*.asc`) are not installable assets
- DTO snake_case fields are actually mapped

The cache tests pin the two rules that make offline viewing honest: a cache older than its TTL is still returned on the offline path, and the age reported alongside it matches the file timestamp.

## Known limitations

- No real device was available during development. Frame rates, the installed-app count on real hardware, and Play-store signature conflicts are untested. Everything above was verified on an x86_64 emulator plus JVM tests.
- **Sign-in is verified end to end on a real device.** The device code, the browser authorisation, polling through to a token and the `60 → 5000` per-hour quota switch after signing in have all been exercised. The token lands in the Android Keystore. **Private repositories are still unproven** — the code path is wired, but there was no real private repo to test it against.
- Discovery is cached for an hour and repository detail for six. A refresh button invalidates the cache first, so it always hits the network. There is no scheduled background refresh yet, so cached data only gets replaced when you pull to refresh or open a screen that misses the cache.
- Package-to-repository lookup checks the bundled F-Droid index first (3698 mappings, zero requests). Anything it misses falls back to searching by package name, which still only finds projects that mention it in the repo name or README — bind the rest manually.
- Tapping a card mid-scroll starts the detail transition from the tap coordinates, so a fast flick can land the panel slightly off. `LazyListState` lookup would fix it.
- `checkReleaseBuilds` is disabled in `app/build.gradle.kts`: AGP 9.1.0's `lintVitalAnalyzeRelease` crashes while analyzing Compose sources. `./gradlew lint` still works.
- The detail transition is an overlay inside a single Activity rather than a NavHost. System back is handled by `BackHandler`.

## Design notes

Pure white surfaces, hairline separators, a single deep green accent, no gradients and no nested cards. Colour is only ever used to express state, never hierarchy. Version numbers, file sizes and checksums render in a monospace face so digits do not shift width.

Motion is restricted to `transform`, `alpha` and `color`. No layout animations. The detail panel and the shrinking content layer share one `Animatable(0f)`, so reversing mid-transition is continuous.

## Contributing

Issues and pull requests are welcome. Two things worth knowing before you send a change:

- `FitEngine` verdicts are covered by unit tests. If you change a verdict, change or add the test with it.
- Keep uncertainty representable. Any new boolean that means "we could not tell" should be a tri-state instead.

## Documentation

- [Product scope](docs/product-scope.md) · [产品概述](docs/产品概述.md) — positioning, per-module implementation state, design tokens, and the issues this app has to get right
- `docs/screens/` — screenshots

## License

[MIT](LICENSE) © 2026 heiyehk
