---

description: "Task list for 001-phone-link-sharing"
---

# Tasks: Send App Links from Phone to TV

**Input**: Design documents from `/specs/001-phone-link-sharing/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: REQUIRED. Constitution Principle II (Test-First, NON-NEGOTIABLE): every test task is
written first and must be observed failing before its implementation task starts.

**Organization**: Tasks are grouped by user story so each story can be implemented and tested
independently.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: US1–US4 map to User Stories 1–4 in spec.md

## Path Conventions

Multi-module Gradle project at repository root (plan.md → Project Structure). Package root is
the placeholder `com.tvmarketplace` (plan.md → Open items), so `<pkg>` below means
`com/tvmarketplace`. Example: `core/domain/src/main/kotlin/<pkg>/core/domain/`.

---

## Implementation notes (adaptations to the bootstrapped template)

- Template conventions win over the paths below: modules are `:features:*` (not `:feature:*`), DI is Metro (not
  Hilt), ViewModels are plain classes with `@Composable fun viewState()` tested with Molecule + Turbine, all strings
  live in `core/strings` (`values/` + `values-uk/`), lint is ktlint (`lintKotlin`) + Android lint, minSdk is 28.
- Package root is `com.serhii.tvmarketplace`. Data is split into `:core:data:api` (models, interfaces) and
  `:core:data:impl` (Room, GitHub client); there is no separate `:core:testing` Hilt setup, but `:core:testing`
  holds shared fakes.
- T001–T009 were verify-and-fill-gaps over the template (catalog entries, new modules, compose test deps in
  `ComposePlugin`); Detekt was not added, ktlint covers formatting/lint as the template does.
- T070 (shorthand parser tests) was written together with T010; `parseUserInput` landed with T012.
- US2: `preferred_port` was not stored (the port list is fixed, research.md R3), so Settings only keeps
  `receive_in_background`. `incoming_link.senderPhoneId` has no SQL foreign key: removing a phone sets it to NULL in
  the repository, the same behavior with a Room auto-migration instead of a table rebuild. The foreground service is
  started when the app comes to the foreground (Android forbids starting it from the background) and keeps the
  process alive; the server itself lives in the process.
- US3: companion strings live in `companion/src/main/res` (a separate app; `:app` lint would flag them as unused in
  `core/strings`). The companion stores its token in app-private DataStore, not encrypted storage
  (EncryptedSharedPreferences is deprecated; the token can only put links into the TV inbox). The iOS Shortcut link
  is read from `core/linkserver/src/main/assets/web/config.json`, empty until the Shortcut is published.
- T074 (Baseline Profile + Macrobenchmark) and T078 (quickstart on real devices) are open: both need a device or an
  emulator, and this machine has no KVM.
- Device validation (quickstart §2–§7) could not run here: the machine has no KVM, so no emulator.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Bootstrap the greenfield Gradle project (plan.md → Summary, research.md R10)

> **Note**: The project owner bootstraps the Gradle project and sets the real `applicationId`
> before implementation. During `/speckit-implement`, treat T001–T009 as **verify and fill gaps**:
> keep the owner's files, module names and package; add only what is missing (modules, catalog
> entries, convention plugins, checks). Replace the `com.tvmarketplace` placeholder in all paths
> below with the actual package.

- [X] T001 Create `settings.gradle.kts` with `pluginManagement { includeBuild("build-logic") }`, `TYPESAFE_PROJECT_ACCESSORS`, and includes for every module in plan.md → Project Structure; add root `build.gradle.kts`
- [X] T002 Create `gradle/libs.versions.toml` pinning Kotlin 2.x, AGP, Compose BOM, `androidx.tv:tv-material`, Navigation 3, Hilt, Ktor server CIO + client OkHttp, kotlinx.serialization, Room, DataStore, Coil 3, ZXing core, Lifecycle, Turbine, Robolectric, kotlin.test, Macrobenchmark, Baseline Profile plugin, Detekt, ktlint (research.md R10, R11)
- [X] T003 [P] Create `gradle.properties` (configuration cache, build cache, parallel, non-transitive R, JVM args) and Gradle wrapper files in `gradle/wrapper/`
- [X] T004 Create convention plugins in `build-logic/convention/src/main/kotlin/`: `AndroidApplicationConventionPlugin.kt`, `AndroidLibraryConventionPlugin.kt`, `ComposeConventionPlugin.kt`, `HiltConventionPlugin.kt`, `JvmLibraryConventionPlugin.kt` (minSdk 26, compile/targetSdk 36, JVM 17, namespace derived from module path, warnings as errors)
- [X] T005 Add the module-dependency check (features never depend on features; core never on features; `core:domain`/`core:protocol` JVM-only) in `build-logic/convention/src/main/kotlin/ModuleDependencyCheck.kt` and apply it from the library and app plugins
- [X] T006 [P] Configure Detekt + ktlint + Android Lint (zero new warnings) in `build-logic/convention/src/main/kotlin/StaticAnalysisConventionPlugin.kt` and `config/detekt/detekt.yml`
- [X] T007 Create empty module skeletons (`build.gradle.kts` applying the right convention plugin, `src/main/kotlin/<pkg>/…`, `src/test/kotlin/<pkg>/…`) for `app`, `companion`, `baselineprofile`, `navigation`, `core/common`, `core/domain`, `core/protocol`, `core/data`, `core/linkserver`, `core/ui`, `core/testing`, `feature/home`, `feature/addapp`, `feature/inbox`, `feature/settings`
- [X] T008 [P] Add `.gitignore` (build outputs, `local.properties`, `.gradle/`, IDE files) at repository root
- [X] T009 Create CI workflow running `testDebugUnitTest lint detekt ktlintCheck` in `.github/workflows/ci.yml`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared code every story needs

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Tests first

- [X] T010 [P] Write failing `GitHubLinkParserTest` covering: first link extracted from surrounding text, `www.` prefix, `/releases`, `/releases/tag/x`, `.git` suffix, query/fragment stripped, owner/repo charset and length limits, reserved paths rejected, non-GitHub URL → null, normalization to `https://github.com/<owner>/<repo>` (research.md R7) in `core/domain/src/test/kotlin/<pkg>/core/domain/GitHubLinkParserTest.kt`
- [X] T011 [P] Write failing serialization round-trip tests for every DTO in contracts/lan-http-api.md (requests, success bodies, error bodies with `error` codes) in `core/protocol/src/test/kotlin/<pkg>/core/protocol/ProtocolSerializationTest.kt`

