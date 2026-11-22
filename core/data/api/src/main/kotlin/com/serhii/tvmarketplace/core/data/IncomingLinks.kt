package com.serhii.tvmarketplace.core.data

import com.serhii.tvmarketplace.core.domain.AppSource
import kotlinx.coroutines.flow.Flow
import java.time.Instant

enum class IncomingLinkStatus { PENDING, ADDED, DISMISSED }

data class IncomingLink(
  val id: Long,
  val rawText: String,
  val ref: AppSource,
  val senderPhoneId: String?,
  val senderLabel: String,
  val receivedAt: Instant,
  val status: IncomingLinkStatus,
)

interface IncomingLinksRepository {
  // a pending link to the same app is refreshed instead of duplicated
  suspend fun receive(
    rawText: String,
    ref: AppSource,
    senderPhoneId: String?,
    senderLabel: String,
  ): IncomingLink

  fun pending(): Flow<List<IncomingLink>>

  suspend fun get(id: Long): IncomingLink?

  suspend fun markAdded(id: Long)

  suspend fun markDismissed(id: Long)

  // added and dismissed links are kept for a while, then removed
  suspend fun purgeHandledBefore(before: Instant)
}
