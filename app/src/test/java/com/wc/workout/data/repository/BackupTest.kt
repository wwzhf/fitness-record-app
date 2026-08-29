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
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId

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
    fun importPreservesArchivedState() = runBlocking {
        val archivedJson = """
            {"schemaVersion":1,"exportedAt":1,
             "weights":[],
             "exercises":[{"name":"旧动作","createdAt":1,"isArchived":true}],
             "sessions":[],
             "sets":[]}
        """.trimIndent()
        BackupRepository(db).import(archivedJson)
        assertTrue(db.exerciseDao().findByName("旧动作")!!.isArchived)
    }

    @Test
    fun importPreservesDataOutsideBackupDays() = runBlocking {
        val json = seedSource() // 备份：1970-01-01 的“推日”与 day=20600 的体重
        val dest = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java
        ).allowMainThreadQueries().build()
        // 目标库里预先存在的、备份里没有的数据
        dest.exerciseDao().insert(com.wc.workout.data.local.Exercise(name = "旧动作", createdAt = 1))
        val oldStart = LocalDateTime.of(2026, 1, 1, 10, 0)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        dest.workoutDao().insertSession(
            com.wc.workout.data.local.WorkoutSession(title = "旧训练", startTime = oldStart)
        )
        dest.weightDao().insert(
            com.wc.workout.data.local.WeightRecord(dateEpochDay = 1, weightKg = 1.0, recordedAt = 1)
        )
        BackupRepository(dest).import(json)
        // 备份内容写入，备份之外的动作、训练、体重全部保留
        assertEquals(setOf("卧推", "旧动作"), dest.exerciseDao().getAll().map { it.name }.toSet())
        val sessions = dest.workoutDao().getAllSessions()
        assertEquals(setOf("推日", "旧训练"), sessions.map { it.title }.toSet())
        assertEquals(oldStart, sessions.first { it.title == "旧训练" }.startTime)
        val weights = dest.weightDao().getAll().associateBy { it.dateEpochDay }
        assertEquals(2, weights.size)
        assertEquals(1.0, weights.getValue(1L).weightKg, 0.001)
        assertEquals(72.0, weights.getValue(20_600L).weightKg, 0.001)
        dest.close()
    }

    @Test
    fun reimportDoesNotDuplicateSessions() = runBlocking {
        val json = seedSource()
        val dest = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java
        ).allowMainThreadQueries().build()
        BackupRepository(dest).import(json)
        BackupRepository(dest).import(json)
        assertEquals(1, dest.workoutDao().getAllSessions().size)
        assertEquals(1, dest.workoutDao().getAllSets().size)
        dest.close()
    }

    @Test
    fun importOverwritesSameDaySessions() = runBlocking {
        val json = seedSource() // 备份里也是 1970-01-01（startTime=100，标题“推日”）
        val dest = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java
        ).allowMainThreadQueries().build()
        val e = dest.exerciseDao().insert(com.wc.workout.data.local.Exercise(name = "旧动作", createdAt = 1))
        val oldSession = dest.workoutDao().insertSession(
            com.wc.workout.data.local.WorkoutSession(title = "旧", startTime = 50)
        )
        dest.workoutDao().insertSet(
            com.wc.workout.data.local.WorkoutSet(
                sessionId = oldSession, exerciseId = e, weightKg = 10.0, reps = 5, exerciseOrder = 1, setOrder = 1
            )
        )
        BackupRepository(dest).import(json)
        // 同一天旧训练被删（组级联删除），只留备份里的那次
        val sessions = dest.workoutDao().getAllSessions()
        assertEquals(1, sessions.size)
        assertEquals("推日", sessions[0].title)
        val sets = dest.workoutDao().getAllSets()
        assertEquals(1, sets.size)
        assertEquals(60.0, sets[0].weightKg, 0.001)
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
