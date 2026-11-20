package com.serhii.tvmarketplace.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage

private const val FOCUSED_SCALE = 1.1F

// wide rows grow less than buttons and tiles, or they would cover their neighbours
private const val ROW_FOCUSED_SCALE = 1.03F

// the focus ring of every control: 2dp wide, 2dp outside the edge. Border's inset moves the stroke's center out
// of the edge, so the ring's inner side sits a gap away from it
private val RING_WIDTH = 2.dp
private val RING_GAP = 2.dp

@Composable
private fun ring(shape: Shape) = Border(
  border = BorderStroke(RING_WIDTH, MaterialTheme.colorScheme.onSurface),
  inset = RING_GAP + RING_WIDTH / 2,
  shape = shape,
)

// every button is quiet at rest and takes the primary color when focused: the focus is always the loudest thing
@Composable
fun PillButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  leadingIcon: ImageVector? = null,
  trailingIcon: ImageVector? = null,
  trailing: (@Composable () -> Unit)? = null,
) {
  val colors = MaterialTheme.colorScheme
  Button(
    onClick = onClick,
    modifier = modifier.height(40.dp),
    shape = ButtonDefaults.shape(CircleShape),
    scale = ButtonDefaults.scale(focusedScale = FOCUSED_SCALE),
    border = ButtonDefaults.border(focusedBorder = ring(CircleShape), pressedBorder = ring(CircleShape)),
    colors = ButtonDefaults.colors(
      containerColor = AppTheme.surfaces.mid,
      contentColor = colors.onSurface,
      focusedContainerColor = colors.primary,
      focusedContentColor = colors.onPrimary,
      pressedContainerColor = colors.primary,
      pressedContentColor = colors.onPrimary,
    ),
    contentPadding = PaddingValues(start = if (leadingIcon != null) 18.dp else 22.dp, end = if (trailingIcon != null) 18.dp else 22.dp),
  ) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
      leadingIcon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)) }
      Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
      trailingIcon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)) }
      trailing?.invoke()
    }
  }
}

// a row of a list: Material's TV list item with the app's surfaces and focus ring
@Composable
fun ListRow(
  onClick: () -> Unit,
  headline: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  onLongClick: (() -> Unit)? = null,
  supporting: (@Composable () -> Unit)? = null,
  leading: (@Composable BoxScope.() -> Unit)? = null,
  trailing: (@Composable () -> Unit)? = null,
  interactionSource: MutableInteractionSource? = null,
) {
  val shape = RoundedCornerShape(14.dp)
  ListItem(
    selected = false,
    onClick = onClick,
    onLongClick = onLongClick,
    headlineContent = headline,
    supportingContent = supporting,
    leadingContent = leading,
    trailingContent = trailing,
    modifier = modifier,
    shape = ListItemDefaults.shape(shape),
    scale = ListItemDefaults.scale(focusedScale = ROW_FOCUSED_SCALE),
    border = ListItemDefaults.border(focusedBorder = ring(shape), pressedBorder = ring(shape)),
    glow = ListItemDefaults.glow(),
    colors = ListItemDefaults.colors(
      containerColor = AppTheme.surfaces.low,
      contentColor = MaterialTheme.colorScheme.onSurface,
      focusedContainerColor = AppTheme.surfaces.high,
      focusedContentColor = MaterialTheme.colorScheme.onSurface,
      pressedContainerColor = AppTheme.surfaces.high,
      pressedContentColor = MaterialTheme.colorScheme.onSurface,
    ),
    interactionSource = interactionSource,
  )
}

// a tile of a grid: Material's TV card with the app's surfaces and focus ring
@Composable
fun TileCard(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  val shape = RoundedCornerShape(16.dp)
  Card(
    onClick = onClick,
    modifier = modifier,
    shape = CardDefaults.shape(shape),
    scale = CardDefaults.scale(focusedScale = FOCUSED_SCALE),
    border = CardDefaults.border(focusedBorder = ring(shape), pressedBorder = ring(shape)),
    glow = CardDefaults.glow(),
    colors = CardDefaults.colors(
      containerColor = AppTheme.surfaces.low,
      contentColor = MaterialTheme.colorScheme.onSurface,
      focusedContainerColor = AppTheme.surfaces.high,
      focusedContentColor = MaterialTheme.colorScheme.onSurface,
      pressedContainerColor = AppTheme.surfaces.high,
      pressedContentColor = MaterialTheme.colorScheme.onSurface,
    ),
    content = content,
  )
}

// the app's own icon when it has one, otherwise its first letter on a tonal tile: never a person's avatar,
// which reads as a portrait rather than an app
@Composable
fun AppIcon(
  name: String,
  iconUrl: String?,
  size: Dp,
  modifier: Modifier = Modifier,
) {
  val shape = RoundedCornerShape(size / 4)
  if (iconUrl != null) {
    AsyncImage(model = iconUrl, contentDescription = null, modifier = modifier.size(size).clip(shape))
  } else {
    LetterTile(name, size, shape, modifier)
  }
}

@Composable
private fun LetterTile(
  name: String,
  size: Dp,
  shape: Shape,
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  // the same name always gets the same color
  val (container, content) = when (Math.floorMod(name.hashCode(), 3)) {
    0 -> colors.primaryContainer to colors.onPrimaryContainer
    1 -> colors.secondaryContainer to colors.onSecondaryContainer
    else -> colors.tertiaryContainer to colors.onTertiaryContainer
  }
  Surface(
    modifier = modifier.size(size),
    shape = shape,
    colors = androidx.tv.material3.SurfaceDefaults.colors(containerColor = container, contentColor = content),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Text(
        name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?",
        style = MaterialTheme.typography.displaySmall.copy(fontSize = MaterialTheme.typography.displaySmall.fontSize * (size / 64.dp)),
        fontWeight = FontWeight.Medium,
      )
    }
  }
}

@Composable
fun ScreenTitle(
  text: String,
  modifier: Modifier = Modifier,
) {
  Text(text, modifier = modifier, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
}
