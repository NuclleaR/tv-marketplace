# Security Review: LAN link server (T077)

**Scope**: `core/linkserver` against research.md R4 and contracts/lan-http-api.md
**Date**: 2026-11-18
**Method**: code review plus the automated route tests named below.

## Checks

- [x] **DNS-rebinding guard**: a request whose `Host` is not the bound `ip:port` gets 421
  (`LinkServerModule.kt` intercept, `LinkServerRoutesTest` "rejects a foreign host header"). A missing `Host` is
  rejected too.
- [x] **Bound interface**: the server binds to the Wi-Fi/Ethernet IPv4 only, not `0.0.0.0` (`LinkServer.bind`).
- [x] **Rate limit**: 30 requests a minute per client address, then 429 (`RateLimiter`, test "limits a client").
- [x] **Body limit**: 8 KiB, checked by `Content-Length` and by reading at most 8 KiB + 1 (test "rejects bodies over
  8 KiB").
- [x] **No CORS**: no `Access-Control-*` headers are sent, so other websites can't read responses (test "never sends
  CORS headers"). JSON bodies are needed for every write.
- [x] **Session token entropy**: 128-bit `SecureRandom`, URL-safe Base64 (`SessionManagerTest`).
- [x] **PIN lockout**: 5 wrong PINs rotate the session (`SessionManagerTest` "five failed pins rotate the session").
- [x] **Constant-time comparison** for the token and the PIN (`constantTimeEquals`).
- [x] **Session lifetime**: 10 minutes, and ended when the Add app screen closes (`AddAppViewModelTest`).
- [x] **Device tokens**: 256-bit, only the SHA-256 is stored, removal revokes at once (`PairingManagerTest`,
  `PairedRoutesTest`).
- [x] **Pairing needs proof of presence**: a device token is only issued with a valid session token or PIN, never
  to an already-paired or anonymous caller (`PairedRoutesTest`, `CompanionRoutesTest`).
- [x] **No auto-add**: every received link waits for a Select on the TV (FR-005); `/send?u=` only prefills
  (`CompanionRoutesTest` "never sends by itself").
- [x] **Logs**: `AppLog` only takes names, numbers and enums: no links, IPs, tokens, device names or exception
  messages reach logcat.
- [x] **Page output**: received text is never echoed into HTML; the page sets text with `textContent` only.

## Accepted risks

- **Plain HTTP**: a device on the same Wi-Fi that can sniff traffic can see a session token or a device token
  and then put links into the inbox. A link still needs a Select on the TV, so the impact is inbox spam, not
  installs. Accepted for v1 (research.md R1, R4).
- **Rate limit by address**: a device that changes addresses can get around it. Each request still needs a valid
  token.
