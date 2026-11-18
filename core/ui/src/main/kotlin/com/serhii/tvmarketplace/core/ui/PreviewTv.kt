package com.serhii.tvmarketplace.core.ui

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

// a 1080p TV screen; night mode because the app is always dark, so no light variant is rendered
@Preview(widthDp = 960, heightDp = 540, uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_TELEVISION)
annotation class PreviewTv
