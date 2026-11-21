# Research: Install, Update and Manage Tracked Apps

Decisions for [plan.md](plan.md). Each one: decision, rationale, alternatives considered.

## R1. Reading releases from GitHub

**Decision**: `GET repos/{owner}/{repo}/releases?per_page=10` with
`Accept: application/vnd.github.html+json`, one request per app per check. The newest eligible
release is the first one in the list that is not a draft and, unless the app opted in, not a
pre-release. The response's `ETag` is stored per app and sent back as `If-None-Match`; a `304`
keeps the stored release. Assets come from the same response: `name`, `size`, `content_type`,
`browser_download_url`, and `digest` when GitHub provides it.

**Rationale**: `releases/latest` skips pre-releases, so it can't serve the opt-in (FR-014); the list
serves both cases with one request. The HTML media type returns `body_html` next to `body`, so the
release notes need no Markdown parser (R9). A `304` is cheap, and GitHub documents that it doesn't
count against the primary limit for some clients. Implementation checks whether that applies to
anonymous requests, and the request budget (R7) doesn't depend on it. Downloads use
`browser_download_url`, which redirects to GitHub's file CDN, so they don't count against the API
limit.

**Alternatives**:

- `releases/latest` plus a separate request for pre-releases: two requests for opted-in apps.
- GraphQL: needs a token, and signing in is out of scope.
- The Atom feed `releases.atom`: it has no asset list, so it can't pick a package.

## R2. Choosing the package for this device (FR-004, FR-005, SC-002)

**Decision**: `PackageChooser` is pure Kotlin in `:core:domain`. It takes the release's assets and the
device's ABIs (`Build.SUPPORTED_ABIS`, in preference order) and returns every APK with its
compatibility, plus the recommended one.

- **Package**: the name ends in `.apk`, or the content type is
  `application/vnd.android.package-archive`. Everything else (`.aab`, `.apks`, `.xapk`, `.zip`,
  checksums, `.idsig`, source archives) is not a package and isn't listed.
- **ABI**: tokens in the file name, split on `-_. `:
  - `arm64-v8a`/`arm64`/`aarch64`/`armv8`;
  - `armeabi-v7a`/`armeabi`/`armv7`/`arm32`/`arm`;
  - `x86_64`/`x64`/`amd64`;
  - `x86`/`i686`.
  A name without a token, or with `universal`/`all`/`fat`, fits every ABI. An APK for an ABI the
  device doesn't support is listed as not for this device.
- **Ranking** of the compatible ones:
  1. an exact ABI match ranks by the device's ABI order, above universal;
  2. a name with `tv`/`androidtv`/`atv`/`leanback`, above one with `mobile`/`phone`, above neither;
  3. `release` above `debug`; names with `debug`, `unsigned` or `test` rank last, but are still
     listed.
  A tie keeps the release's order. With no compatible APK, the result is "no package for this
  device" (FR-015).
- **Tests**: a fixture corpus of asset lists recorded from real TV-capable GitHub apps, at least
  20, kept as JSON in tests. SC-002 is measured on it.

**Rationale**: File names are the only per-asset signal GitHub gives. Reading the ABI from inside each
APK would need a download per candidate. 32-bit-only TV boxes (`armeabi-v7a` without 64-bit) are
common, so ABI order matters more than on phones.

**Alternatives**:

- Obtainium-style regex filter: out of scope (FR-018).
- Reading `lib/<abi>/` from each APK via ranged requests: too costly per release.

## R3. Remembering the user's package choice (FR-018)

**Decision**: When the user installs a package other than the recommended one, the app stores a
*name pattern*: the file name with every version-like run replaced by a placeholder. A
version-like run is digits with dots, an optional `v` prefix, and a suffix such as `-beta1`. The
release's tag and version name are removed first when they appear in the name. On the next
release, the package whose pattern equals the stored one is used, otherwise the automatic choice
is offered, with a notice. Choosing the recommended package, or "Use automatic choice", clears the
pattern.

