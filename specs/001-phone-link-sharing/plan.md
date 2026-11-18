# Implementation Plan: Send App Links from Phone to TV

**Branch**: `001-phone-link-sharing` | **Date**: 2026-11-18 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-phone-link-sharing/spec.md`

## Summary

Let a user get a GitHub link from their phone onto an Android TV app manager without typing.
The TV app runs a small LAN HTTP server and shows a QR code containing
`http://<tv-ip>:<port>/s/<sessionToken>`. The phone camera opens a "Send to TV" page served by the
TV itself; the user pastes a link and taps Send. The TV parses the first GitHub link, fetches a
preview from the GitHub API, and adds the app to the tracked list after one Select press.
Phones can stay paired (device token) and send later into a persistent Inbox. Share-menu
sending uses an Android companion app (NSD discovery + PIN pairing) and an iOS Shortcut that
opens the page prefilled. On-TV `owner/repo` entry is the fallback.

This is the first feature of a greenfield repository, so the plan also bootstraps the Gradle
project, convention plugins and module layout required by the constitution.

## Technical Context

**Language/Version**: Kotlin 2.x (K2 compiler), JVM target 17; versions pinned in
`gradle/libs.versions.toml` at bootstrap.

**Primary Dependencies**: Jetpack Compose + Compose for TV (`androidx.tv:tv-material`),
Navigation 3, Hilt, Ktor server (CIO) + Ktor client (OkHttp), kotlinx.serialization, Room,
DataStore, Coil 3, ZXing core, AndroidX Lifecycle (ProcessLifecycleOwner).

**Storage**: Room (`tracked_app`, `incoming_link`, `paired_phone`); DataStore
(`link_share_settings`); in-memory `SendSession`. See [data-model.md](data-model.md).

**Testing**: kotlin.test on JUnit runner, Turbine, Robolectric (Room, Compose UI with D-pad key
input), Ktor `testApplication` and `MockEngine` with JSON fixtures, Macrobenchmark + Baseline
Profiles.

**Target Platform**: Android TV / Google TV, minSdk 26, target/compileSdk 36. Companion: Android
phones, minSdk 26. iOS: Shortcuts app (no code in this repo beyond the page link).

**Project Type**: Mobile (Android TV app + Android phone companion), multi-module Gradle.

**Performance Goals**: Link appears on TV ≤ 3 s after Send (SC-003); Add-app screen shows QR
≤ 500 ms; cold start ≤ 2.0 s p90; 60 fps navigation (Constitution IV).

**Constraints**: LAN only, no backend, no third-party data sharing (FR-018); plain HTTP
(research R1); app heap ≤ 150 MB; server idle cost negligible; D-pad-only UX.

**Scale/Scope**: Single household; 1 TV, a few phones; tens of tracked apps; 6 TV screens, 3
companion screens, 1 web page.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Status |
|-----------|-------|--------|
| I. TV-First, Remote-Only UX | Every TV screen in [contracts/tv-screens.md](contracts/tv-screens.md) defines initial focus, focus order and Back; QR flow means no on-TV typing; fallback typing is minimal. | ✅ Pass |
| II. Test-First | Parser, server routes, repositories, ViewModels and D-pad UI tests are written first; GitHub and server tests run without live network (R11). | ✅ Pass |
| III. Modular, Layered | `:app`, `:feature:*`, `:core:*`; features independent; `:core:domain` pure Kotlin; UDF with `StateFlow`; Hilt injection. | ✅ Pass |
| IV. Performance Budgets | Server starts off main thread; Baseline Profile covers startup + Add app; Macrobenchmark in quickstart §7. | ✅ Pass |
| V. Simplicity & Observability | New libraries justified in research (Ktor, ZXing, Coil). Logs never contain links, IPs, tokens or device names; errors are focusable states. | ✅ Pass with justified items below |
| Stack constraints | Kotlin, Compose for TV, coroutines, version catalog, leanback feature declared, touchscreen not required. | ✅ Pass |

Post-design re-check (after Phase 1): still passing; deviations are listed in Complexity
Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/001-phone-link-sharing/
├── plan.md              # This file
├── research.md          # Phase 0: decisions R1–R12
├── data-model.md        # Phase 1: entities, states, persistence
├── quickstart.md        # Phase 1: end-to-end validation guide
├── contracts/
│   ├── lan-http-api.md  # Phone ⇄ TV HTTP API and page behavior
│   ├── nsd-discovery.md # Companion discovery contract
│   └── tv-screens.md    # TV screens, focus and Back contract
└── tasks.md             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
build-logic/convention/         # Convention plugins: android app/library, compose, hilt, jvm-library,
                                # module-dependency check
