package com.wc.workout.ui.workout

import android.util.Log
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WorkoutSessionViewModel(
    private val workoutRepo: WorkoutRepository,
    private val exerciseRepo: ExerciseRepository,
    private val sessionId: Long,
) : ViewModel() {

    private val _session = MutableStateFlow<WorkoutSession?>(null)
    val session: StateFlow<WorkoutSession?> = _session.asStateFlow()

    init {
        viewModelScope.launch {
            _session.value = runCatching { workoutRepo.getSession(sessionId) }.getOrNull()
        }
    }

    val groups: StateFlow<List<SetWithExercise>> =
        workoutRepo.observeSetsWithExerciseNames(sessionId)
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

    fun setSessionTitle(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            runCatching { workoutRepo.setSessionTitle(sessionId, title.trim()) }
                .onFailure { Log.w("WorkoutSessionViewModel", "setSessionTitle failed", it) }
            _session.value = runCatching { workoutRepo.getSession(sessionId) }.getOrNull()
        }
    }

    fun setDurationMinutes(minutes: Int) {
        if (minutes <= 0) return
        viewModelScope.launch {
            runCatching {
                val s = workoutRepo.getSession(sessionId) ?: return@launch
                workoutRepo.endSession(sessionId, s.startTime + minutes * 60_000L)
            }.onFailure { Log.w("WorkoutSessionViewModel", "setDurationMinutes failed", it) }
            _session.value = runCatching { workoutRepo.getSession(sessionId) }.getOrNull()
        }
    }

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
        runCatching {
            when (val r = exerciseRepo.addExercise(name)) {
                is ExerciseNameResult.Success -> exerciseRepo.getById(r.id)
                ExerciseNameResult.Duplicate -> null
            }
        }.getOrElse { Log.w("WorkoutSessionViewModel", "createExercise failed", it); null }

    suspend fun addSet(exerciseId: Long, weightKg: Double, reps: Int) {
        runCatching { workoutRepo.addSet(sessionId, exerciseId, weightKg, reps) }
            .onFailure { Log.w("WorkoutSessionViewModel", "addSet failed", it) }
    }

    suspend fun updateSet(set: WorkoutSet) {
        runCatching { workoutRepo.updateSet(set) }
            .onFailure { Log.w("WorkoutSessionViewModel", "updateSet failed", it) }
    }

    suspend fun deleteSet(id: Long) {
        runCatching { workoutRepo.deleteSet(id) }
            .onFailure { Log.w("WorkoutSessionViewModel", "deleteSet failed", it) }
    }

    suspend fun removeExercise(exerciseId: Long) {
        runCatching { workoutRepo.removeExerciseFromSession(sessionId, exerciseId) }
            .onFailure { Log.w("WorkoutSessionViewModel", "removeExercise failed", it) }
        pendingExerciseIds.value = pendingExerciseIds.value - exerciseId
    }

    suspend fun lastPerformance(exerciseId: Long): List<WorkoutSet> =
        workoutRepo.lastPerformance(exerciseId, sessionId)

    suspend fun endSession() {
        runCatching { workoutRepo.endSession(sessionId) }
            .onFailure { Log.w("WorkoutSessionViewModel", "endSession failed", it) }
    }

    suspend fun abandon() {
        runCatching { workoutRepo.abandonSession(sessionId) }
            .onFailure { Log.w("WorkoutSessionViewModel", "abandon failed", it) }
    }
}
