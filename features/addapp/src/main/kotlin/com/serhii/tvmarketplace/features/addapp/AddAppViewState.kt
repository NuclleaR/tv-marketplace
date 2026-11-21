package com.serhii.tvmarketplace.features.addapp

data class AddAppViewState(
  val query: String = "",
  val phone: PhoneState = PhoneState.Loading,
  val results: SearchResults = SearchResults.None,
  // a new answer is on its way; the previous results stay on screen until it arrives
  val searching: Boolean = false,
)

// sending a link from the phone: shown while nothing is typed
sealed interface PhoneState {
  data object Loading : PhoneState
  data object NoNetwork : PhoneState
  data object Failed : PhoneState
  data class Ready(
    val qrContent: String,
    val address: String,
    // grouped for reading at a distance: "482 913"
    val pin: String,
  ) : PhoneState
}

sealed interface SearchResults {
  // nothing typed: the phone panel takes the place of results
  data object None : SearchResults
  data object KeepTyping : SearchResults

  // repositories named like the text, from anyone, then owners whose login matches it
  data class Matches(
    val repos: List<Repo>,
    val owners: List<Owner>,
  ) : SearchResults
  data class Repos(
    val owner: String,
    val repos: List<Repo>,
    // the owner has more repositories than were listed; typing a name searches all of them
    val partial: Boolean,
  ) : SearchResults
  data object NothingFound : SearchResults
  data class OwnerNotFound(val owner: String) : SearchResults
  data object RateLimited : SearchResults
  data object NetworkError : SearchResults

  data class Owner(
    val login: String,
    val avatarUrl: String?,
    val organization: Boolean,
  )

  data class Repo(
    val owner: String,
    val name: String,
    val description: String?,
  ) {
    val key: String get() = "$owner/$name"
  }
}
