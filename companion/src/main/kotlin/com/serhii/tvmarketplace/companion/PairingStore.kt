package com.serhii.tvmarketplace.companion

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class PairedTv(
  val id: String,
  val name: String,
  val deviceToken: String,
)

// file and key names are persisted user data: never rename them
private val Context.pairingStore: DataStore<Preferences> by preferencesDataStore(name = "pairing")
private val TV_ID = stringPreferencesKey("tv_id")
private val TV_NAME = stringPreferencesKey("tv_name")
private val DEVICE_TOKEN = stringPreferencesKey("device_token")

// app-private storage; the token only lets this phone put links into the TV's inbox
class PairingStore(context: Context) {

  private val store = context.applicationContext.pairingStore

  val pairedTv: Flow<PairedTv?> = store.data.map { prefs ->
    val id = prefs[TV_ID] ?: return@map null
    val token = prefs[DEVICE_TOKEN] ?: return@map null
    PairedTv(id = id, name = prefs[TV_NAME].orEmpty(), deviceToken = token)
  }

  suspend fun save(tv: PairedTv) {
    val _ = store.edit {
      it[TV_ID] = tv.id
      it[TV_NAME] = tv.name
      it[DEVICE_TOKEN] = tv.deviceToken
    }
  }

  suspend fun clear() {
    val _ = store.edit { it.clear() }
  }
}
