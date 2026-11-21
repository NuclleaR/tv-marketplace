package com.serhii.tvmarketplace.core.data.impl

// the best bitmap launcher icon among a repository's files: the 512px store icon Android Studio writes next to the
// sources, else the densest mipmap, square before round, release sources before debug or test ones. Adaptive icons
// that exist only as XML have no bitmap to show, so they give nothing.
internal fun launcherIconPath(paths: List<String>): String? = paths
  .mapNotNull { path -> launcherIconScore(path)?.let { path to it } }
  .maxWithOrNull(compareBy<Pair<String, Int>> { it.second }.thenBy { -it.first.length })
  ?.first

private val DENSITIES = mapOf("mdpi" to 1, "hdpi" to 2, "xhdpi" to 3, "xxhdpi" to 4, "xxxhdpi" to 5)
private val IGNORED_SOURCE_SETS = setOf("debug", "test", "androidTest", "build", "beta", "nightly", "dev")

private fun launcherIconScore(path: String): Int? {
  val segments = path.split("/")
  if (segments.any { it.startsWith(".") || it in IGNORED_SOURCE_SETS }) return null
  val file = segments.last()
  val name = file.substringBeforeLast(".")
  if (file.substringAfterLast(".").lowercase() !in setOf("png", "webp")) return null
  val inMain = if ("main" in segments) 100 else 0
  if (name == "ic_launcher-playstore" || name == "ic_launcher-web") return inMain + 60
  val density = segments.getOrNull(segments.size - 2)
    ?.takeIf { it.startsWith("mipmap-") || it.startsWith("drawable-") }
    ?.split("-")
    ?.firstNotNullOfOrNull { DENSITIES[it] }
    ?: return null
  return when (name) {
    "ic_launcher" -> inMain + density * 10 + 1
    "ic_launcher_round" -> inMain + density * 10
    else -> null
  }
}
