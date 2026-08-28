package com.wc.workout.data.repository

import com.wc.workout.data.local.AppDatabase
import com.wc.workout.data.local.Exercise
import kotlinx.coroutines.flow.Flow

sealed interface ExerciseNameResult {
    data class Success(val id: Long) : ExerciseNameResult
    data object Duplicate : ExerciseNameResult
}

class ExerciseRepository(private val db: AppDatabase) {
    private val dao = db.exerciseDao()

    fun observeActive(): Flow<List<Exercise>> = dao.observeActive()
    fun observeArchived(): Flow<List<Exercise>> = dao.observeArchived()

    suspend fun addExercise(name: String): ExerciseNameResult {
        val trimmed = name.trim()
        if (dao.findByName(trimmed) != null) return ExerciseNameResult.Duplicate
        return ExerciseNameResult.Success(dao.insert(Exercise(name = trimmed, createdAt = System.currentTimeMillis())))
    }

    suspend fun rename(id: Long, newName: String): ExerciseNameResult {
        val trimmed = newName.trim()
        val existing = dao.findByName(trimmed)
        if (existing != null && existing.id != id) return ExerciseNameResult.Duplicate
        dao.rename(id, trimmed)
        return ExerciseNameResult.Success(id)
    }

    suspend fun setArchived(id: Long, archived: Boolean) = dao.setArchived(id, archived)

    suspend fun isReferenced(id: Long): Boolean = dao.countSetsForExercise(id) > 0

    /** 只有从未被引用过的动作允许物理删除 */
    suspend fun tryDelete(id: Long): Boolean {
        if (isReferenced(id)) return false
        dao.deleteById(id)
        return true
    }

    suspend fun getById(id: Long): Exercise? = dao.getById(id)
}
