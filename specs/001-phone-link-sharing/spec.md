# Feature Specification: Send App Links from Phone to TV

**Feature Branch**: `001-phone-link-sharing`

**Created**: 2026-11-18

**Status**: Draft

**Input**: User description: "ми будемо робити апку під ТВ по типу https://github.com/ImranR98/Obtainium бо вона взагалі не розрахована на ТВ тому що flutter хуйово дуже працює з пультами. що я хочу це щоб можна було лінку пошарити з телефона в цю апку легко запропонуй рішення як це зробити може скан QR коду чи як я не знаю, просто на ТВ вводити лінки буде дуже довго тому треба максимально зручно придумати спосіб шарити з телефона"

## Context

The product is a TV-native app manager, similar in spirit to Obtainium: the user adds an app by
pointing it at a source link (for example a GitHub repository's releases page), and the TV app
tracks that source and installs/updates the app directly from it. Existing tools are not usable
with a TV remote, and typing a long URL with an on-screen keyboard and D-pad takes minutes.

This feature defines how a user gets a link from their phone into the TV app with minimum effort.

### Proposed solution (summary)

1. **Scan-to-send (primary)**: The TV shows a QR code on an "Add app" screen. The user points the
   phone's regular camera at it; a small "Send to TV" page opens in the phone's browser — no phone
   app install required. The user pastes the link (or it is pre-filled from the clipboard where the
   phone allows it) and taps Send. The link appears on the TV within seconds, and the TV shows a
   preview of the app to confirm with one press of Select.
2. **Remember this phone (convenience)**: After the first scan, the phone can keep the "Send to
   TV" page (bookmark / home-screen shortcut) and send more links later without rescanning, until
   the user revokes it on the TV.
3. **Share-menu sending (later)**: Sending a link straight from the phone's system Share menu
   (e.g. while viewing a repository in the phone browser), so no copy/paste is needed.
4. **On-TV entry (fallback)**: When no phone is at hand, the TV accepts short forms such as
   `owner/repo` so the user types as little as possible.

Rejected alternatives: TV-side camera scanning (most TVs have no camera); typing full URLs only
(the problem being solved); cloud account sync (requires sign-in and a server, disproportionate
for v1).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Send a link from phone by scanning a QR code (Priority: P1)

The user opens "Add app" on the TV with the remote. A QR code and a short human-readable address
are shown. They scan the code with their phone camera, a "Send to TV" page opens in the phone
browser, they paste a link and tap Send. The TV immediately shows the received link resolved into
an app preview (name, icon if available, source, latest version) with "Add" focused. Pressing
Select adds the app.

**Why this priority**: This is the core problem — getting a link onto the TV without typing.
Without it, the product is no better than existing tools on TV.

**Independent Test**: With the TV app and a phone on the same home network, open "Add app", scan,
paste a valid repository link, send, and confirm on TV. The app appears in the tracked list. No
other feature is needed.

**Acceptance Scenarios**:

1. **Given** the TV is on the "Add app" screen, **When** the user scans the QR code with a phone
   camera, **Then** a "Send to TV" page opens on the phone without installing anything.
2. **Given** the "Send to TV" page is open, **When** the user pastes a supported link and taps
   Send, **Then** the phone shows "Sent" and the TV shows the app preview within 3 seconds.
3. **Given** the preview is shown on the TV, **When** the user presses Select on the focused
   "Add" button, **Then** the app is added to the tracked list and the user sees confirmation.
4. **Given** the preview is shown, **When** the user presses Back, **Then** nothing is added and
   the TV returns to the "Add app" screen with the QR code still valid.
5. **Given** the phone sends a link the app does not support, **When** the TV receives it,
   **Then** both the phone page and the TV show a clear message explaining the link is not
   supported, with examples of supported links.

---

### User Story 2 - Send more links later without rescanning (Priority: P2)

After the first scan, the user saves the "Send to TV" page on their phone. Days later, they open
it from the phone, paste a new link, and send it. The TV — if the app is open — shows an incoming
link notice; otherwise the link waits in an "Inbox" on the TV until the user opens the app.

**Why this priority**: Users will add several apps over time; rescanning each time is friction,
but the core flow already works without this.

**Independent Test**: Pair a phone once, close the TV app, send a link from the saved page, open
the TV app and confirm the link is waiting in the Inbox and can be added.

**Acceptance Scenarios**:

1. **Given** a phone was previously paired, **When** the user sends a link from the saved page
   while the TV app is open, **Then** the TV shows a non-blocking notice that can be opened with
   one press.
2. **Given** a phone was previously paired and the TV app is not in the foreground, **When** a
   link is sent, **Then** it is stored in the TV Inbox and shown the next time the app opens.
3. **Given** a paired phone, **When** the user removes it in TV settings, **Then** further sends
   from that phone are rejected and the phone page tells the user to scan again.
4. **Given** the user sends several links in a row, **When** they arrive, **Then** each appears
   as a separate Inbox entry, and duplicates of already-tracked apps are flagged as such.

---

### User Story 3 - Share directly from the phone's Share menu (Priority: P3)

While browsing a repository in the phone browser, the user taps Share and picks the TV as the
target. The link arrives on the TV exactly as in Story 2, with no copy/paste.

**Why this priority**: Removes the last manual step, but depends on Story 2's pairing and may
require extra setup on the phone.

**Independent Test**: With a paired phone, share a repository page from the phone browser via the
system Share menu and confirm it arrives in the TV Inbox.

**Acceptance Scenarios**:

1. **Given** a paired phone with Share-menu sending set up, **When** the user shares a link to the
   TV, **Then** the link arrives in the TV Inbox and the phone confirms it was sent.

---

### User Story 4 - Enter a link on the TV with minimal typing (Priority: P3)

Without a phone, the user can choose "Type it" on the "Add app" screen and enter a short form like
`owner/repo`; the app expands it to the full source link and shows the same preview.

**Why this priority**: A fallback for when no phone is available; not the main path.

**Independent Test**: On the TV, type `owner/repo` with the remote and confirm the preview matches
the full repository link.

**Acceptance Scenarios**:

1. **Given** the "Type it" field, **When** the user enters `owner/repo` and confirms, **Then** the
   TV shows the preview for that repository.
2. **Given** the "Type it" field, **When** the user enters a full URL, **Then** it is accepted as is.

---

### Edge Cases

- Phone and TV are on different networks (e.g. phone on mobile data, guest Wi-Fi): the phone page
  cannot reach the TV — the page MUST explain "Connect your phone to the same Wi-Fi as the TV"
  rather than spinning forever; the TV screen MUST show the same hint.
- The TV's network address changes (router restart): previously saved phone pages MUST detect it
  and ask the user to rescan instead of failing silently.
- Someone else on the same network tries to send a link without scanning: the send MUST be
  rejected; nothing is added without the TV-side confirmation in any case.
- The pasted text contains extra words around the link (e.g. copied from a chat message): the
  first supported link in the text is extracted.
- A link points to an app already tracked: the TV says so and offers to open the existing entry.
- The source has no installable release yet: preview shows "No releases found", and the user can
  still add it to track future releases.
- The TV app is on the QR screen for a long time: the code is refreshed automatically and old codes
  stop working.
- Two phones send at once: both links are received and queued; none is lost.

## Requirements *(mandatory)*

### Functional Requirements

**Receiving links (Story 1)**

- **FR-001**: The TV "Add app" screen MUST display a scannable QR code and an equivalent short
  address that can be typed into a phone browser manually.
- **FR-002**: Scanning the QR code with a standard phone camera MUST open a "Send to TV" page in the
  phone's browser; no phone app installation MUST be required.
- **FR-003**: The "Send to TV" page MUST let the user paste or type a link and send it with a
  single tap, and MUST show success or a specific error.
- **FR-004**: The TV MUST accept links only from phones that obtained the current code by scanning
  (or a previously paired phone, FR-010). Codes MUST expire after 10 minutes of display or once
  the screen is left, whichever comes first, and be refreshed automatically.
- **FR-005**: The TV MUST NOT add any app without explicit user confirmation on the TV.
- **FR-006**: On receiving a link, the TV MUST show a preview (app name, source, latest available
  version, icon if available) with the "Add" action focused by default.
- **FR-007**: The system MUST extract the first supported link from arbitrary pasted text.
- **FR-008**: The system MUST support GitHub repository links (repository page, releases page,
  or a specific release) as sources in v1. Any other link MUST be reported as "not supported yet"
  with examples of supported GitHub links. Other sources (GitLab, Codeberg, F-Droid, direct APK)
  are out of scope for this feature.
- **FR-009**: After the user confirms, the app MUST be added to the tracked-apps list and the user
  MUST see a confirmation. Downloading and installing are out of scope for this feature and are
  covered by a separate specification.

**Pairing and inbox (Story 2)**

- **FR-010**: After a successful send, the phone page MUST offer to remember the TV, so later sends
  work without scanning while the pairing remains valid.
- **FR-011**: The TV MUST keep an Inbox of received-but-not-yet-confirmed links that survives app
  restarts, until the user adds or dismisses each one.
- **FR-012**: The TV MUST list paired phones in settings and let the user remove any of them;
  removed phones MUST be rejected on their next send.
- **FR-013**: Links already tracked MUST be flagged as duplicates in the preview and Inbox.

**Share menu (Story 3)**

- **FR-014**: A paired phone MUST be able to send a link via the phone's system Share menu without
  copying and pasting.

**On-TV entry (Story 4)**

- **FR-015**: The TV MUST accept `owner/repo` shorthand and expand it to the default source, as well
  as full URLs.

**General (Constitution I & V)**

- **FR-016**: Every TV screen in this feature MUST be fully operable with D-pad, Select and Back,
  with a defined initial focus and visible focus indicator.
- **FR-017**: When the phone cannot reach the TV, both the phone page and the TV MUST show a
  same-network hint instead of an indefinite wait.
- **FR-018**: Received links and pairing information MUST stay on the local network and the TV;
  they MUST NOT be sent to any third-party service.

### Key Entities

- **Send Session**: A short-lived invitation shown as a QR code on the TV; has an expiry and is
  tied to the currently displayed screen.
- **Paired Phone**: A phone the user allowed to send links without rescanning; has a display name
  (e.g. phone browser/model), paired date, last-used date; revocable.
- **Incoming Link**: A link received from a phone; has the raw text, extracted link, sender,
  received time and status (pending, added, dismissed, unsupported, duplicate).
- **Tracked App**: An app the user added; has its source link, display name, and latest known
  version. (Full lifecycle defined by later features.)

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A first-time user can get a link from their phone onto the TV and add the app in under
  30 seconds from opening "Add app", versus several minutes typing with the remote.
- **SC-002**: A paired user can send a subsequent link and add it in under 15 seconds.
- **SC-003**: 95% of sends on the same network appear on the TV within 3 seconds.
- **SC-004**: 90% of first-time users complete the scan-to-send flow on the first attempt without
  instructions beyond what is on screen.
- **SC-005**: Zero apps are added without a confirmation press on the TV.
- **SC-006**: The flow works on current iPhone and Android phones using only the built-in camera
  and browser.

## Assumptions

- The phone and the TV are on the same home network for v1; sending over the internet (remote
  relay or account sync) is out of scope.
- The phone's built-in camera app can open URLs from QR codes (true for current iOS and Android).
- The phone browser may block automatic clipboard reading; the page offers a large "Paste" button
  instead of relying on auto-fill.
- Story 3 (Share menu) may require a one-time setup on the phone; exact mechanism is decided in
  planning.
- Shorthand `owner/repo` defaults to GitHub (the only supported source in v1).
- This feature ends when the app is added to the tracked list; download/install is a separate
  feature.
- Updating, uninstalling and the tracked-apps list UI beyond "added" confirmation are separate
  features.
