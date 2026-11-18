package com.serhii.tvmarketplace.core.data.impl

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.serhii.tvmarketplace.core.common.TimeSource
import com.serhii.tvmarketplace.core.data.LinkShareSettings
import com.serhii.tvmarketplace.core.data.PairedPhone
import com.serhii.tvmarketplace.core.data.PairedPhoneKind
import com.serhii.tvmarketplace.core.data.PairedPhonesRepository
import com.serhii.tvmarketplace.core.data.impl.db.IncomingLinkDao
import com.serhii.tvmarketplace.core.data.impl.db.PairedPhoneDao
import com.serhii.tvmarketplace.core.data.impl.db.PairedPhoneEntity
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private const val MAX_DISPLAY_NAME = 60

@ContributesBinding(AppScope::class)
@Inject
class RoomPairedPhonesRepository(
  private val dao: PairedPhoneDao,
  private val incomingLinks: IncomingLinkDao,
  private val timeSource: TimeSource,
) : PairedPhonesRepository {

  override fun phones(): Flow<List<PairedPhone>> = dao.all().map { rows -> rows.map { it.toModel() } }

  override suspend fun add(
    displayName: String,
    kind: PairedPhoneKind,
    tokenHash: String,
  ): PairedPhone {
    val now = timeSource.now()
    val entity = PairedPhoneEntity(
      id = UUID.randomUUID().toString(),
      displayName = displayName.take(MAX_DISPLAY_NAME),
      kind = kind.name,
      tokenHash = tokenHash,
      pairedAt = now,
      lastUsedAt = now,
    )
    dao.upsert(entity)
    return entity.toModel()
  }

  override suspend fun useByTokenHash(tokenHash: String): PairedPhone? {
    val entity = dao.byTokenHash(tokenHash) ?: return null
    val now = timeSource.now()
    dao.touch(entity.id, now)
    return entity.copy(lastUsedAt = now).toModel()
  }

  override suspend fun remove(id: String) {
    dao.delete(id)
    incomingLinks.detachPhone(id)
  }

  private fun PairedPhoneEntity.toModel() = PairedPhone(
    id = id,
    displayName = displayName,
    kind = PairedPhoneKind.valueOf(kind),
    pairedAt = pairedAt,
    lastUsedAt = lastUsedAt,
  )
}

// key names are persisted user data: never rename them
private val RECEIVE_IN_BACKGROUND = booleanPreferencesKey("receive_in_background")
private val INSTALL_ID = stringPreferencesKey("install_id")

class DataStoreLinkShareSettings(private val store: DataStore<Preferences>) : LinkShareSettings {

  override val receiveInBackground: Flow<Boolean> = store.data.map { it[RECEIVE_IN_BACKGROUND] ?: true }

  override suspend fun setReceiveInBackground(enabled: Boolean) {
    val _ = store.edit { it[RECEIVE_IN_BACKGROUND] = enabled }
  }

  override suspend fun installId(): String {
    store.data.first()[INSTALL_ID]?.let { return it }
    return store.edit { prefs -> if (prefs[INSTALL_ID] == null) prefs[INSTALL_ID] = UUID.randomUUID().toString() }[INSTALL_ID]!!
  }
}
