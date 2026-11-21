package com.serhii.tvmarketplace.core.data.impl

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.serhii.tvmarketplace.core.common.TimeSource
import com.serhii.tvmarketplace.core.data.InstallRecord
import com.serhii.tvmarketplace.core.data.StoredRelease
import com.serhii.tvmarketplace.core.data.impl.db.AppDatabase
import com.serhii.tvmarketplace.core.domain.AppPackage
import com.serhii.tvmarketplace.core.domain.AppPreview
import com.serhii.tvmarketplace.core.domain.GitHubRepoRef
import com.serhii.tvmarketplace.core.domain.Release
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.runner.RunWith
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class RoomReleasesRepositoryTest {

  private val database = Room.inMemoryDatabaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext())
    .allowMainThreadQueries()
    .build()
  private val now = Instant.parse("2026-11-20T12:00:00Z")
  private val trackedApps = RoomTrackedAppsRepository(database.trackedAppDao(), TimeSource { now })
  private val releases = RoomReleasesRepository(database.appReleaseDao(), database.installRecordDao())
  private val preview = AppPreview(GitHubRepoRef("yuliskov", "SmartTube"), "SmartTube", null, "v1", null)
  private val release = Release(
    tag = "v1.3.0",
    name = "1.3.0",
    publishedAt = Instant.parse("2026-11-10T10:00:00Z"),
    prerelease = false,
    notesHtml = "<p>notes</p>",
    packages = listOf(AppPackage(21, "SmartTube_1.3.0_arm64-v8a.apk", 19_000_000, "https://github.com/a.apk", sha256 = "ab12")),
  )

  @After
  fun close() {
    database.close()
  }

  @Test
  fun `stores the latest release with its packages and etag, and replaces it`() = runTest {
    val app = trackedApps.add(preview)
    releases.store(app.id, release, "\"e1\"", now)
    assertEquals(StoredRelease(release, "\"e1\"", now), releases.latest(app.id))
    val newer = release.copy(tag = "v1.4.0", packages = emptyList())
    releases.store(app.id, newer, "\"e2\"", now.plusSeconds(60))
    assertEquals(mapOf(app.id to StoredRelease(newer, "\"e2\"", now.plusSeconds(60))), releases.latestReleases().first())
  }

  @Test
  fun `not modified keeps the release and moves the check time, a forgotten etag is gone`() = runTest {
    val app = trackedApps.add(preview)
    releases.store(app.id, release, "\"e1\"", now)
    releases.touch(app.id, now.plusSeconds(3600))
    assertEquals(now.plusSeconds(3600), releases.latest(app.id)?.checkedAt)
    releases.forgetEtag(app.id)
    assertNull(releases.latest(app.id)?.etag)
  }

  @Test
  fun `no eligible release any more removes the stored one`() = runTest {
    val app = trackedApps.add(preview)
    releases.store(app.id, release, null, now)
    releases.store(app.id, null, null, now)
    assertNull(releases.latest(app.id))
  }

  @Test
  fun `install records are kept per app`() = runTest {
    val app = trackedApps.add(preview)
    val record = InstallRecord(app.id, "com.liskovsoft.smarttubetv", "v1.3.0", 130, "SmartTube_1.3.0_arm64-v8a.apk", now)
    releases.recordInstall(record)
    releases.recordInstall(record.copy(tag = "v1.4.0", versionCode = 140))
    assertEquals(mapOf(app.id to record.copy(tag = "v1.4.0", versionCode = 140)), releases.installRecords().first())
  }

  @Test
  fun `removing a tracked app removes its release and install record`() = runTest {
    val app = trackedApps.add(preview)
    releases.store(app.id, release, null, now)
    releases.recordInstall(InstallRecord(app.id, "a.b", "v1", 1, "a.apk", now))
    trackedApps.remove(app.id)
    assertEquals(emptyMap(), releases.latestReleases().first())
    assertEquals(emptyMap(), releases.installRecords().first())
    assertNull(trackedApps.get(app.id))
  }

  @Test
  fun `a known package name is never replaced, settings are stored`() = runTest {
    val app = trackedApps.add(preview)
    trackedApps.setPackageName(app.id, "com.liskovsoft.smarttubetv")
    trackedApps.setPackageName(app.id, "com.other")
    trackedApps.setIncludePrereleases(app.id, true)
    trackedApps.setPackagePattern(app.id, "SmartTube_{v}_arm64-v8a.apk")
    trackedApps.setSourceGone(app.id, true)
    val stored = checkNotNull(trackedApps.get(app.id))
    assertEquals("com.liskovsoft.smarttubetv", stored.packageName)
    assertEquals(true, stored.includePrereleases)
    assertEquals("SmartTube_{v}_arm64-v8a.apk", stored.packagePattern)
    assertEquals(true, stored.sourceGone)
    assertEquals(stored, trackedApps.observe(app.id).first())
  }
}
