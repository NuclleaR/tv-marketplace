package com.serhii.tvmarketplace.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme

private val RING_WIDTH = 2.dp
private val RING_GAP = 2.dp

/**
 * Scales a focused element and draws a ring around it, outside its edge with a small gap, as the design asks.
 * Material's TV border draws inside the element, so the ring is drawn here instead; both live in one layer,
 * so the ring grows with the element. A null [cornerRadius] means a pill.
 */
@Composable
fun Modifier.focusRing(
  interactionSource: InteractionSource,
  focusedScale: Float,
  cornerRadius: Dp? = null,
): Modifier {
  val focused by interactionSource.collectIsFocusedAsState()
  val scale by animateFloatAsState(if (focused) focusedScale else 1F, label = "focusScale")
  val color = MaterialTheme.colorScheme.onSurface
  return graphicsLayer {
    scaleX = scale
    scaleY = scale
  }.drawWithContent {
    drawContent()
    if (focused) {
      val stroke = RING_WIDTH.toPx()
      // the stroke is centered on the path, so the path sits half a stroke further out than the gap
      val outset = RING_GAP.toPx() + stroke / 2
      val radius = (cornerRadius?.toPx() ?: (size.height / 2)) + outset
      drawRoundRect(
        color = color,
        topLeft = Offset(-outset, -outset),
        size = Size(size.width + outset * 2, size.height + outset * 2),
        cornerRadius = CornerRadius(radius),
        style = Stroke(stroke),
      )
    }
  }
}
