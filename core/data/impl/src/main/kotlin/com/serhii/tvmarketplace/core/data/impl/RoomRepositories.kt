package com.serhii.tvmarketplace.core.data.impl

import com.serhii.tvmarketplace.core.common.TimeSource
import com.serhii.tvmarketplace.core.data.IncomingLink
import com.serhii.tvmarketplace.core.data.IncomingLinkStatus
import com.serhii.tvmarketplace.core.data.IncomingLinksRepository
import com.serhii.tvmarketplace.core.data.TrackedApp
import com.serhii.tvmarketplace.core.data.TrackedAppsRepository
import com.serhii.tvmarketplace.core.data.impl.db.IncomingLinkDao
import com.serhii.tvmarketplace.core.data.impl.db.IncomingLinkEntity
import com.serhii.tvmarketplace.core.data.impl.db.TrackedAppDao
import com.serhii.tvmarketplace.core.data.impl.db.TrackedAppEntity
import com.serhii.tvmarketplace.core.domain.AppPreview
import com.serhii.tvmarketplace.core.domain.GitHubRepoRef
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

internal val GitHubRepoRef.key: String get() = "${owner.lowercase()}/${repo.lowercase()}"

private const val MAX_RAW_TEXT = 4000

@ContributesBinding(AppScope::class)
@Inject
class RoomTrackedAppsRepository(
  private val dao: TrackedAppDao,
  private val timeSource: TimeSource,
) : TrackedAppsRepository {

  override fun trackedApps(): Flow<List<TrackedApp>> = dao.all().map { rows -> rows.map { it.toModel() } }

  override suspend fun find(ref: GitHubRepoRef): TrackedApp? = dao.byKey(ref.key)?.toModel()

  override suspend fun add(preview: AppPreview): TrackedApp {
    dao.byKey(preview.ref.key)?.let { return it.toModel() }
    val entity = TrackedAppEntity(
      owner = preview.ref.owner,
      repo = preview.ref.repo,
      repoKey = preview.ref.key,
      sourceUrl = preview.ref.url,
      displayName = preview.displayName,
      latestVersion = preview.latestVersion,
      avatarUrl = preview.avatarUrl,
      addedAt = timeSource.now(),
    )
    return entity.copy(id = dao.upsert(entity)).toModel()
  }

  private fun TrackedAppEntity.toModel() = TrackedApp(
    id = id,
    ref = GitHubRepoRef(owner = owner, repo = repo),
    displayName = displayName,
    latestVersion = latestVersion,
    avatarUrl = avatarUrl,
    addedAt = addedAt,
  )
}

@ContributesBinding(AppScope::class)
@Inject
class RoomIncomingLinksRepository(
  private val dao: IncomingLinkDao,
  private val timeSource: TimeSource,
) : IncomingLinksRepository {

  override suspend fun receive(
    rawText: String,
    ref: GitHubRepoRef,
    senderPhoneId: String?,
    senderLabel: String,
  ): IncomingLink {
    val existing = dao.pendingByKey(ref.key)
    val entity = IncomingLinkEntity(
      id = existing?.id ?: 0,
      rawText = rawText.take(MAX_RAW_TEXT),
      owner = ref.owner,
      repo = ref.repo,
      repoKey = ref.key,
      normalizedUrl = ref.url,
      senderPhoneId = senderPhoneId,
      senderLabel = senderLabel,
      receivedAt = timeSource.now(),
      status = IncomingLinkStatus.PENDING.name,
    )
    val id = dao.upsert(entity).takeIf { it > 0 } ?: entity.id
    return entity.copy(id = id).toModel()
  }

  override fun pending(): Flow<List<IncomingLink>> = dao.pending().map { rows -> rows.map { it.toModel() } }

  override suspend fun get(id: Long): IncomingLink? = dao.byId(id)?.toModel()

  override suspend fun markAdded(id: Long) = dao.setStatus(id, IncomingLinkStatus.ADDED.name)

  override suspend fun markDismissed(id: Long) = dao.setStatus(id, IncomingLinkStatus.DISMISSED.name)

  override suspend fun purgeHandledBefore(before: Instant) = dao.purgeHandled(before)

  private fun IncomingLinkEntity.toModel() = IncomingLink(
    id = id,
    rawText = rawText,
    ref = GitHubRepoRef(owner = owner, repo = repo),
    senderPhoneId = senderPhoneId,
    senderLabel = senderLabel,
    receivedAt = receivedAt,
    status = IncomingLinkStatus.valueOf(status),
  )
}
