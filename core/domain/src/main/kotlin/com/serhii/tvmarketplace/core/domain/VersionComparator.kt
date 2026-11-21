package com.serhii.tvmarketplace.core.domain

// tags are free-form, so no comparison is right for every repository: numbers where both sides have them,
// publication time where they don't, and never an "update" to an older version
object VersionComparator {

  private val prefix = Regex("^[^0-9]*")
  private val version = Regex("^(\\d+(?:\\.\\d+)*)(.*)$")
  private val preRelease = Regex("^[-_.+]?(alpha|beta|rc|pre|preview|dev|a|b)[-_.]?(\\d*)", RegexOption.IGNORE_CASE)
  private val hash = Regex("^[0-9a-f]{7,40}$")

  // > 0 when a is newer, null when the two can't be compared as versions
  fun compare(
    a: String,
    b: String,
  ): Int? {
    val left = parse(a) ?: return null
    val right = parse(b) ?: return null
    val core = compareNumbers(left.numbers, right.numbers)
    if (core != 0) return core
    return when {
      left.preRelease == right.preRelease -> if (left.rest == right.rest || left.preRelease != null) 0 else null
      // a pre-release comes before its release
      left.preRelease == null -> if (left.rest.isEmpty() || !right.rest.isEmpty()) 1 else null
      right.preRelease == null -> if (right.rest.isEmpty() || !left.rest.isEmpty()) -1 else null
      else -> comparePreRelease(left.preRelease, right.preRelease)
    }
  }

  // installedTag: the release this app installed, while the installed version code still is the one it installed
  fun isNewer(
    release: Release,
    installed: InstalledApp,
    installedTag: String?,
  ): Boolean {
    if (installedTag != null) {
      if (release.tag == installedTag) return false
      return (compare(release.tag, installedTag) ?: 1) > 0
    }
    val name = installed.versionName ?: return true
    compare(release.tag, name)?.let { return it > 0 }
    return normalized(release.tag) != normalized(name) && release.publishedAt.isAfter(installed.lastUpdateTime)
  }

  private data class Parsed(
    val numbers: List<Int>,
    val preRelease: Pair<String, Int>?,
    val rest: String,
  )

  private fun normalized(value: String) = value.trim().lowercase().replace(prefix, "")

  private fun parse(value: String): Parsed? {
    val trimmed = value.trim()
    if (hash.matches(trimmed.lowercase())) return null
    val match = version.find(trimmed.replace(prefix, "")) ?: return null
    val numbers = match.groupValues[1].split('.').map { it.toIntOrNull() ?: return null }
    val rest = match.groupValues[2]
    // a date like 2026-09-17 only has its year in the numbers: the rest decides, so it isn't comparable
    val pre = preRelease.find(rest)?.let { it.groupValues[1].lowercase() to (it.groupValues[2].toIntOrNull() ?: 0) }
    return Parsed(numbers, pre, if (pre != null) "" else rest.trim().lowercase().takeUnless { it.length == 1 && it[0].isLetter() } ?: "")
  }

  private fun compareNumbers(
    a: List<Int>,
    b: List<Int>,
  ): Int {
    for (i in 0 until maxOf(a.size, b.size)) {
      val diff = a.getOrElse(i) { 0 }.compareTo(b.getOrElse(i) { 0 })
      if (diff != 0) return diff
    }
    return 0
  }

  private val stages = listOf("dev", "a", "alpha", "b", "beta", "pre", "preview", "rc")

  private fun comparePreRelease(
    a: Pair<String, Int>,
    b: Pair<String, Int>,
  ): Int {
    val stage = stages.indexOf(a.first).compareTo(stages.indexOf(b.first))
    return if (stage != 0) stage else a.second.compareTo(b.second)
  }
}
