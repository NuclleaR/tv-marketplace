# Implementation Plan: Install, Update and Manage Tracked Apps

**Branch**: `002-app-install-updates` | **Date**: 2026-11-20 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/002-app-install-updates/spec.md`

## Summary

This feature turns the tracked list from feature 001 into an app manager. Selecting a tracked app
opens App details. The details screen shows the latest eligible GitHub release, its notes, and
every APK in it, with the device's best match recommended. From there the user installs, updates,
opens or removes the app.

- **Downloads**: they run in a WorkManager foreground worker, which streams to app storage.
  Before install, the APK's size, its SHA-256 (when GitHub gives one) and its package name are
  checked.
- **Installs**: through a `PackageInstaller` session, with the confirmation shown while the app is
  in the foreground.
- **Installed state**: read from `PackageManager` by the package name. For apps installed before
  tracking, that name is learned from the APK's manifest through ranged requests, without a full
  download.
- **Update checks**: a 6-hour periodic worker, plus a manual check, both cheap thanks to ETags. A
  check only marks updates on Home; nothing installs on its own.
- **Update all**: installs the updates one at a time.
- **The user's package choice**: remembered as a version-independent name pattern.
- **Sources**: GitHub is the only one, behind a small source seam, with F-Droid next.

## Technical Context

**Language/Version**: Kotlin 2.4 (K2), JVM toolchain 21, bytecode 17 (`gradle/libs.versions.toml`)

**Primary Dependencies**:

- **Existing**: Compose + `androidx.tv:tv-material`, Navigation3, Metro DI, Ktor client (OkHttp),
  kotlinx.serialization, Room, DataStore, Coil 3.
- **New**: `androidx.work:work-runtime-ktx` (R12).

**Storage**:
- Room v5: new `tracked_app` columns, plus `app_release` and `install_record`;
- DataStore keys for the update check;
- APK files in `noBackupFilesDir/downloads/`.

Details are in [data-model.md](data-model.md).

**Testing**:

- **Unit tests**: kotlin.test on JUnit with power-assert, Turbine and Molecule for ViewModels.
- **Robolectric**: Room migration, `PackageInstaller` shadows, and Compose UI with D-pad key input.
- **Ktor**: `MockEngine` with recorded release fixtures.
- **WorkManager**: `work-testing`.
- **Fixtures**: an APK fixture corpus for `ApkManifestReader`.
- **Performance**: Macrobenchmark.

**Target Platform**: Android TV / Google TV. minSdk 28, targetSdk 36, compileSdk 37. Reference
device: Box R Lite 4K, API 34 (R13).

**Project Type**: mobile-app (Android TV), multi-module Gradle.

**Performance Goals**:
- **App details**: ≤ 300 ms from Select on a tile to a focused primary action, with stored
  release data.
- **Downloads**: progress updates at most every 250 ms, and 60 fps while scrolling the package
  list.

**Constraints**:
- **GitHub limit**: 60 anonymous API requests an hour, shared with Add app.
- **Heap**: ≤ 150 MB during downloads, which stream with no buffering of the whole file.
- **Remote**: D-pad only.
- **Installing**: no install without the user's action (FR-012).
- **Background starts**: the system blocks activity starts from the background on API 29+.

**Scale/Scope**: tens of tracked apps; APKs up to about 200 MB; 4 new screens or dialogs (App
details, Release notes, App settings, Remove) plus Update failures; Home extended.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Status |
|-----------|-------|--------|
| I. TV-First, Remote-Only UX | [contracts/tv-screens.md](contracts/tv-screens.md) defines initial focus, focus order and Back for every new screen and dialog. No text input anywhere. The system install and uninstall prompts are remote-driven. Errors are focusable states. | ✅ Pass |
| II. Test-First | Domain logic (`PackageChooser`, `VersionComparator`, name patterns, `ApkManifestReader`) is pure Kotlin with table and fixture tests. The releases client is tested against `MockEngine` fixtures, and the installer through fakes and Robolectric shadows. Every story has a D-pad UI test. No test uses the live network. | ✅ Pass |
| III. Modular, Layered | A new feature `:features:appdetails`. A new `:core:installer:api`/`:impl`, a contract that features use through fakes. Domain types stay pure in `:core:domain`. Features don't depend on each other. Metro DI. | ✅ Pass |
| IV. Performance Budgets | Downloads stream off the main thread. Release data is stored, so details open without the network. There is a new `AppDetailsBenchmark`, and the Baseline Profile gains Home → details. | ✅ Pass |
| V. Simplicity & Observability | The one new library, WorkManager, is justified in R12. The release notes use GitHub's HTML and Compose, with no Markdown library. `AppLog` records events, statuses and enum reasons, never repository names, URLs or file names. | ✅ Pass, with the justified items in Complexity Tracking |
| Stack constraints | Kotlin, Compose for TV, coroutines and Flow, version catalog. The leanback feature stays required. | ✅ Pass |

Post-design re-check (after Phase 1): still passing. The deviations are in Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/002-app-install-updates/
├── plan.md               # This file
├── research.md           # Phase 0: decisions R1–R13
├── data-model.md         # Phase 1: domain types, Room v5, states
├── quickstart.md         # Phase 1: end-to-end validation guide
├── contracts/
│   ├── tv-screens.md     # Screens, focus and Back
│   ├── github-releases.md# Releases API, downloads, ranged manifest reads
│   └── system-install.md # PackageInstaller / PackageManager contract
└── tasks.md              # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
core/domain/                  # + AppSource, Release, Package, PackageChooser, PackageChoice,
                              #   PackagePattern, VersionComparator, ApkManifestReader, AppStatus
core/data/api/                # + ReleaseSource, ReleasesRepository, UpdateChecker, UpdateCheckState;
                              #   TrackedApp gains packageName, includePrereleases, packagePattern
core/data/impl/               # + GitHubReleaseSource (Ktor), Room v5 (app_release, install_record,
                              #   new columns, schema 5.json), UpdateCheckWorker, check scheduling
core/installer/api/           # NEW: InstalledApps, InstallPermission, PackageInspector, Installer,
                              #   Downloads (start/cancel/observe), InstallQueue, outcomes
core/installer/impl/          # NEW: PackageInstaller sessions, InstallResultReceiver, PackageManager
                              #   reader + package broadcasts, DownloadWorker (WorkManager),
                              #   manifest-reading ranged fetch
core/testing/                 # + FakeInstalledApps, FakeInstaller, FakeDownloads, FakeReleases,
                              #   release/asset fixture builders
core/ui/                      # + StatusLine/ProgressLine components shared by Home tiles and details
core/strings/                 # + en/uk strings for everything above
features/appdetails/          # NEW: App details, package list, Release notes, App settings, Remove,
                              #   their ViewModels and previews
features/home/                # tiles show AppStatus; updates row, Update all, Check for updates,
                              #   Update failures list; tile Select → AppDetails
navigation/                   # + Destination.AppDetails(appId), ReleaseNotes(appId), UpdateFailures
app/                          # manifest permissions, Metro WorkerFactory, confirmation launcher in
                              #   MainActivity, periodic check enqueue at start
baselineprofile/              # + AppDetailsBenchmark, Home → details journey
```