**Rationale**: It covers `App_1.2.3_arm64.apk` → `App_1.3.0_arm64.apk` and
`app-v2.0-tv-release.apk` → `app-v2.1-tv-release.apk` with no input from the user. That is what
the user asked for instead of a text filter.

**Alternatives**:

- Storing the exact name: breaks on every release.
- Storing only ABI and variant tokens: loses user choices that differ only by flavour names.

## R4. Learning package identity, including apps installed before tracking (FR-010, US3)

**Decision**: Package identity (the package name) is learned in this order.

1. From any APK this app downloads, through `PackageManager.getPackageArchiveInfo`, which also
   gives `versionCode`/`versionName` (verification, R6).
2. For an app with no known package name, when its details screen opens or its first check runs,
   from the recommended APK's manifest without downloading the APK:
   - a ranged request for the zip end-of-central-directory and the central directory;
   - a ranged request for the `AndroidManifest.xml` entry, a few KB;
   - inflate it and read the `package` attribute of the root element of the binary XML.

   This is pure Kotlin (`ApkManifestReader` in `:core:domain`, tested against small real APK
   fixtures). The asset is fetched with plain `Range` requests on the redirected download URL.
3. If the server ignores `Range` (a 200 with the full body), the read is abandoned after the first
   64 KB. The app stays "not known installed" until the first install from this app, which then
   replaces the existing app through the system as a normal update when the signatures match.

The learned name is stored on the tracked app and is never replaced by a different one. A package
with a different name is refused before install, as the different-app edge case requires.

**Rationale**: SC-006 needs apps installed by other means to be recognized. Guessing by the
launcher label is unreliable: forks share names, and labels are localized. Downloading a full APK
just to read its name costs tens of MB per app.

**Alternatives**:

- Label or name heuristics with a "is this it?" prompt: extra UX, and still wrong for forks.
- A full download: too costly on TV networks and storage.

## R5. Installed state and keeping it current (FR-010, FR-011)

**Decision**:

- **Lookup**: `InstalledApps` reads `PackageManager.getPackageInfo(packageName)` for every tracked
  app with a known package name. That gives `versionName`, `longVersionCode` and
  `installerPackageName` via `getInstallSourceInfo`.
- **Refresh**: on every `ON_START` of the process, and while the process is alive, through a
  runtime-registered receiver for `PACKAGE_ADDED`/`PACKAGE_REPLACED`/`PACKAGE_REMOVED` with the
  `package` data scheme.
- **Permission**: the manifest declares `QUERY_ALL_PACKAGES`, because the packages to check are
  only known at runtime and `<queries>` must be static.

**Rationale**: These implicit broadcasts can't be received through the manifest since API 26. A
refresh on start covers changes made while the app wasn't running. App managers are an allowed
use of `QUERY_ALL_PACKAGES`, and this app ships through GitHub releases, not Play.

**Alternatives**:

- `<queries>` with fixed package names: impossible for user-tracked apps.
- Polling: wasteful.

## R6. Download and verification (FR-006, FR-007)

**Decision**:

- **Where downloads run**: in a WorkManager `CoroutineWorker`, `DownloadWorker`, with unique work
  per tracked app, running as a foreground worker (`setForeground`, type `dataSync`) with a
  progress notification. That way a download survives leaving the screen or the app.
- **How**: the bytes stream through the existing Ktor client (OkHttp engine) into
  `noBackupFilesDir/downloads/<appId>-<assetId>.apk.part`, then are renamed to `.apk` when
  complete. Progress is published through `setProgress` at most every 250 ms or 1 %.
- **Cancel or failure** deletes the `.part` file. Leftover files are deleted on process start.
- **Before starting**, it checks `StatFs` for the asset size plus 10 %, and fails with
  "needs X MB" when there isn't enough.
- **Verification**:
  1. The size equals the asset's `size`.
  2. The SHA-256 equals the asset's `digest`, when GitHub provides one.
  3. `getPackageArchiveInfo` parses the APK.
  4. Its package name equals the learned one, when one is known.

