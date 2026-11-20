package com.serhii.tvmarketplace.core.testing

import com.serhii.tvmarketplace.core.common.TimeSource
import com.serhii.tvmarketplace.core.data.GitHubOwner
import com.serhii.tvmarketplace.core.data.GitHubRepoSummary
import com.serhii.tvmarketplace.core.data.GitHubRepository
import com.serhii.tvmarketplace.core.data.IncomingLink
import com.serhii.tvmarketplace.core.data.IncomingLinkStatus
import com.serhii.tvmarketplace.core.data.IncomingLinksRepository
import com.serhii.tvmarketplace.core.data.LinkShareSettings
import com.serhii.tvmarketplace.core.data.LookupResult
import com.serhii.tvmarketplace.core.data.OwnerRepos
import com.serhii.tvmarketplace.core.data.PairedPhone
import com.serhii.tvmarketplace.core.data.PairedPhoneKind
import com.serhii.tvmarketplace.core.data.PairedPhonesRepository
import com.serhii.tvmarketplace.core.data.PreviewResult
import com.serhii.tvmarketplace.core.data.TrackedApp
import com.serhii.tvmarketplace.core.data.TrackedAppsRepository
import com.serhii.tvmarketplace.core.domain.AppPreview
import com.serhii.tvmarketplace.core.domain.GitHubRepoRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

class FakeIncomingLinks : IncomingLinksRepository {
  val links = MutableStateFlow<List<IncomingLink>>(emptyList())

  override suspend fun receive(
    rawText: String,
    ref: GitHubRepoRef,
    senderPhoneId: String?,
    senderLabel: String,
  ): IncomingLink {
    val link = IncomingLink(links.value.size + 1L, rawText, ref, senderPhoneId, senderLabel, Instant.EPOCH, IncomingLinkStatus.PENDING)
    links.value += link
    return link
  }

  // newest first, like the real repository
  override fun pending(): Flow<List<IncomingLink>> = links.map { all ->
    all.filter { it.status == IncomingLinkStatus.PENDING }.sortedByDescending { it.id }
  }

  override suspend fun get(id: Long): IncomingLink? = links.value.firstOrNull { it.id == id }

  override suspend fun markAdded(id: Long) = setStatus(id, IncomingLinkStatus.ADDED)

  override suspend fun markDismissed(id: Long) = setStatus(id, IncomingLinkStatus.DISMISSED)

  override suspend fun purgeHandledBefore(before: Instant) {
    links.value = links.value.filter { it.status == IncomingLinkStatus.PENDING || !it.receivedAt.isBefore(before) }
  }

  private fun setStatus(
    id: Long,
    status: IncomingLinkStatus,
  ) {
    links.value = links.value.map { if (it.id == id) it.copy(status = status) else it }
  }
}

// the apps given here count as added before icons were looked up
class FakeTrackedApps(vararg tracked: GitHubRepoRef) : TrackedAppsRepository {
  val apps = MutableStateFlow(tracked.mapIndexed { index, ref -> TrackedApp(index + 1L, ref, ref.repo, null, null, Instant.EPOCH) })
  private val iconChecked = mutableSetOf<Long>()

  override suspend fun withUncheckedIcon(): List<TrackedApp> = apps.value.filter { it.id !in iconChecked }

  override suspend fun setIcon(
    id: Long,
    iconUrl: String?,
  ) {
    iconChecked += id
    apps.value = apps.value.map { if (it.id == id) it.copy(iconUrl = iconUrl) else it }
  }

  override fun trackedApps(): Flow<List<TrackedApp>> = apps

  override suspend fun find(ref: GitHubRepoRef): TrackedApp? =
    apps.value.firstOrNull { it.ref.owner.equals(ref.owner, true) && it.ref.repo.equals(ref.repo, true) }

  override suspend fun add(preview: AppPreview): TrackedApp {
    find(preview.ref)?.let { return it }
    val app =
      TrackedApp(
        apps.value.size + 1L,
        preview.ref,
        preview.displayName,
        preview.latestVersion,
        preview.avatarUrl,
        Instant.EPOCH,
        preview.iconUrl,
      )
    iconChecked += app.id
    apps.value += app
    return app
  }
}

class FakeGitHub(var result: (GitHubRepoRef) -> PreviewResult = { ref -> PreviewResult.Found(previewOf(ref)) }) : GitHubRepository {
  val requests = mutableListOf<GitHubRepoRef>()

  override suspend fun preview(ref: GitHubRepoRef): PreviewResult {
    requests += ref
    return result(ref)
  }

  var owners: (String) -> LookupResult<List<GitHubOwner>> = { LookupResult.Found(emptyList()) }
  var repos: (String) -> LookupResult<OwnerRepos> = { owner -> LookupResult.Found(OwnerRepos(owner, emptyList(), complete = true)) }
  var searchedRepos: (String, String) -> LookupResult<List<GitHubRepoSummary>> = { _, _ -> LookupResult.Found(emptyList()) }
  val lookups = mutableListOf<String>()

  override suspend fun searchOwners(query: String): LookupResult<List<GitHubOwner>> {
    lookups += "owners:$query"
    return owners(query)
  }

  override suspend fun ownerRepos(owner: String): LookupResult<OwnerRepos> {
    lookups += "repos:$owner"
    return repos(owner)
  }

  override suspend fun searchOwnerRepos(
    owner: String,
    name: String,
  ): LookupResult<List<GitHubRepoSummary>> {
    lookups += "search:$owner/$name"
    return searchedRepos(owner, name)
  }
}

fun repoSummary(
  owner: String,
  name: String,
  description: String? = null,
) = GitHubRepoSummary(owner = owner, name = name, description = description, fork = false, archived = false, stars = 0)

fun previewOf(
  ref: GitHubRepoRef,
  latestVersion: String? = "v1.0.0",
) = AppPreview(ref = ref, displayName = ref.repo, description = "About ${ref.repo}", latestVersion = latestVersion, avatarUrl = null)

class MutableTimeSource(var now: Instant = Instant.parse("2026-11-18T12:00:00Z")) : TimeSource {
  override fun now(): Instant = now
}

class FakePairedPhones(private val timeSource: TimeSource = MutableTimeSource()) : PairedPhonesRepository {
  val hashes = mutableMapOf<String, String>()
  val phones = MutableStateFlow<List<PairedPhone>>(emptyList())

  override fun phones(): Flow<List<PairedPhone>> = phones

  override suspend fun add(
    displayName: String,
    kind: PairedPhoneKind,
    tokenHash: String,
  ): PairedPhone {
    val now = timeSource.now()
    val phone = PairedPhone("phone-${phones.value.size + 1}", displayName.take(60), kind, now, now)
    phones.value += phone
    hashes[tokenHash] = phone.id
    return phone
  }

  override suspend fun useByTokenHash(tokenHash: String): PairedPhone? {
    val id = hashes[tokenHash] ?: return null
    val used = phones.value.first { it.id == id }.copy(lastUsedAt = timeSource.now())
    phones.value = phones.value.map { if (it.id == id) used else it }
    return used
  }

  override suspend fun remove(id: String) {
    phones.value = phones.value.filterNot { it.id == id }
    hashes.values.removeAll { it == id }
  }
}

class FakeLinkShareSettings(enabled: Boolean = true) : LinkShareSettings {
  override val receiveInBackground = MutableStateFlow(enabled)

  override suspend fun setReceiveInBackground(enabled: Boolean) {
    receiveInBackground.value = enabled
  }

  override suspend fun installId(): String = "install-id"
}
