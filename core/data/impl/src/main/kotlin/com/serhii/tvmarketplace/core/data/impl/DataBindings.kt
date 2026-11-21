package com.serhii.tvmarketplace.core.data.impl

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.serhii.tvmarketplace.core.common.DeviceAbis
import com.serhii.tvmarketplace.core.common.TimeSource
import com.serhii.tvmarketplace.core.data.LinkShareSettings
import com.serhii.tvmarketplace.core.data.PackageIdentity
import com.serhii.tvmarketplace.core.data.ReleaseSource
import com.serhii.tvmarketplace.core.data.ReleasesRepository
import com.serhii.tvmarketplace.core.data.TrackedAppsRepository
import com.serhii.tvmarketplace.core.data.UpdateChecker
import com.serhii.tvmarketplace.core.data.impl.db.AppDatabase
import com.serhii.tvmarketplace.core.data.impl.db.AppReleaseDao
import com.serhii.tvmarketplace.core.data.impl.db.IncomingLinkDao
import com.serhii.tvmarketplace.core.data.impl.db.InstallRecordDao
import com.serhii.tvmarketplace.core.data.impl.db.PairedPhoneDao
import com.serhii.tvmarketplace.core.data.impl.db.TrackedAppDao
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

@BindingContainer
@ContributesTo(AppScope::class)
object DataBindings {

  // the file name is where users' data lives: never rename it
  @Provides
  @SingleIn(AppScope::class)
  fun database(application: Application): AppDatabase = Room.databaseBuilder<AppDatabase>(application, "tv_marketplace.db").build()

  @Provides
  fun trackedAppDao(database: AppDatabase): TrackedAppDao = database.trackedAppDao()

  @Provides
  fun incomingLinkDao(database: AppDatabase): IncomingLinkDao = database.incomingLinkDao()

  @Provides
  fun pairedPhoneDao(database: AppDatabase): PairedPhoneDao = database.pairedPhoneDao()

  @Provides
  fun appReleaseDao(database: AppDatabase): AppReleaseDao = database.appReleaseDao()

  @Provides
  fun installRecordDao(database: AppDatabase): InstallRecordDao = database.installRecordDao()

  // the file name is where users' settings live: never rename it
  @Provides
  @SingleIn(AppScope::class)
  fun linkShareSettings(application: Application): LinkShareSettings =
    DataStoreLinkShareSettings(PreferenceDataStoreFactory.create { application.preferencesDataStoreFile("link_share_settings") })

  // the file name is where the check's state lives: never rename it
  @Provides
  @SingleIn(AppScope::class)
  fun updateChecker(
    application: Application,
    trackedApps: TrackedAppsRepository,
    releases: ReleasesRepository,
    source: ReleaseSource,
    timeSource: TimeSource,
    identity: PackageIdentity,
    deviceAbis: DeviceAbis,
  ): UpdateChecker = DefaultUpdateChecker(
    trackedApps,
    releases,
    source,
    timeSource,
    PreferenceDataStoreFactory.create { application.preferencesDataStoreFile("update_check") },
    identity,
    deviceAbis,
  )

  @Provides
  @SingleIn(AppScope::class)
  fun httpClient(): HttpClient = gitHubHttpClient(OkHttp.create())
}
