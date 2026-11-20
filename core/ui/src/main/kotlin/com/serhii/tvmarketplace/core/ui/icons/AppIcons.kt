package com.serhii.tvmarketplace.core.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

// outline icons as path data on a 24 grid (Lucide, ISC license), so the app needs no icon library; add more the same way
object AppIcons {
  val Add by stroke("Add", "M12 5v14M5 12h14", width = 2.25F)
  val Alert by stroke("Alert", "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20zM12 8v4M12 16h.01")
  val ArrowForward by stroke("ArrowForward", "M5 12h14M12 5l7 7-7 7", autoMirror = true)
  val Check by stroke("Check", "M20 6 9 17l-5-5", width = 2.5F)
  val Inbox by stroke(
    "Inbox",
    "M22 12h-6l-2 3h-4l-2-3H2M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z",
  )
  val Info by stroke("Info", "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20zM12 16v-4M12 8h.01")
  val Keyboard by stroke(
    "Keyboard",
    "M4 4h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2zM6 8h.01M10 8h.01M14 8h.01M18 8h.01M6 12h.01M10 12h.01M14 12h.01M18 12h.01M7 16h10",
  )
  val Phone by stroke("Phone", "M7 2h10a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2zM12 18h.01")
  val Refresh by stroke(
    "Refresh",
    "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8M21 3v5h-5M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16M8 16H3v5",
  )
  val Search by stroke("Search", "M11 3a8 8 0 1 0 0 16a8 8 0 1 0 0-16zM21 21l-4.3-4.3")
  val Settings by stroke("Settings", "M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3M1 14h6M9 8h6M17 16h6")
  val Tag by stroke(
    "Tag",
    "M12.586 2.586A2 2 0 0 0 11.172 2H4a2 2 0 0 0-2 2v7.172a2 2 0 0 0 .586 1.414l8.704 8.704a2.426 2.426 0 0 0 3.42 0l6.58-6.58a2.426 2.426 0 0 0 0-3.42zM7.5 7.5h.01",
  )
  val Trash by stroke("Trash", "M3 6h18M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2")
  val Tv by stroke("Tv", "M4 7h16a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2zM17 2l-5 5-5-5")
  val Wifi by stroke("Wifi", "M5 13a10 10 0 0 1 14 0M8.5 16.5a5 5 0 0 1 7 0M2 8.82a15 15 0 0 1 20 0M12 20h.01")
  val WifiOff by stroke("WifiOff", "M5 13a10 10 0 0 1 14 0M8.5 16.5a5 5 0 0 1 7 0M2 8.82a15 15 0 0 1 20 0M12 20h.01M2 2l20 20")

  private fun stroke(
    name: String,
    pathData: String,
    width: Float = 2F,
    autoMirror: Boolean = false,
  ): Lazy<ImageVector> = lazy {
    ImageVector.Builder(
      name = name,
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24F,
      viewportHeight = 24F,
      autoMirror = autoMirror,
    ).addPath(
      pathData = PathParser().parsePathString(pathData).toNodes(),
      stroke = SolidColor(Color.Black),
      strokeLineWidth = width,
      strokeLineCap = StrokeCap.Round,
      strokeLineJoin = StrokeJoin.Round,
    ).build()
  }
}
