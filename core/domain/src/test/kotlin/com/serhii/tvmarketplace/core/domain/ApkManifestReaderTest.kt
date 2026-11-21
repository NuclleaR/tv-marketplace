package com.serhii.tvmarketplace.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ApkManifestReaderTest {

  private fun fixture(name: String) = checkNotNull(javaClass.getResource("/apk/$name")).readBytes()

  // what the ranged requests return: the tail, the central directory, then the manifest entry
  private fun read(apk: ByteArray): String? {
    val tail = apk.copyOfRange(maxOf(0, apk.size - ApkManifestReader.TAIL_SIZE), apk.size)
    val directory = ApkManifestReader.centralDirectory(tail, apk.size.toLong()) ?: return null
    val entry =
      ApkManifestReader.manifestEntry(apk.copyOfRange(directory.offset.toInt(), (directory.offset + directory.size).toInt())) ?: return null
    val end = minOf(apk.size.toLong(), entry.localHeaderOffset + entry.fetchSize).toInt()
    val manifest = ApkManifestReader.manifest(apk.copyOfRange(entry.localHeaderOffset.toInt(), end), entry) ?: return null
    return ApkManifestReader.packageName(manifest)
  }

  @Test
  fun `reads the package name from deflated and stored manifests`() {
    assertEquals("com.example.tvfixture", read(fixture("aapt2-deflated.apk")))
    assertEquals("org.example.another.app", read(fixture("stored.apk")))
    // other entries before the manifest, and a zip comment after the directory
    assertEquals("org.example.another.app", read(fixture("padded-deflated.apk")))
  }

  @Test
  fun `anything that isn't an APK reads as nothing`() {
    assertNull(read(ByteArray(2000) { it.toByte() }))
    assertNull(ApkManifestReader.packageName(ByteArray(64)))
    val apk = fixture("aapt2-deflated.apk")
    // cut off before the manifest's data ends
    val tail = apk.copyOfRange(0, apk.size)
    val directory = checkNotNull(ApkManifestReader.centralDirectory(tail, apk.size.toLong()))
    val entry =
      checkNotNull(ApkManifestReader.manifestEntry(apk.copyOfRange(directory.offset.toInt(), (directory.offset + directory.size).toInt())))
    assertNull(ApkManifestReader.manifest(apk.copyOfRange(entry.localHeaderOffset.toInt(), entry.localHeaderOffset.toInt() + 40), entry))
  }
}