Dependency direction is unchanged:
`app → features:* → navigation → core:*`; `:core:installer:impl` and `:core:data:impl` only from
`:app`. Both `:features:home` and `:features:appdetails` use `:core:installer:api` and
`:core:data:api`. `checkModuleDependencies.kt` gains the new modules.

**Structure Decision**:
- **Install and download logic** live in core, not in a feature, because Home (Update all, tile
  progress) and App details both use it. That is CLAUDE.md's "extract when a second feature needs
  it", known up front.
- **`:core:installer`** is split into api and impl because features and data are tested against
  fakes, while the impl is Android-system-heavy.
- **Update checks** stay in `:core:data`. They are data fetching and storing, with the worker next
  to the repository it drives.

## Key flows

1. **Open details**:
   1. Home tile → `Destination.AppDetails(appId)`.
   2. `AppDetailsViewModel` combines:
      - the tracked app, the stored `app_release` and `install_record`;
      - `InstalledApps.get(packageName)`;
      - `Downloads.observe(appId)`.
   3. `PackageChooser.choose(release.packages, abis, pattern)` and
      `AppStatus.of(...)` give the view state.
   4. If the release is older than 10 minutes, a background refresh of this app runs.
2. **Install**:
   1. **Start**: primary action or a package row → `Downloads.start(appId, package)`. That is
      unique work `download-<appId>`, a foreground worker streaming to `.part`. It then verifies
      the size, the SHA-256 and the parsed APK.
   2. **Hand over**: `Installer.install(...)` returns `NeedsPermission`, or opens a session and
      commits. `STATUS_PENDING_USER_ACTION` goes through `pendingConfirmation`, which
      `MainActivity` launches in the foreground.
   3. **Result**: `Installed` → write `install_record`, set `packageName`, delete the file, and
      set or clear `packagePattern` (R3).
