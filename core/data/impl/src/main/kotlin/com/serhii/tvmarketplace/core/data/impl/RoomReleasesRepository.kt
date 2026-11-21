package com.serhii.tvmarketplace.core.data.impl

import com.serhii.tvmarketplace.core.data.InstallRecord
import com.serhii.tvmarketplace.core.data.ReleasesRepository
import com.serhii.tvmarketplace.core.data.StoredRelease
import com.serhii.tvmarketplace.core.data.impl.db.AppReleaseDao
import com.serhii.tvmarketplace.core.data.impl.db.AppReleaseEntity
import com.serhii.tvmarketplace.core.data.impl.db.InstallRecordDao
import com.serhii.tvmarketplace.core.data.impl.db.InstallRecordEntity
import com.serhii.tvmarketplace.core.domain.AppPackage
import com.serhii.tvmarketplace.core.domain.Release
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.Instant

private val storedJson = Json { ignoreUnknownKeys = true }

@ContributesBinding(AppScope::class)
@Inject
class RoomReleasesRepository(
  private val releaseDao: AppReleaseDao,
  private val installRecordDao: InstallRecordDao,
) : ReleasesRepository {

  override fun latestReleases(): Flow<Map<Long, StoredRelease>> =
    releaseDao.all().map { rows -> rows.associate { it.appId to it.toModel() } }

  override suspend fun latest(appId: Long): StoredRelease? = releaseDao.get(appId)?.toModel()

  override suspend fun store(
    appId: Long,
    release: Release?,
    etag: String?,
    checkedAt: Instant,
  ) {
    if (release == null) {
      releaseDao.delete(appId)
      return
    }
    releaseDao.upsert(
      AppReleaseEntity(
        appId = appId,
        tag = release.tag,
        name = release.name,
        publishedAt = release.publishedAt,
        prerelease = release.prerelease,
        notesHtml = release.notesHtml,
        packagesJson = storedJson.encodeToString(ListSerializer(PackageJson.serializer()), release.packages.map { PackageJson.of(it) }),
        etag = etag,
        checkedAt = checkedAt,
      ),
    )
  }

  override suspend fun touch(
    appId: Long,
    checkedAt: Instant,
  ) = releaseDao.touch(appId, checkedAt)

  override suspend fun forgetEtag(appId: Long) {
    val stored = releaseDao.get(appId) ?: return
    releaseDao.upsert(stored.copy(etag = null))
  }

  override fun installRecords(): Flow<Map<Long, InstallRecord>> =
    installRecordDao.all().map { rows -> rows.associate { it.appId to it.toModel() } }

  override suspend fun recordInstall(record: InstallRecord) = installRecordDao.upsert(
    InstallRecordEntity(
      appId = record.appId,
      packageName = record.packageName,
      tag = record.tag,
      versionCode = record.versionCode,
      packageFileName = record.packageFileName,
      installedAt = record.installedAt,
    ),
  )

  private fun AppReleaseEntity.toModel() = StoredRelease(
    release = Release(
      tag = tag,
      name = name,
      publishedAt = publishedAt,
      prerelease = prerelease,
      notesHtml = notesHtml,
      packages = storedJson.decodeFromString(ListSerializer(PackageJson.serializer()), packagesJson).map { it.toModel() },
    ),
    etag = etag,
    checkedAt = checkedAt,
  )

  private fun InstallRecordEntity.toModel() = InstallRecord(appId, packageName, tag, versionCode, packageFileName, installedAt)
}

// app_release.packagesJson: stored user data, so field names never change
@Serializable
private data class PackageJson(
  val id: Long,
  val fileName: String,
  val sizeBytes: Long,
  val downloadUrl: String,
  val sha256: String? = null,
) {
  fun toModel() = AppPackage(id, fileName, sizeBytes, downloadUrl, sha256)

  companion object {
    fun of(pkg: AppPackage) = PackageJson(pkg.id, pkg.fileName, pkg.sizeBytes, pkg.downloadUrl, pkg.sha256)
  }
}
