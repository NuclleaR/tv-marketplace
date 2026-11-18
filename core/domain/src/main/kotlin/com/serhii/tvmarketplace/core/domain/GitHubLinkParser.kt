package com.serhii.tvmarketplace.core.domain

data class GitHubRepoRef(
  val owner: String,
  val repo: String,
) {
  val url: String get() = "https://github.com/$owner/$repo"
}

object GitHubLinkParser {

  // owner: 1..39 alphanumerics or single dashes, not starting with a dash; repo: 1..100 of [A-Za-z0-9._-]
  private const val OWNER = "[A-Za-z0-9][A-Za-z0-9-]{0,38}"
  private const val REPO = "[A-Za-z0-9._-]{1,100}"
  private val link = Regex(
    """(?<![A-Za-z0-9.-])(?:https?://)?(?:www\.)?github\.com/($OWNER)/($REPO)(?![A-Za-z0-9._-])""",
    RegexOption.IGNORE_CASE,
  )
  private val shorthand = Regex("""^($OWNER)/($REPO)$""")

  // first path segments that are GitHub pages, not users or organizations
  private val reservedOwners = setOf(
    "about", "apps", "collections", "codespaces", "contact", "customer-stories", "enterprise", "events", "explore",
    "features", "join", "login", "logout", "marketplace", "new", "notifications", "orgs", "organizations", "pricing",
    "pulls", "issues", "search", "security", "settings", "site", "sponsors", "team", "topics", "trending", "users",
  )

  // a link only counts with a scheme or "github.com/" in pasted text; bare host links are fine for user input
  fun extractFirst(text: String): GitHubRepoRef? = link.findAll(text)
    .filter { it.value.startsWith("http", ignoreCase = true) }
    .firstNotNullOfOrNull { match -> toRef(match.groupValues[1], match.groupValues[2]) }

  fun parseUserInput(input: String): GitHubRepoRef? {
    val trimmed = input.trim()
    if (trimmed.contains("github.com", ignoreCase = true)) {
      val match = link.find(trimmed) ?: return null
      return toRef(match.groupValues[1], match.groupValues[2])
    }
    val match = shorthand.find(trimmed) ?: return null
    return toRef(match.groupValues[1], match.groupValues[2])
  }

  private fun toRef(
    owner: String,
    rawRepo: String,
  ): GitHubRepoRef? {
    if (owner.endsWith("-") || owner.contains("--") || owner.lowercase() in reservedOwners) return null
    val repo = rawRepo.removeSuffix(".git").trimEnd('.')
    if (repo.isEmpty() || repo.all { it == '.' }) return null
    return GitHubRepoRef(owner = owner, repo = repo)
  }
}