### Implementation

- [X] T012 [P] Implement `GitHubRepoRef(owner, repo)` and `GitHubLinkParser.extractFirst(text)` (no shorthand; shorthand is US4) in `core/domain/src/main/kotlin/<pkg>/core/domain/GitHubLinkParser.kt` — makes T010 pass
- [X] T013 [P] Implement `@Serializable` DTOs and `ApiError` codes (`invalid_pin`, `no_active_session`, `session_expired`, `device_revoked`, `unsupported_link`, `bad_request`) per contracts/lan-http-api.md in `core/protocol/src/main/kotlin/<pkg>/core/protocol/LinkShareProtocol.kt` — makes T011 pass
- [X] T014 [P] Implement `DispatcherProvider`, `Clock` abstraction and privacy-safe `Logger` (never logs links, IPs, tokens, device names — Constitution V) in `core/common/src/main/kotlin/<pkg>/core/common/`
- [X] T015 [P] Implement test helpers: `TestDispatcherProvider`, `FakeClock`, `MainDispatcherRule` in `core/testing/src/main/kotlin/<pkg>/core/testing/`
- [X] T016 [P] Implement TV theme, `Modifier.tvFocusIndicator()` (scale 1.1 + border), overscan-safe `TvScreenScaffold` (48 dp / 27 dp margins) in `core/ui/src/main/kotlin/<pkg>/core/ui/` (contracts/tv-screens.md)
- [X] T017 [P] Define `@Serializable` destinations `Home`, `AddApp`, `TypeIt`, `Preview(linkId: Long?, owner: String?, repo: String?)`, `Inbox`, `Settings` and the `Navigator` interface in `navigation/src/main/kotlin/<pkg>/navigation/`
- [X] T018 Create `TvMarketplaceApp` (`@HiltAndroidApp`), `MainActivity` with Navigation 3 host collecting feature entry providers, and `AndroidManifest.xml` declaring `android.software.leanback` required, touchscreen not required, `LEANBACK_LAUNCHER` intent, `INTERNET` + `ACCESS_NETWORK_STATE` permissions, banner, in `app/src/main/`
- [X] T019 [P] Add base strings (English) and Ukrainian translations for shared UI in `core/ui/src/main/res/values/strings.xml` and `core/ui/src/main/res/values-uk/strings.xml` (research.md R12)

