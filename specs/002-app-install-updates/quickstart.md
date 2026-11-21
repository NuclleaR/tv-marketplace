# Quickstart: validating install, update and manage

This guide proves feature 002 works end to end. The contracts are
[tv-screens.md](contracts/tv-screens.md), [github-releases.md](contracts/github-releases.md) and
[system-install.md](contracts/system-install.md); the states are in [data-model.md](data-model.md).

## Prerequisites

- **Devices**:
  - an agent emulator (`android:emulator` skill), for UI flows;
  - the user's TV, Box R Lite 4K at API 34, through wireless adb, for real installs and budgets;
  - the Legion TV emulator (API 29), for the pre-API-31 install path.
- **Test repositories**: at least the following, tracked through Add app.
  - one with a single universal APK;
  - one with per-ABI APKs, for the package list and the recommendation;
  - one with pre-releases;
  - one whose app is already installed on the device by other means.

## 1. Automated checks (must pass first)

```sh
./gradlew appUnitTest lintKotlin :app:lintDebug :app:assembleDebug :companion:lintDebug :companion:assembleDebug
```

These cover:
- **Domain**: `PackageChooser` on the recorded fixture corpus, at least 95 % correct (SC-002);
  `VersionComparator` tables; package-pattern matching; and `ApkManifestReader` on fixture APKs.
- **Data**: the releases client against fixtures, including 200, 304, 404 and the rate limit, and
  the Room 4 → 5 migration test.
- **Installer**: status mapping, the single-install queue, and the permission path.
- **ViewModels**: the details state machine, Home's update counts and Update all, under Molecule
  and Turbine.
- **D-pad UI tests**: every screen's initial focus and Back behaviour in tv-screens.md.

## 2. Story 1: install (P1)

1. Home: focus the per-ABI app's tile, then press Select. App details opens with **Install**
   focused, and the package list marks the device's ABI as Recommended.
2. Press Select. Progress shows, and **Install** becomes **Cancel**.
3. Press Back to Home while it downloads. The tile shows progress, and the download continues.
   Reopen the app: Cancel and progress are back.
4. The system prompt: Install. The details screen shows the installed version and **Open**.
   **Open** launches the app.
5. **Permission**: on a fresh install of TV Marketplace, press Install. The permission panel
   shows, and **Open settings** opens the system screen. Allow it and press Back: the install
   continues without another press.
6. **Cancel**: start an install and select Cancel. Retry is focused, and
   `adb shell run-as <pkg> ls no_backup/downloads` shows no `.part` file.
7. **Other package**: in the list, select a non-recommended compatible package. It installs, and
   its row says "Your choice".

## 3. Story 2: updates (P1)

1. Install an older release by hand: download its APK and `adb install` it. Then press **Check
   for updates** on Home. The updates row shows the count, and the tile shows `old → new`.
2. Select **Update all** with two apps pending. Each app installs in turn: on API 34, without a
   second prompt for apps TV Marketplace installed; on API 29, one prompt each.
3. **Pre-releases**: in the app's settings, turn on **Include pre-releases**. Its next check offers
   the newer pre-release; other apps don't.
4. **Rate limit**: run the check with a `MockEngine` build, or exhaust the limit with
   `for i in $(seq 60); do curl -s -o /dev/null https://api.github.com/rate_limit; done` from the
   TV's network. Home shows the "try after" time, and nothing becomes "up to date".
5. **Scheduled check**: run `adb shell cmd jobscheduler run -f <pkg> <job id>`, using the job id
   from `dumpsys jobscheduler`. Home updates without a manual check (SC-003).

## 4. Story 3: installed state (P2)

1. Track the repository of an app installed by other means. Its details show "Installed <version>"
   without downloading the APK. `AppLog` shows `manifest_read` with status 206.
2. Uninstall that app from the system Settings, then return to TV Marketplace. The tile shows
   "Not installed" (SC-006).

## 5. Story 4: remove (P2)

1. On App details, select **Remove**. **Keep on TV** is focused. Selecting it removes the tile,
   and the app stays in the launcher.
2. Track the app again, then select **Remove** and **Uninstall too**. The system prompt shows:
   - accept it, and the app is gone from the TV and from the list;
   - repeat, decline, and the app stays installed but is removed from the list.

## 6. Story 5: per-app settings (P3)

1. Install a non-recommended package. When the next release has a package with the same name
   pattern, that package is used, and its row is tagged "Your choice".
2. Select **Use automatic choice**, and the Recommended package is used again.

## 7. Edge cases worth a manual pass

- **Network drop**: turn airplane mode on mid-download. Retry is focused, and nothing is left on
  disk.
- **Different signature**: install a debug-signed build, then update to the release build. The
  signature-conflict message shows, with Retry and Back only.
- **Full storage**: `adb shell fallocate` a large file into `/data/local/tmp`. The download
  doesn't start, and the needed MB are shown.
- **Source gone**: track a repository, then rename it on GitHub, or use a fixture. The tile shows
  "Source unavailable", and Remove is offered.

## 8. Performance (Constitution IV)

- **Startup**: run `StartupBenchmark` and confirm startup still meets ≤ 2.0 s p90 on the TV.
- **New benchmark**: `AppDetailsBenchmark` opens details from Home and scrolls the package list.
  Frame timing must stay within budget, and the focus response ≤ 100 ms.
- **Downloads**: a 100 MB download holds the heap under 150 MB. Check it with
  `dumpsys meminfo <pkg>` during the download.
- **Baseline Profile**: add the Home → details journey and regenerate the profile on the TV.