**Rationale**: One library, WorkManager, also runs the scheduled update checks (R8). It handles
process death, constraints and foreground promotion, all of which a hand-written service would
need. The download stays in app storage and needs no storage permission.

**Alternatives**:

- The system `DownloadManager`: its state lives outside the app, it gives poor control over
  verification and partial files, and some TV builds hide or disable it.
- A custom foreground service: duplicates what WorkManager does.

## R7. Installing and uninstalling through the system (FR-008, FR-009, FR-017)

**Decision**:

- **Install API**: the `PackageInstaller` session API. The APK is streamed into a session and
  committed with a `PendingIntent` to an internal receiver. The status is mapped as follows:

  | Status | Shown as |
  |--------|----------|
  | `STATUS_PENDING_USER_ACTION` | launch `EXTRA_INTENT` when the app is in the foreground, else keep "Ready to install" until the user returns and selects it |
  | `STATUS_SUCCESS` | installed |
  | `STATUS_FAILURE_ABORTED` | cancelled by the user |
  | `STATUS_FAILURE_CONFLICT` / `STATUS_FAILURE_INCOMPATIBLE` | signed by another key, or not compatible |
  | `STATUS_FAILURE_STORAGE` | no space |
  | anything else | failed, with the system's message |

- **Permission**: before the first install, `canRequestPackageInstalls()` is checked. If it's
  false, the app explains why it needs the permission and opens
  `ACTION_MANAGE_UNKNOWN_APP_SOURCES` for this package. If the TV lacks that screen, the app
  falls back to `ACTION_SECURITY_SETTINGS`. On `ON_START`, the pending install resumes without
  downloading again.
- **Prompts for user-started updates**: on API 31+, a session for an app this app installed
  earlier sets `setRequireUserAction(USER_ACTION_NOT_REQUIRED)`, so updates the user started skip
  the extra prompt where the system allows it. Background checks never install (FR-012).
- **Uninstall**: `PackageInstaller.uninstall(packageName, IntentSender)` with
  `REQUEST_DELETE_PACKAGES`, through the same receiver and the same confirmation path.
- **Update all**: one install at a time, in a queue. The next starts after the previous one's
  final status.

**Rationale**: `ACTION_VIEW`/`ACTION_INSTALL_PACKAGE` with a content URI is deprecated and reports
no result. Android 10+ blocks activity starts from the background, so the confirmation must wait
for the foreground. That is the spec's "installing still needs the user's confirmation" edge case.

**Alternatives**:

- Root or shell installs: not available on consumer TVs.
- Shizuku: an extra dependency and setup, out of scope.

## R8. Update checks: scheduling, overlap and request limits (FR-012, FR-016, SC-003)

**Decision**:

- **Schedule**: a `PeriodicWorkRequest` `UpdateCheckWorker` every 6 hours with a
  `NetworkType.CONNECTED` constraint, enqueued with `KEEP` at app start. "Check for updates" on
  Home enqueues a one-time request under its own unique name, with `KEEP` while one is running.
- **Overlap**: both run through `UpdateChecker.check()`, which holds a process-wide `Mutex`, so a
  manual check during a scheduled one joins its result instead of starting a second.
- **Order**: the check walks tracked apps oldest-checked first.
- **Request limit**: on a 403/429 with `X-RateLimit-Remaining: 0`, the check stops, keeps every
  stored result, and stores `X-RateLimit-Reset` as "can resume at". Home shows it, and the
  scheduler doesn't retry before then.
- **What is stored**: every result, the latest release and the ETag, per app, as soon as that
  app's request ends. A stopped check loses nothing.

**Rationale**: Anonymous GitHub allows 60 requests an hour, shared with Add app's lookups. Tens of
apps at one request each, ETag-conditional, every 6 hours stay well inside it. After an upload,
SC-003 is met by the next check, or at once by a manual one.

**Alternatives**:

