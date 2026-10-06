# FitHub — Product Scope

中文 · [English](产品概述.md)

Version 0.0.4 · 2026-10-06

This document describes product positioning and feature scope. Each module is marked with its current implementation state — the README has a more detailed status breakdown.

## One-liner

FitHub is an open-source app discovery and sideloading client that works without signing in: it turns GitHub Releases into a browsable store, reads each archive's platform and architecture requirements, and tells you which one fits the device in your hand. If the app is already installed, it scans your packages and reports whether an update exists and whether the signature allows an in-place install.

## Scope

| | |
|---|---|
| Category | Open-source app discovery + release sideloading |
| Core difference | Device fit recommendation (picking the right asset from a pile) + installed-app scan |
| Works without login | Discovery, search, user/org browsing, release viewing, fit parsing |
| Unlocked by login | Rate limit 60/h → 5000/h (verified on a device), private repositories (wired, unproven) |
| Out of scope | Issues / PRs / code browsing, CI Actions, APK repackaging, ads, recommendation algorithms |

Login exists to raise the API quota, not as a gate on any browsing or download feature.

## Modules

### 1. Discovery

- Three sections: popular / recently updated / featured
- Hard rule: only show repositories with real downloadable assets
- Ordering uses objective signals only: stars, latest release time
- Agent section: topic search against real repositories, user-triggered

State: **implemented**. GitHub has no Trending API, so "popular" uses `sort=stars` and the UI says so.

### 2. Unified search

One input, three result tabs (repositories / users / organizations). GitHub search syntax passes through unchanged (`has:release`, `language:kotlin`, `topic:compose`). 300ms debounce.

State: **implemented**.

### 3. User / organization page

Avatar, bio, followers / following, repository list sortable by recent activity or stars.

Honest boundary: `/search/users` does not return followers, so search results do not show a follower count — "not returned" is not "zero".

State: **implemented**.

### 4. Repository detail

Release list, asset list, fit verdict per asset, repository metadata.

State: **implemented**. README is fetched on demand from `GET /repos/{owner}/{repo}/readme` and rendered as Markdown, with a 512 KB per-file cap. Language breakdown and changelog highlighting are not implemented.

### 5. Release and fit recommendation ★ core

**Step 1 — classify assets**

By extension: APK / AAB / MSI / DMG / DEB / RPM / tar.gz / zip / exe / checksum files.

**Step 2 — parse the APK**

- `PackageManager.getPackageArchiveInfo()` — no third-party dependency
- Yields packageName, versionCode, versionName, minSdk, targetSdk, signing fingerprint
- `ZipFile` reads `lib/<abi>/` and `split_*.apk`

**Step 3 — cross-check against the device**

| Verdict | Presentation |
|---|---|
| Match | Pinned, highlighted, primary download button |
| Degrade | Universal package, with the size cost stated |
| Mismatch | Greyed out but visible, with the reason |
| Unknown | Not guessed; the reason and the raw link are shown |

**Step 4 — prerelease control** (implemented)

The *Profile → Include prereleases* toggle, **off by default**: a prerelease is for people who opted
in, and letting one count as "latest" points the fit verdict at a package nobody else has installed.
Repositories that ship a prerelease as their newest tag are common enough to be a real trap, so
`ReleasePick` and `ReleasePickTest` pin the rule.

**Step 5 — download and install** (implemented)

A foreground service with a progress notification, SHA-256 computed on the finished file, handed to
`PackageInstaller`. A checksum only counts as *verified* when at least 32 authoritative digits are
available; otherwise the UI says it was computed so you can check it yourself — comparing our own
digest against itself proves nothing. The "require a checksum before installing" toggle is off by
default, because most Releases ship no checksum file and on-by-default would block most repositories.

State: Steps 1–5 **implemented**. APK parsing runs when the user taps "Parse" on an asset row; nothing is downloaded automatically.

### 6. Installed-app scan ★ core

**Scan**: batched `PackageManager.getInstalledPackages(GET_SIGNING_CERTIFICATES)`.

