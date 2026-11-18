# Data Model: Send App Links from Phone to TV

**Feature**: `001-phone-link-sharing` | **Date**: 2026-11-18

Persistent entities live in a Room database on the TV (`:core:data`). `SendSession` is in-memory
only. Field names in Room are the persisted contract: never rename without a migration.

## SendSession (in-memory)

The short-lived invitation behind the QR code.

| Field | Type | Notes |
|-------|------|-------|
| token | String | 128-bit random, URL-safe Base64, in QR path |
| pin | String | 6 digits, for typed-address entry |
| createdAt | Instant | |
| expiresAt | Instant | `createdAt + 10 min` |
| failedPinAttempts | Int | ≥ 5 → session rotated |

Rules: at most one active session; rotated on expiry, on 5 failed PIN attempts, and invalidated
when the Add-app screen leaves composition (FR-004).

States: `Active → Expired | Invalidated` (terminal; a new session replaces it).

## PairedPhone (`paired_phone`)

A phone allowed to send without rescanning (FR-010, FR-012).

| Field | Type | Notes |
|-------|------|-------|
| id | String (UUID) | PK |
| displayName | String | From User-Agent ("Safari on iPhone") or companion device name; ≤ 60 chars |
| kind | Enum `BROWSER`, `COMPANION` | |
| tokenHash | String | SHA-256 hex of device token; unique |
| pairedAt | Instant | |
| lastUsedAt | Instant | Updated on each accepted send |

Rules: deleting a row revokes the phone; requests with an unknown token → `403 revoked`.

## IncomingLink (`incoming_link`)

A link received from a phone (FR-011, FR-013).

| Field | Type | Notes |
|-------|------|-------|
| id | Long | PK, autoincrement |
| rawText | String | As sent, ≤ 4 000 chars |
| owner | String? | Parsed GitHub owner, null if unsupported |
| repo | String? | Parsed GitHub repo |
| normalizedUrl | String? | `https://github.com/<owner>/<repo>` |
| senderPhoneId | String? | FK → `paired_phone.id`, `SET NULL` on delete; null for one-off session sends |
| senderLabel | String | Snapshot of sender name for display |
| receivedAt | Instant | |
| status | Enum | see below |

Status transitions:

```
             ┌──────────► DISMISSED
PENDING ─────┤
             └──────────► ADDED
UNSUPPORTED (terminal at creation; not stored in inbox, only reported back to phone)
```

`DUPLICATE` is not a stored status: it is derived at read time when `(owner, repo)` matches a
TrackedApp, so it stays correct if the tracked app is later removed.

Rules:
- Unsupported links are rejected at the HTTP layer (422) and not persisted.
- Inbox shows `PENDING` items, newest first. `ADDED`/`DISMISSED` rows are purged after 30 days.
- Two links with the same `normalizedUrl` while `PENDING` collapse into one (latest
  `receivedAt`).

## TrackedApp (`tracked_app`)

An app the user added. This feature only creates rows; other features extend it.

| Field | Type | Notes |
|-------|------|-------|
| id | Long | PK |
| owner | String | |
| repo | String | |
| sourceUrl | String | Normalized URL |
| displayName | String | Repo name, or `name` from GitHub |
| latestVersion | String? | Tag of newest release at add time, null if none |
| avatarUrl | String? | Owner avatar |
| addedAt | Instant | |

Rules: unique index on `(lower(owner), lower(repo))` — adding an existing app is a no-op that
returns the existing row (FR-013).

## AppPreview (transient, not stored)

What the confirmation screen shows (FR-006): `owner`, `repo`, `displayName`, `description?`,
`latestVersion?`, `avatarUrl?`, `isAlreadyTracked`. Built from the GitHub API (research R6).

## Settings (DataStore `link_share_settings`)

| Key | Type | Default |
|-----|------|---------|
| receive_in_background | Boolean | true |
| preferred_port | Int | 8765 |

## Relationships

```
PairedPhone 1 ──── 0..* IncomingLink   (senderPhoneId, nullable)
IncomingLink ..... TrackedApp          (logical match on owner+repo, no FK)
```
