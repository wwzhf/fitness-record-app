package com.wc.workout.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises WHERE isArchived = 0 ORDER BY name")
    fun observeActive(): Flow<List<Exercise>>

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

    @Query("SELECT COUNT(*) FROM workout_sets WHERE exerciseId = :id")
    suspend fun countSetsForExercise(id: Long): Int

    @Query("DELETE FROM exercises WHERE id = :id")
    suspend fun deleteById(id: Long)
}
