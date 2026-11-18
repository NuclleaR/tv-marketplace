package com.serhii.tvmarketplace.features.addapp.typeit

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.serhii.tvmarketplace.core.ui.AppTheme
import com.serhii.tvmarketplace.navigation.Destination
import com.serhii.tvmarketplace.navigation.Navigator
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class TypeItTest {

  @get:Rule
  val compose = createComposeRule()

  private val navigator = mockk<Navigator>(relaxUnitFun = true)
  private val viewModel = TypeItViewModel(navigator)

  @Test
  fun `shorthand continues to the preview`() {
    viewModel.onInputChange(" ImranR98/Obtainium ")
    viewModel.onContinue()
    verify { navigator.goTo(Destination.Preview(owner = "ImranR98", repo = "Obtainium")) }
  }

  @Test
  fun `a full link continues to the preview`() {
    viewModel.onInputChange("https://github.com/ImranR98/Obtainium/releases")
    viewModel.onContinue()
    verify { navigator.goTo(Destination.Preview(owner = "ImranR98", repo = "Obtainium")) }
  }

  @Test
  fun `invalid input shows an error and stays`() {
    viewModel.onInputChange("Obtainium")
    viewModel.onContinue()
    assertEquals(true, viewModel.state.invalid)
    verify(exactly = 0) { navigator.goTo(any()) }
    viewModel.onInputChange("Obtainium/")
    assertEquals(false, viewModel.state.invalid)
  }

  @Test
  fun `the field is focused first and keeps focus with an error`() {
    compose.setContent {
      AppTheme {
        TypeItView(viewState = TypeItViewState(input = "x", invalid = true), onInputChange = {}, onContinue = {})
      }
    }
    compose.onNodeWithTag(TYPE_IT_FIELD).assertIsFocused()
    compose.onNodeWithText("Enter owner/repo or a GitHub link.").assertExists()
  }

  @Test
  fun `continue submits`() {
    var submitted = 0
    compose.setContent {
      AppTheme { TypeItView(viewState = TypeItViewState(input = "a/b"), onInputChange = {}, onContinue = { submitted++ }) }
    }
    compose.onNodeWithText("Continue").performSemanticsAction(SemanticsActions.OnClick)
    assertEquals(1, submitted)
  }
}
