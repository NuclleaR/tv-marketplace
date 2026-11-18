package com.serhii.tvmarketplace.features.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.serhii.tvmarketplace.core.ui.AppTheme
import com.serhii.tvmarketplace.core.strings.R as StringsR

// the remote's back button goes back through NavDisplay's onBack, so this screen has no back control of its own
@Composable
internal fun DetailView(
  viewState: DetailViewState,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 27.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    when (viewState) {
      DetailViewState.Loading -> Unit
      DetailViewState.Missing -> Text(stringResource(StringsR.string.detail_missing), style = MaterialTheme.typography.headlineMedium)
      is DetailViewState.Content -> {
        Text(viewState.title, style = MaterialTheme.typography.displaySmall)
        Text(viewState.description, style = MaterialTheme.typography.bodyLarge)
      }
    }
  }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun DetailViewPreview() {
  AppTheme {
    DetailView(viewState = DetailViewState.Content(title = "Item 1", description = "The description of item 1."))
  }
}
