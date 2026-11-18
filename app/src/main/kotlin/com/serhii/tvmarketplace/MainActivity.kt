package com.serhii.tvmarketplace

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.serhii.tvmarketplace.core.common.rootGraphAs
import com.serhii.tvmarketplace.core.ui.AppTheme
import com.serhii.tvmarketplace.navigation.Destination
import com.serhii.tvmarketplace.navigation.NavEntryResolver
import com.serhii.tvmarketplace.navigation.NavigationCommand
import com.serhii.tvmarketplace.navigation.Navigator
import com.serhii.tvmarketplace.navigation.startDestinations
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo

@ContributesTo(AppScope::class)
interface MainActivityGraph {
  val navigator: Navigator
  val navEntryResolver: NavEntryResolver
}

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val graph = rootGraphAs<MainActivityGraph>()
    setContent {
      val start = remember { startDestinations(this, intent).toTypedArray() }

      @Suppress("UNCHECKED_CAST")
      val backStack = rememberNavBackStack(*start) as MutableList<Destination>
      AppTheme {
        NavDisplay(
          backStack = backStack,
          onBack = {
            if (backStack.size > 1) backStack.removeLastOrNull()
          },
          entryProvider = { key -> graph.navEntryResolver.create(key) },
        )
      }
      LaunchedEffect(graph.navigator) {
        graph.navigator.navigationCommands.collect { command ->
          when (command) {
            is NavigationCommand.GoTo -> if (backStack.lastOrNull() != command.destination) backStack += command.destination
            NavigationCommand.GoBack -> if (backStack.size > 1) backStack.removeLastOrNull()
          }
        }
      }
    }
  }
}
