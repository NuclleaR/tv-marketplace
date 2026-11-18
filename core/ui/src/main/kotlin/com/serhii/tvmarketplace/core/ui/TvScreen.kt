package com.serhii.tvmarketplace.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// 48dp by 27dp keeps content inside the overscan-safe area of a TV
@Composable
fun TvScreen(
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  Column(modifier = modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 27.dp), content = content)
}