**Checkpoint**: Project builds, parser and protocol tests green, empty app launches on Android TV

---

## Phase 3: User Story 1 - Send a link from phone by scanning a QR code (Priority: P1) 🎯 MVP

**Goal**: TV shows a QR; phone scans, pastes a GitHub link, taps Send; TV shows a preview and adds the app on Select.

**Independent Test**: quickstart.md §3 — scan, send `https://github.com/ImranR98/Obtainium`, press Select, app appears on Home; unsupported link shows "not supported yet" on both sides.

### Tests for User Story 1 ⚠️ (write first, must fail)

- [X] T020 [P] [US1] Write failing `SessionManagerTest`: one active session, token is 128-bit URL-safe Base64 (data-model.md `SendSession.token`), 6-digit PIN (`SendSession.pin`), expiry at 10 min (`SendSession.expiresAt`), rotation after 5 failed PINs (`SendSession.failedPinAttempts`), invalidate on demand, in `core/linkserver/src/test/kotlin/<pkg>/core/linkserver/SessionManagerTest.kt`
- [X] T021 [P] [US1] Write failing Ktor `testApplication` route tests for `GET /`, `GET /s/{token}` (valid + expired), `POST /api/v1/sessions/pin` (200/401/410), `POST /api/v1/links` with session token (202/401/422/400), Host-header guard (421), body > 8 KiB rejected, no CORS headers, 30 req/min rate limit (429), per contracts/lan-http-api.md, in `core/linkserver/src/test/kotlin/<pkg>/core/linkserver/LinkServerRoutesTest.kt`
- [X] T022 [P] [US1] Write failing Room DAO tests (Robolectric, in-memory) for `tracked_app` unique `(lower(owner), lower(repo))` (data-model.md `TrackedApp.owner`, `TrackedApp.repo`) and `incoming_link` PENDING collapse by `normalizedUrl` (data-model.md `IncomingLink.normalizedUrl`, `IncomingLink.status`) in `core/data/src/test/kotlin/<pkg>/core/data/db/DaoTest.kt`
- [X] T023 [P] [US1] Write failing `GitHubRepositoryTest` with Ktor `MockEngine` and JSON fixtures in `core/data/src/test/resources/github/` covering: repo + latest release, empty releases → `latestVersion = null`, pre-release only, 404 repo, rate-limit 403, network error; 10-min in-memory cache (research.md R6, plan.md Risks) in `core/data/src/test/kotlin/<pkg>/core/data/github/GitHubRepositoryTest.kt`
- [X] T024 [P] [US1] Write failing `AddAppViewModelTest` (session shown, rotation updates QR without changing focus target, no-network state, incoming link → navigate to Preview, leaving screen invalidates session) in `feature/addapp/src/test/kotlin/<pkg>/feature/addapp/AddAppViewModelTest.kt`
- [X] T025 [P] [US1] Write failing `PreviewViewModelTest` (loading → content, error → retry, already-tracked → Open, Add → TrackedApp created and link ADDED, Cancel leaves link PENDING) in `feature/addapp/src/test/kotlin/<pkg>/feature/addapp/PreviewViewModelTest.kt`
- [X] T026 [P] [US1] Write failing Compose UI tests with D-pad key input asserting initial focus, Back and states for Add app and Preview exactly as in contracts/tv-screens.md in `feature/addapp/src/test/kotlin/<pkg>/feature/addapp/AddAppScreenTest.kt` and `PreviewScreenTest.kt`
- [X] T027 [P] [US1] Write failing `HomeScreenTest` (Add app initially focused, tracked app names listed, Back exits) in `feature/home/src/test/kotlin/<pkg>/feature/home/HomeScreenTest.kt`

