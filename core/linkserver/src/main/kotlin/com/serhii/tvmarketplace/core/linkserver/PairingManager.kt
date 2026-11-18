package com.serhii.tvmarketplace.core.linkserver

import com.serhii.tvmarketplace.core.data.PairedPhone
import com.serhii.tvmarketplace.core.data.PairedPhoneKind
import com.serhii.tvmarketplace.core.data.PairedPhonesRepository
import dev.zacsweers.metro.Inject
import java.security.MessageDigest

// a remembered phone holds a long-lived device token; the TV keeps only its SHA-256
@Inject
class PairingManager(
  private val sessions: SessionManager,
  private val phones: PairedPhonesRepository,
) {

  suspend fun pair(
    displayName: String,
    kind: PairedPhoneKind,
  ): Pair<String, PairedPhone> {
    val token = sessions.randomToken(bytes = 32)
    return token to phones.add(displayName = displayName.trim().ifEmpty { "Phone" }, kind = kind, tokenHash = hash(token))
  }

  suspend fun authenticate(token: String): PairedPhone? = phones.useByTokenHash(hash(token))

  private fun hash(token: String): String =
    MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
}
