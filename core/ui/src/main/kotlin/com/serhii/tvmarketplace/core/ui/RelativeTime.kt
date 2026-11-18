package com.serhii.tvmarketplace.core.ui

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import java.time.Duration
import java.time.Instant
import com.serhii.tvmarketplace.core.strings.R as StringsR

// "just now" instead of "0 minutes ago" for the first minute
@Composable
fun relativeTime(
  time: Instant,
  now: Instant = Instant.now(),
): String = if (Duration.between(time, now) < Duration.ofMinutes(1)) {
  stringResource(StringsR.string.time_just_now)
} else {
  DateUtils.getRelativeTimeSpanString(time.toEpochMilli(), now.toEpochMilli(), DateUtils.MINUTE_IN_MILLIS).toString()
}