### Implementation for User Story 1

- [X] T028 [P] [US1] Implement Room entity `TrackedAppEntity` and `TrackedAppDao` with unique case-insensitive index (data-model.md `TrackedApp.owner`, `TrackedApp.repo`, `TrackedApp.sourceUrl`, `TrackedApp.latestVersion`, `TrackedApp.avatarUrl`, `TrackedApp.addedAt`) in `core/data/src/main/kotlin/<pkg>/core/data/db/TrackedApp.kt`
- [X] T029 [P] [US1] Implement Room entity `IncomingLinkEntity` with `IncomingLinkStatus` enum `PENDING|ADDED|DISMISSED` and `IncomingLinkDao` (insert-or-refresh PENDING by normalized URL, observe pending newest first) (data-model.md `IncomingLink.rawText` ≤ 4 000 chars enforced on insert, `IncomingLink.owner`, `IncomingLink.repo`, `IncomingLink.normalizedUrl`, `IncomingLink.senderLabel`, `IncomingLink.receivedAt`, `IncomingLink.status`; `IncomingLink.senderPhoneId` column added as nullable without FK until US2) in `core/data/src/main/kotlin/<pkg>/core/data/db/IncomingLink.kt`
- [X] T030 [US1] Implement `TvMarketplaceDatabase` (version 1, exported schema in `core/data/schemas/`) and Hilt `DatabaseModule` in `core/data/src/main/kotlin/<pkg>/core/data/db/` — makes T022 pass
- [X] T031 [P] [US1] Implement `GitHubRepository.preview(ref): AppPreview` with Ktor client + 10-min cache, and `AppPreview` model (data-model.md AppPreview) in `core/data/src/main/kotlin/<pkg>/core/data/github/GitHubRepository.kt` and `core/domain/src/main/kotlin/<pkg>/core/domain/AppPreview.kt` — makes T023 pass
- [X] T032 [US1] Implement `TrackedAppsRepository` (observe all, `add(preview)` returning existing row on duplicate, `isTracked(ref)`) and `IncomingLinksRepository` (receive, observe, markAdded, markDismissed, `isDuplicate` derived at read time per data-model.md IncomingLink rules) in `core/data/src/main/kotlin/<pkg>/core/data/`
- [X] T033 [P] [US1] Implement `SessionManager` (in-memory `SendSession`, `StateFlow<SendSession?>`, SecureRandom tokens) in `core/linkserver/src/main/kotlin/<pkg>/core/linkserver/SessionManager.kt` — makes T020 pass
- [X] T034 [P] [US1] Implement `LanAddressProvider` (IPv4 of active Wi-Fi/Ethernet via `ConnectivityManager`/`LinkProperties`, `Flow<LanAddress?>`) in `core/linkserver/src/main/kotlin/<pkg>/core/linkserver/LanAddressProvider.kt`
- [X] T035 [US1] Implement `LinkServer` (Ktor CIO, bind to LAN address, port 8765 → 8766..8775 → ephemeral per research.md R3, start/stop off main thread), Host guard, body limit, rate limiter, and routes `/`, `/s/{token}`, `/assets/*`, `/api/v1/sessions/pin`, `/api/v1/links` (session auth only) storing via `IncomingLinksRepository` and emitting `IncomingLinkEvents` in `core/linkserver/src/main/kotlin/<pkg>/core/linkserver/` — makes T021 pass
- [X] T036 [P] [US1] Create the send page `index.html`, `send.css`, `send.js` (paste field with big Paste button, Send, "Sent"/error states, 5 s timeout same-Wi-Fi hint, unsupported examples, en + uk by `Accept-Language`) in `core/linkserver/src/main/assets/web/`
- [X] T037 [US1] Wire `LinkServer` lifecycle to the app process (start on app start, stop on process death) via `ProcessLifecycleOwner` observer and Hilt module in `app/src/main/kotlin/<pkg>/app/LinkServerLifecycle.kt`
- [X] T038 [P] [US1] Implement `QrCode` composable using ZXing `QRCodeWriter` (ECC M, quiet zone, dark-on-light in both themes) in `feature/addapp/src/main/kotlin/<pkg>/feature/addapp/QrCode.kt`
- [X] T039 [US1] Implement `AddAppViewState`, `AddAppViewModel`, stateless `AddAppView` (+ @Previews) and `AddAppScreen` per contracts/tv-screens.md "Add app" in `feature/addapp/src/main/kotlin/<pkg>/feature/addapp/` — makes T024 and Add-app part of T026 pass
- [X] T040 [US1] Implement `PreviewViewState`, `PreviewViewModel`, `PreviewView` (+ @Previews) and `PreviewScreen` per contracts/tv-screens.md "Preview" in `feature/addapp/src/main/kotlin/<pkg>/feature/addapp/preview/` — makes T025 and Preview part of T026 pass
- [X] T041 [US1] Implement Home shell (Add app, Inbox placeholder hidden until US2, Settings placeholder hidden until US2, tracked app names) in `feature/home/src/main/kotlin/<pkg>/feature/home/` — makes T027 pass
- [X] T042 [US1] Register Home and AddApp/Preview nav entries and Hilt contributions in `feature/home/src/main/kotlin/<pkg>/feature/home/di/HomeModule.kt`, `feature/addapp/src/main/kotlin/<pkg>/feature/addapp/di/AddAppModule.kt`, and add the modules to `app/build.gradle.kts`
- [X] T043 [P] [US1] Add English + Ukrainian strings for Add app, Preview and Home in `feature/addapp/src/main/res/values{,-uk}/strings.xml` and `feature/home/src/main/res/values{,-uk}/strings.xml`

