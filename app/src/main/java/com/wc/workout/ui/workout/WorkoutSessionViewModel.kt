package com.wc.workout.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wc.workout.data.local.Exercise
import com.wc.workout.data.local.SetWithExercise
import com.wc.workout.data.local.WorkoutSession
import com.wc.workout.data.local.WorkoutSet
import com.wc.workout.data.repository.ExerciseNameResult
import com.wc.workout.data.repository.ExerciseRepository
import com.wc.workout.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class WorkoutSessionViewModel(
    private val workoutRepo: WorkoutRepository,
    private val exerciseRepo: ExerciseRepository,
    private val sessionId: Long,
) : ViewModel() {

    val session: StateFlow<WorkoutSession?> = flow { emit(workoutRepo.getSession(sessionId)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val reload = MutableStateFlow(0)

    val groups: StateFlow<List<SetWithExercise>> = reload
        .map { workoutRepo.getSetsWithExerciseNames(sessionId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val exercises: StateFlow<List<Exercise>> = exerciseRepo.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val exerciseQuery = MutableStateFlow("")

    val filteredExercises: StateFlow<List<Exercise>> =
        combine(exercises, exerciseQuery) { list, q ->
            if (q.isBlank()) list else list.filter { it.name.contains(q, ignoreCase = true) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 已选但尚无组记录的动作（未持久化，仅本次页面生命周期） */
    val pendingExerciseIds = MutableStateFlow<List<Long>>(emptyList())

    fun refresh() { reload.value++ }

    /** 返回 false 表示该动作已有卡片（用于提示/滚动定位） */
    suspend fun addPendingExercise(exerciseId: Long): Boolean {
        if (groups.value.any { it.set.exerciseId == exerciseId }) return false
        if (exerciseId !in pendingExerciseIds.value) {
            pendingExerciseIds.value = pendingExerciseIds.value + exerciseId
        }
        return true
    }

    /** 新建动作入库；重名返回 null */
    suspend fun createExercise(name: String): Exercise? =
        when (val r = exerciseRepo.addExercise(name)) {
            is ExerciseNameResult.Success -> exerciseRepo.getById(r.id)
            ExerciseNameResult.Duplicate -> null
        }

    suspend fun addSet(exerciseId: Long, weightKg: Double, reps: Int) {
        workoutRepo.addSet(sessionId, exerciseId, weightKg, reps)
        refresh()
    }

    suspend fun updateSet(set: WorkoutSet) { workoutRepo.updateSet(set); refresh() }
    suspend fun deleteSet(id: Long) { workoutRepo.deleteSet(id); refresh() }
    suspend fun removeExercise(exerciseId: Long) {
        workoutRepo.removeExerciseFromSession(sessionId, exerciseId)
        refresh()
    }

    suspend fun lastPerformance(exerciseId: Long): List<WorkoutSet> =
        workoutRepo.lastPerformance(exerciseId, sessionId)

    suspend fun endSession() = workoutRepo.endSession(sessionId)
    suspend fun abandon() = workoutRepo.abandonSession(sessionId)
}