**Reverse lookup to a repository**:

1. User binds explicitly
2. Package-name search returns candidates

Search results are candidates only. Binding requires user confirmation — silently treating an installed package as some similarly-named repository produces an upgrade notice for a relationship that does not exist.

**Four verdicts**:

| Verdict | Condition |
|---|---|
| Latest | Local versionCode ≥ release |
| Upgrade | Local versionCode < release |
| Signing conflict | Both certificate fingerprints read, and they differ — Android refuses to overwrite-install |
| Version unknown | Remote APK unparsed, versionCodes not comparable |

**Package visibility**: since Android 11, package visibility filtering silently truncates `getInstalledPackages` without `QUERY_ALL_PACKAGES`. `ScanResult.complete` records whether the scan was truncated, and the UI distinguishes "N apps installed" from "N apps visible".

State: **implemented**.

### 7. Subscriptions (partially implemented)

Follow a repository. The list lives on the device (`filesDir`); nothing is stored server-side, so signing in is not required.

- Entry point: the bookmark icon in the repository detail panel
- Metadata is kept as a local snapshot, so the tab reads offline and costs no API quota on open
- Snapshots older than 6 hours are labelled with their age in the row
- Refresh: per-row, or all at once from the header. Runs **serially** — a full refresh costs one request per subscription
- Import/export: own JSON (not OPML), through the system file picker. Import **merges** by default and never overwrites local entries

Not implemented: scheduled checks (WorkManager) and push notifications for new releases. WebDAV sync is implemented — opt-in, last-write-wins on `exportedAt`; it carries subscriptions only, never history.

### 7.5 History (implemented)

Repositories viewed, assets parsed, apps installed through FitHub. Stored in `filesDir`.

- **Local only, never synced via WebDAV** — history is device state, not account content. Syncing it would surface browsing records on a device where they never happened
- Viewing the same target again updates the existing entry instead of appending a duplicate
- Ring buffer capped at 200 entries, oldest dropped first
- Reachable from settings, with per-entry and bulk clearing

### 8. Sharing (implemented, text only)

System share sheet carrying the repository, its fit verdict for this device, and the Release URL.
The share text is text, not an image card: an image card would need a FileProvider plus a Compose
render pass, and the text version already carries the part that makes sharing worth doing — the
verdict for *your* device. A repo detail panel also has an "open the original release" button that
hands off to the browser.

### 9. GitHub OAuth login (implemented, verified on a device)

- Device Flow (native GitHub support, no callback URI required)
- Minimal scope: `read:user` only — no write scopes, no cloud sync
- Token in `EncryptedSharedPreferences` backed by the Android Keystore
- Reads `X-RateLimit-Remaining` for the UI
- The client ID is a build-time value (`FITHUB_CLIENT_ID`), never committed; without it the screen
  says so plainly instead of failing at the first request

**What is and is not verified**: the client ID reaches `BuildConfig`, requests do go out to
`https://github.com/login/device/code`, and each failure state (no client ID, request failed,
denied, code expired, `slow_down` back-off) has its own screen. **A full round trip has since been
completed on a real device** — device code, browser authorisation, polling through to a token, and the
`60 → 5000` per-hour quota switch afterwards, with the token landing in the Android Keystore.

**Private repositories are still unproven.** That code path is wired, but there was no real private
repository to exercise it against, so it should not be counted as a verified capability.

### 10. Localization (implemented)

English and Simplified Chinese. English is the default resource; Chinese is the translation, so
system languages other than Chinese fall back to English. The in-app override lives under
*Profile → Language* with **Follow system / 简体中文 / English**, and on Android 13+ the choice is
the platform's own per-app language, so it also shows up in system Settings.

No `androidx.appcompat`: `AppCompatDelegate.setApplicationLocales()` would require an
`AppCompatActivity` and an AppCompat theme, and this app's theme parent is the platform
`@android:style/Theme.Material.Light.NoActionBar`. `AppLocale` uses the platform `LocaleManager` on
API 33+ and wraps the context in `attachBaseContext` below that. The two paths must stay mutually
exclusive, or a language change made in system Settings gets overwritten by the in-app preference.