**Checkpoint**: quickstart.md §3 passes on a real TV — MVP complete

---

## Phase 4: User Story 2 - Send more links later without rescanning (Priority: P2)

**Goal**: Phones can be remembered; later sends land in a persistent Inbox even when the app is in background; paired phones can be revoked.

**Independent Test**: quickstart.md §4 — pair once, background the app, send, reopen → Inbox item → Add; remove phone → next send rejected.

### Tests for User Story 2 ⚠️ (write first, must fail)

- [X] T044 [P] [US2] Write failing `PairingManagerTest`: 256-bit device token issued, only SHA-256 stored (data-model.md `PairedPhone.tokenHash` unique), `displayName` derived from User-Agent and truncated to 60 chars (`PairedPhone.displayName`), `kind` BROWSER (`PairedPhone.kind`), `lastUsedAt` updated on accepted send (`PairedPhone.lastUsedAt`), revoke → unknown token in `core/linkserver/src/test/kotlin/<pkg>/core/linkserver/PairingManagerTest.kt`
- [X] T045 [P] [US2] Extend route tests: `POST /api/v1/links` with `pair: true` returns `deviceToken`; Bearer device-token send → 202; revoked → 403 `device_revoked`; `GET /api/v1/ping` (200 paired/unpaired, 403) per contracts/lan-http-api.md in `core/linkserver/src/test/kotlin/<pkg>/core/linkserver/PairedRoutesTest.kt`
- [X] T046 [P] [US2] Write failing Room migration test v1 → v2 (adds `paired_phone`, FK `incoming_link.senderPhoneId` → `paired_phone.id` ON DELETE SET NULL per data-model.md `IncomingLink.senderPhoneId`) and purge of ADDED/DISMISSED older than 30 days in `core/data/src/test/kotlin/<pkg>/core/data/db/MigrationV1V2Test.kt`
- [X] T047 [P] [US2] Write failing `LinkShareSettingsStoreTest` (defaults `receive_in_background = true`, `preferred_port = 8765` per data-model.md Settings) in `core/data/src/test/kotlin/<pkg>/core/data/settings/LinkShareSettingsStoreTest.kt`
- [X] T048 [P] [US2] Write failing `InboxViewModelTest` + D-pad `InboxScreenTest` (newest first, duplicate badge, Select → Preview, Menu → Dismiss, empty state with Add app focused, Back → Home) per contracts/tv-screens.md "Inbox" in `feature/inbox/src/test/kotlin/<pkg>/feature/inbox/`
- [X] T049 [P] [US2] Write failing `PairedPhonesViewModelTest` + D-pad `SettingsScreenTest` (list, remove dialog with Cancel focused, background toggle, IP tip) per contracts/tv-screens.md "Settings → Paired phones" in `feature/settings/src/test/kotlin/<pkg>/feature/settings/`
- [X] T050 [P] [US2] Write failing test for incoming-link notice on Home (shown 8 s, does not steal focus, opens Preview) in `feature/home/src/test/kotlin/<pkg>/feature/home/IncomingNoticeTest.kt`

