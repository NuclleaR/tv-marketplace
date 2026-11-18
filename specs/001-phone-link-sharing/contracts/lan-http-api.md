# Contract: LAN HTTP API (TV ⇄ phone)

**Version**: v1 | Served by the TV app on `http://<tv-lan-ip>:<port>` (research R1–R4).

All JSON is UTF-8. Request bodies ≤ 8 KiB. Any request whose `Host` header does not equal the
bound `ip:port` → `421 Misdirected Request`. No CORS headers are ever sent. Rate limit: 30
requests/min per client IP → `429`.

## Authentication

| Credential | How sent | Obtained from |
|-----------|----------|---------------|
| Session token | `sessionToken` field in JSON body | QR path `/s/{token}` or `POST /api/v1/sessions/pin` |
| Device token | `Authorization: Bearer <token>` | `pair: true` on a send, or `POST /api/v1/pair` |

## Pages (HTML)

| Method & path | Behavior |
|---------------|----------|
| `GET /` | Send page. If the page has a stored device token, it is used; otherwise a 6-digit PIN field is shown. |
| `GET /s/{sessionToken}` | Send page with the session token embedded. Expired token → page shows "Code expired, scan the new one on your TV". |
| `GET /send?u={url}` | Same as `/`, link field prefilled from `u` (iOS Shortcut, research R9). Never auto-sends. |
| `GET /assets/*` | Static CSS/JS for the page. |

Page language follows `Accept-Language` (`uk` or default `en`).

## API

### `POST /api/v1/sessions/pin`

Exchange the PIN typed on the phone for the current session token.

Request: `{ "pin": "482913" }`

| Status | Body |
|--------|------|
| 200 | `{ "sessionToken": "…", "expiresAt": "2026-11-18T12:10:00Z" }` |
| 401 | `{ "error": "invalid_pin", "attemptsLeft": 3 }` |
| 410 | `{ "error": "no_active_session" }` — Add-app screen not open on TV |

### `POST /api/v1/links`

Send a link. Authenticated by session token (body) **or** device token (header).

Request:

```json
{
  "text": "check this https://github.com/owner/repo/releases",
  "sessionToken": "…",
  "pair": true,
  "deviceName": "optional, companion only"
}
```

| Status | Body | Meaning |
|--------|------|---------|
| 202 | `{ "linkId": 17, "normalizedUrl": "https://github.com/owner/repo", "duplicate": false, "deviceToken": "…" }` | Stored as PENDING. `deviceToken` only when `pair: true` and authenticated by session. |
| 401 | `{ "error": "session_expired" }` | Rescan the QR. |
| 403 | `{ "error": "device_revoked" }` | Paired phone was removed on TV. |
| 422 | `{ "error": "unsupported_link", "examples": ["https://github.com/owner/repo", "https://github.com/owner/repo/releases"] }` | No GitHub link found in `text` (FR-008). |
| 400 | `{ "error": "bad_request" }` | Malformed JSON / missing fields. |

Effects on TV: a `202` creates or refreshes a PENDING `IncomingLink` and, if the app is in the
foreground, shows the incoming-link notice (or opens the preview directly when the Add-app
screen is open).

### `POST /api/v1/pair` (companion app)

Request: `{ "pin": "482913", "deviceName": "Pixel 9" }`

| Status | Body |
|--------|------|
| 200 | `{ "deviceToken": "…", "tvName": "Living room TV" }` |
| 401 | `{ "error": "invalid_pin", "attemptsLeft": 3 }` |
| 410 | `{ "error": "no_active_session" }` |

### `GET /api/v1/ping`

Optional `Authorization: Bearer <deviceToken>`. Used by pages and the companion to check
reachability and pairing state.

| Status | Body |
|--------|------|
| 200 | `{ "tvName": "Living room TV", "apiVersion": 1, "paired": true }` |
| 403 | `{ "error": "device_revoked" }` |

## Client-side behavior (phone page)

- Fetch timeout 5 s → show "Can't reach your TV. Is your phone on the same Wi-Fi?" (FR-017).
- After a `202` with `deviceToken`, store it in `localStorage` and show "Saved — add this page
  to your home screen".
- After `403 device_revoked`, delete the stored token and ask to scan again.
- The link field has a large "Paste" button; clipboard read is not available on plain HTTP, so
  it focuses the field and prompts the system paste action.
