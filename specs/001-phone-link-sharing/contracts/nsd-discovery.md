# Contract: LAN discovery (companion app)

The TV advertises itself with Android NSD (DNS-SD/mDNS) while the HTTP server is running.

| Property | Value |
|----------|-------|
| Service type | `_tvmarket._tcp.` |
| Service name | TV device name, e.g. `Living room TV` (NSD may append ` (2)`) |
| Port | The server's actual port |
| TXT `v` | `1` (API version, see `lan-http-api.md`) |
| TXT `id` | Stable random install id of the TV app (UUID), so the companion recognizes the same TV after an IP change |

Companion behavior:

1. Browse `_tvmarket._tcp.` for up to 10 s; list results by service name.
2. Pair: user picks a TV, opens "Add app" on TV, enters the PIN → `POST /api/v1/pair`.
3. Store `{ id, deviceToken }`. On every send, resolve the service with matching TXT `id` to get
   the current address, then `POST /api/v1/links` with the bearer token.
4. If no TV with that `id` is found in 10 s → "TV not found. Is the TV app open and on the same
   Wi-Fi?"
