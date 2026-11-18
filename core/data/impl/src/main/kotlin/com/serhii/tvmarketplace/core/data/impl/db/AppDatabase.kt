package com.serhii.tvmarketplace.core.data.impl.db

import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.Instant

// column and table names are persisted user data: never rename them without a migration
@Entity(tableName = "tracked_app", indices = [Index(value = ["repoKey"], unique = true)])
data class TrackedAppEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val owner: String,
  val repo: String,
  // lowercase "owner/repo": GitHub names are case-insensitive
  val repoKey: String,
  val sourceUrl: String,
  val displayName: String,
  val latestVersion: String?,
  val avatarUrl: String?,
  val addedAt: Instant,
)

@Entity(tableName = "incoming_link", indices = [Index(value = ["repoKey", "status"])])
data class IncomingLinkEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val rawText: String,
  val owner: String,
  val repo: String,
  val repoKey: String,
  val normalizedUrl: String,
  val senderPhoneId: String?,
  val senderLabel: String,
  val receivedAt: Instant,
  val status: String,
)

@Entity(tableName = "paired_phone", indices = [Index(value = ["tokenHash"], unique = true)])
data class PairedPhoneEntity(
  @PrimaryKey val id: String,
  val displayName: String,
  val kind: String,
  val tokenHash: String,
  val pairedAt: Instant,
  val lastUsedAt: Instant,
)

@Dao
interface PairedPhoneDao {
  @Query("SELECT * FROM paired_phone ORDER BY lastUsedAt DESC")
  fun all(): Flow<List<PairedPhoneEntity>>

  @Query("SELECT * FROM paired_phone WHERE tokenHash = :tokenHash")
  suspend fun byTokenHash(tokenHash: String): PairedPhoneEntity?

  @Upsert
  suspend fun upsert(entity: PairedPhoneEntity)

  @Query("UPDATE paired_phone SET lastUsedAt = :at WHERE id = :id")
  suspend fun touch(
    id: String,
    at: Instant,
  )

  @Query("DELETE FROM paired_phone WHERE id = :id")
  suspend fun delete(id: String)
}

@Dao
interface TrackedAppDao {
  @Query("SELECT * FROM tracked_app ORDER BY addedAt DESC")
  fun all(): Flow<List<TrackedAppEntity>>

  @Query("SELECT * FROM tracked_app WHERE repoKey = :repoKey")
  suspend fun byKey(repoKey: String): TrackedAppEntity?

  @Upsert
  suspend fun upsert(entity: TrackedAppEntity): Long
}

@Dao
interface IncomingLinkDao {
  @Query("SELECT * FROM incoming_link WHERE status = 'PENDING' ORDER BY receivedAt DESC")
  fun pending(): Flow<List<IncomingLinkEntity>>

  @Query("SELECT * FROM incoming_link WHERE id = :id")
  suspend fun byId(id: Long): IncomingLinkEntity?

  @Query("SELECT * FROM incoming_link WHERE repoKey = :repoKey AND status = 'PENDING' LIMIT 1")
  suspend fun pendingByKey(repoKey: String): IncomingLinkEntity?

  @Upsert
  suspend fun upsert(entity: IncomingLinkEntity): Long

  @Query("UPDATE incoming_link SET status = :status WHERE id = :id")
  suspend fun setStatus(
    id: Long,
    status: String,
  )

  @Query("DELETE FROM incoming_link WHERE status != 'PENDING' AND receivedAt < :before")
  suspend fun purgeHandled(before: Instant)

  // a removed phone keeps its links, they just no longer point to it
  @Query("UPDATE incoming_link SET senderPhoneId = NULL WHERE senderPhoneId = :phoneId")
  suspend fun detachPhone(phoneId: String)
}

class Converters {
  @TypeConverter
  fun fromInstant(value: Instant): Long = value.toEpochMilli()

  @TypeConverter
  fun toInstant(value: Long): Instant = Instant.ofEpochMilli(value)
}

@Database(
  entities = [TrackedAppEntity::class, IncomingLinkEntity::class, PairedPhoneEntity::class],
  version = 2,
  autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
  abstract fun trackedAppDao(): TrackedAppDao

  abstract fun incomingLinkDao(): IncomingLinkDao

  abstract fun pairedPhoneDao(): PairedPhoneDao
}
