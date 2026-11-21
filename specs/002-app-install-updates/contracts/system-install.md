# Contract: system install, uninstall and installed state

This is the contract between the app and Android's `PackageInstaller` and `PackageManager`.
`:core:installer:api` exposes it as Kotlin interfaces, and `:core:installer:impl` implements it.
Features and the data layer use only the interfaces; tests use fakes from `:core:testing`.

## Manifest additions (`:app`)

```xml
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
<uses-permission android:name="android.permission.REQUEST_DELETE_PACKAGES" />
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<!-- WorkManager's foreground worker service, typed for downloads -->
<service android:name="androidx.work.impl.foreground.SystemForegroundService"
         android:foregroundServiceType="dataSync" tools:node="merge" />
<receiver android:name=".core.installer.InstallResultReceiver" android:exported="false" />
```

## Interfaces

```text
interface InstalledApps
  fun get(packageName): InstalledApp?
  fun changes(): Flow<Unit>                    # PACKAGE_ADDED/REPLACED/REMOVED + process ON_START

interface InstallPermission
  fun granted(): Boolean                       # canRequestPackageInstalls()
  fun settingsIntent(): Intent                 # MANAGE_UNKNOWN_APP_SOURCES, else SECURITY_SETTINGS

interface PackageInspector
  fun inspect(file): ApkInfo?                  # getPackageArchiveInfo: packageName, versionName, versionCode

interface Installer
  suspend fun install(appId, file, expectedPackage?, allowSilentUpdate): InstallOutcome
  suspend fun uninstall(packageName): UninstallOutcome
  val pendingConfirmation: StateFlow<PendingConfirmation?>   # an EXTRA_INTENT waiting for the foreground
  fun confirmationShown(id)                    # MainActivity launched it
```

`InstallOutcome`:
- `Installed(packageName, versionCode)`;
- `CancelledByUser`;
- `SignatureConflict`;
- `Incompatible`;
- `NoSpace`;
- `DifferentApp`, when the APK's package differs from `expectedPackage`, checked before the
  session opens;
- `NeedsPermission`;
- `Failed(systemMessage)`.

`UninstallOutcome`: `Uninstalled`, `CancelledByUser`, `Failed(systemMessage)`.

## Session flow

1. **Check the permission.** If `granted()` is false, return `NeedsPermission` without opening a
   session. The UI shows the permission panel, and retries on `ON_START`.
2. **Open a session.** `createSession(MODE_FULL_INSTALL)`, with `setAppPackageName(expected)` when
   known. On API 31+, `allowSilentUpdate` sets `setRequireUserAction(USER_ACTION_NOT_REQUIRED)`.
   It is true only for user-started updates of apps whose installer of record is this app.
3. **Commit.** The APK is streamed into `openWrite("base.apk", 0, size)` with `fsync`, then
   committed with a mutable `PendingIntent` to `InstallResultReceiver`, whose extras carry the
   session id and appId.
4. **Map the status.** The receiver maps `EXTRA_STATUS`:
   - `STATUS_PENDING_USER_ACTION`: the `EXTRA_INTENT` goes to `pendingConfirmation`.
     `MainActivity` collects it while started and launches it with `FLAG_ACTIVITY_NEW_TASK`.
   - `STATUS_SUCCESS`: `Installed`.
   - `STATUS_FAILURE_ABORTED`: `CancelledByUser`.
   - `STATUS_FAILURE_CONFLICT`: `SignatureConflict`. That includes the "existing package has a
     different signature" case.
   - `STATUS_FAILURE_INCOMPATIBLE`: `Incompatible`.
   - `STATUS_FAILURE_STORAGE`: `NoSpace`.
   - anything else: `Failed(EXTRA_STATUS_MESSAGE)`.
5. **Complete.** The `install(...)` suspend call completes on the final status. One install runs
   at a time; others suspend in order (R7).

Uninstall follows the same flow with `PackageInstaller.uninstall(packageName, intentSender)`.

## Process death

A pending session survives in the system. On startup, `Installer` lists `mySessions`, abandons
sessions with no matching download record, and deletes their files. The details screen then shows
the download as `ReadyToInstall` again when its file is still complete, otherwise as `NotInstalled`
or `UpdateAvailable`.
