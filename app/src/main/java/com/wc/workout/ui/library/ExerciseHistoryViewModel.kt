package com.wc.workout.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wc.workout.data.local.Exercise
import com.wc.workout.data.repository.ExerciseHistoryEntry
import com.wc.workout.data.repository.ExerciseRepository
import com.wc.workout.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

class ExerciseHistoryViewModel(
    private val repo: WorkoutRepository,
    exerciseRepo: ExerciseRepository,
    exerciseId: Long
) : ViewModel() {

    val exercise: StateFlow<Exercise?> = flow { emit(exerciseRepo.getById(exerciseId)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val entries: StateFlow<List<ExerciseHistoryEntry>> = flow { emit(repo.getExerciseHistory(exerciseId)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
