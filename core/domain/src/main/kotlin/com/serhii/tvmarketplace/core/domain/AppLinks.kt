package com.serhii.tvmarketplace.core.domain

// the first app link in shared text, from any supported source
object AppLinks {
  fun extractFirst(text: String): AppSource? = GitHubLinkParser.extractFirst(text) ?: FDroidLinkParser.extractFirst(text)
}

// f-droid.org/packages/<package> links, with or without a language: f-droid.org/uk/packages/org.schabi.newpipe/
object FDroidLinkParser {

  // a Java package name: two or more dot-separated identifiers
  private const val PACKAGE = "[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+"
  private val link = Regex(
    """(?<![A-Za-z0-9.-])https?://(?:www\.)?f-droid\.org/(?:[A-Za-z_-]{2,10}/)?packages/($PACKAGE)(?![A-Za-z0-9_])""",
    RegexOption.IGNORE_CASE,
  )

  fun extractFirst(text: String): FDroidApp? = link.find(text)?.let { FDroidApp(it.groupValues[1]) }
}
