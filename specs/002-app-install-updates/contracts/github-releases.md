# Contract: GitHub releases API usage

The client side of the GitHub REST API used by this feature. Tests run against `MockEngine` with
recorded JSON fixtures, never live (Constitution II).

## List releases

```
GET https://api.github.com/repos/{owner}/{repo}/releases?per_page=10
Accept: application/vnd.github.html+json
X-GitHub-Api-Version: 2022-11-28
If-None-Match: <stored etag>          # when one is stored
```

Fields read from each release: `tag_name`, `name`, `draft`, `prerelease`, `published_at`,
`body_html`, and `assets[]`. From each asset: `id`, `name`, `size`, `content_type`,
`browser_download_url`, and `digest` (`sha256:<hex>`; optional). Unknown fields are ignored.

| Response | Meaning | Result |
|----------|---------|--------|
| 200 | releases list (may be empty) | latest eligible release, or "no releases"; store `ETag` |
| 304 | unchanged since the stored ETag | keep stored release; update `checkedAt` |
| 404 | repository gone or private | `SourceGone`; set `sourceGone`; installed app untouched |
| 403/429 with `X-RateLimit-Remaining: 0` | request limit reached | `RateLimited(resetAt = X-RateLimit-Reset)` |
| 403/429 without it (secondary limit) | `Retry-After` seconds, else 60 s | `RateLimited(now + retryAfter)` |
| other 4xx/5xx, IO error, bad JSON | | `NetworkError`; stored data kept |

Eligible means: `draft == false`, and `prerelease == false` unless the app opted in. The first
eligible item in the response's order is the latest.

## Download an asset

```
GET {browser_download_url}            # follows redirects to GitHub's file CDN
Accept: application/octet-stream
```

This is not an API request, so it doesn't use or report the API limit. A redirect target is
followed only when its host is `github.com`, `objects.githubusercontent.com` or
`release-assets.githubusercontent.com`. Any other host fails the download as `Corrupt`, so that
nothing unexpected gets installed. The body streams to disk (R6).

## Read the manifest of an asset (R4)

```
GET {resolved download url}
Range: bytes=-65557                   # end of central directory + comment, at most
Range: bytes=<cd offset>-<cd end>     # central directory
Range: bytes=<entry offset>-<entry end + 1 KB>   # AndroidManifest.xml local header + data
```

- A `206` is required for every step. A `200` stops the read after 64 KB, and identity stays
  unknown.
- At most 3 requests per app, and only for apps with no known package name. These don't count
  against the API limit either.

## Privacy and logging

Requests carry no token and no user data. Logs (`AppLog`) record only event names, HTTP status
codes, counts and enum reasons, never owner/repo names, URLs or file names.
