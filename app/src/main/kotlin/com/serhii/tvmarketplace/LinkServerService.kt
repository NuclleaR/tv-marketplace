package com.serhii.tvmarketplace

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.serhii.tvmarketplace.core.strings.R as StringsR

// keeps the process, and with it the link server, alive while the app is in the background
class LinkServerService : Service() {

  override fun onCreate() {
    super.onCreate()
    val manager = getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
      NotificationChannel(CHANNEL_ID, getString(StringsR.string.notification_channel), NotificationManager.IMPORTANCE_MIN),
    )
    val notification = Notification.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_launcher_foreground)
      .setContentTitle(getString(StringsR.string.app_name))
      .setContentText(getString(StringsR.string.notification_text))
      .setOngoing(true)
      .build()
    // the special-use type exists from Android 14; earlier versions take no type
    val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
    ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
  }

  override fun onStartCommand(
    intent: Intent?,
    flags: Int,
    startId: Int,
  ): Int = START_STICKY

  override fun onBind(intent: Intent?): IBinder? = null

  private companion object {
    const val CHANNEL_ID = "link_server"
    const val NOTIFICATION_ID = 1
  }
}