3. **Check**:
   1. A periodic or manual worker calls `UpdateChecker.check()`, under the mutex.
   2. Each app goes to `ReleaseSource.latest(app, etag)`, and the result is stored at once.
   3. A rate limit stops the check and stores `resumeAt`.
   4. Apps without a known package name get one manifest read (R4).
4. **Update all**: Home collects the `UpdateAvailable` apps → `InstallQueue.run(appIds)`, which runs
   flow 2 for each in turn. On API 31+, `allowSilentUpdate` is set for apps this app installed.
5. **Remove**:
   1. Cancel the download work and delete the files.
   2. Only when the user chose to, call `Installer.uninstall(packageName)`.
   3. Delete the `tracked_app` row, whatever the uninstall outcome (US4 #2).

## Risks

- **Package-name learning can fail** (R4): the CDN might ignore `Range`, or the zip might be
  unusual. Then a pre-installed app shows as "Not installed" until the first install through this
  app, which updates it in place when the signatures match. The fallback is logged, so its rate is
  visible.
- **Version comparison can be wrong** for exotic tags when no install record exists (R10). The
  rule is publication date plus "different", so the worst case is an "update" offer that
  reinstalls the same version, never a downgrade.
- **Some TV builds lack the unknown-sources screen**: the app falls back to Security settings, and
  the text explains where to find the permission.
- **Manufacturer TV launchers can kill workers**: downloads run as a foreground worker with a
  notification. Checks are periodic and simply run later.
- **`QUERY_ALL_PACKAGES`** would matter only if the app were ever submitted to Play. It isn't
  (CI publishes GitHub releases).

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| New library: WorkManager | Downloads that survive leaving the app (FR-006); scheduled checks with network constraints (FR-012) | A hand-written foreground service plus `AlarmManager` duplicates WorkManager's persistence, constraints and foreground handling (R6, R8) |
| `QUERY_ALL_PACKAGES` and `REQUEST_INSTALL_PACKAGES`/`REQUEST_DELETE_PACKAGES` permissions | Installed state of arbitrary tracked apps (FR-010); install and uninstall (FR-008, FR-017) | `<queries>` must list fixed packages; there is no install path without the permission (R5, R7) |
| Ranged-request manifest reader (`ApkManifestReader`) | Recognize apps installed before tracking (US3, SC-006) without downloading them | Label heuristics misidentify forks; a full download costs tens of MB per app (R4) |
| Second foreground service type (`dataSync`) next to 001's `specialUse` | Background downloads must run as foreground work on API 31+ | A plain background worker is stopped after 10 minutes and can't show progress (R6) |