### Implementation for User Story 2

- [X] T051 [P] [US2] Implement `PairedPhoneEntity` + `PairedPhoneDao` (data-model.md `PairedPhone.id`, `PairedPhone.displayName`, `PairedPhone.kind` enum `BROWSER|COMPANION`, `PairedPhone.tokenHash` unique index, `PairedPhone.pairedAt`, `PairedPhone.lastUsedAt`) in `core/data/src/main/kotlin/<pkg>/core/data/db/PairedPhone.kt`
- [X] T052 [US2] Bump `TvMarketplaceDatabase` to version 2 with `Migration(1, 2)` adding the FK for data-model.md `IncomingLink.senderPhoneId`, plus a periodic purge query in `IncomingLinkDao` — makes T046 pass
- [X] T053 [P] [US2] Implement `PairedPhonesRepository` (observe, revoke, touch) in `core/data/src/main/kotlin/<pkg>/core/data/PairedPhonesRepository.kt`
- [X] T054 [P] [US2] Implement `LinkShareSettingsStore` on DataStore file `link_share_settings` (data-model.md Settings `receive_in_background`, `preferred_port`) in `core/data/src/main/kotlin/<pkg>/core/data/settings/LinkShareSettingsStore.kt` — makes T047 pass
- [X] T055 [US2] Implement `PairingManager` and add device-token auth, `pair: true` handling and `GET /api/v1/ping` to `LinkServer` routes in `core/linkserver/src/main/kotlin/<pkg>/core/linkserver/` — makes T044, T045 pass
- [X] T056 [US2] Update `core/linkserver/src/main/assets/web/send.js`: "Remember this TV" opt-in, store token in `localStorage`, use Bearer on later sends, ping on load, handle `device_revoked` and unreachable TV ("Open TV Marketplace on your TV")
- [X] T057 [US2] Implement `LinkServerService` foreground service (`foregroundServiceType="specialUse"`, notification channel) started on `ON_STOP` when `receive_in_background` is on, stopped on `ON_START`; declare in `app/src/main/AndroidManifest.xml` with `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS` in `app/src/main/kotlin/<pkg>/app/LinkServerService.kt` and update `LinkServerLifecycle.kt`
- [X] T058 [US2] Implement Inbox feature (`InboxViewState`, `InboxViewModel`, `InboxView` + @Previews, `InboxScreen`, nav entry) in `feature/inbox/src/main/kotlin/<pkg>/feature/inbox/` — makes T048 pass
- [X] T059 [US2] Implement Settings feature (paired phones list, remove dialog, background toggle, IP-reservation tip, nav entry) in `feature/settings/src/main/kotlin/<pkg>/feature/settings/` — makes T049 pass
- [X] T060 [US2] Show Inbox (n) and Settings on Home and implement the incoming-link notice in `feature/home/src/main/kotlin/<pkg>/feature/home/` — makes T050 pass
- [X] T061 [P] [US2] Add English + Ukrainian strings for Inbox, Settings, notice and new page texts in `feature/inbox/src/main/res/values{,-uk}/strings.xml`, `feature/settings/src/main/res/values{,-uk}/strings.xml`, `core/linkserver/src/main/assets/web/send.js`

**Checkpoint**: quickstart.md §4 passes; US1 still passes

---

## Phase 5: User Story 3 - Share directly from the phone's Share menu (Priority: P3)

