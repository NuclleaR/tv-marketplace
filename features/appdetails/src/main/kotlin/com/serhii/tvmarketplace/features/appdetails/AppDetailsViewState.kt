package com.serhii.tvmarketplace.features.appdetails

import com.serhii.tvmarketplace.core.domain.AppStatus
import com.serhii.tvmarketplace.core.domain.FailureReason
import java.time.Instant

sealed interface AppDetailsViewState {
  data object Loading : AppDetailsViewState

  // removed meanwhile, or a stale back stack entry
  data object Missing : AppDetailsViewState

  data class Content(
    val name: String,
    val ownerAndRepo: String,
    val iconUrl: String?,
    val description: String?,
    val status: AppStatus,
    val primary: PrimaryAction,
    val failure: FailureReason?,
    val latest: Latest?,
    // asking the source right now, with nothing stored yet
    val checking: Boolean,
    val packages: List<PackageRow>,
    val choiceLost: Boolean,
    // the user's own package choice is in effect
    val userChoice: Boolean,
    val notesHtml: String?,
    val needsPermission: Boolean,
    val installed: Boolean,
    val includePrereleases: Boolean,
    val dialog: Dialog?,
  ) : AppDetailsViewState

  data class Latest(
    val version: String,
    val publishedAt: Instant,
  )

  data class PackageRow(
    val id: Long,
    val fileName: String,
    val sizeBytes: Long,
    val tag: PackageTag?,
    val compatible: Boolean,
  )

  enum class PackageTag { RECOMMENDED, YOUR_CHOICE, NOT_FOR_DEVICE }

  enum class PrimaryAction { INSTALL, UPDATE, OPEN, CANCEL, INSTALL_NOW, RETRY, RELEASE_NOTES, REMOVE, CHECK }

  enum class Dialog { SETTINGS, REMOVE }
}
