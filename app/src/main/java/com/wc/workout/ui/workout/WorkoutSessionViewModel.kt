package com.wc.workout.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wc.workout.data.local.SetWithExercise
import com.wc.workout.data.local.WorkoutSession
import com.wc.workout.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class WorkoutSessionViewModel(
    private val workoutRepo: WorkoutRepository,
    private val sessionId: Long,
) : ViewModel() {

    val session: StateFlow<WorkoutSession?> = flow { emit(workoutRepo.getSession(sessionId)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val reload = MutableStateFlow(0)

    val groups: StateFlow<List<SetWithExercise>> = reload
        .map { workoutRepo.getSetsWithExerciseNames(sessionId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun refresh() { reload.value++ }

    suspend fun endSession() = workoutRepo.endSession(sessionId)
    suspend fun abandon() = workoutRepo.abandonSession(sessionId)
}
