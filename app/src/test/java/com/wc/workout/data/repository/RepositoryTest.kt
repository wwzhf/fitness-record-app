package com.wc.workout.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wc.workout.data.local.AppDatabase
import com.wc.workout.data.local.WorkoutSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var weightRepo: WeightRepository
    private lateinit var exerciseRepo: ExerciseRepository
    private lateinit var workoutRepo: WorkoutRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        weightRepo = WeightRepository(db)
        exerciseRepo = ExerciseRepository(db)
        workoutRepo = WorkoutRepository(db)
    }

    @After
    fun teardown() = db.close()

    @Test
    fun saveWeightTwiceSameDayUpdatesOneRow() = runBlocking {
        val today = LocalDate.of(2026, 8, 28)
        weightRepo.saveWeight(today, 72.0, recordedAt = 1_000)
        weightRepo.saveWeight(today, 71.4, recordedAt = 2_000)
        val rows = db.weightDao().getAll()
        assertEquals(1, rows.size)
        assertEquals(71.4, rows[0].weightKg, 0.001)
        assertEquals(2_000, rows[0].recordedAt)
    }

    @Test
    fun addDuplicateExerciseReturnsDuplicate() = runBlocking {
        assertTrue(exerciseRepo.addExercise("卧推") is ExerciseNameResult.Success)
        assertEquals(ExerciseNameResult.Duplicate, exerciseRepo.addExercise("卧推 "))
    }

    @Test
    fun renameToExistingOtherNameReturnsDuplicate() = runBlocking {
        val a = exerciseRepo.addExercise("卧推") as ExerciseNameResult.Success
        exerciseRepo.addExercise("飞鸟")
        assertEquals(ExerciseNameResult.Duplicate, exerciseRepo.rename(a.id, "飞鸟"))
        assertTrue(exerciseRepo.rename(a.id, "上斜卧推") is ExerciseNameResult.Success)
    }

    @Test
    fun tryDeleteRules() = runBlocking {
        val e = exerciseRepo.addExercise("深蹲") as ExerciseNameResult.Success
        assertTrue(exerciseRepo.tryDelete(e.id))          // 未被引用，可删
        val e2 = exerciseRepo.addExercise("硬拉") as ExerciseNameResult.Success
        val s = workoutRepo.startSession("腿日")
        workoutRepo.addSet(s, e2.id, 100.0, 5)
        assertFalse(exerciseRepo.tryDelete(e2.id))        // 被引用，拒绝删除
        assertTrue(exerciseRepo.isReferenced(e2.id))
    }

    @Test
    fun addSetAssignsOrdersCorrectly() = runBlocking {
        val e1 = exerciseRepo.addExercise("卧推") as ExerciseNameResult.Success
        val e2 = exerciseRepo.addExercise("飞鸟") as ExerciseNameResult.Success
        val s = workoutRepo.startSession("推日")
        workoutRepo.addSet(s, e1.id, 60.0, 8)
        workoutRepo.addSet(s, e1.id, 60.0, 10)
        workoutRepo.addSet(s, e2.id, 15.0, 12)
        val sets = db.workoutDao().getSetsForSession(s)
        assertEquals(3, sets.size)
        assertEquals(1, sets[0].exerciseOrder); assertEquals(1, sets[0].setOrder)
        assertEquals(1, sets[1].exerciseOrder); assertEquals(2, sets[1].setOrder)
        assertEquals(2, sets[2].exerciseOrder); assertEquals(1, sets[2].setOrder)
    }

    @Test
    fun startEndSessionAndOngoing() = runBlocking {
        val s = workoutRepo.startSession("推日", now = 1_000)
        assertEquals(s, workoutRepo.observeOngoing().first()!!.id)
        workoutRepo.endSession(s, now = 2_500)
        assertNull(workoutRepo.observeOngoing().first())
        assertEquals(1_500L, workoutRepo.getSession(s)!!.endTime!! - workoutRepo.getSession(s)!!.startTime)
    }

    @Test
    fun abandonRemovesSessionAndSets() = runBlocking {
        val e = exerciseRepo.addExercise("卧推") as ExerciseNameResult.Success
        val s = workoutRepo.startSession("推日")
        workoutRepo.addSet(s, e.id, 60.0, 8)
        workoutRepo.abandonSession(s)
        assertTrue(db.workoutDao().getAllSets().isEmpty())
        assertNull(workoutRepo.getSession(s))
    }

    @Test
    fun recentTitlesDedupesAndLimits() = runBlocking {
        workoutRepo.startSession("推日", now = 1_000)
        workoutRepo.startSession("拉日", now = 2_000)
        workoutRepo.startSession("推日", now = 3_000)
        workoutRepo.startSession("腿日", now = 4_000)
        val titles = workoutRepo.recentTitles(limit = 3)
        assertEquals(listOf("腿日", "推日", "拉日"), titles)
    }

    @Test
    fun lastPerformanceExcludesCurrentSession() = runBlocking {
        val e = exerciseRepo.addExercise("卧推") as ExerciseNameResult.Success
        val s1 = workoutRepo.startSession("一", now = 1_000)
        workoutRepo.addSet(s1, e.id, 60.0, 8)
        workoutRepo.addSet(s1, e.id, 60.0, 10)
        workoutRepo.endSession(s1, now = 2_000)
        val s2 = workoutRepo.startSession("二", now = 3_000)
        val perf = workoutRepo.lastPerformance(e.id, currentSessionId = s2)
        assertEquals(2, perf.size)
        assertEquals(8, perf[0].reps); assertEquals(10, perf[1].reps)
    }

    @Test
    fun addPastSessionAppearsOnItsDay() = runBlocking {
        val day = LocalDate.of(2026, 8, 1)
        val start = day.atTime(10, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = day.atTime(11, 30).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        workoutRepo.addPastSession("补记腿日", start, end)
        val dayStart = day.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val sessions = workoutRepo.getSessionsBetween(dayStart, dayStart + 86_400_000L)
        assertEquals(1, sessions.size)
        assertEquals("补记腿日", sessions[0].title)
        assertEquals(5_400_000L, sessions[0].endTime!! - sessions[0].startTime)
    }
}
