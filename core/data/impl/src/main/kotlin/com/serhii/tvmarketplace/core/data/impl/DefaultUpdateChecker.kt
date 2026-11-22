package com.serhii.tvmarketplace.core.data.impl

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.serhii.tvmarketplace.core.common.AppLog
import com.serhii.tvmarketplace.core.common.DeviceAbis
import com.serhii.tvmarketplace.core.common.TimeSource
import com.serhii.tvmarketplace.core.data.CheckResult
import com.serhii.tvmarketplace.core.data.PackageIdentity
import com.serhii.tvmarketplace.core.data.ReleaseResult
import com.serhii.tvmarketplace.core.data.ReleaseSource
import com.serhii.tvmarketplace.core.data.ReleasesRepository
import com.serhii.tvmarketplace.core.data.TrackedApp
import com.serhii.tvmarketplace.core.data.TrackedAppsRepository
import com.serhii.tvmarketplace.core.data.UpdateCheckState
import com.serhii.tvmarketplace.core.data.UpdateChecker
import com.serhii.tvmarketplace.core.domain.GitHubRepoRef
import com.serhii.tvmarketplace.core.domain.PackageChooser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

private val LAST_FINISHED = longPreferencesKey("lastCheckFinishedAt")
private val RESUME_AT = longPreferencesKey("resumeAt")

// UpdateCheckBindings provides it, with its own settings file
class DefaultUpdateChecker(
  private val trackedApps: TrackedAppsRepository,
  private val releases: ReleasesRepository,
  private val source: ReleaseSource,
  private val timeSource: TimeSource,
  private val store: DataStore<Preferences>,
  private val identity: PackageIdentity,
  private val deviceAbis: DeviceAbis,
) : UpdateChecker {

  // packages whose name was asked for already in this process: a server that can't tell isn't asked again
  private val identityAsked = ConcurrentHashMap.newKeySet<Long>()

  private val mutex = Mutex()
  private val running = MutableStateFlow(false)

  // a check of everything that is under way; a second caller waits for it instead of asking GitHub again
  @Volatile
  private var current: CompletableDeferred<CheckResult>? = null

  override fun state(): Flow<UpdateCheckState> = combine(store.data, running) { prefs, running ->
    UpdateCheckState(
      running = running,
      lastFinishedAt = prefs[LAST_FINISHED]?.let(Instant::ofEpochMilli),
      resumeAt = prefs[RESUME_AT]?.let(Instant::ofEpochMilli)?.takeIf { it.isAfter(timeSource.now()) },
    )
  }

  override suspend fun checkAll(): CheckResult {
    current?.let { return it.await() }
    val result = CompletableDeferred<CheckResult>()
    current = result
    return try {
      mutex.withLock {
        running.value = true
        val stored = releases.latestReleases().first()
        // the ones checked longest ago first: a stop on the request limit leaves the freshest data behind
        val apps = trackedApps.trackedApps().first().sortedBy { stored[it.id]?.checkedAt ?: Instant.EPOCH }
        val outcome = run(apps)
        if (outcome == CheckResult.Done) {
          val _ = store.edit { it[LAST_FINISHED] = timeSource.now().toEpochMilli() }
        }
        AppLog.event("update_check", "apps" to apps.size, "result" to outcome.code())
        outcome
      }.also(result::complete)
    } finally {
      running.value = false
      current = null
      if (!result.isCompleted) result.complete(CheckResult.NetworkError)
    }
  }

  override suspend fun check(appId: Long): CheckResult = mutex.withLock {
    val app = trackedApps.get(appId) ?: return@withLock CheckResult.Done
    run(listOf(app))
  }

  // an app installed before it was tracked: its package name tells whether, and which version, is installed
  private suspend fun learnPackageName(app: TrackedApp) {
    val release = releases.latest(app.id)?.release ?: return
    val pkg = PackageChooser.choose(release.packages, deviceAbis.abis()).recommended ?: return
    if (!identityAsked.add(pkg.id)) return
    identity.learn(pkg)?.let { trackedApps.setPackageName(app.id, it) }
  }

  private suspend fun run(apps: List<TrackedApp>): CheckResult {
    val now = timeSource.now()
    // GitHub's request limit holds back only GitHub apps: the others are still checked
    var limitedUntil = store.data.first()[RESUME_AT]?.let(Instant::ofEpochMilli)?.takeIf { it.isAfter(now) }
    for (app in apps) {
      if (limitedUntil != null && app.source is GitHubRepoRef) continue
      val stored = releases.latest(app.id)
      when (val answer = source.latest(app.source, app.includePrereleases, stored?.etag)) {
        is ReleaseResult.Found -> {
          releases.store(app.id, answer.release, answer.etag, timeSource.now())
          if (app.sourceGone) trackedApps.setSourceGone(app.id, false)
        }
        ReleaseResult.NotModified -> {
          releases.touch(app.id, timeSource.now())
          if (app.sourceGone) trackedApps.setSourceGone(app.id, false)
        }
        ReleaseResult.SourceGone -> {
          trackedApps.setSourceGone(app.id, true)
          continue
        }
        is ReleaseResult.RateLimited -> {
          val _ = store.edit { it[RESUME_AT] = answer.resumeAt.toEpochMilli() }
          AppLog.event("rate_limited")
          limitedUntil = answer.resumeAt
          continue
        }
        // offline: asking about the rest would fail the same way
        ReleaseResult.NetworkError -> return CheckResult.NetworkError
      }
      if (app.packageName == null) learnPackageName(app)
    }
    return limitedUntil?.let { CheckResult.RateLimited(it) } ?: CheckResult.Done
  }
}

private fun CheckResult.code(): Int = when (this) {
  CheckResult.Done -> 0
  is CheckResult.RateLimited -> 1
  CheckResult.NetworkError -> 2
}
