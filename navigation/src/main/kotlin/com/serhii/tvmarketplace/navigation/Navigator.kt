package com.serhii.tvmarketplace.navigation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

@SingleIn(AppScope::class)
@Inject
class Navigator {

  private val commands = Channel<NavigationCommand>(Channel.UNLIMITED)
  val navigationCommands: Flow<NavigationCommand> = commands.receiveAsFlow()

  fun goTo(destination: Destination) {
    val _ = commands.trySend(NavigationCommand.GoTo(destination))
  }

  fun goBack() {
    val _ = commands.trySend(NavigationCommand.GoBack)
  }
}
