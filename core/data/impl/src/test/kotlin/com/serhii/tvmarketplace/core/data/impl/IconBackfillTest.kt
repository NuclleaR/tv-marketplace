package com.serhii.tvmarketplace.core.data.impl

import com.serhii.tvmarketplace.core.data.PreviewResult
import com.serhii.tvmarketplace.core.domain.GitHubRepoRef
import com.serhii.tvmarketplace.core.testing.FakeGitHub
import com.serhii.tvmarketplace.core.testing.FakeTrackedApps
import com.serhii.tvmarketplace.core.testing.previewOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class IconBackfillTest {

  private val obtainium = GitHubRepoRef("ImranR98", "Obtainium")
  private val gone = GitHubRepoRef("someone", "deleted")
  private val trackedApps = FakeTrackedApps(obtainium, gone)

  @Test
  fun `apps added before icons get theirs, and are not asked about again`() = runTest {
    val gitHub = FakeGitHub { ref ->
      if (ref == gone) PreviewResult.NotFound else PreviewResult.Found(previewOf(ref).copy(iconUrl = "https://raw/${ref.repo}.png"))
    }
    IconBackfill(trackedApps, gitHub).run()
    assertEquals(listOf("https://raw/Obtainium.png", null), trackedApps.apps.value.map { it.iconUrl })
    assertEquals(emptyList(), trackedApps.withUncheckedIcon())
    IconBackfill(trackedApps, gitHub).run()
    assertEquals(2, gitHub.requests.size)
  }

  @Test
  fun `when GitHub limits requests it stops and tries again next time`() = runTest {
    val gitHub = FakeGitHub { PreviewResult.RateLimited }
    IconBackfill(trackedApps, gitHub).run()
    assertEquals(1, gitHub.requests.size)
    assertEquals(2, trackedApps.withUncheckedIcon().size)
  }
}