The domain layer holds resource IDs rather than strings — see the README's Localization section.

## Design

### Direction

White surfaces, hairline separators, a single accent color, 20–28dp radii, restrained state colors, spring motion. Hierarchy comes from whitespace and weight; color expresses state only.

### Color

Currently a fixed palette; Material You dynamic color is not implemented.

| Token | Value | Use |
|---|---|---|
| surface | `#FEFDFC` | Page background |
| hairline | `#E8E6E1` | Separators |
| accent | `#14624F` | Sole accent |
| warn / bad | `#8A5A12` / `#A32B22` | Warnings and signing conflicts only |

### Typography

- System font (MiSans / HarmonyOS Sans / Source Han Sans)
- Version numbers, sizes, and checksums in a monospace face so digit width changes do not shift layout

### Layout

- Card radius 18–20dp, 20dp page margins
- Floating tab bar 56dp tall, not edge-to-edge

### Motion

| Scene | Motion | Parameters |
|---|---|---|
| Detail enter | Panel slides in from the right, page scales down | spring(damping 0.90, stiffness 420) |
| List enter | Content rises and fades, staggered | 16dp, 240ms, 24ms stagger |
| Card tap | scale 0.97 | spring(damping 0.65, stiffness 950) |
| Device scan | Three rings expanding outward | 2400ms loop |

Only transform, alpha, and color animate — no layout animations. Target 60/120fps.

### Rules

1. One decision per screen — home answers "what do I install today", detail answers "which one do I download"
2. Secondary signals stay out of the first screen — stars, forks, watchers
3. Exactly one primary action
4. No decoration — no illustrative filler, no gradient stacking, at most one level of card nesting
5. Color expresses state, never hierarchy
6. Empty states offer a next action, not "no data"

## Tech choices

| Layer | Choice | State |
|---|---|---|
| UI | Jetpack Compose + Material 3 | Implemented |
| Architecture | data / ui, two layers | Implemented |
| Network | Ktor Client CIO + kotlinx.serialization | Implemented |
| Storage | JSON file cache under cacheDir, 6h TTL | Implemented |
| APK parsing | `getPackageArchiveInfo()` + `ZipFile` | Implemented |
| Device scan | `getInstalledPackages(GET_SIGNING_CERTIFICATES)` | Implemented |
| DI | None, constructed by hand | Implemented |
| Navigation | Single-Activity overlay plus a page stack, no NavHost | Implemented |
| Persistence | Room | Not used — `filesDir` + SharedPreferences + Keystore, which is enough at this size |
| DI framework | Hilt | Not currently needed |
| Language | Android resources + platform per-app locale | Implemented (zh/en, zero new dependencies) |
| Login | GitHub Device Flow | Planned |

## Comparison with RepoStore

Source: [F-Droid: com.samyak.repostore](https://f-droid.org/de/packages/com.samyak.repostore)

**Shared**

- Popular / recently updated / featured home sections
- Only repositories with real APKs
- No ads, no tracking, open source

**Different**

- RepoStore takes assets from the latest release only; FitHub covers all releases and the full fit matrix per release
- RepoStore has no installed-app scan and no signing-conflict detection
- RepoStore is repo-centric; FitHub adds user and organization browsing

**Unverified**: the differences above are based on the F-Droid listing, not a line-by-line comparison of the two apps.

## Problems to handle

1. **Signing conflict** — a locally built package is signed differently from the installed one; Android refuses the overwrite and silent failure means data loss
2. **API quota** — 60/h unauthenticated; needs caching, a quota indicator, and a login prompt
3. **APK parse failure** — packing tools and non-standard builds can defeat parsing; say so rather than guess
4. **Private assets** — `browser_download_url` can be a placeholder without authorization; needs a null check
5. **minSdk too high** — installing something that cannot run is the worst outcome; block it before download
6. **Architecture misjudgment** — a universal package runs on arm64 but wastes space; a 32-bit package fails on a 64-bit-only device
