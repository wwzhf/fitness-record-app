package com.wc.workout.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Insert
    suspend fun insertSession(session: WorkoutSession): Long

    @Query("UPDATE workout_sessions SET endTime = :endTime WHERE id = :id")
    suspend fun setEndTime(id: Long, endTime: Long)

    @Query("UPDATE workout_sessions SET title = :title WHERE id = :id")
    suspend fun setSessionTitle(id: Long, title: String)

    @Query("DELETE FROM workout_sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("SELECT * FROM workout_sessions WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    fun observeOngoing(): Flow<WorkoutSession?>

    @Query("SELECT * FROM workout_sessions WHERE startTime BETWEEN :start AND :end ORDER BY startTime")
    fun observeSessionsBetween(start: Long, end: Long): Flow<List<WorkoutSession>>

    @Query("SELECT * FROM workout_sessions WHERE startTime BETWEEN :start AND :end ORDER BY startTime")
    suspend fun getSessionsBetween(start: Long, end: Long): List<WorkoutSession>

    @Query("SELECT * FROM workout_sessions WHERE id = :id LIMIT 1")
    suspend fun getSession(id: Long): WorkoutSession?

    @Query("SELECT * FROM workout_sessions ORDER BY startTime DESC LIMIT :limit")
    suspend fun getRecentSessions(limit: Int): List<WorkoutSession>

    @Query("SELECT * FROM workout_sessions")
    suspend fun getAllSessions(): List<WorkoutSession>

    /** 删除 [start, end) 内开始的训练（end 为次日零点，须保持开区间） */
    @Query("DELETE FROM workout_sessions WHERE startTime >= :start AND startTime < :end")
    suspend fun deleteSessionsBetween(start: Long, end: Long)

    @Insert
    suspend fun insertSet(set: WorkoutSet): Long

    @Update
    suspend fun updateSet(set: WorkoutSet)

    @Query("DELETE FROM workout_sets WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Query("SELECT * FROM workout_sets WHERE sessionId = :sessionId ORDER BY exerciseOrder, setOrder")
    suspend fun getSetsForSession(sessionId: Long): List<WorkoutSet>

    @Query("SELECT * FROM workout_sets")
    suspend fun getAllSets(): List<WorkoutSet>

    @Query("SELECT MAX(exerciseOrder) FROM workout_sets WHERE sessionId = :sessionId")
    suspend fun maxExerciseOrder(sessionId: Long): Int?

    @Query("SELECT MAX(setOrder) FROM workout_sets WHERE sessionId = :sessionId AND exerciseId = :exerciseId")
    suspend fun maxSetOrder(sessionId: Long, exerciseId: Long): Int?

    @Query(
        """SELECT ws.sessionId FROM workout_sets ws
           INNER JOIN workout_sessions s ON s.id = ws.sessionId
           WHERE ws.exerciseId = :exerciseId AND ws.sessionId != :currentSessionId
           ORDER BY s.startTime DESC LIMIT 1"""
    )
    suspend fun findLastSessionIdWithExercise(exerciseId: Long, currentSessionId: Long): Long?

    @Query("SELECT * FROM workout_sets WHERE sessionId = :sessionId AND exerciseId = :exerciseId ORDER BY setOrder")
    suspend fun getSetsOfExercise(sessionId: Long, exerciseId: Long): List<WorkoutSet>

    @Query(
        """SELECT ws.*, e.name AS exerciseName FROM workout_sets ws
           INNER JOIN exercises e ON e.id = ws.exerciseId
           WHERE ws.sessionId = :sessionId
           ORDER BY ws.exerciseOrder, ws.setOrder"""
    )
    suspend fun getSetsWithExerciseNames(sessionId: Long): List<SetWithExercise>
}
