package com.serhii.tvmarketplace.core.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.darkColorScheme

// TV is dark by convention: a light screen in a dark living room is glaring
@Composable
fun AppTheme(
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  MaterialTheme(colorScheme = darkColorScheme()) {
    Surface(modifier = modifier.fillMaxSize(), shape = RectangleShape) {
      content()
    }
  }
}
