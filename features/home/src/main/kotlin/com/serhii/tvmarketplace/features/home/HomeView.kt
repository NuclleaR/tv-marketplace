package com.serhii.tvmarketplace.features.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.serhii.tvmarketplace.core.ui.AppTheme
import com.serhii.tvmarketplace.core.strings.R as StringsR

@Composable
internal fun HomeView(
  viewState: HomeViewState,
  onItemClick: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  // 48dp by 27dp keeps content inside the overscan-safe area of a TV
  Column(modifier = modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 27.dp)) {
    Text(stringResource(StringsR.string.home_title), style = MaterialTheme.typography.displaySmall)
    when (viewState) {
      HomeViewState.Loading -> Unit
      is HomeViewState.Content -> if (viewState.items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text(stringResource(StringsR.string.home_empty))
        }
      } else {
        LazyColumn(
          contentPadding = PaddingValues(vertical = 16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(viewState.items, key = { it.id }) { row ->
            ListItem(
              selected = false,
              onClick = { onItemClick(row.id) },
              headlineContent = { Text(row.title) },
              supportingContent = { Text(row.description) },
            )
          }
        }
      }
    }
  }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun HomeViewPreview() {
  AppTheme {
    HomeView(
      viewState = HomeViewState.Content(
        items = List(5) { HomeViewState.Row(id = "$it", title = "Item $it", description = "The description of item $it.") },
      ),
      onItemClick = {},
    )
  }
}
