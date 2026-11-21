package com.serhii.tvmarketplace.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.serhii.tvmarketplace.core.domain.AppStatus
import kotlin.math.roundToInt
import com.serhii.tvmarketplace.core.strings.R as StringsR

@Composable
fun statusText(status: AppStatus): String = when (status) {
  AppStatus.NotInstalled -> stringResource(StringsR.string.status_not_installed)
  is AppStatus.UpToDate -> status.installedVersion?.let { stringResource(StringsR.string.status_installed, it) }
    ?: stringResource(StringsR.string.status_installed_unknown)
  is AppStatus.UpdateAvailable -> status.installedVersion?.let { stringResource(StringsR.string.status_update, it, status.latestVersion) }
    ?: stringResource(StringsR.string.status_update_unknown, status.latestVersion)
  is AppStatus.Downloading -> status.fraction?.let { stringResource(StringsR.string.status_downloading, (it * 100).roundToInt()) }
    ?: stringResource(StringsR.string.status_downloading_unknown)
  AppStatus.ReadyToInstall -> stringResource(StringsR.string.status_ready)
  AppStatus.Installing -> stringResource(StringsR.string.status_installing)
  is AppStatus.NoPackage -> stringResource(StringsR.string.status_no_package)
  AppStatus.NoRelease -> stringResource(StringsR.string.details_no_release)
  AppStatus.Unavailable -> stringResource(StringsR.string.status_unavailable)
}

// one line under an app's name; an update stands out, everything else is quiet
@Composable
fun StatusLine(
  status: AppStatus,
  modifier: Modifier = Modifier,
  text: String = statusText(status),
) {
  Text(
    text,
    modifier = modifier,
    style = MaterialTheme.typography.bodyMedium,
    color = if (status is AppStatus.UpdateAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
  )
}

// a thin progress bar; without a fraction only the track shows, until the size is known
@Composable
fun ProgressLine(
  fraction: Float?,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier
      .fillMaxWidth()
      .height(4.dp)
      .clip(CircleShape)
      .background(MaterialTheme.colorScheme.surfaceVariant),
  ) {
    if (fraction != null) {
      Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0F, 1F)).background(MaterialTheme.colorScheme.primary))
    }
  }
}

@Preview(widthDp = 320, heightDp = 200, showBackground = true, backgroundColor = 0xFF101318)
@Composable
private fun StatusPreview() {
  AppTheme {
    Column(Modifier.padding(16.dp).width(280.dp)) {
      StatusLine(AppStatus.UpdateAvailable("1.2.0", "1.3.0"))
      StatusLine(AppStatus.UpToDate("1.2.0"))
      StatusLine(AppStatus.NotInstalled)
      StatusLine(AppStatus.Downloading(0.6F))
      ProgressLine(0.6F, Modifier.padding(top = 4.dp))
      StatusLine(AppStatus.NoPackage(null), Modifier.padding(top = 8.dp))
    }
  }
}
