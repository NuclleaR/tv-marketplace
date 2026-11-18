# Research: Send App Links from Phone to TV

**Feature**: `001-phone-link-sharing` | **Date**: 2026-11-18

Each entry: Decision / Rationale / Alternatives considered.

## R1. How the phone reaches the TV

- **Decision**: The TV app runs an embedded HTTP server on the local network and serves the
  "Send to TV" page itself. The QR code encodes `http://<tv-lan-ip>:<port>/s/<sessionToken>`.
- **Rationale**: Works with any phone camera + browser (FR-002), no install, no backend, no
  account, and data never leaves the LAN (FR-018). Latency on a LAN is well under the 3 s budget
  (SC-003).
- **Alternatives considered**:
  - Cloud relay + HTTPS page (GitHub Pages + WebSocket relay): works across networks and unlocks
    the async clipboard API and PWA share target, but needs a server to run and secure, and links
    transit a third party. Rejected for v1 (spec assumption: same network only).
  - HTTPS page on the internet calling the TV's LAN address: blocked by mixed-content rules and
    Private Network Access. Not viable.
  - TV camera scanning a QR on the phone: most TVs have no camera.
  - Bluetooth / Wi-Fi Direct: needs a phone app on every platform; iOS Web Bluetooth absent.

## R2. HTTP server library

- **Decision**: Ktor server with the CIO engine.
- **Rationale**: Pure Kotlin, coroutine-native, small, works on Android, has `testApplication`
  for in-process route tests (Constitution II). Same ecosystem as the Ktor HTTP client (R6) and
  kotlinx.serialization.
- **Alternatives considered**: NanoHTTPD (tiny but unmaintained, blocking, Java API);
  AndroidAsync (unmaintained); raw `ServerSocket` (reinvents HTTP parsing).

## R3. Port, address and interface

- **Decision**: Bind to a fixed preferred port `8765`; if busy, try `8766..8775`, then an
  ephemeral port. The displayed address is the IPv4 of the active Wi-Fi or Ethernet network from
  `ConnectivityManager` / `LinkProperties`. The server binds only to that address, not `0.0.0.0`.
- **Rationale**: A stable port keeps saved phone bookmarks valid across app restarts. Android TV
  devices are frequently on Ethernet, so both transports are needed. Binding to the LAN address
  avoids exposure on VPN/other interfaces.
- **Alternatives considered**: Random port every time (breaks bookmarks); mDNS `.local` hostname
  as primary address (unreliable in Android browsers; used only for the companion app via NSD,
  R9).
- **Known limitation**: If the router assigns the TV a new IP, browser-saved pages stop working
  (their origin, and therefore their localStorage token, is tied to `ip:port`). The page detects
  the failure and asks to rescan; TV settings suggest reserving the TV's IP on the router.

## R4. Session tokens, PIN and pairing security

- **Decision**:
  - **Send session**: 128-bit random token (URL-safe Base64) in the QR path, plus a 6-digit PIN
    shown next to the plain address for manual entry. Valid ≤ 10 min or until the Add-app screen
    closes; rotated automatically (FR-004). Single active session at a time.
  - **Pairing**: On the first successful send, the phone may opt in to "Remember this TV"; the TV
    issues a 256-bit device token. Only its SHA-256 hash is stored on the TV. Browser stores it in
    `localStorage`; companion app in its own encrypted storage. Revocation deletes the hash
    (FR-012).
  - **Hardening**: `Host` header must equal the bound `ip:port` (DNS-rebinding guard); no CORS
    headers; JSON-only `POST` bodies; request body limit 8 KiB; 5 failed PIN attempts lock the
    session (rotate PIN); per-IP rate limit of 30 requests/min. Every link still requires a TV-side
    confirmation (FR-005).
- **Rationale**: Anyone on the LAN can reach the port, so knowledge of a fresh secret proves the
  user saw the TV screen. Hashing device tokens limits damage if the TV database leaks.
- **Alternatives considered**: TLS with a self-signed certificate (browser warnings make the
  flow worse than plain HTTP on a home LAN); no token, confirmation only (lets any LAN device spam
  the inbox).

## R5. Server lifetime (Story 2: TV app not in foreground)

- **Decision**: The server starts when the app process starts (off the main thread) and stays up
  while the app is in the foreground. When the app goes to the background, a foreground service
  (`foregroundServiceType="specialUse"`) keeps the server alive so links land in the Inbox. A
  setting "Receive links in background" (default **on**) controls this. No start on boot in v1.
- **Rationale**: Meets Story 2 scenario 2 for the common case (TV app opened at least once since
  power-on) without boot receivers. TV power/memory budgets tolerate a small idle server.
