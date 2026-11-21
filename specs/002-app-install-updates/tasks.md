---

description: "Task list for feature 002: install, update and manage tracked apps"
---

# Tasks: Install, Update and Manage Tracked Apps

**Input**: Design documents from `/specs/002-app-install-updates/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: Required. Constitution II makes TDD mandatory, so every story starts with failing tests:
- unit tests for domain, data and installer logic;
- Molecule + Turbine for ViewModels;
- a Compose UI test driving each story with D-pad keys.

**Organization**: Tasks are grouped by user story, so each story can be implemented and tested
independently.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: US1–US5 map to User Stories 1–5 in spec.md

## Path Conventions

This is a multi-module Gradle project at the repository root (plan.md → Project Structure).
`<pkg>` means `com/serhii/tvmarketplace`, for example
`core/domain/src/main/kotlin/<pkg>/core/domain/`.

The project conventions from CLAUDE.md apply to every task:
- **DI and ViewModels**: Metro DI. ViewModels are plain classes with `@Composable fun viewState()`.
  `XScreen` gets its ViewModel via `retain { rootGraphAs<…>() }`, and the stateless `XView` holds
  the previews.
- **UI**: only standard tv-material controls; `focusRestorer()` before `focusGroup()`.
- **Strings**: every string goes in `core/strings` (`values/` + `values-uk/`).
- **Logs**: `AppLog` takes names, numbers and enums only.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: new modules, the new dependency, permissions and the worker plumbing.

- [X] T001 Add `androidx.work:work-runtime-ktx` and `androidx.work:work-testing` at the latest stable version (research.md R12) to `gradle/libs.versions.toml`
- [X] T002 Create modules `core/installer/api`, `core/installer/impl` and `features/appdetails`, each with a `build.gradle.kts` applying `convention.library` (+ `convention.compose` and Metro for `features/appdetails`, Metro for `core/installer/impl`), and include them in `settings.gradle.kts`
- [X] T003 Register the new modules in `build-logic/src/main/kotlin/checkModuleDependencies.kt`: `:core:installer:impl` only from `:app`; `:features:appdetails` depends on no other feature
- [X] T004 [P] Add the `REQUEST_INSTALL_PACKAGES`, `REQUEST_DELETE_PACKAGES`, `QUERY_ALL_PACKAGES` and `FOREGROUND_SERVICE_DATA_SYNC` permissions, and the merged `SystemForegroundService` with `foregroundServiceType="dataSync"` (contracts/system-install.md → Manifest additions), to `app/src/main/AndroidManifest.xml`
- [X] T005 Give WorkManager its dependencies through Metro:
  - **Worker factory**: an `@IntoMap` multibinding of assisted worker factories, assembled into a `WorkerFactory` (research.md R12), in `app/src/main/kotlin/<pkg>/di/WorkerFactory.kt`.
  - **Initialization**: `App` implements `Configuration.Provider`, and the default WorkManager initializer is removed in `app/src/main/AndroidManifest.xml`.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: the domain types, the storage, the releases source, the installer contract and the
navigation that every story uses.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

### Tests first

- [X] T006 [P] Write a failing Room 4 → 5 migration test in `core/data/impl/src/test/kotlin/<pkg>/core/data/impl/MigrationTest.kt`. It covers:
  - the new `tracked_app` columns and their defaults (data-model.md `tracked_app.sourceKind`, `tracked_app.packageName`, `tracked_app.includePrereleases`, `tracked_app.packagePattern`, `tracked_app.sourceGone`);
  - the new tables `app_release` and `install_record`, with ON DELETE CASCADE (data-model.md `app_release.appId`, `install_record.appId`);
  - existing rows survive with their values.
- [X] T007 [P] Write a failing `GitHubReleaseSourceTest` against `MockEngine`, using recorded fixtures in `core/data/impl/src/test/resources/github/releases/`. It lives in `core/data/impl/src/test/kotlin/<pkg>/core/data/impl/GitHubReleaseSourceTest.kt` and covers:
  - **Request**: the headers and the `If-None-Match` header (contracts/github-releases.md → List releases).
  - **Responses**: 200 → the first eligible release, skipping drafts and pre-releases unless opted in; an empty list → none; 304 → unchanged; 404 → `SourceGone`.
  - **Limits**: 403 with `X-RateLimit-Remaining: 0` → `RateLimited(reset)`; 429 with `Retry-After` → `RateLimited`.
  - **Errors**: an IO error or bad JSON → `NetworkError`.
  - **Assets**: mapped to `Package`, keeping APK assets only (data-model.md `Package.id`, `Package.fileName`, `Package.sizeBytes`, `Package.downloadUrl`, `Package.sha256` parsed from `digest`), and `body_html` to `Release.notesHtml`.
- [X] T008 [P] Write a failing table-driven `AppStatusTest` in `core/domain/src/test/kotlin/<pkg>/core/domain/AppStatusTest.kt`. It checks the first-match rule order and the `Failed` overlay, using a stub "is newer" function until US2 (data-model.md → AppStatus).

### Implementation

- [X] T009 [P] Implement `AppSource` (sealed, `GitHub(owner, repo)`, kind key `github`; research.md R11), `Release`, `Package` and `InstalledApp` (data-model.md → Domain types) in `core/domain/src/main/kotlin/<pkg>/core/domain/Releases.kt`
- [X] T010 Implement `AppStatus` and `FailureReason` with the derivation function in `core/domain/src/main/kotlin/<pkg>/core/domain/AppStatus.kt`, which makes T008 pass
- [X] T011 Implement the Room v5 schema, which makes T006 pass:
  - **Entities**: new columns on `TrackedAppEntity`, and new `AppReleaseEntity` and `InstallRecordEntity`, with `packagesJson` serialized via kotlinx.serialization (data-model.md `app_release.packagesJson`).
  - **DAOs**: `AppReleaseDao` and `InstallRecordDao`.
  - **Migration**: `AutoMigration(4, 5)`, with `schemas/…/5.json` exported.

  The entities and DAOs go in `core/data/impl/src/main/kotlin/<pkg>/core/data/impl/db/AppDatabase.kt`.
- [X] T012 Extend `TrackedApp` with `source: AppSource`, `packageName`, `includePrereleases`, `packagePattern` and `sourceGone`, and add `setPackageName` (never overwrite a different one: data-model.md → Validation rules), `setIncludePrereleases`, `setPackagePattern` and `setSourceGone`. The model goes in `core/data/api/src/main/kotlin/<pkg>/core/data/TrackedApps.kt`, the implementation in `core/data/impl/src/main/kotlin/<pkg>/core/data/impl/RoomRepositories.kt`; extend `RoomRepositoriesTest.kt` first.
- [X] T013 Implement the release fetching, which makes T007 pass:
  - **Contract**: a `ReleaseSource` interface (`latest(app, etag): ReleaseResult`) and `ReleaseResult` in `core/data/api/src/main/kotlin/<pkg>/core/data/Releases.kt`.
  - **GitHub**: `GitHubReleaseSource` over the existing Ktor client, with an `Accept: application/vnd.github.html+json` override (research.md R1), in `core/data/impl/src/main/kotlin/<pkg>/core/data/impl/GitHubReleaseSource.kt`.
- [X] T014 Implement `ReleasesRepository`, which observes and stores the latest release per app along with its ETag and `checkedAt` (data-model.md `app_release.etag`, `app_release.checkedAt`) and observes `install_record`. The interface goes in `core/data/api/src/main/kotlin/<pkg>/core/data/Releases.kt`, the implementation in `core/data/impl/src/main/kotlin/<pkg>/core/data/impl/RoomReleasesRepository.kt`, with a Robolectric test next to `RoomRepositoriesTest.kt`.
- [X] T015 [P] Define the installer contract in `core/installer/api/src/main/kotlin/<pkg>/core/installer/`:
  - **Interfaces**: `InstalledApps`, `InstallPermission`, `PackageInspector` and `Installer`, as in contracts/system-install.md → Interfaces.
  - **Outcomes**: `InstallOutcome` and `UninstallOutcome`.
  - **Downloads**: a `Downloads` interface (`start(appId, package)`, `cancel(appId)`, `observe(appId): Flow<Download?>`), with `Download` and its state (data-model.md → Download).
  - **Queue**: an `InstallQueue` interface.
- [X] T016 [P] Add the test doubles to `core/testing/src/main/kotlin/<pkg>/core/testing/Fakes.kt`:
  - **Installer fakes**: `FakeInstalledApps`, `FakeInstallPermission`, `FakeInstaller`, with a scripted outcome and recorded calls, and `FakeDownloads`, with manually advanced states.
  - **Releases fakes**: `FakeReleaseSource` and `FakeReleasesRepository`.
  - **Builders**: `release()` and `pkg()`.
- [X] T017 [P] Add the destinations `AppDetails(appId: Long)`, `ReleaseNotes(appId: Long)` and `UpdateFailures` to `navigation/src/main/kotlin/<pkg>/navigation/Destination.kt`
- [X] T018 [P] Add `StatusLine(status)` and `ProgressLine(fraction)` components, with focus previews, to `core/ui/src/main/kotlin/<pkg>/core/ui/Components.kt`. They are shared by Home tiles and App details (contracts/tv-screens.md).
- [X] T019 [P] Add en/uk strings for every status in data-model.md → AppStatus and every `FailureReason`, to `core/strings/src/main/res/values/strings.xml` and `values-uk/strings.xml`

**Checkpoint**: The foundation is ready, and user stories can start.

---

## Phase 3: User Story 1 - Install a tracked app from its latest release (Priority: P1) 🎯 MVP

**Goal**: Home tile → App details → Install → download with progress → system prompt →
installed, with Open.

**Independent Test**: Track a repository with an APK release, open it from Home, install it,
accept the prompt, and launch it from App details (quickstart §2).

### Tests for User Story 1 ⚠️ (write first, must fail)

- [X] T020 [P] [US1] Record a fixture corpus of at least 20 asset lists from real TV-capable GitHub apps, as `core/domain/src/test/resources/packages/*.json`. Each file holds the expected recommended package for `arm64-v8a,armeabi-v7a`, for `armeabi-v7a` only, and for `x86`.
- [X] T021 [P] [US1] Write a failing `PackageChooserTest` in `core/domain/src/test/kotlin/<pkg>/core/domain/PackageChooserTest.kt`. It covers:
  - **Filtering**: non-package files are excluded.
  - **ABI tokens** and universal packages.
  - **Ranking**: device ABI order, then TV over mobile, then release over debug, and the tie order (research.md R2).
  - **No compatible package** → `recommended == null`.
  - **The corpus**: at least 95 % of its expectations match (SC-002).
  - **Result fields**: data-model.md `PackageChoice.candidates`, `PackageChoice.recommended`.
- [X] T022 [P] [US1] Write a failing `DownloadWorkerTest` using `work-testing` and `MockEngine` in `core/installer/impl/src/test/kotlin/<pkg>/core/installer/impl/DownloadWorkerTest.kt`. It covers:
  - **Progress**: published through `setProgress`, with throttling (research.md R6).
  - **Disk**: the `.part` file is renamed when complete; cancel and failure delete it.
  - **Verification**: a size mismatch or SHA-256 mismatch → `Corrupt`.
  - **Space**: not enough space → `NoSpace(neededBytes)`.
  - **Redirects**: a redirect to a host off the allow-list → `Corrupt` (contracts/github-releases.md → Download an asset).
- [X] T023 [P] [US1] Write a failing Robolectric `PackageInstallerInstallerTest`, using `ShadowPackageInstaller`, in `core/installer/impl/src/test/kotlin/<pkg>/core/installer/impl/PackageInstallerInstallerTest.kt`. It covers:
  - **Before a session opens**: `NeedsPermission` when installs aren't allowed; `DifferentApp` when the expected package differs.
  - **Session**: one is created and committed.
  - **Status mapping**: every status mapped as in contracts/system-install.md → Session flow; `PENDING_USER_ACTION` is published to `pendingConfirmation`.
  - **Ordering**: one install at a time.
- [X] T024 [P] [US1] Write a failing `AppDetailsViewModelTest` (Molecule + Turbine, fakes) in `features/appdetails/src/test/kotlin/<pkg>/features/appdetails/AppDetailsViewModelTest.kt`. It covers:
  - **Actions**: the primary action for each status (contracts/tv-screens.md → App details).
  - **Package list**: it carries the recommended package.
  - **Install**: Install → Downloading → ReadyToInstall → Installed → Open.
  - **Cancel and Retry.**
  - **Permission**: the panel shows, and the install continues after the permission is granted.
  - **Other package**: installing a non-recommended package.
  - **Install record**: written and `packageName` learned after `Installed`.
  - **Refresh**: a release older than 10 minutes is refreshed in the background.
- [X] T025 [P] [US1] Write failing D-pad UI tests in `features/appdetails/src/test/kotlin/<pkg>/features/appdetails/AppDetailsViewTest.kt`:
  - **Focus**: the primary action has initial focus; Left/Right move through the buttons row; Down enters the package list on the recommended row.
  - **Package rows**: incompatible rows are disabled.
  - **States**: the permission panel has **Open settings** focused; failure states keep a focused Retry.
  - **Back**: returns to Home.
- [X] T026 [P] [US1] Write a failing `ReleaseNotesViewTest` in `features/appdetails/src/test/kotlin/<pkg>/features/appdetails/ReleaseNotesViewTest.kt`:
  - **Rendering**: HTML renders with no focusable links.
  - **Scrolling**: Up/Down scroll, and the end doesn't wrap.
  - **Back**: returns to App details with focus on **Release notes** (contracts/tv-screens.md → Release notes).
- [X] T027 [P] [US1] Extend `HomeViewModelTest.kt` and `HomeViewTest.kt` in `features/home/src/test/kotlin/<pkg>/features/home/` with failing cases:
  - Selecting a tile navigates to `AppDetails(appId)`, and Back refocuses that tile.
  - A tile shows its status line and download progress.

### Implementation for User Story 1

- [X] T028 [P] [US1] Implement `PackageChooser.choose(packages, abis, pattern = null)` → `PackageChoice` in `core/domain/src/main/kotlin/<pkg>/core/domain/PackageChooser.kt`. This makes T021 pass. The pattern stays unused until US5.
- [X] T029 [P] [US1] Implement `AndroidPackageInspector` (`getPackageArchiveInfo`) and `AndroidInstallPermission` (`canRequestPackageInstalls`, with the settings intent and the Security-settings fallback: research.md R7) in `core/installer/impl/src/main/kotlin/<pkg>/core/installer/impl/`
- [X] T030 [US1] Implement `PackageInstallerInstaller` and `InstallResultReceiver`, and declare the receiver in `core/installer/impl/src/main/AndroidManifest.xml`. They go in `core/installer/impl/src/main/kotlin/<pkg>/core/installer/impl/` and make T023 pass. They cover:
  - **Session**: the flow in contracts/system-install.md → Session flow, with a `Mutex` for one install at a time.
  - **Silent update**: `allowSilentUpdate` sets `setRequireUserAction` on API 31+.
- [X] T031 [US1] Implement `WorkManagerDownloads`, `DownloadWorker` and the assisted factory in `core/installer/impl/src/main/kotlin/<pkg>/core/installer/impl/`, which makes T022 pass. They cover:
  - **Worker**: a foreground worker with a progress notification, as unique work `download-<appId>`.
  - **Files**: `noBackupFilesDir/downloads/` (data-model.md → Download), with leftover `.part` files deleted at startup.
  - **Verification**: size, SHA-256, then parse.
- [X] T032 [US1] Implement `AndroidInstalledApps.get(packageName)`: `getPackageInfo` plus the installer of record via `getInstallSourceInfo` (data-model.md `InstalledApp.installedByThisApp`), in `core/installer/impl/src/main/kotlin/<pkg>/core/installer/impl/AndroidInstalledApps.kt`. `changes()` emits on `ON_START` only, until US3.
- [X] T033 [US1] Clean up sessions on startup: abandon those with no matching download record, delete their files (contracts/system-install.md → Process death), and call this from `App.onCreate`. It goes in `core/installer/impl/src/main/kotlin/<pkg>/core/installer/impl/SessionCleanup.kt`.
- [X] T034 [US1] Collect `Installer.pendingConfirmation` in `MainActivity` while it is started, launch `EXTRA_INTENT`, and call `confirmationShown`, in `app/src/main/kotlin/<pkg>/MainActivity.kt`
- [X] T035 [US1] Implement the App details state and logic, which makes T024 pass:
  - **State**: `AppDetailsViewState` with header, status, primary action, packages, notes preview and permission panel.
  - **ViewModel**: `AppDetailsViewModel` (`@AssistedInject`, appId) combines the tracked app, the release, the install record, `InstalledApps`, `Downloads` and `PackageChooser`.
  - **After an install**: it writes `install_record` (data-model.md `install_record.tag`, `install_record.versionCode`, `install_record.packageFileName`), sets `packageName`, and deletes the file.

  Both go in `features/appdetails/src/main/kotlin/<pkg>/features/appdetails/`.
- [X] T036 [US1] Implement the App details UI and its navigation entry, which makes T025 pass. It follows contracts/tv-screens.md → App details:
  - **Screens**: `AppDetailsScreen` and `AppDetailsView`, with previews for every primary-action state and for the permission panel.
  - **Navigation**: the `NavEntryProvider` `@IntoSet` entry.
  - **Open**: launches the installed app with `getLeanbackLaunchIntentForPackage`, falling back to `getLaunchIntentForPackage`.

  These go in `features/appdetails/src/main/kotlin/<pkg>/features/appdetails/`.
- [X] T037 [US1] Implement `ReleaseNotesScreen` and `ReleaseNotesView` (`AnnotatedString.fromHtml` with link annotations stripped; research.md R9) in `features/appdetails/src/main/kotlin/<pkg>/features/appdetails/notes/`, which makes T026 pass
- [X] T038 [US1] Make Home tiles open `AppDetails`, show `StatusLine` and `ProgressLine`, and restore focus to the opener. This goes in `features/home/src/main/kotlin/<pkg>/features/home/` (`HomeViewModel.kt`, `HomeViewState.kt`, `HomeView.kt`) and makes T027 pass.
- [X] T039 [US1] Wire `:features:appdetails` and `:core:installer:impl` into `app/build.gradle.kts` and the Metro graph in `app/src/main/kotlin/<pkg>/di/AppGraph.kt`, then add the en/uk strings for App details and Release notes to `core/strings/src/main/res/values*/strings.xml`

**Checkpoint**: Installing works end to end, and that is the MVP.

---

## Phase 4: User Story 2 - Know about updates and install them (Priority: P1)

**Goal**: scheduled and manual checks, update marks on Home, Update and Update all.

**Independent Test**: Install an older release, check for updates, see it on Home, update it
(quickstart §3).

### Tests for User Story 2 ⚠️ (write first, must fail)

- [X] T040 [P] [US2] Write a failing table-driven `VersionComparatorTest` in `core/domain/src/test/kotlin/<pkg>/core/domain/VersionComparatorTest.kt`:
  - **Install record**: when a record exists and the version code is unchanged, the comparison uses publication time.
  - **Version formats**: `v` prefixes, semver with pre-release suffixes, four-part versions, dates, commit hashes.
  - **Fallback**: when names aren't comparable, the release counts as newer when it differs and was published after `lastUpdateTime`.
  - **Downgrades**: never offered (research.md R10).
- [X] T041 [P] [US2] Write a failing `UpdateCheckerTest` (fakes, TestScope) in `core/data/impl/src/test/kotlin/<pkg>/core/data/impl/UpdateCheckerTest.kt`:
  - **Order and storage**: apps are checked oldest-checked first, and each result is stored as soon as it arrives.
  - **Concurrency**: a mutex makes overlapping calls join the running check.
  - **Rate limit**: the check stops, keeps previous results, and stores `resumeAt` (data-model.md `update_check.resumeAt`). It also stores `lastCheckFinishedAt`.
  - **Source gone**: a 404 sets `sourceGone`.
  - **Pre-releases**: they count only for opted-in apps (FR-014).
- [X] T042 [P] [US2] Write a failing `UpdateCheckWorkerTest` (`work-testing`) in `core/data/impl/src/test/kotlin/<pkg>/core/data/impl/UpdateCheckWorkerTest.kt`:
  - **Scheduling**: periodic work every 6 hours with a network constraint, enqueued with `KEEP`.
  - **Manual checks**: a manual check is unique.
  - **Rate limit**: the scheduled check doesn't run before `resumeAt` (research.md R8).
- [X] T043 [P] [US2] Write a failing `InstallQueueTest` in `core/installer/impl/src/test/kotlin/<pkg>/core/installer/impl/InstallQueueTest.kt`:
  - **Order**: updates run one at a time, and the next starts after the previous one's final status.
  - **Progress and failures**: overall progress, with failures collected along with their reasons.
  - **Stop**: **Stop after this one** stops the queue.
  - **Silent update**: `allowSilentUpdate` only for apps whose installer of record is this app.
- [X] T044 [P] [US2] Extend the Home tests with failing cases for the updates row, in `features/home/src/test/kotlin/<pkg>/features/home/HomeViewModelTest.kt` and `HomeViewTest.kt` (contracts/tv-screens.md → Home):
  - **Visibility**: the count, and the row shown only when there are updates or a check is running.
  - **Buttons**: Update all, Check for updates disabled while checking, "try after" when requests are limited.
  - **Queue**: "Updating 2 of 3", then failures with **Show**.
  - **Focus**: Down from the top bar reaches the row.
- [X] T045 [P] [US2] Write failing `UpdateFailuresViewTest` D-pad tests, in which the first row is focused, Select opens details, and Back returns to **Show**. They go in `features/home/src/test/kotlin/<pkg>/features/home/UpdateFailuresViewTest.kt`.
- [X] T046 [P] [US2] Add failing cases to `AppDetailsViewModelTest.kt`:
  - **Update**: shown when the app is `UpdateAvailable`.
  - **No package**: `NoPackage` explains that the latest release has no package for this device.
  - **Source unavailable**: `Unavailable` offers Remove.

### Implementation for User Story 2

- [X] T047 [P] [US2] Implement `VersionComparator` in `core/domain/src/main/kotlin/<pkg>/core/domain/VersionComparator.kt`, which makes T040 pass, and use it for `UpdateAvailable` in `AppStatus.kt`
- [X] T048 [US2] Implement `UpdateChecker` and the `UpdateCheckState` flow (running, last finished, `resumeAt`), with the DataStore keys from data-model.md → DataStore `update_check`. The interface goes in `core/data/api/src/main/kotlin/<pkg>/core/data/Updates.kt`, the implementation in `core/data/impl/src/main/kotlin/<pkg>/core/data/impl/DefaultUpdateChecker.kt`, and it makes T041 pass.
- [X] T049 [US2] Implement `UpdateCheckWorker` and `UpdateCheckScheduler` (periodic plus manual) in `core/data/impl/src/main/kotlin/<pkg>/core/data/impl/UpdateCheckWorker.kt`, and enqueue the periodic check in `App.onCreate`. This makes T042 pass.
- [X] T050 [US2] Implement `DefaultInstallQueue` in `core/installer/impl/src/main/kotlin/<pkg>/core/installer/impl/DefaultInstallQueue.kt`. It runs `Downloads.start` followed by `Installer.install` for each app, in order, and makes T043 pass. It lives in process memory only (data-model.md → InstallQueue).
- [X] T051 [US2] Add the updates row and Check for updates to Home: update count, Update all / Stop after this one, progress text, the "try after" message, and "checked N h ago". This goes in `features/home/src/main/kotlin/<pkg>/features/home/` and makes T044 pass.
- [X] T052 [US2] Implement the Update failures screen and its `NavEntryProvider` entry in `features/home/src/main/kotlin/<pkg>/features/home/failures/`, which makes T045 pass
- [X] T053 [US2] Add the Update, NoPackage and Unavailable states to App details in `features/appdetails/src/main/kotlin/<pkg>/features/appdetails/`, which makes T046 pass, and add the en/uk strings for the updates row, failures and these states

**Checkpoint**: US1 and US2 together are the full P1 product.

---

## Phase 5: User Story 3 - See what is installed, including apps installed before tracking (Priority: P2)

**Goal**: The installed state is correct for apps installed by other means, and it stays current.

**Independent Test**: Install an app with `adb`, track its repository, and see "Installed
<version>" without a download. Uninstall it outside the app, and Home shows "Not installed"
(quickstart §4).

### Tests for User Story 3 ⚠️ (write first, must fail)

- [X] T054 [P] [US3] Add small real APK fixtures and write a failing `ApkManifestReaderTest` in `core/domain/src/test/kotlin/<pkg>/core/domain/ApkManifestReaderTest.kt`, with the fixtures in `core/domain/src/test/resources/apk/`. It covers:
  - **Zip structure**: locating the end-of-central-directory record, the central directory, and the local header of `AndroidManifest.xml`.
  - **Manifest**: inflating it and reading the root `package` attribute from the binary XML.
  - **Errors**: a malformed zip → null (research.md R4).
- [X] T055 [P] [US3] Write a failing `RangedManifestFetcherTest` against `MockEngine` in `core/installer/impl/src/test/kotlin/<pkg>/core/installer/impl/RangedManifestFetcherTest.kt`:
  - **Ranged reads**: a sequence of 206 responses → the package name, in at most 3 requests.
  - **No range support**: a 200 → abandoned after 64 KB.
  - **Hosts**: the redirect host allow-list (contracts/github-releases.md → Read the manifest of an asset).
- [X] T056 [P] [US3] Write a failing Robolectric `AndroidInstalledAppsTest`, using `ShadowPackageManager`, in `core/installer/impl/src/test/kotlin/<pkg>/core/installer/impl/AndroidInstalledAppsTest.kt`:
  - **Version**: the installed version is read.
  - **Changes**: `changes()` emits on `PACKAGE_ADDED`, `PACKAGE_REPLACED` and `PACKAGE_REMOVED`, and on `ON_START`.
- [X] T057 [P] [US3] Add failing cases to `UpdateCheckerTest.kt` and `AppDetailsViewModelTest.kt`:
  - **Learning the package name**: for apps without a known one, it is read once through the manifest reader, and an existing different name is never replaced (data-model.md `tracked_app.packageName`).
  - **Uninstalled elsewhere**: a tracked app uninstalled outside this app becomes `NotInstalled`.

### Implementation for User Story 3

- [X] T058 [P] [US3] Implement `ApkManifestReader` (pure Kotlin, `java.util.zip.Inflater`) in `core/domain/src/main/kotlin/<pkg>/core/domain/ApkManifestReader.kt`, which makes T054 pass
- [X] T059 [US3] Implement `RangedManifestFetcher` over the Ktor client in `core/installer/impl/src/main/kotlin/<pkg>/core/installer/impl/RangedManifestFetcher.kt`, and expose it as `PackageIdentity.learn(package)` in `core/installer/api`. This makes T055 pass.
- [X] T060 [US3] Complete `AndroidInstalledApps.changes()` with a runtime-registered package broadcast receiver plus the process `ON_START` (research.md R5), which makes T056 pass
- [X] T061 [US3] Call `PackageIdentity.learn` from `DefaultUpdateChecker` for apps whose package name is unknown, and from `AppDetailsViewModel` when details open. Refresh the Home and details state on `InstalledApps.changes()`. This makes T057 pass.

**Checkpoint**: The installed state matches the TV's real state (SC-006).

---

## Phase 6: User Story 4 - Remove a tracked app (Priority: P2)

**Goal**: Remove from the list, and optionally uninstall through the system.

**Independent Test**: With **Keep on TV**, the app leaves the list and stays installed. With
**Uninstall too**, the system prompt appears; declining still removes the app from the list
(quickstart §5).

### Tests for User Story 4 ⚠️ (write first, must fail)

- [X] T062 [P] [US4] Add failing uninstall cases to `PackageInstallerInstallerTest.kt`: the uninstall status maps to `Uninstalled`, `CancelledByUser` or `Failed` (contracts/system-install.md → Session flow).
- [X] T063 [P] [US4] Add a failing `remove(appId)` test to `RoomRepositoriesTest.kt`: the `app_release` and `install_record` rows cascade (data-model.md → Validation rules).
- [X] T064 [P] [US4] Write a failing `RemoveDialogTest` (ViewModel plus D-pad) in `features/appdetails/src/test/kotlin/<pkg>/features/appdetails/RemoveDialogTest.kt`:
  - **Focus**: **Keep on TV** is focused; when the app isn't installed, **Remove** is.
  - **Uninstall too**: an accepted prompt uninstalls and removes the app; a declined prompt removes it from the list only.
  - **Cleanup**: the download is cancelled and its files deleted.
  - **After removal**: Home focuses the neighbouring tile, else **Add app**.
  - **Back**: behaves like Cancel.

### Implementation for User Story 4

- [X] T065 [P] [US4] Add `TrackedAppsRepository.remove(appId)` in `core/data/api/src/main/kotlin/<pkg>/core/data/TrackedApps.kt` and `core/data/impl/src/main/kotlin/<pkg>/core/data/impl/RoomRepositories.kt`, which makes T063 pass
- [X] T066 [P] [US4] Implement `Installer.uninstall` with `PackageInstaller.uninstall` through `InstallResultReceiver` in `core/installer/impl/src/main/kotlin/<pkg>/core/installer/impl/PackageInstallerInstaller.kt`, which makes T062 pass
- [X] T067 [US4] Implement the Remove dialog and its wiring from App details in `features/appdetails/src/main/kotlin/<pkg>/features/appdetails/remove/`, which makes T064 pass, and restore Home's focus after a removal in `features/home/src/main/kotlin/<pkg>/features/home/HomeView.kt`. Add the en/uk strings.

**Checkpoint**: The list stays manageable without uninstalling anything by surprise.

---

## Phase 7: User Story 5 - Per-app settings (Priority: P3)

**Goal**: A pre-releases opt-in, and a remembered package choice that carries over to future
releases.

**Independent Test**: Install a non-recommended package; the next release's update uses the
matching package. **Use automatic choice** goes back to the recommended one (quickstart §6).

### Tests for User Story 5 ⚠️ (write first, must fail)

- [X] T068 [P] [US5] Write a failing `PackagePatternTest` in `core/domain/src/test/kotlin/<pkg>/core/domain/PackagePatternTest.kt`:
  - **Pattern**: the tag and version name are removed first, then version-like runs are replaced (research.md R3).
  - **Matching**: `App_1.2.3_arm64.apk` → `App_1.3.0_arm64.apk`; `app-v2.0-tv-release.apk` → `app-v2.1-tv-release.apk`.
  - **Separate patterns**: different flavours stay different.
- [X] T069 [P] [US5] Add failing cases to `PackageChooserTest.kt`, using data-model.md `PackageChoice.chosen` and `PackageChoice.choiceLost`:
  - **A matching pattern**: it gives `chosen`.
  - **No match**: the recommended package is chosen, with `choiceLost = true`.
- [X] T070 [P] [US5] Add failing cases to `AppDetailsViewModelTest.kt`, `AppDetailsViewTest.kt` and `InstallQueueTest.kt`:
  - **Setting the choice**: installing a non-recommended package sets `packagePattern`, and installing the recommended one clears it (data-model.md `tracked_app.packagePattern`).
  - **List**: the "Your choice" tag, the **Use automatic choice** row, and the choice-lost note.
  - **Update all**: it uses the remembered package.
- [X] T071 [P] [US5] Write a failing `AppSettingsDialogTest` (ViewModel plus D-pad) in `features/appdetails/src/test/kotlin/<pkg>/features/appdetails/AppSettingsDialogTest.kt`:
  - **Focus**: **Include pre-releases** is focused.
  - **Toggle**: it stores `includePrereleases` (data-model.md `tracked_app.includePrereleases`) and starts a check of this app only.
  - **Back**: focus returns to **Settings**.

### Implementation for User Story 5

- [X] T072 [P] [US5] Implement `PackagePattern` in `core/domain/src/main/kotlin/<pkg>/core/domain/PackagePattern.kt`, and use the pattern argument in `PackageChooser.kt`. This makes T068 and T069 pass.
- [X] T073 [US5] Set and clear `packagePattern` on install, and pass it through App details, Home and `DefaultInstallQueue`, in `features/appdetails/…/AppDetailsViewModel.kt` and `core/installer/impl/…/DefaultInstallQueue.kt`. Add the "Your choice" tag, **Use automatic choice** and the choice-lost note to `AppDetailsView.kt`. This makes T070 pass.
- [X] T074 [US5] Implement the App settings dialog (`ListItem` with a trailing `Switch`) and a single-app check (`UpdateChecker.check(appId)`) in `features/appdetails/src/main/kotlin/<pkg>/features/appdetails/settings/` and `core/data/impl/…/DefaultUpdateChecker.kt`. This makes T071 pass. Add the en/uk strings.

**Checkpoint**: All five stories work independently.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T075 [P] Add `AppLog` events (`download_*`, `install_result`, `update_check`, `manifest_read`, `rate_limited`) with status codes, counts and enum reasons only (contracts/github-releases.md → Privacy and logging), across `core/installer/impl` and `core/data/impl`
- [X] T076 [P] Add `AppDetailsBenchmark` (open details from Home, scroll the package list) and the Home → details journey to `baselineprofile/src/main/kotlin/<pkg>/baselineprofile/`. Then regenerate the Baseline Profile on the TV (quickstart §8).
- [X] T077 [P] Update `CLAUDE.md` with an Install and updates section: the modules, the WorkManager workers and factory, and how to test installs on a device.
- [X] T078 Run `./gradlew formatKotlin lintKotlin` and the full CI command from CLAUDE.md, and fix everything until it is clean
- [ ] T079 Run quickstart §2–§8 on the agent emulator, the Legion TV (API 29) and the user's TV (API 34), and record the results and the open items in this file's notes

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (Phase 1)**: none.
- **Foundational (Phase 2)**: after Setup; it blocks all stories.
- **US1 (Phase 3)**: after Foundational. It is the MVP.
- **US2 (Phase 4)**: after Foundational, and it uses US1's installer, downloads and App details:
  - Update reuses the install flow;
  - Update all reuses `Downloads` and `Installer`.

  Its tests can be written alongside US1.
- **US3 (Phase 5)**: after Foundational. Its domain and installer parts (T054–T060) are
  independent of US1 and US2. T061 touches the update checker (US2) and App details (US1).
- **US4 (Phase 6)**: after US1 for App details. It is independent of US2 and US3.
- **US5 (Phase 7)**: after US1. Its Update all part (T073) needs US2's `DefaultInstallQueue`.
- **Polish (Phase 8)**: after the stories that are in scope.

### Within each story

The tests are written and seen failing first. Then come the domain parts, then data and installer,
then the ViewModels, then the UI and wiring, and the strings with the UI.

## Parallel Examples

### Phase 2

```text
T006 MigrationTest · T007 GitHubReleaseSourceTest · T008 AppStatusTest   (tests, together)
T009 domain types · T015 installer api · T016 fakes · T017 destinations · T018 ui · T019 strings
```

### User Story 1

```text
T020 corpus · T021 PackageChooserTest · T022 DownloadWorkerTest · T023 InstallerTest
T024 AppDetailsViewModelTest · T025 AppDetailsViewTest · T026 ReleaseNotesViewTest · T027 Home tests
then T028 PackageChooser ∥ T029 inspector/permission, then T030–T039 in order
```

### User Story 2

```text
T040–T046 tests together; T047 VersionComparator ∥ T050 InstallQueue; then T048 → T049, T051 → T052, T053
```

### User Story 3

```text
T054 ∥ T055 ∥ T056 ∥ T057; then T058 ∥ T060, T059, T061
```

## Implementation Strategy

### MVP first

1. Phase 1 and Phase 2.
2. Phase 3 (US1). **Stop and validate** with quickstart §2 on the emulator and the TV: any GitHub
   app can now be installed with the remote.

### Incremental delivery

1. **US2** completes the P1 promise: updates. Release after it.
2. **US3** and **US4** (P2): correct installed state and removal. They can run in parallel after
   US2.
3. **US5** (P3): the pre-release opt-in and the remembered package choice.
4. **Polish**, including the device runs (T079).

Each checkpoint leaves a working app. Work happens on branch `002-app-install-updates`, created at
`/speckit-implement`, with the commits the user approves.

## Notes

- [P] means different files and no dependency on an unfinished task.
- Every constrained field cites data-model.md as `Entity.field`.
- Never write private project names in code, commits or docs.
