package com.serhii.tvmarketplace.core.data.impl

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.serhii.tvmarketplace.core.common.TimeSource
import com.serhii.tvmarketplace.core.data.PairedPhoneKind
import com.serhii.tvmarketplace.core.data.impl.db.AppDatabase
import com.serhii.tvmarketplace.core.domain.GitHubRepoRef
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class PairedPhonesAndSettingsTest {

  @get:Rule
  val folder = TemporaryFolder()

  private val database = Room.inMemoryDatabaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext())
    .allowMainThreadQueries()
    .build()
  private var now = Instant.parse("2026-11-18T12:00:00Z")
  private val time = TimeSource { now }
  private val phones = RoomPairedPhonesRepository(database.pairedPhoneDao(), database.incomingLinkDao(), time)
  private val incomingLinks = RoomIncomingLinksRepository(database.incomingLinkDao(), time)

  @After
  fun close() {
    database.close()
  }

  @Test
  fun `a paired phone is found by its token hash, which records its use`() = runTest {
    val phone = phones.add("Safari on iPhone", PairedPhoneKind.BROWSER, tokenHash = "hash")
    now += Duration.ofHours(1)
    assertEquals(now, phones.useByTokenHash("hash")?.lastUsedAt)
    assertEquals(listOf(phone.id), phones.phones().first().map { it.id })
    assertNull(phones.useByTokenHash("other"))
  }

  @Test
  fun `display names are capped at 60 characters`() = runTest {
    assertEquals(60, phones.add("x".repeat(80), PairedPhoneKind.COMPANION, "hash").displayName.length)
  }

  @Test
  fun `removing a phone revokes it and detaches its links`() = runTest {
    val phone = phones.add("Phone", PairedPhoneKind.BROWSER, "hash")
    val link = incomingLinks.receive("text", GitHubRepoRef("a", "b"), senderPhoneId = phone.id, senderLabel = "Phone")
    phones.remove(phone.id)
    assertNull(phones.useByTokenHash("hash"))
    assertNull(incomingLinks.get(link.id)?.senderPhoneId)
  }

  @Test
  fun `handled links older than the cutoff are purged`() = runTest {
    val added = incomingLinks.receive("a", GitHubRepoRef("a", "a"), null, "Phone")
    val pending = incomingLinks.receive("b", GitHubRepoRef("b", "b"), null, "Phone")
    incomingLinks.markAdded(added.id)
    incomingLinks.purgeHandledBefore(now + Duration.ofDays(1))
    assertNull(incomingLinks.get(added.id))
    assertEquals(pending.id, incomingLinks.get(pending.id)?.id)
  }

  @Test
  fun `receiving links in the background is on by default`() = runTest {
    val settings = DataStoreLinkShareSettings(
      PreferenceDataStoreFactory.create(scope = TestScope(testScheduler).backgroundScope) { folder.newFile("settings.preferences_pb") },
    )
    assertEquals(true, settings.receiveInBackground.first())
    settings.setReceiveInBackground(false)
    assertEquals(false, settings.receiveInBackground.first())
  }

  @Test
  fun `the install id is created once and then stays`() = runTest {
    val settings = DataStoreLinkShareSettings(
      PreferenceDataStoreFactory.create(scope = TestScope(testScheduler).backgroundScope) { folder.newFile("id.preferences_pb") },
    )
    val id = settings.installId()
    assertEquals(36, id.length)
    assertEquals(id, settings.installId())
  }
}
