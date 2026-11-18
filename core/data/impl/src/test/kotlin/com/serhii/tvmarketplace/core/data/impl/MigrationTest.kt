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
}
