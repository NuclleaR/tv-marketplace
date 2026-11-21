package com.serhii.tvmarketplace.core.data

import com.serhii.tvmarketplace.core.domain.AppSource
import com.serhii.tvmarketplace.core.domain.Release
import kotlinx.coroutines.flow.Flow
import java.time.Instant

sealed interface ReleaseResult {
  // release is null when the source has no eligible release; etag lets the next check ask "changed since?"
  data class Found(
    val release: Release?,
    val etag: String?,
  ) : ReleaseResult

  // nothing changed since the etag that was sent
  data object NotModified : ReleaseResult

  // the repository was deleted, renamed away or made private
  data object SourceGone : ReleaseResult

  data class RateLimited(val resumeAt: Instant) : ReleaseResult

  data object NetworkError : ReleaseResult
}

// one per AppSource kind
interface ReleaseSource {
  // the newest release that isn't a draft and, unless includePrereleases, not a pre-release
  suspend fun latest(
    source: AppSource,
    includePrereleases: Boolean,
    etag: String?,
  ): ReleaseResult
}

data class StoredRelease(
  val release: Release,
  val etag: String?,
  val checkedAt: Instant,
)

// what this app installed for a tracked app
data class InstallRecord(
  val appId: Long,
  val packageName: String,
  val tag: String,
  val versionCode: Long,
  val packageFileName: String,
  val installedAt: Instant,
)

interface ReleasesRepository {
  // by tracked app id
  fun latestReleases(): Flow<Map<Long, StoredRelease>>

  suspend fun latest(appId: Long): StoredRelease?

  // release null: the source has no eligible release any more
  suspend fun store(
    appId: Long,
    release: Release?,
    etag: String?,
    checkedAt: Instant,
  )

  // the source answered "not modified"
  suspend fun touch(
    appId: Long,
    checkedAt: Instant,
  )

  // forgets the etag, so the next check fetches again; needed when what counts as eligible changes
  suspend fun forgetEtag(appId: Long)

  fun installRecords(): Flow<Map<Long, InstallRecord>>

  suspend fun recordInstall(record: InstallRecord)
}
