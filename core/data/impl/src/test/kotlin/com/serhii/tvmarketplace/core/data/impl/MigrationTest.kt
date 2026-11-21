package com.serhii.tvmarketplace.core.data.impl

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.serhii.tvmarketplace.core.data.impl.db.AppDatabase
import org.junit.Rule
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class MigrationTest {

  @get:Rule
  val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

  @Test
  fun `version 1 data survives the migration to version 2`() {
    helper.createDatabase("migration-test", 1).use { db ->
      db.execSQL(
        "INSERT INTO tracked_app (owner, repo, repoKey, sourceUrl, displayName, latestVersion, avatarUrl, addedAt) " +
          "VALUES ('ImranR98', 'Obtainium', 'imranr98/obtainium', 'https://github.com/ImranR98/Obtainium', 'Obtainium', 'v1', NULL, 0)",
      )
    }
    helper.runMigrationsAndValidate("migration-test", 2, true).use { db ->
      db.query("SELECT displayName FROM tracked_app").use { cursor ->
        cursor.moveToFirst()
        assertEquals("Obtainium", cursor.getString(0))
      }
      db.query("SELECT COUNT(*) FROM paired_phone").use { cursor ->
        cursor.moveToFirst()
        assertEquals(0, cursor.getInt(0))
      }
    }
  }

  @Test
  fun `version 2 tracked apps survive the migration to version 3 without an icon`() {
    helper.createDatabase("migration-test-3", 2).use { db ->
      db.execSQL(
        "INSERT INTO tracked_app (owner, repo, repoKey, sourceUrl, displayName, latestVersion, avatarUrl, addedAt) " +
          "VALUES ('ImranR98', 'Obtainium', 'imranr98/obtainium', 'https://github.com/ImranR98/Obtainium', 'Obtainium', 'v1', NULL, 0)",
      )
    }
    helper.runMigrationsAndValidate("migration-test-3", 3, true).use { db ->
      db.query("SELECT displayName, iconUrl FROM tracked_app").use { cursor ->
        cursor.moveToFirst()
        assertEquals("Obtainium", cursor.getString(0))
        assertEquals(true, cursor.isNull(1))
      }
    }
  }

  @Test
  fun `version 3 tracked apps come to version 4 with their icon unchecked`() {
    helper.createDatabase("migration-test-4", 3).use { db ->
      db.execSQL(
        "INSERT INTO tracked_app (owner, repo, repoKey, sourceUrl, displayName, latestVersion, avatarUrl, addedAt, iconUrl) " +
          "VALUES ('ImranR98', 'Obtainium', 'imranr98/obtainium', 'https://github.com/ImranR98/Obtainium', 'Obtainium', 'v1', NULL, 0, NULL)",
      )
    }
    helper.runMigrationsAndValidate("migration-test-4", 4, true).use { db ->
      db.query("SELECT iconChecked FROM tracked_app").use { cursor ->
        cursor.moveToFirst()
        assertEquals(0, cursor.getInt(0))
      }
    }
  }

  @Test
  fun `version 4 tracked apps come to version 5 as GitHub apps with nothing learned yet`() {
    helper.createDatabase("migration-test-5", 4).use { db ->
      db.execSQL(
        "INSERT INTO tracked_app (owner, repo, repoKey, sourceUrl, displayName, latestVersion, avatarUrl, addedAt, iconUrl, iconChecked) " +
          "VALUES ('ImranR98', 'Obtainium', 'imranr98/obtainium', 'https://github.com/ImranR98/Obtainium', 'Obtainium', 'v1', NULL, 0, NULL, 1)",
      )
    }
    helper.runMigrationsAndValidate("migration-test-5", 5, true).use { db ->
      db.query(
        "SELECT displayName, sourceKind, packageName, includePrereleases, packagePattern, sourceGone FROM tracked_app",
      ).use { cursor ->
        cursor.moveToFirst()
        assertEquals("Obtainium", cursor.getString(0))
        assertEquals("github", cursor.getString(1))
        assertEquals(true, cursor.isNull(2))
        assertEquals(0, cursor.getInt(3))
        assertEquals(true, cursor.isNull(4))
        assertEquals(0, cursor.getInt(5))
      }
      db.execSQL(
        "INSERT INTO app_release (appId, tag, name, publishedAt, prerelease, notesHtml, packagesJson, etag, checkedAt) " +
          "VALUES (1, 'v2', NULL, 0, 0, NULL, '[]', NULL, 0)",
      )
      db.execSQL(
        "INSERT INTO install_record (appId, packageName, tag, versionCode, packageFileName, installedAt) VALUES (1, 'a.b', 'v2', 2, 'a.apk', 0)",
      )
      // removing the app takes what was stored about it along
      db.execSQL("PRAGMA foreign_keys = ON")
      db.execSQL("DELETE FROM tracked_app")
      db.query("SELECT (SELECT COUNT(*) FROM app_release) + (SELECT COUNT(*) FROM install_record)").use { cursor ->
        cursor.moveToFirst()
        assertEquals(0, cursor.getInt(0))
      }
    }
  }
}
