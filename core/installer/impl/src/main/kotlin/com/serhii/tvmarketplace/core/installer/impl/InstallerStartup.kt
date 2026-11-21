package com.serhii.tvmarketplace.core.installer.impl

import android.app.Application
import com.serhii.tvmarketplace.core.common.AppLog
import dev.zacsweers.metro.Inject
import java.io.File

// after the process was gone: sessions it left are abandoned (the queue that waited for them is gone too), and
// unfinished downloads are deleted. Finished ones stay, so their app shows "Ready to install"
@Inject
class InstallerStartup(private val application: Application) {

  fun run() {
    val installer = application.packageManager.packageInstaller
    val sessions = installer.mySessions
    sessions.forEach { installer.abandonQuietly(it.sessionId) }
    val parts = File(application.noBackupFilesDir, "downloads").listFiles { file -> file.name.endsWith(".part") }.orEmpty()
    parts.forEach { it.delete() }
    if (sessions.isNotEmpty() || parts.isNotEmpty()) AppLog.event("installer_cleanup", "sessions" to sessions.size, "parts" to parts.size)
  }
}
