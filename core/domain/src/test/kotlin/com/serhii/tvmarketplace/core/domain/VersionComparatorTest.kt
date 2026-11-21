package com.serhii.tvmarketplace.core.domain

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class VersionComparatorTest {

  @Test
  fun `compares versions however tags spell them`() {
    val cases = listOf(
      Triple("v1.3.0", "1.2.9", 1),
      Triple("1.10", "1.9", 1),
      Triple("v2.0", "2.0.0", 0),
      Triple("version 3.1", "v3.1", 0),
      Triple("1.2.3.4", "1.2.3", 1),
      Triple("SmartTube 32.80s", "32.79s", 1),
      Triple("2.0.0-beta.2", "2.0.0-beta.1", 1),
      Triple("2.0.0", "2.0.0-rc1", 1),
      Triple("2.0.0-alpha", "1.9", 1),
    )
    for ((a, b, expected) in cases) {
      assertEquals(expected, VersionComparator.compare(a, b)?.coerceIn(-1, 1), "$a vs $b")
      assertEquals(-expected, VersionComparator.compare(b, a)?.coerceIn(-1, 1), "$b vs $a")
    }
  }

  @Test
  fun `hashes and dates with the same leading number are not comparable`() {
    assertEquals(null, VersionComparator.compare("a1b2c3d", "e4f5a6b"))
    assertEquals(null, VersionComparator.compare("2026-09-17", "2026-08-01"))
    assertEquals(null, VersionComparator.compare("nightly", "1.0"))
  }

  private val published = Instant.parse("2026-11-10T10:00:00Z")

  private fun release(tag: String) = Release(tag, null, published, prerelease = false, notesHtml = null, packages = emptyList())

  private fun installed(
    versionName: String?,
    updated: Instant = Instant.parse("2026-11-01T10:00:00Z"),
  ) = InstalledApp("a.b", versionName, 1, updated, installedByThisApp = false)

  @Test
  fun `what this app installed is known exactly`() {
    assertEquals(false, VersionComparator.isNewer(release("v1.3"), installed("weird"), installedTag = "v1.3"))
    assertEquals(true, VersionComparator.isNewer(release("v1.4"), installed("weird"), installedTag = "v1.3"))
    // an older tag than the installed one is no update
    assertEquals(false, VersionComparator.isNewer(release("v1.2"), installed("weird"), installedTag = "v1.3"))
  }

  @Test
  fun `otherwise the installed version name is compared, and never downgraded`() {
    assertEquals(true, VersionComparator.isNewer(release("v1.3.0"), installed("1.2.0"), installedTag = null))
    assertEquals(false, VersionComparator.isNewer(release("v1.3.0"), installed("1.3.0"), installedTag = null))
    assertEquals(false, VersionComparator.isNewer(release("v1.2.0"), installed("1.3.0"), installedTag = null))
  }

  @Test
  fun `names that don't compare count as newer when they differ and were published after the last update`() {
    assertEquals(true, VersionComparator.isNewer(release("2026-11-10"), installed("2026-10-01"), installedTag = null))
    assertEquals(false, VersionComparator.isNewer(release("2026-11-10"), installed("2026-11-10"), installedTag = null))
    assertEquals(
      false,
      VersionComparator.isNewer(
        release("nightly-abc"),
        installed("nightly-def", updated = Instant.parse("2026-11-12T00:00:00Z")),
        installedTag = null,
      ),
    )
    assertEquals(true, VersionComparator.isNewer(release("nightly-abc"), installed(null), installedTag = null))
  }
}
