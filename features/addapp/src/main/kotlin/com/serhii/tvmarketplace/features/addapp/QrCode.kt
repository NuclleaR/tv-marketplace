package com.serhii.tvmarketplace.features.addapp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

private const val QUIET_ZONE = 4

// always dark on light, whatever the theme: phone cameras read inverted codes badly
@Composable
fun QrCode(
  content: String,
  contentDescription: String,
  size: Dp,
  modifier: Modifier = Modifier,
) {
  val matrix = remember(content) {
    QRCodeWriter().encode(
      content,
      BarcodeFormat.QR_CODE,
      0,
      0,
      mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
    )
  }
  Canvas(modifier.size(size).semantics { this.contentDescription = contentDescription }) {
    val modules = matrix.width + QUIET_ZONE * 2
    val cell = this.size.minDimension / modules
    drawRect(Color.White)
    for (y in 0 until matrix.height) {
      for (x in 0 until matrix.width) {
        if (matrix[x, y]) {
          drawRect(Color.Black, topLeft = Offset((x + QUIET_ZONE) * cell, (y + QUIET_ZONE) * cell), size = Size(cell, cell))
        }
      }
    }
  }
}
