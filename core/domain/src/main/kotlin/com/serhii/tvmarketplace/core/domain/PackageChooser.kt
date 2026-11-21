package com.serhii.tvmarketplace.core.domain

enum class Abi(val systemName: String) {
  ARM64_V8A("arm64-v8a"),
  ARMEABI_V7A("armeabi-v7a"),
  ARMEABI("armeabi"),
  X86_64("x86_64"),
  X86("x86"),
  ;

  companion object {
    fun of(systemName: String): Abi? = entries.firstOrNull { it.systemName == systemName }
  }
}

enum class Variant { TV, NONE, MOBILE }

data class Candidate(
  val pkg: AppPackage,
  // runs on this device
  val compatible: Boolean,
  // empty: built for every architecture
  val abis: Set<Abi>,
  val variant: Variant,
  val debug: Boolean,
)

data class PackageChoice(
  // every package of the release, in its order
  val candidates: List<Candidate>,
  // the best package for this device; null when none runs here
  val recommended: AppPackage?,
  // what gets installed: the user's remembered choice when it's in this release, else the recommended one
  val chosen: AppPackage?,
  // a choice is remembered, but nothing in this release matches it
  val choiceLost: Boolean,
)

// picks a release's package for this device from file names, the only per-file signal releases give
object PackageChooser {

  private const val APK_CONTENT_TYPE = "application/vnd.android.package-archive"

  // file name spellings of each architecture; bounded so "alarm" isn't "arm", 64-bit x86 is matched before x86
  private val abiPatterns = listOf(
    Abi.X86_64 to "x86[_-]?64|x64|amd64",
    Abi.ARM64_V8A to "arm64[_-]?v8a?|arm64|aarch64|armv8a?",
    Abi.ARMEABI_V7A to "armeabi[_-]?v7a?|armv7a?|arm32v7|arm32|armhf|v7a",
    Abi.X86 to "x86|i[36]86",
    Abi.ARMEABI to "armeabi|arm",
  ).map { (abi, pattern) -> abi to Regex("(?<![a-z0-9])(?:$pattern)(?![a-z0-9])") }

  private val tvWords = Regex("(?<![a-z0-9])(?:tv|androidtv|atv|leanback|firetv)(?![a-z0-9])")
  private val mobileWords = Regex("(?<![a-z0-9])(?:mobile|phone)(?![a-z0-9])")
  private val debugWords = Regex("(?<![a-z0-9])(?:debug|unsigned|test)(?![a-z0-9])")

  // .aab, .apks and .xapk are bundles the system installer can't take as one file; .idsig and checksums sit next to APKs
  fun isPackageFile(
    fileName: String,
    contentType: String?,
  ): Boolean {
    val name = fileName.lowercase()
    return name.endsWith(".apk") || (contentType == APK_CONTENT_TYPE && !name.contains(".apk."))
  }

  fun candidate(
    pkg: AppPackage,
    deviceAbis: List<Abi>,
  ): Candidate {
    var name = pkg.fileName.lowercase().removeSuffix(".apk")
    val abis = mutableSetOf<Abi>()
    for ((abi, pattern) in abiPatterns) {
      if (pattern.containsMatchIn(name)) {
        abis += abi
        // so x86_64 doesn't also read as x86, and arm64 doesn't leave an "arm"
        name = pattern.replace(name, " ")
      }
    }
    return Candidate(
      pkg = pkg,
      compatible = abis.isEmpty() || abis.any { it in deviceAbis },
      abis = abis,
      variant = when {
        tvWords.containsMatchIn(name) -> Variant.TV
        mobileWords.containsMatchIn(name) -> Variant.MOBILE
        else -> Variant.NONE
      },
      debug = debugWords.containsMatchIn(name),
    )
  }

  // deviceAbis: Build.SUPPORTED_ABIS, the device's preferred architecture first.
  // matches(pkg) says whether a package fits the user's remembered choice; null when there is none
  fun choose(
    packages: List<AppPackage>,
    deviceAbis: List<String>,
    matches: ((AppPackage) -> Boolean)? = null,
  ): PackageChoice {
    val abis = deviceAbis.mapNotNull(Abi::of)
    val candidates = packages.map { candidate(it, abis) }
    val recommended = candidates.withIndex()
      .filter { it.value.compatible }
      .minWithOrNull(
        compareBy<IndexedValue<Candidate>>(
          // a debug build is what nobody means to install
          { it.value.debug },
          { it.value.variant.ordinal },
          { abiRank(it.value, abis) },
          // ties keep the release's order
          { it.index },
        ),
      )?.value?.pkg
    val remembered = matches?.let { match -> candidates.firstOrNull { it.compatible && match(it.pkg) }?.pkg }
    return PackageChoice(
      candidates = candidates,
      recommended = recommended,
      chosen = remembered ?: recommended,
      choiceLost = matches != null && remembered == null,
    )
  }

  // the device's own architecture, then a universal build (which has it too), then the ones it also runs
  private fun abiRank(
    candidate: Candidate,
    deviceAbis: List<Abi>,
  ): Int = when {
    candidate.abis.isEmpty() -> 1
    deviceAbis.firstOrNull() in candidate.abis -> 0
    else -> 1 + deviceAbis.indexOfFirst { it in candidate.abis }.let { if (it < 0) deviceAbis.size else it }
  }
}
