# Contract: TV screens and D-pad behavior

UI contract for Constitution Principle I and FR-016. Each screen lists initial focus, focus order
and Back behavior. Compose UI tests assert these exactly.

## Home (minimal shell for this feature)

- Items: **Add app** (initial focus), **Inbox (n)**, **Settings**, then the tracked apps (names
  only in this feature).
- Back: exit the app.
- Incoming-link notice: a non-blocking toast-like card at the bottom-right, "Link received from
  <sender> — press Select to open"; it does not steal focus — pressing **Menu**/long Select opens
  it, and it disappears after 8 s (the link stays in Inbox).

## Add app

```
┌──────────────────────────────────────────────────────────────┐
│  Add an app                                                  │
│                                                              │
│   ┌──────────┐   1. Scan with your phone camera              │
│   │  QR CODE │   2. Paste a GitHub link and tap Send         │
│   │          │                                               │
│   └──────────┘   Or open  http://192.168.1.42:8765           │
│                  and enter code  482 913                     │
│                                                              │
│   Phone and TV must be on the same Wi-Fi.                    │
│                                                              │
│   [ Type it instead ]                                        │
└──────────────────────────────────────────────────────────────┘
```

- Initial focus: **Type it instead** (the only interactive element; QR is not focusable).
- QR refresh: on session rotation, QR and code change in place without moving focus.
- No network: QR area replaced by "Connect the TV to Wi-Fi or Ethernet" with **Retry** focused.
- Incoming link while open: navigates straight to **Preview**.
- Back: Home; session invalidated.

## Type it

- Text field (initial focus, opens the system keyboard) with hint `owner/repo or GitHub link`,
  and **Continue**.
- Invalid input: inline error under the field; focus stays in the field.
- Back: Add app.

## Preview (confirmation, FR-005/FR-006)

- Shows avatar, display name, `owner/repo`, description, latest version or "No releases found",
  and an "Already added" badge if tracked.
- Buttons: **Add** (initial focus) | **Cancel**. When already tracked: **Open** (initial focus) |
  **Cancel**.
- Loading state: skeleton, **Cancel** focused; error state: message + **Retry** (focused) |
  **Cancel**.
- Add → confirmation "Added <name>" and return to the screen the preview came from (Add app or
  Inbox).
- Back: same as Cancel; link stays PENDING if it came from Inbox.

## Inbox

- Vertical list of PENDING links, newest first: name/`owner/repo`, sender, relative time,
  "Already added" badge. Initial focus: first item. Select → Preview. Menu/long-press → Dismiss.
- Empty: "No links yet. Send one from your phone." with **Add app** focused.
- Back: Home.

## Settings → Paired phones

- List: name, kind, last used. Select → dialog "Remove <name>?" with **Cancel** focused,
  **Remove**.
- Toggle **Receive links in background** (default on).
- Tip text: "Reserve this TV's IP address on your router so saved phone pages keep working."
- Back: Home.

All focusable elements: visible focus (scale 1.1 + border), TalkBack labels, overscan-safe
margins (48 dp horizontal, 27 dp vertical).
