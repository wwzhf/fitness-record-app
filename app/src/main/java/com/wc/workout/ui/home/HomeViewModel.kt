package com.wc.workout.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wc.workout.data.local.WeightRecord
import com.wc.workout.data.local.WorkoutSession
import com.wc.workout.data.repository.WeightRepository
import com.wc.workout.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class HomeViewModel(
    private val weightRepo: WeightRepository,
    private val workoutRepo: WorkoutRepository,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()

    val todayWeight: StateFlow<WeightRecord?> = weightRepo.observeBetween(today, today)
        .map { it.lastOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun saveWeight(kgText: String) {
        val kg = kgText.toDoubleOrNull() ?: return
        if (kg <= 0.0) return
        viewModelScope.launch { weightRepo.saveWeight(today, kg) }
    }

    val ongoingSession: StateFlow<WorkoutSession?> = workoutRepo.observeOngoing()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun startSession(title: String, onStarted: (Long) -> Unit) {
        viewModelScope.launch {
            val finalTitle = title.ifBlank { defaultTitle() }
            onStarted(workoutRepo.startSession(finalTitle))
        }
    }

    /** Task 9 会扩展：开始健身、进行中会话 */
    suspend fun recentTitles(): List<String> = workoutRepo.recentTitles()

    fun defaultTitle(): String =
        today.format(DateTimeFormatter.ISO_LOCAL_DATE) + " 训练"
}
