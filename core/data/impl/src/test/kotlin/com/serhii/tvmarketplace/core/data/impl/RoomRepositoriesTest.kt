package com.serhii.tvmarketplace.core.data.impl

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.serhii.tvmarketplace.core.common.TimeSource
import com.serhii.tvmarketplace.core.data.IncomingLinkStatus
import com.serhii.tvmarketplace.core.data.impl.db.AppDatabase
import com.serhii.tvmarketplace.core.domain.AppPreview
import com.serhii.tvmarketplace.core.domain.GitHubRepoRef
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class RoomRepositoriesTest {

  private val database = Room.inMemoryDatabaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext())
    .allowMainThreadQueries()
    .build()
  private var now = Instant.parse("2026-11-18T12:00:00Z")
  private val time = TimeSource { now }
  private val trackedApps = RoomTrackedAppsRepository(database.trackedAppDao(), time)
  private val incomingLinks = RoomIncomingLinksRepository(database.incomingLinkDao(), time)
  private val ref = GitHubRepoRef(owner = "ImranR98", repo = "Obtainium")
  private val preview = AppPreview(ref, displayName = "Obtainium", description = null, latestVersion = "v1", avatarUrl = null)

  @After
  fun close() {
    database.close()
  }

  @Test
  fun `adding a tracked app twice, in any case, keeps one row`() = runTest {
    val first = trackedApps.add(preview)
    val second = trackedApps.add(preview.copy(ref = GitHubRepoRef(owner = "imranr98", repo = "OBTAINIUM")))
    assertEquals(first, second)
    assertEquals(listOf(first), trackedApps.trackedApps().first())
    assertEquals(first, trackedApps.find(GitHubRepoRef("IMRANR98", "obtainium")))
  }

  @Test
  fun `a tracked app keeps its icon and avatar`() = runTest {
    val added = trackedApps.add(preview.copy(avatarUrl = "https://avatars/1", iconUrl = "https://raw/icon.png"))
    assertEquals("https://raw/icon.png", added.iconUrl)
    assertEquals(
      listOf("https://raw/icon.png" to "https://avatars/1"),
      trackedApps.trackedApps().first().map {
        it.iconUrl to it.avatarUrl
      },
    )
  }

  @Test
  fun `a pending link to the same repository is refreshed, not duplicated`() = runTest {
    val first = incomingLinks.receive("see https://github.com/ImranR98/Obtainium", ref, senderPhoneId = null, senderLabel = "Phone")
    now += Duration.ofMinutes(1)
    val second = incomingLinks.receive("https://github.com/imranr98/obtainium/releases", ref, senderPhoneId = null, senderLabel = "Phone")
    assertEquals(first.id, second.id)
    assertEquals(listOf(now), incomingLinks.pending().first().map { it.receivedAt })
  }

  @Test
  fun `pending links are newest first and leave once added or dismissed`() = runTest {
    val older = incomingLinks.receive("a", GitHubRepoRef("a", "a"), null, "Phone")
    now += Duration.ofMinutes(1)
    val newer = incomingLinks.receive("b", GitHubRepoRef("b", "b"), null, "Phone")
    assertEquals(listOf(newer.id, older.id), incomingLinks.pending().first().map { it.id })
    incomingLinks.markAdded(older.id)
    incomingLinks.markDismissed(newer.id)
    assertEquals(emptyList(), incomingLinks.pending().first())
    assertEquals(IncomingLinkStatus.ADDED, incomingLinks.get(older.id)?.status)
  }

  @Test
  fun `raw text is capped at 4000 characters`() = runTest {
    val link = incomingLinks.receive("x".repeat(5000), ref, null, "Phone")
    assertEquals(4000, link.rawText.length)
  }
}
