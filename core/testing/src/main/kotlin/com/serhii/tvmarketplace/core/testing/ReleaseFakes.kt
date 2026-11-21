package com.serhii.tvmarketplace.core.testing

import com.serhii.tvmarketplace.core.data.InstallRecord
import com.serhii.tvmarketplace.core.data.ReleaseResult
import com.serhii.tvmarketplace.core.data.ReleaseSource
import com.serhii.tvmarketplace.core.data.ReleasesRepository
import com.serhii.tvmarketplace.core.data.StoredRelease
import com.serhii.tvmarketplace.core.domain.AppPackage
import com.serhii.tvmarketplace.core.domain.AppSource
import com.serhii.tvmarketplace.core.domain.Release
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant

fun pkg(
  fileName: String,
  id: Long = fileName.hashCode().toLong(),
  sizeBytes: Long = 10_000_000,
  sha256: String? = null,
) = AppPackage(
  id = id,
  fileName = fileName,
  sizeBytes = sizeBytes,
  downloadUrl = "https://github.com/o/r/releases/download/x/$fileName",
  sha256 = sha256,
)

fun release(
  tag: String = "v1.0.0",
  vararg packages: AppPackage = arrayOf(pkg("app-universal-release.apk")),
  publishedAt: Instant = Instant.parse("2026-11-10T10:00:00Z"),
  prerelease: Boolean = false,
  notesHtml: String? = "<p>Notes for $tag</p>",
) = Release(tag = tag, name = null, publishedAt = publishedAt, prerelease = prerelease, notesHtml = notesHtml, packages = packages.toList())

// stored state that later reads observe, like the Room repository
class FakeReleasesRepository : ReleasesRepository {
  val releases = MutableStateFlow<Map<Long, StoredRelease>>(emptyMap())
  val records = MutableStateFlow<Map<Long, InstallRecord>>(emptyMap())

  override fun latestReleases(): Flow<Map<Long, StoredRelease>> = releases

  override suspend fun latest(appId: Long): StoredRelease? = releases.value[appId]

  override suspend fun store(
    appId: Long,
    release: Release?,
    etag: String?,
    checkedAt: Instant,
  ) {
    releases.value = if (release == null) releases.value - appId else releases.value + (appId to StoredRelease(release, etag, checkedAt))
  }

  override suspend fun touch(
    appId: Long,
    checkedAt: Instant,
  ) {
    val stored = releases.value[appId] ?: return
    releases.value += appId to stored.copy(checkedAt = checkedAt)
  }

  override suspend fun forgetEtag(appId: Long) {
    val stored = releases.value[appId] ?: return
    releases.value += appId to stored.copy(etag = null)
  }

  override fun installRecords(): Flow<Map<Long, InstallRecord>> = records

  override suspend fun recordInstall(record: InstallRecord) {
    records.value += record.appId to record
  }
}

class FakeReleaseSource(
  var answer: (AppSource, Boolean) -> ReleaseResult = { _, _ ->
    ReleaseResult.Found(release(), etag = null)
  },
) : ReleaseSource {
  val requests = mutableListOf<Pair<AppSource, String?>>()

  override suspend fun latest(
    source: AppSource,
    includePrereleases: Boolean,
    etag: String?,
  ): ReleaseResult {
    requests += source to etag
    return answer(source, includePrereleases)
  }
}