**Goal**: Share a GitHub link from the phone's Share sheet into the TV Inbox (Android companion app; iOS Shortcut opening a prefilled page).

**Independent Test**: quickstart.md §5 — Android: pair companion with PIN, share a repo → Inbox; iPhone: Shortcut → prefilled page → Send → Inbox.

**Depends on**: US2 (device tokens, Inbox)

### Tests for User Story 3 ⚠️ (write first, must fail)

- [X] T062 [P] [US3] Write failing route tests for `POST /api/v1/pair` (200/401/410, `kind = COMPANION` per data-model.md `PairedPhone.kind`, `deviceName` truncated per `PairedPhone.displayName`) and `GET /send?u=` (prefill, never auto-send) in `core/linkserver/src/test/kotlin/<pkg>/core/linkserver/CompanionRoutesTest.kt`
- [X] T063 [P] [US3] Write failing `NsdAdvertiserTest` (service type `_tvmarket._tcp.`, TXT `v=1` and stable install `id`, registered while server runs, unregistered on stop) per contracts/nsd-discovery.md in `core/linkserver/src/test/kotlin/<pkg>/core/linkserver/NsdAdvertiserTest.kt`
- [X] T064 [P] [US3] Write failing companion tests: `TvDiscovery` resolves by TXT `id`, 10 s timeout message, `CompanionSender` posts with Bearer token, handles 403 by clearing pairing, share-intent text parsed with `GitHubLinkParser` in `companion/src/test/kotlin/<pkg>/companion/`

### Implementation for User Story 3

- [X] T065 [US3] Add `POST /api/v1/pair` and `GET /send?u=` routes (prefill via page script) in `core/linkserver/src/main/kotlin/<pkg>/core/linkserver/` and `core/linkserver/src/main/assets/web/send.js` — makes T062 pass
- [X] T066 [US3] Implement `NsdAdvertiser` with persistent install id (DataStore key `install_id`) tied to `LinkServer` start/stop in `core/linkserver/src/main/kotlin/<pkg>/core/linkserver/NsdAdvertiser.kt` — makes T063 pass
- [X] T067 [US3] Implement companion app: `ShareReceiverActivity` (`ACTION_SEND` `text/plain`), `TvDiscovery` (NSD browse/resolve), `PairingScreen` (pick TV, enter PIN), `CompanionSender`, encrypted token storage, Material 3 UI, manifest in `companion/src/main/` — makes T064 pass
- [X] T068 [US3] Add "Add to iPhone Share menu" link on the send page, reading the Shortcut iCloud URL from `core/linkserver/src/main/assets/web/config.json` (placeholder until the maintainer publishes the Shortcut, plan.md Open items)
- [X] T069 [P] [US3] Add English + Ukrainian strings for companion in `companion/src/main/res/values{,-uk}/strings.xml`

**Checkpoint**: quickstart.md §5 passes; US1 and US2 still pass

---

## Phase 6: User Story 4 - Enter a link on the TV with minimal typing (Priority: P3)

**Goal**: "Type it instead" accepts `owner/repo` or a full GitHub URL and opens the same Preview.

**Independent Test**: quickstart.md §6 — type `ImranR98/Obtainium` → Continue → Preview.

**Depends on**: US1 (Preview)

### Tests for User Story 4 ⚠️ (write first, must fail)

- [X] T070 [P] [US4] Extend `GitHubLinkParserTest` with `parseUserInput()` cases: `owner/repo` shorthand, full URL, whitespace trimmed, invalid input → error, in `core/domain/src/test/kotlin/<pkg>/core/domain/GitHubLinkParserTest.kt`
- [X] T071 [P] [US4] Write failing `TypeItViewModelTest` + D-pad `TypeItScreenTest` (field initially focused, inline error keeps focus, Continue → Preview(owner, repo), Back → Add app) in `feature/addapp/src/test/kotlin/<pkg>/feature/addapp/typeit/`

### Implementation for User Story 4

