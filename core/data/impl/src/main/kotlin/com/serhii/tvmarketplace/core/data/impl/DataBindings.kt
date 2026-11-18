package com.serhii.tvmarketplace.core.data.impl

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.serhii.tvmarketplace.core.data.LinkShareSettings
import com.serhii.tvmarketplace.core.data.impl.db.AppDatabase
import com.serhii.tvmarketplace.core.data.impl.db.IncomingLinkDao
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

  // the file name is where users' settings live: never rename it
  @Provides
  @SingleIn(AppScope::class)
  fun linkShareSettings(application: Application): LinkShareSettings =
    DataStoreLinkShareSettings(PreferenceDataStoreFactory.create { application.preferencesDataStoreFile("link_share_settings") })

  @Provides
  @SingleIn(AppScope::class)
  fun httpClient(): HttpClient = gitHubHttpClient(OkHttp.create())
}
