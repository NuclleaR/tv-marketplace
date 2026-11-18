package com.serhii.tvmarketplace.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage

private const val FOCUSED_SCALE = 1.1F

// the focus ring sits 2dp outside the element, so it reads on any container color
@Composable
private fun focusBorder(shape: Shape) = Border(
  border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface),
  inset = (-4).dp,
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
    colors = ButtonDefaults.colors(
      containerColor = AppTheme.surfaces.mid,
      contentColor = colors.onSurface,
      focusedContainerColor = colors.primary,
      focusedContentColor = colors.onPrimary,
      pressedContainerColor = colors.primary,
      pressedContentColor = colors.onPrimary,
    ),
    border = ButtonDefaults.border(focusedBorder = focusBorder(CircleShape), pressedBorder = focusBorder(CircleShape)),
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

// a focusable card for list rows and tiles; wide rows pass a smaller scale so they don't overlap their neighbours
@Composable
fun FocusCard(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  onLongClick: (() -> Unit)? = null,
  focusedScale: Float = FOCUSED_SCALE,
  cornerRadius: Dp = 16.dp,
  content: @Composable BoxScope.() -> Unit,
) {
  val shape = RoundedCornerShape(cornerRadius)
  Surface(
    onClick = onClick,
    onLongClick = onLongClick,
    modifier = modifier,
    shape = ClickableSurfaceDefaults.shape(shape),
    scale = ClickableSurfaceDefaults.scale(focusedScale = focusedScale),
    colors = ClickableSurfaceDefaults.colors(
      containerColor = AppTheme.surfaces.low,
      contentColor = MaterialTheme.colorScheme.onSurface,
      focusedContainerColor = AppTheme.surfaces.high,
      focusedContentColor = MaterialTheme.colorScheme.onSurface,
      pressedContainerColor = AppTheme.surfaces.high,
    ),
    border = ClickableSurfaceDefaults.border(focusedBorder = focusBorder(shape)),
    content = content,
  )
}

// the app's own icon when it has one, the owner's avatar otherwise, or the first letter on a tonal tile
@Composable
fun AppIcon(
  name: String,
  iconUrl: String?,
  avatarUrl: String?,
  size: Dp,
  modifier: Modifier = Modifier,
) {
  val shape = RoundedCornerShape(size / 4)
  when {
    iconUrl != null -> AsyncImage(model = iconUrl, contentDescription = null, modifier = modifier.size(size).clip(shape))
    avatarUrl != null -> AsyncImage(model = avatarUrl, contentDescription = null, modifier = modifier.size(size).clip(shape))
    else -> LetterTile(name, size, shape, modifier)
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
