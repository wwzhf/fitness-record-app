package com.wc.workout.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val TEST_DB = "migration-test.db"

/** 真实走一遍 MIGRATION_2_3：schema 2 建库 → 迁移 → 对 schema 3 校验 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate2To3AddsNoteColumnAndKeepsRows() {
        var db = helper.createDatabase(TEST_DB, 2).apply {
            execSQL("INSERT INTO workout_sessions (title, startTime, endTime) VALUES ('旧训练', 100, 200)")
            execSQL("INSERT INTO weight_records (dateEpochDay, weightKg, recordedAt) VALUES (200, 70.0, 1000)")
            close()
        }
        db = helper.runMigrationsAndValidate(TEST_DB, 3, true, AppDatabase.MIGRATION_2_3)

        db.query("SELECT note FROM workout_sessions WHERE title = '旧训练'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("", cursor.getString(0))
        }
        db.query("SELECT weightKg FROM weight_records WHERE dateEpochDay = 200").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(70.0, cursor.getDouble(0), 0.001)
        }
    }
}