gradle/libs.versions.toml
settings.gradle.kts

app/                            # TV application: MainActivity, DI graph, nav host,
                                # LinkServerService (foreground, specialUse), leanback manifest
companion/                      # Phone app: share target, NSD browse, PIN pairing, send
baselineprofile/                # Baseline Profile generator + Macrobenchmarks
navigation/                     # @Serializable destinations, Navigator interface

core/common/                    # DispatcherProvider, Clock, privacy-safe logger
core/domain/                    # Pure Kotlin: GitHubLinkParser, GitHubRepoRef, AppPreview, models
core/protocol/                  # Pure Kotlin: @Serializable DTOs of lan-http-api (TV + companion)
core/data/                      # Room DB + DAOs, repositories (TrackedApps, IncomingLinks,
                                # PairedPhones), GitHubRepository (Ktor client), settings store
core/linkserver/                # Ktor server: routes, SessionManager, PairingManager, Host guard,
                                # rate limiter, NSD advertiser, LAN address provider,
                                # src/main/assets/web/ (send page HTML/CSS/JS, en + uk)
core/ui/                        # TV theme, focus indicator modifier, common TV components
core/testing/                   # Fakes, test DispatcherProvider, fixtures

feature/home/                   # Home shell: Add app / Inbox / Settings / tracked names
feature/addapp/                 # Add app (QR), Type it, Preview + confirm
feature/inbox/                  # Inbox list, dismiss, incoming-link notice state
feature/settings/               # Paired phones, receive-in-background toggle, IP tip
```

Each module has `src/main/kotlin/…` and `src/test/kotlin/…`. Dependency direction:
`app/companion → feature:* → navigation → core:*`; `core:domain` and `core:protocol` depend on
nothing Android. The module-dependency check in `build-logic` fails the build on violations.
`companion` depends only on `core:domain`, `core:protocol` and `core:common`; it does not use
`core:ui` (phone UI uses plain Material 3).

**Structure Decision**: Multi-module Android project as above, matching Constitution III. The
GitHub client lives in `core:data` (one consumer now; the next "install/update" feature also
needs it). `core:protocol` is split out because both the TV server and the companion use it.

## Key flows

1. **Scan-to-send**: `AddAppViewModel` asks `SessionManager` for a session → QR rendered from
   `LanAddressProvider` + token → phone `POST /api/v1/links` → server validates session, parses
   with `GitHubLinkParser`, stores PENDING `IncomingLink`, emits `IncomingLinkEvents` →
   Add-app screen navigates to Preview(linkId) → `GitHubRepository.preview()` → Add →
   `TrackedAppsRepository.add()` + link → ADDED.
2. **Paired send in background**: `ProcessLifecycleOwner` `ON_STOP` → start `LinkServerService`
   if `receive_in_background`; server keeps accepting device-token sends into Inbox.
3. **Companion**: NSD browse → PIN → `POST /api/v1/pair` → token stored → share intent →
   resolve TV by TXT `id` → `POST /api/v1/links`.

## Risks

- **Router client isolation / guest Wi-Fi** blocks the flow entirely → explicit hints on both
  sides (FR-017); no workaround in v1.
- **TV IP changes** break browser-saved pages → rescan prompt + router-reservation tip;
  companion unaffected (NSD).
- **Background server killed by the OS** on low-RAM TVs → foreground service; phone page shows
  "Open TV Marketplace on your TV".
- **GitHub rate limit (60/h)** in heavy testing → previews cached for 10 min in memory.

## Open items for tasks

- `applicationId` not decided yet; use placeholder `com.tvmarketplace` (debug suffix `.debug`)
  until confirmed.
- The iOS Shortcut must be created once by the maintainer and its iCloud link put into the page
  config.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Second application module `companion` (phone, not TV) | Story 3: plain-HTTP pages cannot be share targets; NSD discovery survives IP changes | PWA share target needs HTTPS (R1, R9); browser-only flow covers P1/P2 but not P3 |
| Plain HTTP server on the LAN | Only way for a stock phone browser to reach the TV without a backend | Self-signed TLS shows browser warnings; cloud relay violates FR-018 (R1, R4) |
| Foreground service with `specialUse` type | Story 2: receive links while the app is in background | Server only in foreground fails Story 2 scenario 2; boot start adds more surface (R5) |
| Extra libraries: Ktor server/client, ZXing, Coil | HTTP server, QR rendering, avatar loading | Hand-written HTTP/QR is more code and risk; no AndroidX equivalent (R2, R6, R8) |