- `AlarmManager`: no constraints, no persistence.
- Checking only while the app is open: misses SC-003's scheduled cycle.

## R9. Release notes on TV (FR-003)

**Decision**: `body_html` from R1 is rendered with Compose's `AnnotatedString.fromHtml`. Link
annotations are dropped, so nothing in the text is focusable. On the details screen the notes are
a short preview. "Release notes" opens a full-screen reader:
- one focusable container scrolls by a step per D-pad Up/Down, and by a page per long press;
- Back closes it.

**Rationale**: It needs no new dependency. GitHub already turned the Markdown into HTML, with
GitHub's own extensions, which a third-party parser would get partly wrong.

**Alternatives**:

- The JetBrains Markdown parser plus a custom renderer: more code, same result.
- A Markdown renderer library built on mobile Material 3: mismatched styling.
- A WebView: heavy on low-end TVs, and hard to drive with a D-pad.

## R10. Versions: when a release counts as newer (FR-015, edge cases)

**Decision**:

- **Install record**: when this app installs a release, it stores the release's tag and the
  installed `longVersionCode`. While the TV's version code still equals the stored one, the
  installed release is known exactly. A release is newer when it was published after that one.
- **Without a record** (installed elsewhere, or updated outside this app), the installed
  `versionName` is compared with the release tag:
  1. both are normalized: a leading `v`/`version`/app name is stripped;
  2. when both parse as dot-separated numbers with an optional pre-release suffix, they compare
     numerically by semver precedence;
  3. otherwise the release counts as newer when it differs from the installed name and was
     published after the app's `lastUpdateTime`.
- **Downgrades**: a release older than the installed one is never offered.
- **Where**: `VersionComparator` lives in `:core:domain`, with table-driven tests: dates, commit
  hashes, `v` prefixes, `-beta.2`, four-part versions.

**Rationale**: Tags are free-form, so no comparator is right for every repository. The exact record
covers the common case, apps installed through this app. The spec's fallback rule covers the rest.

**Alternatives**:

- Downloading the new APK's manifest (R4) and comparing `versionCode`: exact, but a ranged
  request per app per check. Kept as a possible later refinement.

## R11. A source that isn't only GitHub (FR-019)

**Decision**:

- **Source**: `AppSource` is a sealed interface in `:core:domain` with one implementation today,
  `GitHub(owner, repo)`. The `tracked_app` row gets a `sourceKind` column (default `github`).
- **Fetching**: release fetching goes through a `ReleaseSource` interface keyed by kind; only
  `GitHubReleaseSource` exists.
- **Shared types**: `Release`, `Package` and everything after them (choosing, download, install,
  checks) never see GitHub types.

**Rationale**: F-Droid is the next planned source, and this is the cheapest point to keep the seam.
It is one interface and one column, nothing speculative beyond that.

**Alternatives**:

- GitHub types throughout: F-Droid would mean rewriting the update pipeline.
- A full plugin system: YAGNI.

## R12. Dependencies

| Dependency | Why |
|------------|-----|
| `androidx.work:work-runtime-ktx` | Background downloads (R6) and scheduled checks (R8) |
| `androidx.work:work-testing` (test) | Testing them |

Both versions go in `libs.versions.toml` at the latest stable when implementing. Workers get their
dependencies through Metro: a `WorkerFactory` in `:app` built from an `@IntoMap` multibinding of
assisted worker factories, the same pattern as ViewModels. Nothing else is added:
- PackageInstaller and PackageManager are platform APIs;
- zip and inflate come from `java.util.zip`;
- HTML comes from Compose.

## R13. Reference device and test devices

The constitution's reference device for budgets is the user's TV: Box R Lite 4K, Android 14
(API 34), at 192.168.50.170 through wireless adb. Agents use their emulator for UI flows. The
Legion TV emulator is API 29. Install flows are tested on API 29, without `setRequireUserAction`,
and API 34. Robolectric covers `PackageInstaller` session handling through its shadows, and the
receiver's status mapping through unit tests.
