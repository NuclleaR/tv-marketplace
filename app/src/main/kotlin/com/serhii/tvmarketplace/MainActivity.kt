package com.serhii.tvmarketplace

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.serhii.tvmarketplace.core.common.AppLog
import com.serhii.tvmarketplace.core.common.rootGraphAs
import com.serhii.tvmarketplace.core.installer.Installer
import com.serhii.tvmarketplace.core.ui.AppTheme
import com.serhii.tvmarketplace.navigation.Destination
import com.serhii.tvmarketplace.navigation.NavEntryResolver
import com.serhii.tvmarketplace.navigation.NavigationCommand
import com.serhii.tvmarketplace.navigation.Navigator
import com.serhii.tvmarketplace.navigation.startDestinations
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

@ContributesTo(AppScope::class)
interface MainActivityGraph {
  val navigator: Navigator
  val navEntryResolver: NavEntryResolver
  val installer: Installer
}

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val graph = rootGraphAs<MainActivityGraph>()
    showSystemPrompts(graph.installer)
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

  // the system's install and uninstall prompts may only be started by a visible activity: whichever install is
  // waiting gets its prompt as soon as the app is on screen
  private fun showSystemPrompts(installer: Installer) {
    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        installer.pendingConfirmation.filterNotNull().collect { prompt ->
          try {
            startActivity(prompt.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
          } catch (e: ActivityNotFoundException) {
            AppLog.error("install_prompt_failed", e)
          }
          installer.confirmationShown(prompt.id)
        }
      }
    }
  }
}
