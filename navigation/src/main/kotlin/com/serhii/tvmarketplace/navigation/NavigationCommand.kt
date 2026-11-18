package com.serhii.tvmarketplace.navigation

sealed interface NavigationCommand {
  data class GoTo(val destination: Destination) : NavigationCommand
  data object GoBack : NavigationCommand
}
