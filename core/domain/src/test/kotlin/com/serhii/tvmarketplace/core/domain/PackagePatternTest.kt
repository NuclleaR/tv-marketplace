package com.serhii.tvmarketplace.core.domain

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PackagePatternTest {

  private fun release(
    tag: String,
    name: String? = null,
  ) = Release(tag, name, Instant.EPOCH, prerelease = false, notesHtml = null, packages = emptyList())

  private fun same(
    first: Pair<String, Release>,
    second: Pair<String, Release>,
  ) = PackagePattern.matches(PackagePattern.of(first.first, first.second), second.first, second.second)

  @Test
  fun `the same file of the next release matches`() {
    assertEquals(true, same("App_1.2.3_arm64.apk" to release("1.2.3"), "App_1.3.0_arm64.apk" to release("1.3.0")))
    assertEquals(true, same("app-v2.0-tv-release.apk" to release("v2.0"), "app-v2.1-tv-release.apk" to release("v2.1")))
    assertEquals(
      true,
      same(
        "SmartTube_stable_32.80_armeabi-v7a.apk" to release("32.80s"),
        "SmartTube_stable_33.01_armeabi-v7a.apk" to release("33.01s"),
      ),
    )
    assertEquals(true, same("mpv-2026-09-17-arm64.apk" to release("2026-09-17"), "mpv-2026-11-02-arm64.apk" to release("2026-11-02")))
    assertEquals(true, same("seal-1.13.1-beta2-x86.apk" to release("v1.13.1-beta2"), "seal-1.14.0-x86.apk" to release("v1.14.0")))
  }

  @Test
  fun `other flavours and architectures stay different`() {
    val pattern = PackagePattern.of("YTDLnis-1.9.1-foss-arm64-v8a-release.apk", release("v1.9.1"))
    assertEquals(false, PackagePattern.matches(pattern, "YTDLnis-1.9.2-arm64-v8a-release-github.apk", release("v1.9.2")))
    assertEquals(false, PackagePattern.matches(pattern, "YTDLnis-1.9.2-foss-armeabi-v7a-release.apk", release("v1.9.2")))
    assertNotEquals(PackagePattern.of("app-legacy.apk", release("v1")), PackagePattern.of("app.apk", release("v1")))
  }
}
