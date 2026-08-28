package com.wc.workout.ui.home

import android.util.Log
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

    val todayWeight: StateFlow<WeightRecord?> = weightRepo.observeAll()
        .map { list -> list.lastOrNull { it.dateEpochDay == LocalDate.now().toEpochDay() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun saveWeight(kgText: String) {
        val kg = kgText.toDoubleOrNull() ?: return
        if (kg <= 0.0) return
        val today = LocalDate.now()
        viewModelScope.launch {
            runCatching { weightRepo.saveWeight(today, kg) }
                .onFailure { Log.w("HomeViewModel", "saveWeight failed", it) }
        }
    }

    val ongoingSession: StateFlow<WorkoutSession?> = workoutRepo.observeOngoing()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun startSession(title: String, onStarted: (Long) -> Unit) {
        viewModelScope.launch {
            runCatching {
                val finalTitle = title.ifBlank { defaultTitle() }
                onStarted(workoutRepo.startSession(finalTitle))
            }.onFailure { Log.w("HomeViewModel", "startSession failed", it) }
        }
    }

    /** Task 9 会扩展：开始健身、进行中会话 */
    suspend fun recentTitles(): List<String> = workoutRepo.recentTitles()

    fun defaultTitle(): String {
        val today = LocalDate.now()
        return today.format(DateTimeFormatter.ISO_LOCAL_DATE) + " 训练"
    }
}
