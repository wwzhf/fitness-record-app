package com.wc.workout.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun weightUpdateKeepsOneRowPerDay() = runBlocking {
        val dao = db.weightDao()
        dao.insert(WeightRecord(dateEpochDay = 100, weightKg = 70.0, recordedAt = 1_000))
        val existing = dao.getByDate(100)!!
        dao.update(existing.copy(weightKg = 71.5, recordedAt = 2_000))

        val rows = dao.getAll()
        assertEquals(1, rows.size)
        assertEquals(71.5, rows[0].weightKg, 0.001)
        assertEquals(2_000, rows[0].recordedAt)
        assertEquals(existing.id, rows[0].id)
    }

    @Test
    fun weightUniqueIndexRejectsSecondRowSameDay() {
        val dao = db.weightDao()
        runBlocking { dao.insert(WeightRecord(dateEpochDay = 100, weightKg = 70.0, recordedAt = 1_000)) }
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { dao.insert(WeightRecord(dateEpochDay = 100, weightKg = 72.0, recordedAt = 2_000)) }
        }
    }

    @Test
    fun observeBetweenFiltersByRange() = runBlocking {
        val dao = db.weightDao()
        dao.insert(WeightRecord(dateEpochDay = 99, weightKg = 70.0, recordedAt = 1))
        dao.insert(WeightRecord(dateEpochDay = 100, weightKg = 70.5, recordedAt = 2))
        dao.insert(WeightRecord(dateEpochDay = 131, weightKg = 71.0, recordedAt = 3))
        dao.insert(WeightRecord(dateEpochDay = 130, weightKg = 70.8, recordedAt = 4))
        val rows = dao.observeBetween(100, 130).first()
        assertEquals(2, rows.size)
        assertEquals(100L, rows[0].dateEpochDay)
        assertEquals(130L, rows[1].dateEpochDay)
    }

    @Test
    fun sessionDeleteCascadesSets() = runBlocking {
        val sessionId = db.workoutDao().insertSession(WorkoutSession(title = "推日", startTime = 1_000))
        val exerciseId = db.exerciseDao().insert(Exercise(name = "卧推", createdAt = 1))
        db.workoutDao().insertSet(WorkoutSet(sessionId = sessionId, exerciseId = exerciseId, weightKg = 60.0, reps = 8, exerciseOrder = 1, setOrder = 1))
        db.workoutDao().insertSet(WorkoutSet(sessionId = sessionId, exerciseId = exerciseId, weightKg = 60.0, reps = 6, exerciseOrder = 1, setOrder = 2))

        db.workoutDao().deleteSession(sessionId)

        assertTrue(db.workoutDao().getSetsForSession(sessionId).isEmpty())
        assertTrue(db.workoutDao().getAllSets().isEmpty())
    }

    @Test
    fun exerciseDeleteRestrictedWhenReferenced() {
        val exerciseId = runBlocking { db.exerciseDao().insert(Exercise(name = "深蹲", createdAt = 1)) }
        val sessionId = runBlocking { db.workoutDao().insertSession(WorkoutSession(title = "腿日", startTime = 1_000)) }
        runBlocking {
            db.workoutDao().insertSet(WorkoutSet(sessionId = sessionId, exerciseId = exerciseId, weightKg = 100.0, reps = 5, exerciseOrder = 1, setOrder = 1))
        }
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { db.exerciseDao().deleteById(exerciseId) }
        }
    }

    @Test
    fun observeOngoingReturnsOnlyNullEndSession() = runBlocking {
        val dao = db.workoutDao()
        dao.insertSession(WorkoutSession(title = "旧训练", startTime = 1_000, endTime = 2_000))
        val ongoingId = dao.insertSession(WorkoutSession(title = "进行中", startTime = 3_000))
        assertEquals(ongoingId, dao.observeOngoing().first()!!.id)
        dao.setEndTime(ongoingId, 4_000)
        assertNull(dao.observeOngoing().first())
    }

    @Test
    fun lastSessionQueryExcludesCurrentAndOrdersByStartTime() = runBlocking {
        val dao = db.workoutDao()
        val e = db.exerciseDao().insert(Exercise(name = "硬拉", createdAt = 1))
        val s1 = dao.insertSession(WorkoutSession(title = "一", startTime = 1_000))
        val s2 = dao.insertSession(WorkoutSession(title = "二", startTime = 2_000))
        val s3 = dao.insertSession(WorkoutSession(title = "三", startTime = 3_000))
        dao.insertSet(WorkoutSet(sessionId = s1, exerciseId = e, weightKg = 80.0, reps = 5, exerciseOrder = 1, setOrder = 1))
        dao.insertSet(WorkoutSet(sessionId = s2, exerciseId = e, weightKg = 90.0, reps = 5, exerciseOrder = 1, setOrder = 1))

        assertEquals(s2, dao.findLastSessionIdWithExercise(e, s3))
        assertEquals(s1, dao.findLastSessionIdWithExercise(e, s2))
    }

    @Test
    fun getSetsWithExerciseNamesJoinsName() = runBlocking {
        val dao = db.workoutDao()
        val e = db.exerciseDao().insert(Exercise(name = "卧推", createdAt = 1))
        val s = dao.insertSession(WorkoutSession(title = "推日", startTime = 1_000))
        dao.insertSet(WorkoutSet(sessionId = s, exerciseId = e, weightKg = 60.0, reps = 8, exerciseOrder = 1, setOrder = 1))
        dao.insertSet(WorkoutSet(sessionId = s, exerciseId = e, weightKg = 60.0, reps = 10, exerciseOrder = 1, setOrder = 2))

        val rows = dao.getSetsWithExerciseNames(s)
        assertEquals(2, rows.size)
        assertEquals("卧推", rows[0].exerciseName)
        assertEquals(60.0, rows[0].set.weightKg, 0.001)
        assertEquals(2, rows[1].set.setOrder)
    }

    @Test
    fun setSessionTitleUpdatesTitle() = runBlocking {
        val dao = db.workoutDao()
        val s = dao.insertSession(WorkoutSession(title = "旧标题", startTime = 1_000))
        dao.setSessionTitle(s, "新标题")
        assertEquals("新标题", dao.getSession(s)!!.title)
    }

    @Test
    fun sessionNoteDefaultsEmptyAndSetNoteUpdates() = runBlocking {
        val dao = db.workoutDao()
        val id = dao.insertSession(WorkoutSession(title = "测试", startTime = 1_000))
        assertEquals("", dao.getSession(id)!!.note)
        dao.setNote(id, "状态不错")
        assertEquals("状态不错", dao.getSession(id)!!.note)
        dao.setNote(id, "")
        assertEquals("", dao.getSession(id)!!.note)
    }

    @Test
    fun observeActiveByRecentUseOrdersByLastUse() = runBlocking {
        val dao = db.exerciseDao()
        val a = dao.insert(Exercise(name = "A", createdAt = 1))
        val b = dao.insert(Exercise(name = "B", createdAt = 2))
        dao.insert(Exercise(name = "C", createdAt = 3))
        val workoutDao = db.workoutDao()
        val s1 = workoutDao.insertSession(WorkoutSession(title = "一", startTime = 1_000))
        val s2 = workoutDao.insertSession(WorkoutSession(title = "二", startTime = 2_000))
        workoutDao.insertSet(WorkoutSet(sessionId = s1, exerciseId = a, weightKg = 60.0, reps = 8, exerciseOrder = 1, setOrder = 1))
        workoutDao.insertSet(WorkoutSet(sessionId = s2, exerciseId = b, weightKg = 60.0, reps = 8, exerciseOrder = 1, setOrder = 1))

        assertEquals(listOf("B", "A", "C"), dao.observeActiveByRecentUse().first().map { it.name })
    }

    @Test
    fun observeSessionVolumesSumsPerSession() = runBlocking {
        val workoutDao = db.workoutDao()
        val e = db.exerciseDao().insert(Exercise(name = "卧推", createdAt = 1))
        val s1 = workoutDao.insertSession(WorkoutSession(title = "一", startTime = 1_000))
        val s2 = workoutDao.insertSession(WorkoutSession(title = "二", startTime = 2_000))
        workoutDao.insertSet(WorkoutSet(sessionId = s1, exerciseId = e, weightKg = 60.0, reps = 10, exerciseOrder = 1, setOrder = 1))
        workoutDao.insertSet(WorkoutSet(sessionId = s1, exerciseId = e, weightKg = 70.0, reps = 5, exerciseOrder = 1, setOrder = 2))
        workoutDao.insertSet(WorkoutSet(sessionId = s2, exerciseId = e, weightKg = 20.0, reps = 8, exerciseOrder = 1, setOrder = 1))

        val volumes = workoutDao.observeSessionVolumes().first()
        assertEquals(listOf(1_000L, 2_000L), volumes.map { it.startTime })
        assertEquals(listOf(950.0, 160.0), volumes.map { it.volume })
    }

    @Test
    fun deleteByDateRemovesWeightRow() = runBlocking {
        val dao = db.weightDao()
        dao.insert(WeightRecord(dateEpochDay = 200, weightKg = 70.0, recordedAt = 1_000))
        dao.insert(WeightRecord(dateEpochDay = 201, weightKg = 71.0, recordedAt = 2_000))
        dao.deleteByDate(200)
        assertNull(dao.getByDate(200))
        assertEquals(71.0, dao.getByDate(201)!!.weightKg, 0.001)
    }

    @Test
    fun migrationSqlDeletesOnlyBogusWeightDates() = runBlocking {
        val dao = db.weightDao()
        dao.insert(WeightRecord(dateEpochDay = 20101, weightKg = 51.8, recordedAt = 1_000))   // 2025-01-13, 合法
        dao.insert(WeightRecord(dateEpochDay = 739264, weightKg = 99.9, recordedAt = 2_000))  // Excel 导入 bug 的坏行
        db.openHelper.writableDatabase.execSQL("DELETE FROM weight_records WHERE dateEpochDay > 100000")
        assertNull(dao.getByDate(739264))
        assertEquals(51.8, dao.getByDate(20101)!!.weightKg, 0.001)
    }
}
