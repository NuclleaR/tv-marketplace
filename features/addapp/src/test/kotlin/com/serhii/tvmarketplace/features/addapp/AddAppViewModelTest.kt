package com.serhii.tvmarketplace.features.addapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import app.cash.turbine.test
import com.serhii.tvmarketplace.core.data.GitHubOwner
import com.serhii.tvmarketplace.core.data.LookupResult
import com.serhii.tvmarketplace.core.data.OwnerRepos
import com.serhii.tvmarketplace.core.domain.GitHubRepoRef
import com.serhii.tvmarketplace.core.linkserver.IncomingLinkEvents
import com.serhii.tvmarketplace.core.linkserver.LinkServer
import com.serhii.tvmarketplace.core.linkserver.ServerState
import com.serhii.tvmarketplace.core.linkserver.SessionManager
import com.serhii.tvmarketplace.core.testing.FakeGitHub
import com.serhii.tvmarketplace.core.testing.MutableTimeSource
import com.serhii.tvmarketplace.core.testing.repoSummary
import com.serhii.tvmarketplace.navigation.Destination
import com.serhii.tvmarketplace.navigation.Navigator
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class AddAppViewModelTest {

  private val time = MutableTimeSource()
  private val sessions = SessionManager(time)
  private val serverState = MutableStateFlow<ServerState>(ServerState.Running(ip = "192.168.1.42", port = 8765))
  private val server = mockk<LinkServer> { every { state } returns serverState }
  private val events = IncomingLinkEvents()
  private val navigator = mockk<Navigator>(relaxUnitFun = true)
  private val gitHub = FakeGitHub()
  private val viewModel = AddAppViewModel(sessions, server, events, navigator, gitHub)

  // the screen keeps the query and hands it over
  private var query by mutableStateOf("")

  @Test
  fun `shows the QR link, the address and the pin of a new session`() = runTest {
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState(query) }.test {
      val ready = assertIs<PhoneState.Ready>(awaitMatching { it.phone !is PhoneState.Loading }.phone)
      val session = checkNotNull(sessions.current.value)
      assertEquals("http://192.168.1.42:8765/s/${session.token}", ready.qrContent)
      assertEquals("http://192.168.1.42:8765", ready.address)
      assertEquals(session.pin.chunked(3).joinToString(" "), ready.pin)
    }
  }

  @Test
  fun `shows the network problems of the server`() = runTest {
    serverState.value = ServerState.NoNetwork
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState(query) }.test {
      assertEquals(PhoneState.NoNetwork, awaitMatching { it.phone != PhoneState.Loading }.phone)
      serverState.value = ServerState.Failed
      assertEquals(PhoneState.Failed, awaitItem().phone)
    }
  }

  @Test
  fun `rotates an expired session in place`() = runTest {
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState(query) }.test {
      val first = assertIs<PhoneState.Ready>(awaitMatching { it.phone !is PhoneState.Loading }.phone)
      time.now += Duration.ofMinutes(10)
      advanceTimeBy(SESSION_CHECK_INTERVAL_MS + 1)
      val second = assertIs<PhoneState.Ready>(expectMostRecentItem().phone)
      assertNotEquals(first.qrContent, second.qrContent)
    }
  }

  @Test
  fun `opens the preview of a link received while open, not of an older one`() = runTest {
    events.emit(linkId = 1, ref = GitHubRepoRef("old", "link"), senderLabel = "Phone")
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState(query) }
    runCurrent()
    verify(exactly = 0) { navigator.goTo(any()) }
    events.emit(linkId = 2, ref = GitHubRepoRef("new", "link"), senderLabel = "Phone")
    runCurrent()
    verify { navigator.goTo(Destination.Preview(linkId = 2)) }
  }

  @Test
  fun `leaving the screen ends the session`() = runTest {
    val job = launch { launchMolecule(RecompositionMode.Immediate) { viewModel.viewState(query) }.collect {} }
    runCurrent()
    assertNotEquals(null, sessions.current.value)
    job.cancel()
    runCurrent()
    assertNull(sessions.current.value)
  }

  @Test
  fun `hiding the app ends the session and ignores links until it is shown again`() = runTest {
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState(query) }
    runCurrent()
    viewModel.onHidden()
    assertNull(sessions.current.value)
    events.emit(linkId = 3, ref = GitHubRepoRef("a", "b"), senderLabel = "Phone")
    runCurrent()
    verify(exactly = 0) { navigator.goTo(any()) }
    viewModel.onVisible()
    assertNotEquals(null, sessions.current.value)
  }

  @Test
  fun `one letter asks for more, two search repositories by name and owners after a pause`() = runTest {
    gitHub.owners = { LookupResult.Found(listOf(GitHubOwner("NuclleaR", "https://a/1", organization = false))) }
    gitHub.namedRepos = { LookupResult.Found(listOf(repoSummary("someone", "nuke"), repoSummary("nu-org", "nu"))) }
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState(query) }.test {
      query += "n"
      assertEquals(SearchResults.KeepTyping, awaitMatching { it.results != SearchResults.None }.results)
      query += "u"
      runCurrent()
      assertEquals(emptyList(), gitHub.lookups)
      advanceTimeBy(SEARCH_DEBOUNCE_MS + 1)
      val matches = assertIs<SearchResults.Matches>(expectMostRecentItem().results)
      // the repository named exactly like the text first, the rest in GitHub's order
      assertEquals(listOf("nu-org/nu", "someone/nuke"), matches.repos.map { it.key })
      assertEquals(listOf("NuclleaR"), matches.owners.map { it.login })
      assertEquals(setOf("named:nu", "owners:nu"), gitHub.lookups.toSet())
    }
  }

  @Test
  fun `repositories found by name still show when the owner search fails`() = runTest {
    gitHub.owners = { LookupResult.RateLimited }
    gitHub.namedRepos = { LookupResult.Found(listOf(repoSummary("jellyfin", "jellyfin-androidtv"))) }
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState(query) }.test {
      query += "jellyfin"
      advanceTimeBy(SEARCH_DEBOUNCE_MS + 1)
      val matches = assertIs<SearchResults.Matches>(expectMostRecentItem().results)
      assertEquals(listOf("jellyfin/jellyfin-androidtv"), matches.repos.map { it.key })
      assertEquals(emptyList(), matches.owners)
    }
  }

  @Test
  fun `typing fast sends one search for the last text`() = runTest {
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState(query) }
    "nucl".forEach {
      query += it.toString()
      advanceTimeBy(SEARCH_DEBOUNCE_MS / 2)
    }
    advanceTimeBy(SEARCH_DEBOUNCE_MS + 1)
    assertEquals(setOf("named:nucl", "owners:nucl"), gitHub.lookups.toSet())
  }

  @Test
  fun `picking an owner lists their repositories and typing filters them, prefixes first`() = runTest {
    gitHub.repos = { owner ->
      LookupResult.Found(
        OwnerRepos(
          "NuclleaR",
          listOf(repoSummary("NuclleaR", "android-template"), repoSummary("NuclleaR", "tv-marketplace")),
          complete = true,
        ),
      ).also { assertEquals("NuclleaR", owner) }
    }
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState(query) }.test {
      query = "NuclleaR/"
      val listed = awaitMatching { it.results is SearchResults.Repos }
      assertEquals(listOf("android-template", "tv-marketplace"), assertIs<SearchResults.Repos>(listed.results).repos.map { it.name })
      query += "t"
      runCurrent()
      val filtered = assertIs<SearchResults.Repos>(expectMostRecentItem().results)
      assertEquals(listOf("tv-marketplace", "android-template"), filtered.repos.map { it.name })
      query += "v"
      runCurrent()
      assertEquals(listOf("tv-marketplace"), assertIs<SearchResults.Repos>(expectMostRecentItem().results).repos.map { it.name })
      query = query.dropLast(1)
      query = query.dropLast(1)
      query = query.dropLast(1)
      runCurrent()
      advanceTimeBy(SEARCH_DEBOUNCE_MS + 1)
      assertIs<SearchResults.NothingFound>(expectMostRecentItem().results)
    }
  }

  @Test
  fun `an owner with more repositories than listed is searched by name`() = runTest {
    gitHub.repos = { LookupResult.Found(OwnerRepos("google", listOf(repoSummary("google", "guava")), complete = false)) }
    gitHub.searchedRepos = { _, _ -> LookupResult.Found(listOf(repoSummary("google", "tv-samples"))) }
    "google/tv".forEach { query += it.toString() }
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState(query) }.test {
      advanceTimeBy(SEARCH_DEBOUNCE_MS + 1)
      val repos = assertIs<SearchResults.Repos>(expectMostRecentItem().results)
      assertEquals(listOf("tv-samples"), repos.repos.map { it.name })
      assertEquals(listOf("repos:google", "search:google/tv"), gitHub.lookups)
    }
  }

  @Test
  fun `a missing owner and GitHub's limits are told apart`() = runTest {
    gitHub.repos = { LookupResult.NotFound }
    gitHub.namedRepos = { LookupResult.RateLimited }
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState(query) }.test {
      "nobody/".forEach { query += it.toString() }
      assertEquals(SearchResults.OwnerNotFound("nobody"), awaitMatching { it.results is SearchResults.OwnerNotFound }.results)
      query = query.dropLast(1)
      advanceTimeBy(SEARCH_DEBOUNCE_MS + 1)
      assertEquals(SearchResults.RateLimited, expectMostRecentItem().results)
    }
  }

  @Test
  fun `picking a repository opens its preview`() {
    viewModel.onRepoClick(SearchResults.Repo("NuclleaR", "tv-marketplace", null))
    verify { navigator.goTo(Destination.Preview(owner = "NuclleaR", repo = "tv-marketplace")) }
  }
}
