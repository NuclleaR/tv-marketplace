package com.serhii.tvmarketplace.core.data.impl.db

import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
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
  @ColumnInfo(defaultValue = "NULL") val iconUrl: String? = null,
  // whether the icon was looked up; apps tracked before that are checked once in the background
  @ColumnInfo(defaultValue = "0") val iconChecked: Boolean = false,
  // the kind of AppSource; every app tracked before sources were named comes from GitHub
  @ColumnInfo(defaultValue = "'github'") val sourceKind: String = "github",
  // learned from the first APK or manifest read, and never replaced by a different one
  @ColumnInfo(defaultValue = "NULL") val packageName: String? = null,
  @ColumnInfo(defaultValue = "0") val includePrereleases: Boolean = false,
  // the package the user picked, as a version-independent file name pattern; NULL means the automatic choice
  @ColumnInfo(defaultValue = "NULL") val packagePattern: String? = null,
  @ColumnInfo(defaultValue = "0") val sourceGone: Boolean = false,
  // the repository's description as it was when the app was added; NULL for apps added before it was kept
  @ColumnInfo(defaultValue = "NULL") val description: String? = null,
)

// the latest eligible release of each tracked app, as last seen at its source
@Entity(
  tableName = "app_release",
  foreignKeys = [
    ForeignKey(
      entity = TrackedAppEntity::class,
      parentColumns = ["id"],
      childColumns = ["appId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
)
data class AppReleaseEntity(
  @PrimaryKey val appId: Long,
  val tag: String,
  val name: String?,
  val publishedAt: Instant,
  val prerelease: Boolean,
  val notesHtml: String?,
  val packagesJson: String,
  val etag: String?,
  val checkedAt: Instant,
)

// what this app installed for a tracked app, to know the installed release exactly while its version code holds
@Entity(
  tableName = "install_record",
  foreignKeys = [
    ForeignKey(
      entity = TrackedAppEntity::class,
      parentColumns = ["id"],
      childColumns = ["appId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
)
data class InstallRecordEntity(
  @PrimaryKey val appId: Long,
  val packageName: String,
  val tag: String,
  val versionCode: Long,
  val packageFileName: String,
  val installedAt: Instant,
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

  @Query("SELECT * FROM tracked_app WHERE iconChecked = 0")
  suspend fun withUncheckedIcon(): List<TrackedAppEntity>

  @Query("UPDATE tracked_app SET iconUrl = :iconUrl, iconChecked = 1 WHERE id = :id")
  suspend fun setIcon(
    id: Long,
    iconUrl: String?,
  )

  @Query("SELECT * FROM tracked_app WHERE id = :id")
  suspend fun byId(id: Long): TrackedAppEntity?

  @Query("SELECT * FROM tracked_app WHERE id = :id")
  fun observe(id: Long): Flow<TrackedAppEntity?>

  // only sets a name the app doesn't have yet
  @Query("UPDATE tracked_app SET packageName = :packageName WHERE id = :id AND packageName IS NULL")
  suspend fun setPackageNameIfUnknown(
    id: Long,
    packageName: String,
  )

  @Query("UPDATE tracked_app SET includePrereleases = :include WHERE id = :id")
  suspend fun setIncludePrereleases(
    id: Long,
    include: Boolean,
  )

  @Query("UPDATE tracked_app SET packagePattern = :pattern WHERE id = :id")
  suspend fun setPackagePattern(
    id: Long,
    pattern: String?,
  )

  @Query("UPDATE tracked_app SET sourceGone = :gone WHERE id = :id")
  suspend fun setSourceGone(
    id: Long,
    gone: Boolean,
  )

  @Query("UPDATE tracked_app SET latestVersion = :version WHERE id = :id")
  suspend fun setLatestVersion(
    id: Long,
    version: String?,
  )

  @Query("DELETE FROM tracked_app WHERE id = :id")
  suspend fun delete(id: Long)
}

@Dao
interface AppReleaseDao {
  @Query("SELECT * FROM app_release")
  fun all(): Flow<List<AppReleaseEntity>>

  @Query("SELECT * FROM app_release WHERE appId = :appId")
  suspend fun get(appId: Long): AppReleaseEntity?

  @Upsert
  suspend fun upsert(entity: AppReleaseEntity)

  @Query("UPDATE app_release SET checkedAt = :checkedAt WHERE appId = :appId")
  suspend fun touch(
    appId: Long,
    checkedAt: Instant,
  )

  @Query("DELETE FROM app_release WHERE appId = :appId")
  suspend fun delete(appId: Long)
}

@Dao
interface InstallRecordDao {
  @Query("SELECT * FROM install_record")
  fun all(): Flow<List<InstallRecordEntity>>

  @Upsert
  suspend fun upsert(entity: InstallRecordEntity)
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
  entities = [
    TrackedAppEntity::class,
    IncomingLinkEntity::class,
    PairedPhoneEntity::class,
    AppReleaseEntity::class,
    InstallRecordEntity::class,
  ],
  version = 5,
  autoMigrations = [
    AutoMigration(from = 1, to = 2),
    AutoMigration(from = 2, to = 3),
    AutoMigration(from = 3, to = 4),
    AutoMigration(from = 4, to = 5),
  ],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
  abstract fun trackedAppDao(): TrackedAppDao

  abstract fun incomingLinkDao(): IncomingLinkDao

  abstract fun pairedPhoneDao(): PairedPhoneDao

  abstract fun appReleaseDao(): AppReleaseDao

  abstract fun installRecordDao(): InstallRecordDao
}
