package com.serhii.tvmarketplace.core.ui

import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.serhii.tvmarketplace.core.domain.FailureReason
import java.util.Date
import com.serhii.tvmarketplace.core.strings.R as StringsR

@Composable
fun failureText(reason: FailureReason): String {
  val context = LocalContext.current
  return when (reason) {
    FailureReason.Network -> stringResource(StringsR.string.failure_network)
    is FailureReason.RateLimited ->
      stringResource(StringsR.string.failure_rate_limited, DateFormat.getTimeFormat(context).format(Date.from(reason.resumeAt)))
    is FailureReason.NoSpace -> if (reason.neededBytes > 0) {
      stringResource(StringsR.string.failure_no_space, Formatter.formatShortFileSize(context, reason.neededBytes))
    } else {
      stringResource(StringsR.string.failure_system)
    }
    FailureReason.Corrupt -> stringResource(StringsR.string.failure_corrupt)
    FailureReason.DifferentApp -> stringResource(StringsR.string.failure_different_app)
    FailureReason.SignatureConflict -> stringResource(StringsR.string.failure_signature)
    FailureReason.Incompatible -> stringResource(StringsR.string.failure_incompatible)
    FailureReason.CancelledByUser -> stringResource(StringsR.string.failure_cancelled)
    is FailureReason.System -> stringResource(StringsR.string.failure_system)
  }
}
