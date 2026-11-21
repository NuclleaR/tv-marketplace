package com.serhii.tvmarketplace.core.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val corpusJson = Json { ignoreUnknownKeys = true }

class PackageChooserTest {

  private val arm64 = listOf("arm64-v8a", "armeabi-v7a", "armeabi")
  private val armv7 = listOf("armeabi-v7a", "armeabi")
  private val x86 = listOf("x86_64", "x86")

  private fun packages(vararg names: String) = names.mapIndexed { index, name -> AppPackage(index.toLong(), name, 1, "https://x/$name") }

  private fun recommended(
    abis: List<String>,
    vararg names: String,
  ) = PackageChooser.choose(packages(*names), abis).recommended?.fileName

  @Test
  fun `the device's own architecture comes first, then universal, then the ones it also runs`() {
    val names = arrayOf("app-armeabi-v7a.apk", "app-universal.apk", "app-arm64-v8a.apk", "app-x86.apk")
    assertEquals("app-arm64-v8a.apk", recommended(arm64, *names))
    assertEquals("app-armeabi-v7a.apk", recommended(armv7, *names))
    assertEquals("app-universal.apk", recommended(x86, *names))
    assertEquals("app-armeabi-v7a.apk", recommended(arm64, "app-armeabi-v7a.apk", "app-x86.apk"))
  }

  @Test
  fun `architectures are read however the file names spell them`() {
    assertEquals("a-x86_64.apk", recommended(x86, "a-arm64.apk", "a-x86_64.apk", "a-x86.apk"))
    assertEquals("LocalSend-android-arm64v8.apk", recommended(arm64, "LocalSend-android-arm32v7.apk", "LocalSend-android-arm64v8.apk"))
    assertEquals("b_aarch64.apk", recommended(arm64, "b_armv7.apk", "b_aarch64.apk"))
    assertEquals("c-x64.apk", recommended(x86, "c-arm.apk", "c-x64.apk"))
  }

  @Test
  fun `a TV build beats a plain one and a phone one, a release beats a debug build`() {
    assertEquals("app-tv-release.apk", recommended(arm64, "app-mobile-release.apk", "app-release.apk", "app-tv-release.apk"))
    assertEquals("jellyfin-androidtv-release.apk", recommended(arm64, "jellyfin-androidtv-debug.apk", "jellyfin-androidtv-release.apk"))
    assertEquals("app-universal-release.apk", recommended(arm64, "app-arm64-v8a-debug.apk", "app-universal-release.apk"))
  }

  @Test
  fun `ties keep the release's order`() {
    assertEquals("NewPipe.apk", recommended(arm64, "NewPipe.apk", "NewPipe_apksigner31.apk"))
  }

  @Test
  fun `every package is listed, the ones for other devices as incompatible`() {
    val choice = PackageChooser.choose(packages("a-arm64-v8a.apk", "a-x86.apk"), armv7)
    assertEquals(listOf(false, false), choice.candidates.map { it.compatible })
    assertNull(choice.recommended)
    assertNull(choice.chosen)
    val other = PackageChooser.choose(packages("a-arm64-v8a.apk", "a-x86.apk"), arm64)
    assertEquals(listOf(true, false), other.candidates.map { it.compatible })
  }

  @Test
  fun `a remembered choice is used while the release has it, and lost when it doesn't`() {
    val names = packages("app-arm64-v8a.apk", "app-armeabi-v7a.apk")
    val kept = PackageChooser.choose(names, arm64) { it.fileName.contains("armeabi") }
    assertEquals("app-arm64-v8a.apk", kept.recommended?.fileName)
    assertEquals("app-armeabi-v7a.apk", kept.chosen?.fileName)
    assertEquals(false, kept.choiceLost)
    val lost = PackageChooser.choose(names, arm64) { false }
    assertEquals("app-arm64-v8a.apk", lost.chosen?.fileName)
    assertEquals(true, lost.choiceLost)
    // a remembered package that doesn't run here isn't used
    assertEquals(
      true,
      PackageChooser.choose(packages("app-x86.apk", "app-arm64-v8a.apk"), arm64) {
        it.fileName.contains("x86")
      }.choiceLost,
    )
  }

  @Test
  fun `only installable files are packages`() {
    assertTrue(PackageChooser.isPackageFile("app.apk", null))
    assertTrue(PackageChooser.isPackageFile("app.bin", "application/vnd.android.package-archive"))
    for (name in listOf("app.aab", "app.apks", "app.xapk", "app.apk.idsig", "app.apk.sha256", "sums.txt", "app.zip")) {
      assertEquals(false, PackageChooser.isPackageFile(name, "application/octet-stream"), name)
    }
  }

  @Serializable
  private data class Entry(
    val repo: String,
    val assets: List<Asset>,
    val expected: Map<String, List<String>>,
  )

  @Serializable
  private data class Asset(
    val name: String,
    @kotlinx.serialization.SerialName("content_type") val contentType: String? = null,
  )

  @Test
  fun `picks a package that installs and runs for at least 95 percent of real releases`() {
    val corpus = corpusJson.decodeFromString<List<Entry>>(checkNotNull(javaClass.getResource("/packages/corpus.json")).readText())
    val devices = mapOf("arm64" to arm64, "armv7" to armv7, "x86" to x86)
    val misses = mutableListOf<String>()
    var checks = 0
    for (entry in corpus) {
      val packages = entry.assets.filter { PackageChooser.isPackageFile(it.name, it.contentType) }.map { it.name }
      for ((device, abis) in devices) {
        checks++
        val picked = recommended(abis, *packages.toTypedArray())
        if (picked !in checkNotNull(entry.expected[device])) misses += "${entry.repo} on $device: $picked"
      }
    }
    assertTrue(corpus.size >= 20)
    assertTrue(misses.size * 20 <= checks, "missed ${misses.size} of $checks: $misses")
  }
}
