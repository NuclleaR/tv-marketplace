package com.serhii.tvmarketplace.core.data

import kotlinx.coroutines.flow.Flow
import java.time.Instant

enum class PairedPhoneKind { BROWSER, COMPANION }

data class PairedPhone(
  val id: String,
  val displayName: String,
  val kind: PairedPhoneKind,
  val pairedAt: Instant,
  val lastUsedAt: Instant,
)

interface PairedPhonesRepository {
  fun phones(): Flow<List<PairedPhone>>

  // only a hash of the device token is stored
  suspend fun add(
    displayName: String,
    kind: PairedPhoneKind,
    tokenHash: String,
  ): PairedPhone

  // finds the phone and records that it was used
  suspend fun useByTokenHash(tokenHash: String): PairedPhone?

  suspend fun remove(id: String)
}

interface LinkShareSettings {
  val receiveInBackground: Flow<Boolean>

  suspend fun setReceiveInBackground(enabled: Boolean)

  // a random id of this installation, created on first use, so phones recognize the TV after an IP change
  suspend fun installId(): String
}
