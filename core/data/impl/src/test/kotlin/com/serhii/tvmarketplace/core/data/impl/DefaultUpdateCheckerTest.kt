package com.serhii.tvmarketplace.core.data.impl

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.serhii.tvmarketplace.core.data.CheckResult
import com.serhii.tvmarketplace.core.data.PackageIdentity
import com.serhii.tvmarketplace.core.data.ReleaseResult
import com.serhii.tvmarketplace.core.domain.AppSource
import com.serhii.tvmarketplace.core.domain.GitHubRepoRef
import com.serhii.tvmarketplace.core.testing.FakeReleaseSource
import com.serhii.tvmarketplace.core.testing.FakeReleasesRepository
import com.serhii.tvmarketplace.core.testing.FakeTrackedApps
import com.serhii.tvmarketplace.core.testing.MutableTimeSource
import com.serhii.tvmarketplace.core.testing.release
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultUpdateCheckerTest {

  @get:Rule
  val folder = TemporaryFolder()

  private val trackedApps = FakeTrackedApps(GitHubRepoRef("o", "one"), GitHubRepoRef("o", "two"), GitHubRepoRef("o", "three"))
  private val releases = FakeReleasesRepository()
  private val source = FakeReleaseSource()
  private val time = MutableTimeSource()
  private val learned = mutableListOf<String>()
  private val identity = PackageIdentity { pkg ->
    learned += pkg.fileName
    "com.example.${pkg.fileName.substringBefore('.')}"
  }

  private fun TestScope.checker() = DefaultUpdateChecker(
    trackedApps,
    releases,
    source,
    time,
    PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("update_check.preferences_pb") },
    identity,
    { listOf("arm64-v8a") },
  )

  private val repos get() = source.requests.map { (it.first as AppSource.GitHub).repo }

  @Test
  fun `stores each app's latest release, oldest checked first`() = runTest {
    releases.store(1, release("v1"), "\"e1\"", time.now)
    releases.store(2, release("v1"), null, time.now - Duration.ofHours(2))
    val checker = checker()
    assertEquals(CheckResult.Done, checker.checkAll())
    assertEquals(listOf("three", "two", "one"), repos)
    // the etag it had goes along, so an unchanged release costs nothing
    assertEquals("\"e1\"", source.requests.last().second)
    assertEquals("v1.0.0", releases.latest(3)?.release?.tag)
    assertEquals(time.now, checker.state().first().lastFinishedAt)
  }

  @Test
  fun `the request limit stops the check, keeps what was stored and holds further checks`() = runTest {
    releases.store(1, release("v1"), null, time.now)
    val resumeAt = time.now.plusSeconds(600)
    source.answer =
      { appSource, _ ->
        if ((appSource as AppSource.GitHub).repo ==
          "three"
        ) {
          ReleaseResult.RateLimited(resumeAt)
        } else {
          ReleaseResult.Found(release("v2"), null)
        }
      }
    val checker = checker()
    assertEquals(CheckResult.RateLimited(resumeAt), checker.checkAll())
    // never checked ones go first, in list order; the one checked last isn't reached
    assertEquals(listOf("two", "three"), repos)
    assertEquals("v2", releases.latest(2)?.release?.tag)
    assertEquals("v1", releases.latest(1)?.release?.tag)
    assertEquals(resumeAt, checker.state().first().resumeAt)
    assertEquals(CheckResult.RateLimited(resumeAt), checker.checkAll())
    assertEquals(2, source.requests.size)
    time.now = resumeAt.plusSeconds(1)
    assertEquals(null, checker.state().first().resumeAt)
  }

  @Test
  fun `a gone repository is marked, and unmarked when it answers again`() = runTest {
    source.answer = { _, _ -> ReleaseResult.SourceGone }
    val checker = checker()
    val _ = checker.check(1)
    assertEquals(true, trackedApps.get(1)?.sourceGone)
    source.answer = { _, _ -> ReleaseResult.NotModified }
    val _ = checker.check(1)
    assertEquals(false, trackedApps.get(1)?.sourceGone)
  }

  @Test
  fun `pre-releases are asked for only for apps that opted in`() = runTest {
    trackedApps.update(2) { it.copy(includePrereleases = true) }
    val asked = mutableListOf<Boolean>()
    source.answer = { _, include ->
      asked += include
      ReleaseResult.Found(null, null)
    }
    val _ = checker().checkAll()
    assertEquals(listOf(false, true, false), asked)
  }

  @Test
  fun `a check that is running is joined, not repeated`() = runTest {
    val gate = CompletableDeferred<Unit>()
    val slow = object : com.serhii.tvmarketplace.core.data.ReleaseSource {
      var calls = 0

      override suspend fun latest(
        source: AppSource,
        includePrereleases: Boolean,
        etag: String?,
      ): ReleaseResult {
        calls++
        gate.await()
        return ReleaseResult.NotModified
      }
    }
    val checker = DefaultUpdateChecker(
      trackedApps,
      releases,
      slow,
      time,
      PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("joined.preferences_pb") },
      identity,
      { listOf("arm64-v8a") },
    )
    val first = async { checker.checkAll() }
    runCurrent()
    assertEquals(true, checker.state().first().running)
    val second = async { checker.checkAll() }
    runCurrent()
    gate.complete(Unit)
    assertEquals(CheckResult.Done, first.await())
    assertEquals(CheckResult.Done, second.await())
    assertEquals(3, slow.calls)
  }

  @Test
  fun `being offline stops the check`() = runTest {
    source.answer = { _, _ -> ReleaseResult.NetworkError }
    assertEquals(CheckResult.NetworkError, checker().checkAll())
    assertEquals(1, source.requests.size)
  }

  @Test
  fun `an app without a known package name learns it once from its recommended package`() = runTest {
    trackedApps.update(2) { it.copy(packageName = "com.known") }
    val checker = checker()
    val _ = checker.checkAll()
    assertEquals("com.example.app-universal-release", trackedApps.get(1)?.packageName)
    assertEquals("com.known", trackedApps.get(2)?.packageName)
    // the same package isn't asked for again
    assertEquals(listOf("app-universal-release.apk"), learned)
  }
}
