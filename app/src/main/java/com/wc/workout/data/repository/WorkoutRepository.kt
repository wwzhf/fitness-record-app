package com.wc.workout.data.repository

import androidx.room.withTransaction
import com.wc.workout.data.local.AppDatabase
import com.wc.workout.data.local.SetWithExercise
import com.wc.workout.data.local.WorkoutSession
import com.wc.workout.data.local.WorkoutSet
import kotlinx.coroutines.flow.Flow

class WorkoutRepository(private val db: AppDatabase) {
    private val dao = db.workoutDao()

    suspend fun startSession(title: String, now: Long = System.currentTimeMillis()): Long =
        dao.insertSession(WorkoutSession(title = title, startTime = now))

    suspend fun endSession(id: Long, now: Long = System.currentTimeMillis()) = dao.setEndTime(id, now)

    suspend fun abandonSession(id: Long) = dao.deleteSession(id)

    suspend fun setSessionTitle(id: Long, title: String) = dao.setSessionTitle(id, title)

    fun observeOngoing(): Flow<WorkoutSession?> = dao.observeOngoing()

    fun observeSessionsBetween(startMillis: Long, endMillis: Long): Flow<List<WorkoutSession>> =
        dao.observeSessionsBetween(startMillis, endMillis)

    suspend fun getSessionsBetween(startMillis: Long, endMillis: Long): List<WorkoutSession> =
        dao.getSessionsBetween(startMillis, endMillis)

    suspend fun getSession(id: Long): WorkoutSession? = dao.getSession(id)

    /** 最近会话标题去重，供"开始健身"弹窗快捷选择 */
    suspend fun recentTitles(limit: Int = 10): List<String> =
        dao.getRecentSessions(50).map { it.title }.distinct().take(limit)

    /** 补记：直接插入一条带完整起止时间的往期会话 */
    suspend fun addPastSession(title: String, startTime: Long, endTime: Long): Long =
        dao.insertSession(WorkoutSession(title = title, startTime = startTime, endTime = endTime))

    suspend fun addSet(sessionId: Long, exerciseId: Long, weightKg: Double, reps: Int): Long = db.withTransaction {
        val sameExercise = dao.getSetsOfExercise(sessionId, exerciseId)
        val exerciseOrder = sameExercise.firstOrNull()?.exerciseOrder
            ?: ((dao.maxExerciseOrder(sessionId) ?: 0) + 1)
        val setOrder = (dao.maxSetOrder(sessionId, exerciseId) ?: 0) + 1
        dao.insertSet(
            WorkoutSet(
                sessionId = sessionId, exerciseId = exerciseId,
                weightKg = weightKg, reps = reps,
                exerciseOrder = exerciseOrder, setOrder = setOrder
            )
        )
    }

    suspend fun updateSet(set: WorkoutSet) = dao.updateSet(set)
    suspend fun deleteSet(id: Long) = dao.deleteSet(id)

    suspend fun getSetsWithExerciseNames(sessionId: Long): List<SetWithExercise> =
        dao.getSetsWithExerciseNames(sessionId)

    suspend fun lastPerformance(exerciseId: Long, currentSessionId: Long): List<WorkoutSet> {
        val sessionId = dao.findLastSessionIdWithExercise(exerciseId, currentSessionId) ?: return emptyList()
        return dao.getSetsOfExercise(sessionId, exerciseId)
    }

    suspend fun removeExerciseFromSession(sessionId: Long, exerciseId: Long) = db.withTransaction {
        dao.getSetsOfExercise(sessionId, exerciseId).forEach { dao.deleteSet(it.id) }
    }
}