- [X] T072 [US4] Implement `GitHubLinkParser.parseUserInput()` (shorthand defaults to GitHub) in `core/domain/src/main/kotlin/<pkg>/core/domain/GitHubLinkParser.kt` — makes T070 pass
- [X] T073 [US4] Implement Type it screen (`TypeItViewModel`, `TypeItView` + @Previews, `TypeItScreen`, nav entry, en + uk strings) and the "Type it instead" action on Add app in `feature/addapp/src/main/kotlin/<pkg>/feature/addapp/typeit/` — makes T071 pass

**Checkpoint**: All four stories pass their quickstart sections

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T074 [P] Implement Baseline Profile generator (startup → Home, open Add app) and Macrobenchmarks (cold start ≤ 2.0 s p90, Add app QR ≤ 500 ms, jank < 5%) in `baselineprofile/src/main/kotlin/<pkg>/baselineprofile/`
- [X] T075 [P] TalkBack pass: content descriptions for every focusable element and the QR ("QR code to send a link from your phone") across `feature/*/src/main/kotlin/`
- [X] T076 [P] Verify privacy-safe logging: add a Detekt custom rule or test that fails when `Logger` calls receive URL/IP/token-typed arguments in `build-logic/convention/` or `core/common/src/test/`
- [X] T077 Security review of `core/linkserver` against research.md R4 (Host guard, rate limit, body limit, PIN lockout, token entropy, no CORS) and record results in `specs/001-phone-link-sharing/checklists/security.md`
- [ ] T078 Run quickstart.md §1–§7 end to end on a real Android TV and an iPhone + Android phone; fix any failures

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)** → **Foundational (Phase 2)** → user stories → **Polish (Phase 7)**
- T001 → T002 → T004 → T005/T007; T003, T006, T008 parallel after T001; T009 after T007

### User Story Dependencies

- **US1 (P1)**: after Phase 2; no story dependencies — MVP
- **US2 (P2)**: after Phase 2; builds on US1's server, DB and Home (extends them, US1 keeps passing)
- **US3 (P3)**: after US2 (device tokens, Inbox)
- **US4 (P3)**: after US1 (Preview); independent of US2/US3 — can run in parallel with US2

```
Phase 1 → Phase 2 → US1 ─┬─► US2 ─► US3 ─┐
                         └─► US4 ────────┴─► Polish
```

### Within Each User Story

- Test tasks first and observed failing (Constitution II)
- Entities → DAOs/DB → repositories → server/ViewModels → screens → nav wiring → strings

## Parallel Examples

### User Story 1

```text
# All US1 tests together:
T020 SessionManagerTest | T021 LinkServerRoutesTest | T022 DaoTest | T023 GitHubRepositoryTest
T024 AddAppViewModelTest | T025 PreviewViewModelTest | T026 Add app/Preview UI tests | T027 HomeScreenTest

# Then independent implementation files:
T028 TrackedApp entity | T029 IncomingLink entity | T031 GitHubRepository
T033 SessionManager | T034 LanAddressProvider | T036 send page | T038 QrCode
```

### User Story 2

```text
T044 PairingManagerTest | T045 PairedRoutesTest | T046 MigrationV1V2Test | T047 SettingsStoreTest
T048 Inbox tests | T049 Settings tests | T050 IncomingNoticeTest
then: T051 PairedPhone entity | T053 PairedPhonesRepository | T054 LinkShareSettingsStore
```

### User Story 3 / 4

```text
T062 | T063 | T064 in parallel;  T070 | T071 in parallel (US4 can run alongside US2)
```

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 Setup → Phase 2 Foundational
2. Phase 3 (US1) → **STOP and VALIDATE** with quickstart.md §3 on a real TV
3. Demo: scan → send → Add

### Incremental Delivery

1. US1 → MVP
2. US2 → remembered phones + Inbox + background receive
3. US4 → on-TV fallback (small, parallel with US2)
4. US3 → companion app + iOS Shortcut
5. Polish → benchmarks, accessibility, security review

## Notes

- [P] = different files, no dependency on unfinished tasks
- Commit after each task or test/implementation pair, on branch `001-phone-link-sharing`
- Do not rename Room columns, DataStore files or keys once shipped (data-model.md)
- `com.tvmarketplace` is a placeholder; replace across modules before first release