- **Alternatives considered**: Server only while Add-app screen is open (fails Story 2); start on
  boot (extra permission and complexity; revisit if users ask).
- **Known limitation**: After a TV reboot, sends fail until the app is opened once; the phone
  page shows "Open TV Marketplace on your TV".

## R6. Resolving GitHub links (FR-006, FR-008)

- **Decision**: Ktor HTTP client (OkHttp engine) + kotlinx.serialization against the public GitHub
  REST API, unauthenticated: `GET /repos/{owner}/{repo}` and
  `GET /repos/{owner}/{repo}/releases?per_page=1`. Avatar of the repository owner is used as the
  icon. An empty releases list is shown as "No releases found" and the app can still be added.
- **Rationale**: Unauthenticated limit (60 req/h per IP) is ample for manual adds. `releases`
  (not `releases/latest`) also covers repos that only publish pre-releases.
- **Alternatives considered**: Scraping HTML (fragile); GraphQL (requires a token).

## R7. Link extraction and normalization (FR-007, FR-015)

- **Decision**: Pure-Kotlin `GitHubLinkParser` in `:core:domain`. Scans text for the first match
  of `https?://(www\.)?github\.com/<owner>/<repo>[/releases[/tag/<tag>]][.git][?…][#…]`. Owner:
  `[A-Za-z0-9-]{1,39}`; repo: `[A-Za-z0-9._-]{1,100}` minus `.git`. Normalizes to
  `https://github.com/<owner>/<repo>`. Shorthand `owner/repo` is accepted only from on-TV entry.
  Reserved GitHub paths (`orgs`, `settings`, `topics`, …) are rejected.
- **Rationale**: Deterministic, fully unit-testable, shared by TV server and companion app.
- **Alternatives considered**: `Uri` parsing only (fails on surrounding text).

## R8. QR code rendering

- **Decision**: ZXing `core` (pure Java) to produce a bit matrix, drawn in Compose as an
  `ImageBitmap`; error correction level M; rendered dark-on-light with quiet zone regardless of
  theme.
- **Rationale**: No Google Play Services dependency, small, mature.
- **Alternatives considered**: ML Kit (GMS-dependent, and only scans); qrcode-kotlin (fine but
  less proven).

## R9. Story 3: Share-menu sending

- **Decision**:
  - **Android**: a small companion phone app (`:companion`). It appears in the system Share sheet
    (`ACTION_SEND`, `text/plain`), discovers the TV with NSD (`_tvmarket._tcp`), pairs once by
    entering the 6-digit PIN shown on the TV, and posts links with its device token. NSD makes it
    immune to IP changes.
  - **iOS**: an Apple Shortcut ("Send to TV") that accepts URLs from the Share sheet and opens
    `http://<tv>:<port>/send?u=<url>` in Safari. The saved page pre-fills the link; the user taps
    Send once. The page offers the Shortcut via an iCloud link published by the maintainer.
- **Rationale**: Browsers cannot register as share targets over plain HTTP (R1), so a native
  hook is needed per platform. Prefill + explicit tap avoids drive-by sends from other websites.
- **Alternatives considered**: PWA Web Share Target (requires HTTPS; not on iOS); auto-send on
  `?u=` (lets any website push into the inbox).

## R10. Stack choices (Constitution "Technology Stack")

- **Decision**: Kotlin 2.x (K2), Jetpack Compose + Compose for TV (`androidx.tv:tv-material`),
  Navigation 3, Hilt, Room (tracked apps, incoming links, paired phones), DataStore (settings),
  Coil 3 (avatars), kotlinx.serialization, coroutines/Flow. minSdk 26, targetSdk/compileSdk 36.
  All versions pinned in `gradle/libs.versions.toml`.
- **Rationale**: Matches the constitution; minSdk 26 covers current Google TV and Fire OS 7+.
- **Alternatives considered**: Metro DI (faster builds, younger ecosystem; revisit later);
  Koin (runtime resolution errors).

## R11. Testing approach (Constitution II)

- **Decision**: kotlin.test + JUnit runner; Turbine for Flows; Ktor `testApplication` for server
  routes; Ktor `MockEngine` with recorded JSON fixtures for GitHub; Robolectric for Room and
  Compose UI tests; UI tests drive screens with `performKeyInput { pressKey(Key.DirectionDown) }`
  and assert focus. Macrobenchmark + Baseline Profile module for startup and Add-app screen open.
- **Rationale**: All automated, no live network (Constitution II), fast on JVM.
- **Alternatives considered**: Espresso on device only (slow, flaky on TV emulators).

## R12. Localization

- **Decision**: English (default) and Ukrainian for both the TV app and the phone page. The page
  picks language from `Accept-Language`.
- **Rationale**: Primary user is Ukrainian-speaking; the cost of two locales from the start is low.
