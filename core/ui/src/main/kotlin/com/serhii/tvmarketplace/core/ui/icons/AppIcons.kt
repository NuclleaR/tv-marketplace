package com.serhii.tvmarketplace.core.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

// Material Symbols as path data, so the app needs no icon library; add more the same way
object AppIcons {

  val ArrowBack: ImageVector by lazy {
    icon(name = "ArrowBack", autoMirror = true, pathData = "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z")
  }

  private fun icon(
    name: String,
    autoMirror: Boolean,
    pathData: String,
  ): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24F,
    viewportHeight = 24F,
    autoMirror = autoMirror,
  ).addPath(pathData = PathParser().parsePathString(pathData).toNodes(), fill = SolidColor(Color.Black)).build()
}
