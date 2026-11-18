package com.serhii.tvmarketplace.companion

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.serhii.tvmarketplace.core.domain.GitHubLinkParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// no UI of its own: sends the shared text and says how it went
class ShareActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
    if (GitHubLinkParser.extractFirst(text) == null) {
      finishWith(R.string.companion_unsupported)
      return
    }
    lifecycleScope.launch {
      val store = Services.pairing(this@ShareActivity)
      val paired = store.pairedTv.first()
      if (paired == null) {
        startActivity(Intent(this@ShareActivity, MainActivity::class.java))
        finishWith(R.string.companion_not_paired)
        return@launch
      }
      val tv = Services.discovery(this@ShareActivity).find(paired.id)
      val result = if (tv == null) SendResult.Unreachable else Services.client.send(tv, paired.deviceToken, text)
      if (result == SendResult.Revoked) store.clear()
      finishWith(
        when (result) {
          is SendResult.Sent -> if (result.duplicate) R.string.companion_sent_duplicate else R.string.companion_sent
          SendResult.Unsupported -> R.string.companion_unsupported
          SendResult.Revoked -> R.string.companion_revoked
          SendResult.Unreachable -> R.string.companion_unreachable
        },
      )
    }
  }

  private fun finishWith(message: Int) {
    Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
    finish()
  }
}
