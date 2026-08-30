package com.wc.workout.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wc.workout.data.local.Exercise
import com.wc.workout.data.repository.ExerciseHistoryEntry
import com.wc.workout.data.repository.ExerciseRepository
import com.wc.workout.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ExerciseHistoryViewModel(
    repo: WorkoutRepository,
    exerciseRepo: ExerciseRepository,
    exerciseId: Long
) : ViewModel() {

    val exercise: StateFlow<Exercise?> = exerciseRepo.observeById(exerciseId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 动作名或组数据变化时实时刷新（如在训练页补录组后返回本页） */
    val entries: StateFlow<List<ExerciseHistoryEntry>> = repo.observeExerciseHistory(exerciseId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
