package com.serhii.tvmarketplace.features.appdetails

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.serhii.tvmarketplace.core.domain.AppStatus
import com.serhii.tvmarketplace.core.domain.FailureReason
import com.serhii.tvmarketplace.core.ui.AppTheme
import com.serhii.tvmarketplace.features.appdetails.AppDetailsViewState.Content
import com.serhii.tvmarketplace.features.appdetails.AppDetailsViewState.PackageRow
import com.serhii.tvmarketplace.features.appdetails.AppDetailsViewState.PackageTag
import com.serhii.tvmarketplace.features.appdetails.AppDetailsViewState.PrimaryAction
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

private fun SemanticsNodeInteraction.assertInFocus() {
  val _ = assert(isFocused() or hasAnyAncestor(isFocused()))
}

@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
@RunWith(AndroidJUnit4::class)
class AppDetailsViewTest {

  @get:Rule
  val compose = createComposeRule()

  private val content = Content(
    name = "SmartTube",
    ownerAndRepo = "yuliskov/SmartTube",
    iconUrl = null,
    description = "Ad-free YouTube for Android TV",
    status = AppStatus.NotInstalled,
    primary = PrimaryAction.INSTALL,
    failure = null,
    latest = AppDetailsViewState.Latest("v1.3", Instant.parse("2026-11-10T10:00:00Z")),
    checking = false,
    packages = listOf(
      PackageRow(1, "SmartTube_1.3_universal.apk", 30_000_000, null, compatible = true),
      PackageRow(2, "SmartTube_1.3_arm64-v8a.apk", 18_000_000, PackageTag.RECOMMENDED, compatible = true),
      PackageRow(3, "SmartTube_1.3_x86.apk", 20_000_000, PackageTag.NOT_FOR_DEVICE, compatible = false),
    ),
    choiceLost = false,
    userChoice = false,
    notesHtml = "<p>Faster <a href=\"https://example.com\">search</a></p>",
    needsPermission = false,
    installed = false,
    includePrereleases = false,
    dialog = null,
  )

  private val events = mutableListOf<String>()

  private fun show(state: () -> AppDetailsViewState) {
    compose.setContent {
      AppTheme {
        AppDetailsView(
          viewState = state(),
          onPrimary = { events += "primary" },
          onPackage = { events += "package $it" },
          onReleaseNotes = { events += "notes" },
          onSettings = { events += "settings" },
          onRemove = { events += "remove" },
          onOpenPermissionSettings = { events += "permission" },
          onPermissionLater = { events += "later" },
          onUseAutomaticChoice = { events += "automatic" },
          onTogglePrereleases = { events += "prereleases" },
          onConfirmRemove = { events += "confirm $it" },
          onDismissDialog = { events += "dismiss" },
        )
      }
    }
  }

  // a dialog is a window of its own: keys go to the topmost one
  private fun press(key: Key) {
    val _ = compose.onAllNodes(isRoot()).onLast().performKeyInput { pressKey(key) }
    compose.waitForIdle()
  }

  @Test
  fun `the primary action is focused first, the other buttons are beside it`() {
    show { content }
    compose.onNodeWithText("Install").assertIsFocused()
    press(Key.DirectionCenter)
    press(Key.DirectionRight)
    compose.onNodeWithText("Release notes").assertIsFocused()
    press(Key.DirectionRight)
    press(Key.DirectionRight)
    compose.onNodeWithText("Remove").assertIsFocused()
    press(Key.DirectionCenter)
    assertEquals(listOf("primary", "remove"), events)
  }

  @Test
  fun `down from the buttons lands on the recommended package`() {
    show { content }
    press(Key.DirectionDown)
    compose.onNodeWithText("SmartTube_1.3_arm64-v8a.apk").assertInFocus()
    compose.onNodeWithText("Recommended").assertExists()
    compose.onNodeWithText("Not for this device").assertExists()
    press(Key.DirectionCenter)
    press(Key.DirectionUp)
    compose.onNodeWithText("SmartTube_1.3_universal.apk").assertInFocus()
    press(Key.DirectionCenter)
    assertEquals(listOf("package 2", "package 1"), events)
  }

  @Test
  fun `notes show without their links`() {
    show { content }
    compose.onNodeWithText("Faster search").assertExists()
  }

  @Test
  fun `a failure keeps retry focused and says what happened`() {
    show { content.copy(primary = PrimaryAction.RETRY, failure = FailureReason.SignatureConflict) }
    compose.onNodeWithText("Retry").assertIsFocused()
    compose.onNodeWithText("signed differently", substring = true).assertExists()
  }

  @Test
  fun `without the permission, open settings is focused`() {
    show { content.copy(status = AppStatus.ReadyToInstall, needsPermission = true) }
    compose.onNodeWithText("Open settings").assertIsFocused()
    press(Key.DirectionCenter)
    press(Key.DirectionRight)
    press(Key.DirectionCenter)
    assertEquals(listOf("permission", "later"), events)
  }

  @Test
  fun `remove asks with the safe choice focused`() {
    show { content.copy(installed = true, dialog = AppDetailsViewState.Dialog.REMOVE) }
    compose.onNodeWithText("Keep on TV").assertIsFocused()
    press(Key.DirectionRight)
    compose.onNodeWithText("Uninstall too").assertIsFocused()
    press(Key.DirectionCenter)
    assertEquals(listOf("confirm true"), events)
  }

  @Test
  fun `remove of an app that isn't installed only offers removing it`() {
    show { content.copy(dialog = AppDetailsViewState.Dialog.REMOVE) }
    compose.onNodeWithText("Keep on TV").assertDoesNotExist()
    press(Key.DirectionCenter)
    assertEquals(listOf("confirm false"), events)
  }

  @Test
  fun `settings switch pre-releases, and closing returns the focus to settings`() {
    var state by mutableStateOf(content)
    show { state }
    press(Key.DirectionRight)
    press(Key.DirectionRight)
    compose.onNodeWithText("Settings").assertIsFocused()
    press(Key.DirectionCenter)
    state = state.copy(dialog = AppDetailsViewState.Dialog.SETTINGS)
    compose.waitForIdle()
    compose.onNodeWithText("Include pre-releases").assertInFocus()
    press(Key.DirectionCenter)
    assertEquals(listOf("settings", "prereleases"), events)
    state = state.copy(dialog = null)
    compose.waitForIdle()
    compose.onNodeWithText("Settings").assertIsFocused()
  }
}
