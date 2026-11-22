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
import com.serhii.tvmarketplace.core.domain.AppSource
import com.serhii.tvmarketplace.core.domain.FDroidApp
import com.serhii.tvmarketplace.core.domain.GitHubRepoRef
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

private const val MAX_RAW_TEXT = 4000

// the owner and repo columns: an F-Droid app keeps its package in repo and no owner
private fun AppSource.ownerAndRepo(): Pair<String, String> = when (this) {
  is GitHubRepoRef -> owner to repo
  is FDroidApp -> "" to packageName
}

private fun source(
  kind: String,
  owner: String,
  repo: String,
): AppSource = when (kind) {
  FDroidApp.KIND -> FDroidApp(repo)
  else -> GitHubRepoRef(owner = owner, repo = repo)
}

@ContributesBinding(AppScope::class)
@Inject
class RoomTrackedAppsRepository(
  private val dao: TrackedAppDao,
  private val timeSource: TimeSource,
) : TrackedAppsRepository {

  override fun trackedApps(): Flow<List<TrackedApp>> = dao.all().map { rows -> rows.map { it.toModel() } }

  override suspend fun find(ref: AppSource): TrackedApp? = dao.byKey(ref.key)?.toModel()

  override suspend fun add(preview: AppPreview): TrackedApp {
    dao.byKey(preview.ref.key)?.let { return it.toModel() }
    val (owner, repo) = preview.ref.ownerAndRepo()
    val entity = TrackedAppEntity(
      owner = owner,
      repo = repo,
      repoKey = preview.ref.key,
      sourceUrl = preview.ref.url,
      sourceKind = preview.ref.kind,
      // an F-Droid app is its package
      packageName = (preview.ref as? FDroidApp)?.packageName,
      displayName = preview.displayName,
      latestVersion = preview.latestVersion,
      avatarUrl = preview.avatarUrl,
      addedAt = timeSource.now(),
      iconUrl = preview.iconUrl,
      iconChecked = true,
      description = preview.description,
    )
    return entity.copy(id = dao.upsert(entity)).toModel()
  }

  override suspend fun withUncheckedIcon(): List<TrackedApp> = dao.withUncheckedIcon().map { it.toModel() }

  override suspend fun setIcon(
    id: Long,
    iconUrl: String?,
  ) = dao.setIcon(id, iconUrl)

  override suspend fun get(id: Long): TrackedApp? = dao.byId(id)?.toModel()

  override fun observe(id: Long): Flow<TrackedApp?> = dao.observe(id).map { it?.toModel() }

  override suspend fun setPackageName(
    id: Long,
    packageName: String,
  ) = dao.setPackageNameIfUnknown(id, packageName)

  override suspend fun setIncludePrereleases(
    id: Long,
    include: Boolean,
  ) = dao.setIncludePrereleases(id, include)

  override suspend fun setPackagePattern(
    id: Long,
    pattern: String?,
  ) = dao.setPackagePattern(id, pattern)

  override suspend fun setSourceGone(
    id: Long,
    gone: Boolean,
  ) = dao.setSourceGone(id, gone)

  override suspend fun remove(id: Long) = dao.delete(id)

  private fun TrackedAppEntity.toModel() = TrackedApp(
    id = id,
    ref = source(sourceKind, owner, repo),
    displayName = displayName,
    latestVersion = latestVersion,
    avatarUrl = avatarUrl,
    addedAt = addedAt,
    iconUrl = iconUrl,
    packageName = packageName,
    includePrereleases = includePrereleases,
    packagePattern = packagePattern,
    sourceGone = sourceGone,
    description = description,
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
    ref: AppSource,
    senderPhoneId: String?,
    senderLabel: String,
  ): IncomingLink {
    val existing = dao.pendingByKey(ref.key)
    val entity = IncomingLinkEntity(
      id = existing?.id ?: 0,
      rawText = rawText.take(MAX_RAW_TEXT),
      owner = ref.ownerAndRepo().first,
      repo = ref.ownerAndRepo().second,
      sourceKind = ref.kind,
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
    ref = source(sourceKind, owner, repo),
    senderPhoneId = senderPhoneId,
    senderLabel = senderLabel,
    receivedAt = receivedAt,
    status = IncomingLinkStatus.valueOf(status),
  )
}
