package com.wc.workout.ui.trend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wc.workout.data.local.SessionVolume
import com.wc.workout.data.local.WeightRecord
import com.wc.workout.data.repository.WeightRepository
import com.wc.workout.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

enum class TrendRange(val label: String, val days: Int?) {
    D30("近30天", 30), D90("近90天", 90), ALL("全部", null)
}

enum class TrendMetric(val label: String) {
    WEIGHT("体重"), VOLUME("训练量")
}

class TrendViewModel(
    weightRepo: WeightRepository,
    workoutRepo: WorkoutRepository
) : ViewModel() {
    val weights: StateFlow<List<WeightRecord>> = weightRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val volumes: StateFlow<List<SessionVolume>> = workoutRepo.observeSessionVolumes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val range = MutableStateFlow(TrendRange.D30)

    val metric = MutableStateFlow(TrendMetric.WEIGHT)
}
