package com.serhii.tvmarketplace.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppLinksTest {

  @Test
  fun `finds F-Droid package links, with or without a language`() {
    assertEquals(FDroidApp("org.schabi.newpipe"), AppLinks.extractFirst("https://f-droid.org/packages/org.schabi.newpipe/"))
    assertEquals(FDroidApp("org.schabi.newpipe"), AppLinks.extractFirst("look: https://f-droid.org/uk/packages/org.schabi.newpipe."))
    assertEquals(FDroidApp("org.videolan.vlc"), AppLinks.extractFirst("https://www.f-droid.org/en/packages/org.videolan.vlc"))
  }

  @Test
  fun `a GitHub link wins, and other links are not apps`() {
    assertEquals(GitHubRepoRef("a", "b"), AppLinks.extractFirst("https://f-droid.org/packages/x.y https://github.com/a/b"))
    assertNull(AppLinks.extractFirst("https://f-droid.org/packages/"))
    assertNull(AppLinks.extractFirst("https://f-droid.org/packages/newpipe"))
    assertNull(AppLinks.extractFirst("https://example.org/packages/org.schabi.newpipe"))
  }

  @Test
  fun `each source has its own key`() {
    assertEquals("fdroid:org.schabi.newpipe", FDroidApp("org.schabi.newpipe").key)
    assertEquals("imranr98/obtainium", GitHubRepoRef("ImranR98", "Obtainium").key)
  }
}
