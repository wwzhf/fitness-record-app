package com.wc.workout.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wc.workout.data.local.AppDatabase
import com.wc.workout.data.local.Exercise
import com.wc.workout.data.local.WorkoutSession
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() = db.close()

    private suspend fun seedSource(): String {
        val src = BackupRepository(db)
        val e = db.exerciseDao().insert(Exercise(name = "卧推", createdAt = 10))
        val s = db.workoutDao().insertSession(WorkoutSession(title = "推日", startTime = 100, endTime = 200))
        db.workoutDao().insertSet(
            com.wc.workout.data.local.WorkoutSet(
                sessionId = s, exerciseId = e, weightKg = 60.0, reps = 8, exerciseOrder = 1, setOrder = 1
            )
        )
        db.weightDao().insert(
            com.wc.workout.data.local.WeightRecord(dateEpochDay = 20_600, weightKg = 72.0, recordedAt = 111)
        )
        return src.export()
    }

    @Test
    fun exportImportRoundTripPreservesData() = runBlocking {
        val json = seedSource()
        val dest = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java
        ).allowMainThreadQueries().build()
        val summary = BackupRepository(dest).import(json)

        assertEquals(1, summary.weights)
        assertEquals(1, summary.exercises)
        assertEquals(1, summary.sessions)
        assertEquals(1, summary.sets)

        assertEquals(72.0, dest.weightDao().getAll()[0].weightKg, 0.001)
        assertEquals("卧推", dest.exerciseDao().getAll()[0].name)
        val s = dest.workoutDao().getAllSessions()[0]
        assertEquals("推日", s.title); assertEquals(100L, s.startTime); assertEquals(200L, s.endTime)
        val sets = dest.workoutDao().getSetsWithExerciseNames(s.id)
        assertEquals(1, sets.size)
        assertEquals("卧推", sets[0].exerciseName)
        assertEquals(60.0, sets[0].set.weightKg, 0.001)
        dest.close()
    }

    @Test
    fun importOverwritesSameDayWeight() = runBlocking {
        val json = seedSource() // 备份里 day=20600 → 72.0
        val dest = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dest.weightDao().insert(
            com.wc.workout.data.local.WeightRecord(dateEpochDay = 20_600, weightKg = 65.0, recordedAt = 999)
        )
        BackupRepository(dest).import(json)
        val rows = dest.weightDao().getAll()
        assertEquals(1, rows.size)
        assertEquals(72.0, rows[0].weightKg, 0.001)
        assertEquals(111L, rows[0].recordedAt)
        dest.close()
    }

    @Test
    fun importUnarchivesReferencedExercise() = runBlocking {
        val json = seedSource()
        val dest = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java
        ).allowMainThreadQueries().build()
        val id = dest.exerciseDao().insert(
            Exercise(name = "卧推", createdAt = 1, isArchived = true)
        )
        BackupRepository(dest).import(json)
        assertFalse(dest.exerciseDao().getById(id)!!.isArchived)
        dest.close()
    }

    @Test
    fun importRejectsNewerSchemaVersion() {
        val newer = """{"schemaVersion":99,"exportedAt":1,"weights":[],"exercises":[],"sessions":[],"sets":[]}"""
        assertThrows(IllegalStateException::class.java) {
            runBlocking { BackupRepository(db).import(newer) }
        }
    }

    @Test
    fun importRejectsMalformedJson() {
        assertThrows(SerializationException::class.java) {
            runBlocking { BackupRepository(db).import("not a json") }
        }
    }
}
