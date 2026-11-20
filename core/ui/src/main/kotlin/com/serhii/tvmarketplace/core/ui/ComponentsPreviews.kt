package com.serhii.tvmarketplace.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.serhii.tvmarketplace.core.ui.icons.AppIcons

private enum class Focused { Button, Row, Tile }

// one of each control, the focused one next to one at rest: the focus ring sits outside the edge
@Composable
private fun Controls(focused: Focused) {
  val button = remember { FocusRequester() }
  val row = remember { FocusRequester() }
  val tile = remember { FocusRequester() }
  AppTheme {
    Column(Modifier.padding(48.dp), verticalArrangement = Arrangement.spacedBy(32.dp)) {
      Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        PillButton(text = "Button", onClick = {}, leadingIcon = AppIcons.Inbox, modifier = Modifier.focusRequester(button))
        PillButton(text = "At rest", onClick = {})
      }
      ListRow(
        onClick = {},
        modifier = Modifier.width(500.dp).focusRequester(row),
        headline = { Text("Row") },
        supporting = { Text("Supporting text") },
      )
      ListRow(onClick = {}, modifier = Modifier.width(500.dp), headline = { Text("Row at rest") })
      Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        TileCard(onClick = {}, modifier = Modifier.width(240.dp).focusRequester(tile)) {
          Text("Tile", modifier = Modifier.height(80.dp).padding(18.dp))
        }
        TileCard(onClick = {}, modifier = Modifier.width(240.dp)) {
          Text("Tile at rest", modifier = Modifier.height(80.dp).padding(18.dp))
        }
      }
    }
  }
  LaunchedEffect(focused) {
    when (focused) {
      Focused.Button -> button
      Focused.Row -> row
      Focused.Tile -> tile
    }.requestFocus()
  }
}

@PreviewTv
@Composable
private fun ButtonFocusPreview() {
  Controls(Focused.Button)
}

@PreviewTv
@Composable
private fun RowFocusPreview() {
  Controls(Focused.Row)
}

@PreviewTv
@Composable
private fun TileFocusPreview() {
  Controls(Focused.Tile)
}
