package com.serhii.tvmarketplace.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GitHubLinkParserTest {

  private val obtainium = GitHubRepoRef(owner = "ImranR98", repo = "Obtainium")

  @Test
  fun `extracts a plain repository link`() {
    assertEquals(obtainium, GitHubLinkParser.extractFirst("https://github.com/ImranR98/Obtainium"))
  }

  @Test
  fun `extracts the first link from surrounding text`() {
    val text = "look at this https://github.com/ImranR98/Obtainium and https://github.com/other/repo"
    assertEquals(obtainium, GitHubLinkParser.extractFirst(text))
  }

  @Test
  fun `accepts www, http, releases, tags, git suffix, query and fragment`() {
    listOf(
      "http://github.com/ImranR98/Obtainium",
      "https://www.github.com/ImranR98/Obtainium",
      "https://github.com/ImranR98/Obtainium/",
      "https://github.com/ImranR98/Obtainium/releases",
      "https://github.com/ImranR98/Obtainium/releases/tag/v1.2.3",
      "https://github.com/ImranR98/Obtainium.git",
      "https://github.com/ImranR98/Obtainium?tab=readme",
      "https://github.com/ImranR98/Obtainium#readme",
      "(https://github.com/ImranR98/Obtainium).",
    ).forEach { link ->
      assertEquals(obtainium, GitHubLinkParser.extractFirst(link), link)
    }
  }

  @Test
  fun `normalizes to the canonical repository url`() {
    assertEquals("https://github.com/ImranR98/Obtainium", obtainium.url)
  }

  @Test
  fun `keeps dots, dashes and underscores in repository names`() {
    assertEquals(
      GitHubRepoRef(owner = "some-org", repo = "my_app.android"),
      GitHubLinkParser.extractFirst("https://github.com/some-org/my_app.android"),
    )
  }

  @Test
  fun `rejects reserved paths and other hosts`() {
    listOf(
      "https://github.com/orgs/foo",
      "https://github.com/settings/profile",
      "https://github.com/topics/android",
      "https://gitlab.com/owner/repo",
      "https://notgithub.com/owner/repo",
      "https://github.com/ImranR98",
      "just some text",
      "",
    ).forEach { text ->
      assertNull(GitHubLinkParser.extractFirst(text), text)
    }
  }

  @Test
  fun `skips a reserved link and takes the next valid one`() {
    assertEquals(obtainium, GitHubLinkParser.extractFirst("https://github.com/topics/x https://github.com/ImranR98/Obtainium"))
  }

  @Test
  fun `enforces owner and repository limits`() {
    assertNull(GitHubLinkParser.extractFirst("https://github.com/${"a".repeat(40)}/repo"))
    assertNull(GitHubLinkParser.extractFirst("https://github.com/-owner/repo"))
    assertNull(GitHubLinkParser.extractFirst("https://github.com/owner/${"r".repeat(101)}"))
    assertNull(GitHubLinkParser.extractFirst("https://github.com/owner/.."))
  }

  @Test
  fun `user input accepts owner slash repo shorthand`() {
    assertEquals(obtainium, GitHubLinkParser.parseUserInput("  ImranR98/Obtainium "))
  }

  @Test
  fun `user input accepts a full link`() {
    assertEquals(obtainium, GitHubLinkParser.parseUserInput("github.com/ImranR98/Obtainium/releases"))
    assertEquals(obtainium, GitHubLinkParser.parseUserInput("https://github.com/ImranR98/Obtainium"))
  }

  @Test
  fun `user input rejects anything else`() {
    listOf("", "Obtainium", "a/b/c", "orgs/foo", "https://gitlab.com/a/b", "owner / repo").forEach { input ->
      assertNull(GitHubLinkParser.parseUserInput(input), input)
    }
  }
}
