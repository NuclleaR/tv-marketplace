package com.serhii.tvmarketplace.core.data.impl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LauncherIconTest {

  @Test
  fun `prefers the store icon, then the densest square mipmap`() {
    val mipmaps = listOf(
      "app/src/main/res/mipmap-mdpi/ic_launcher.png",
      "app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png",
      "app/src/main/res/mipmap-xxxhdpi/ic_launcher.png",
      "app/src/main/res/mipmap-xxhdpi/ic_launcher.png",
    )
    assertEquals("app/src/main/res/mipmap-xxxhdpi/ic_launcher.png", launcherIconPath(mipmaps))
    assertEquals(
      "app/src/main/ic_launcher-playstore.png",
      launcherIconPath(mipmaps + "app/src/main/ic_launcher-playstore.png"),
    )
  }

  @Test
  fun `release sources win over debug ones`() {
    assertEquals(
      "app/src/main/res/mipmap-hdpi/ic_launcher.png",
      launcherIconPath(listOf("app/src/debug/res/mipmap-xxxhdpi/ic_launcher.png", "app/src/main/res/mipmap-hdpi/ic_launcher.png")),
    )
  }

  @Test
  fun `adaptive icons in XML and unrelated images give nothing`() {
    assertNull(
      launcherIconPath(
        listOf(
          "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml",
          "app/src/main/res/drawable/ic_launcher_foreground.xml",
          "docs/screenshot.png",
          "app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png",
        ),
      ),
    )
  }
}
