# Data Model: Install, Update and Manage Tracked Apps

The data feature 002 adds to feature 001's model, as described in
[../001-phone-link-sharing/data-model.md](../001-phone-link-sharing/data-model.md).
Room schema version 4 → 5 is an auto-migration: new columns with defaults and new tables, with no
renames. Column and table names are persisted user data.

## Domain types (`:core:domain`, pure Kotlin)

### AppSource

```text
sealed AppSource
  GitHub(owner, repo)          # the only kind today (R11); kind key "github"
```

### Release

| Field | Type | Notes |
|-------|------|-------|
| tag | String | e.g. `v1.4.2`; identity of the release at its source |
| name | String? | display title; falls back to tag |
| publishedAt | Instant | |
| prerelease | Boolean | |
| notesHtml | String? | rendered notes (R9) |
| packages | List<Package> | APK assets only, in the release's order |

### Package

| Field | Type | Notes |
|-------|------|-------|
| id | Long | the asset's id at the source |
| fileName | String | |
| sizeBytes | Long | |
| downloadUrl | String | |
| sha256 | String? | from the asset's `digest` when the source provides one |

### PackageChoice (output of `PackageChooser`, R2)

| Field | Type | Notes |
|-------|------|-------|
| candidates | List<Candidate> | every package, in the release's order |
| recommended | Package? | null: no package for this device |
| chosen | Package? | the remembered pattern's match (R3), else `recommended` |
| choiceLost | Boolean | a pattern is stored but nothing matched it in this release |

`Candidate` = package + `compatible: Boolean` + `abi: Abi?` + `variant: Variant`
(`TV`, `MOBILE`, `NONE`) + `debug: Boolean`.

### InstalledApp

| Field | Type | Notes |
|-------|------|-------|
| packageName | String | |
| versionName | String? | |
| versionCode | Long | |
| lastUpdateTime | Instant | |
| installedByThisApp | Boolean | installer of record is this app |

### AppStatus (derived; Home and Details)

Status is computed from the tracked app, its installed app, its latest release and its download.
The first matching rule wins:

```text
Unavailable        source gone (404 at the source)
Downloading(p)     a download is running        → Cancel
ReadyToInstall     downloaded, waiting for the system prompt or permission
NoPackage          latest eligible release has no compatible package
NotInstalled       no installed app known       → Install
UpdateAvailable    installed, and the latest is newer (R10) → Update
UpToDate           installed                    → Open
```

`Failed(reason)` is shown on top of the underlying status until the user selects Retry or
dismisses it. Its reasons are `Network`, `RateLimited(resumeAt)`, `NoSpace(neededBytes)`,
`Corrupt`, `DifferentApp`, `SignatureConflict`, `Incompatible`, `CancelledByUser` and
`System(message)`.

## Persistence (`:core:data:impl`, Room v5)

### tracked_app (existing; new columns)

| Column | Type | Default | Notes |
|--------|------|---------|-------|
| sourceKind | TEXT | `'github'` | R11 |
| packageName | TEXT? | NULL | learned once (R4), never replaced by a different one |
| includePrereleases | INTEGER | 0 | FR-014 |
| packagePattern | TEXT? | NULL | the user's package choice as a name pattern (R3); NULL means automatic |
| sourceGone | INTEGER | 0 | the source answered "not found" on the last check |

The existing `latestVersion` keeps holding the latest eligible release's tag, for Home.

### app_release (new): latest eligible release per app

| Column | Type | Notes |
|--------|------|-------|
| appId | INTEGER PK | FK → tracked_app.id, ON DELETE CASCADE |
| tag | TEXT | |
| name | TEXT? | |
| publishedAt | INTEGER | epoch ms |
| prerelease | INTEGER | |
| notesHtml | TEXT? | |
| packagesJson | TEXT | `List<Package>` as JSON |
| etag | TEXT? | for `If-None-Match` (R1) |
| checkedAt | INTEGER | epoch ms of the last successful answer (200 or 304) |

There is one row per app, replaced on every 200. Only the latest eligible release is stored: older
ones aren't needed for any requirement.

### install_record (new): what this app installed

| Column | Type | Notes |
|--------|------|-------|
| appId | INTEGER PK | FK → tracked_app.id, ON DELETE CASCADE |
| packageName | TEXT | |
| tag | TEXT | release installed |
| versionCode | INTEGER | from the APK at install time |
| packageFileName | TEXT | |
| installedAt | INTEGER | epoch ms |

It is used by R10's exact comparison while the installed `versionCode` still equals `versionCode`.

### DataStore `update_check` (new keys in the existing settings store)

| Key | Type | Notes |
|-----|------|-------|
| lastCheckFinishedAt | Long? | shown on Home ("checked 2 h ago") |
| resumeAt | Long? | set on rate limit (R8); checks don't run before it |

## In-memory / WorkManager state

### Download

| Field | Notes |
|-------|-------|
| appId, package | what is being fetched |
| state | `Queued → Running(bytes, total) → Verifying → Done(file)`; or `Failed(reason)` / `Cancelled` |

This state is WorkManager's work info for unique work `download-<appId>`, mapped to `Download`.
Files live in `noBackupFilesDir/downloads/`. A `Done` file is deleted after a successful install,
or when the app is removed. Leftover `.part` files are deleted at startup.

### InstallQueue

This is an ordered list of appIds for "Update all", with an overall progress of done / total and
a list of failures with reasons. It is process memory only: after process death, Home shows the
remaining updates again, and "Update all" restarts with them.

## State transitions

```text
NotInstalled ──Install──▶ Downloading ──done──▶ Verifying ──ok──▶ ReadyToInstall
     ▲                        │                     │                  │
     │                     Cancel/fail           bad file         system prompt
     │                        ▼                     ▼            accept │  decline
     └──────────────────── Failed(reason) ◀─────────┘               ▼     ▼
                                                              UpToDate  Failed(CancelledByUser)

UpToDate ──check finds newer──▶ UpdateAvailable ──Update──▶ Downloading … (as above)
any ──source 404──▶ Unavailable (Remove offered; installed app kept)
```

## Validation rules

- `packageName` is set only from a parsed APK or manifest (R4). An APK whose package differs from
  a known `packageName` is rejected before install (`DifferentApp`).
- A release is only stored as latest when it's eligible: not a draft, and a pre-release only with
  `includePrereleases`.
- An update is offered only when `AppStatus` is `UpdateAvailable` (FR-015): a compatible package
  exists, and the release is newer by R10.
- `packagePattern` is set only when the user installs a non-recommended package. It's cleared by
  installing the recommended one or by "Use automatic choice".
- Removing an app deletes its `tracked_app` row (cascading `app_release`, `install_record`), cancels
  its download work, and deletes its files. It never uninstalls unless the user chose so.
