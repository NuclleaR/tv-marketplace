package com.serhii.tvmarketplace.core.domain

// the package the user picked, remembered as its file name without versions, so the next release's file
// with the same name pattern is picked too: App_1.2.3_arm64.apk and App_1.3.0_arm64.apk are one pattern
object PackagePattern {

  private const val VERSION = "{v}"

  // a version-like run: digits with dots, an optional v before it and a pre-release suffix after it. Not inside a
  // word, so architectures like arm64-v8a and armeabi-v7a stay as they are
  private val versionRun = Regex("(?i)(?<![a-z0-9])v?\\d+(?:\\.\\d+)*(?:[-_.]?(?:alpha|beta|rc|pre|dev)[-_.]?\\d*)?(?![a-z0-9])")

  fun of(
    fileName: String,
    release: Release,
  ): String {
    var name = fileName
    // the release's own tag and name go first, so "2024.11.20-1" isn't cut into pieces
    for (version in listOfNotNull(release.tag, release.tag.removePrefix("v"), release.name).filter {
      it.length > 1
    }.sortedByDescending { it.length }) {
      name = name.replace(version, VERSION, ignoreCase = true)
    }
    return versionRun.replace(name) { if (it.value == VERSION) it.value else VERSION }
      .replace(Regex("(\\{v\\})+"), VERSION)
  }

  fun matches(
    pattern: String,
    fileName: String,
    release: Release,
  ): Boolean = of(fileName, release).equals(pattern, ignoreCase = true)
}
