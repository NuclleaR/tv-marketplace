# Quickstart: validating "Send App Links from Phone to TV"

How to prove the feature works end to end. Behavior details live in
[contracts/lan-http-api.md](contracts/lan-http-api.md),
[contracts/tv-screens.md](contracts/tv-screens.md) and [data-model.md](data-model.md).

## Prerequisites

- JDK 21, Android SDK with an Android TV system image (API 34+), `adb`.
- An Android TV emulator or a real Android/Google TV device, and a phone (iPhone or Android) on
  the same Wi-Fi as the TV.
- Phone scenarios need a real TV device: an emulator's network is not reachable from a phone.
  On the emulator, verify TV screens only; server routes are covered by automated tests.

## 1. Automated checks (must pass before any manual run)

```sh
./gradlew testDebugUnitTest          # parser, server routes, repositories, ViewModels, UI tests (Robolectric)
./gradlew lint detekt ktlintCheck
```

Expected: all green; UI tests include D-pad focus assertions for every screen in `tv-screens.md`.

## 2. Install on TV

```sh
./gradlew :app:installDebug
adb shell am start -n <applicationId>/.MainActivity
```

Expected: Home opens with **Add app** focused within 2 s (Constitution IV).

## 3. Story 1 — scan to send (P1)

1. On TV: Select **Add app**. A QR code, `http://<ip>:8765` and a 6-digit code appear.
2. Phone: scan the QR with the camera app → "Send to TV" page opens.
3. Paste `https://github.com/ImranR98/Obtainium` → **Send**.
   Expected: phone shows "Sent"; TV shows Preview with **Add** focused in ≤ 3 s.
4. Press Select. Expected: "Added Obtainium"; app listed on Home.
5. Send `https://example.com/foo`. Expected: phone and TV show "not supported yet" + examples.

## 4. Story 2 — remember this phone (P2)

1. After step 3, tap "Remember this TV" on the phone and save the page to the home screen.
2. Press Home on the TV remote (app goes to background). Send another link from the saved page.
3. Reopen the app → Inbox shows 1 item. Select → Preview → Add.
4. Settings → Paired phones → remove the phone. Send again. Expected: page says "This TV
   forgot your phone — scan again".

## 5. Story 3 — Share menu (P3)

- Android: install `:companion` on the phone (`./gradlew :companion:installDebug`), open it,
  pick the TV, enter the code from the TV's Add-app screen. In the phone browser open a GitHub
  repo → Share → **Send to TV**. Expected: link in TV Inbox.
- iPhone: add the "Send to TV" Shortcut from the page, share a repo from Safari → Shortcut →
  page opens prefilled → **Send**.

## 6. Story 4 — type on TV (P3)

Add app → **Type it instead** → `ImranR98/Obtainium` → **Continue**. Expected: same Preview as
step 3.

## 7. Performance

Needs a device on API 33+ (or rooted): Baseline Profile collection refuses older user builds. On an emulator add
`-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR`; numbers from an emulator
only show trends, measure the budgets on a real TV.

```sh
ANDROID_SERIAL=… ./gradlew :app:generateBaselineProfile                    # writes app/src/release/generated/baselineProfiles/
ANDROID_SERIAL=… ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
```

Expected: cold start p90 ≤ 2.0 s; opening Add app shows the QR ≤ 500 ms; jank < 5%.
