package com.serhii.tvmarketplace.core.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.darkColorScheme

// TV is dark by convention: a light screen in a dark living room is glaring
private val colors = darkColorScheme(
  primary = Color(0xFFA8C8FF),
  onPrimary = Color(0xFF05305F),
  primaryContainer = Color(0xFF224777),
  onPrimaryContainer = Color(0xFFD5E3FF),
  secondaryContainer = Color(0xFF3E4759),
  onSecondaryContainer = Color(0xFFDAE2F9),
  tertiaryContainer = Color(0xFF573E5C),
  onTertiaryContainer = Color(0xFFFAD8FD),
  background = Color(0xFF111318),
  onBackground = Color(0xFFE2E2E9),
  surface = Color(0xFF111318),
  onSurface = Color(0xFFE2E2E9),
  surfaceVariant = Color(0xFF282A2F),
  onSurfaceVariant = Color(0xFFC4C6D0),
  border = Color(0xFF8E9099),
  borderVariant = Color(0xFF44474E),
  error = Color(0xFFFFB4AB),
  onError = Color(0xFF690005),
  errorContainer = Color(0xFF93000A),
  onErrorContainer = Color(0xFFFFDAD6),
)

// the TV color scheme has no surface container levels: cards rest on low and lift to high when focused
@Immutable
data class SurfaceLevels(
  val low: Color,
  val mid: Color,
  val high: Color,
)

private val surfaceLevels = SurfaceLevels(low = Color(0xFF1D2024), mid = Color(0xFF282A2F), high = Color(0xFF33353A))

object AppTheme {
  val surfaces: SurfaceLevels = surfaceLevels
}

@Composable
fun AppTheme(
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  MaterialTheme(colorScheme = colors) {
    Surface(modifier = modifier.fillMaxSize(), shape = RectangleShape) {
      content()
    }
  }
}
