package com.wc.workout.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises WHERE isArchived = 0 ORDER BY name")
    fun observeActive(): Flow<List<Exercise>>

    /** 未归档动作按最近使用排序（从未用过排最后，再按名称）；IFNULL 兼容旧版 SQLite */
    @Query(
        """SELECT e.* FROM exercises e
           LEFT JOIN workout_sets ws ON ws.exerciseId = e.id
           LEFT JOIN workout_sessions s ON s.id = ws.sessionId
           WHERE e.isArchived = 0
           GROUP BY e.id
           ORDER BY IFNULL(MAX(s.startTime), 0) DESC, e.name"""
    )
    fun observeActiveByRecentUse(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises WHERE isArchived = 1 ORDER BY name")
    fun observeArchived(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises")
    suspend fun getAll(): List<Exercise>

    @Query("DELETE FROM exercises")
    suspend fun deleteAll()

    @Query("SELECT * FROM exercises WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): Exercise?

    @Query("SELECT * FROM exercises WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Exercise?

    @Insert
    suspend fun insert(exercise: Exercise): Long

    @Query("UPDATE exercises SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE exercises SET isArchived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query("UPDATE exercises SET createdAt = :createdAt, isArchived = :isArchived WHERE id = :id")
    suspend fun overwriteMeta(id: Long, createdAt: Long, isArchived: Boolean)

    @Query("SELECT COUNT(*) FROM workout_sets WHERE exerciseId = :id")
    suspend fun countSetsForExercise(id: Long): Int

    @Query("DELETE FROM exercises WHERE id = :id")
    suspend fun deleteById(id: Long)
}
