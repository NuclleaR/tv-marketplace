package com.serhii.tvmarketplace.features.home

import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import app.cash.turbine.test
import com.serhii.tvmarketplace.core.data.Item
import com.serhii.tvmarketplace.core.data.ItemRepository
import com.serhii.tvmarketplace.navigation.Destination
import com.serhii.tvmarketplace.navigation.Navigator
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeViewModelTest {

  private val repository = mockk<ItemRepository> {
    every { items() } returns flowOf(listOf(Item(id = "1", title = "First", description = "One")))
  }
  private val navigator = mockk<Navigator>(relaxUnitFun = true)
  private val viewModel = HomeViewModel(repository, navigator)

  @Test
  fun `shows the items of the repository`() = runTest {
    backgroundScope.launchMolecule(RecompositionMode.Immediate) {
      viewModel.viewState()
    }.test {
      assertEquals(HomeViewState.Loading, awaitItem())
      assertEquals(HomeViewState.Content(listOf(HomeViewState.Row(id = "1", title = "First", description = "One"))), awaitItem())
    }
  }

  @Test
  fun `opens the detail of a clicked item`() {
    viewModel.onItemClick("1")
    verify { navigator.goTo(Destination.Detail(itemId = "1")) }
  }
}
