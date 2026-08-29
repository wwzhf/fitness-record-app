package com.wc.workout.ui.calendar

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wc.workout.data.local.SetWithExercise
import com.wc.workout.data.local.WeightRecord
import com.wc.workout.data.local.WorkoutSession
import com.wc.workout.data.repository.WeightRepository
import com.wc.workout.data.repository.WorkoutRepository
import com.wc.workout.ui.common.endOfDayMillisExclusive
import com.wc.workout.ui.common.startOfDayMillis
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val weightRepo: WeightRepository,
    private val workoutRepo: WorkoutRepository,
) : ViewModel() {

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month

    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    val selectedDate: StateFlow<LocalDate?> = _selectedDate

    val weightsForMonth: StateFlow<Map<Long, WeightRecord>> = _month
        .flatMapLatest { m -> weightRepo.observeBetween(m.atDay(1), m.atEndOfMonth()) }
        .map { list -> list.associateBy { it.dateEpochDay } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val sessionsForMonth: StateFlow<List<WorkoutSession>> = _month
        .flatMapLatest { m ->
            workoutRepo.observeSessionsBetween(
                startOfDayMillis(m.atDay(1)),
                endOfDayMillisExclusive(m.atEndOfMonth())
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectDay(date: LocalDate?) { _selectedDate.value = date }
    fun prevMonth() { _month.value = _month.value.minusMonths(1) }
    fun nextMonth() { _month.value = _month.value.plusMonths(1) }
    fun goToday() { _month.value = YearMonth.now(); _selectedDate.value = LocalDate.now() }

    suspend fun weightFor(date: LocalDate): WeightRecord? = weightRepo.getByDate(date)

    suspend fun sessionsFor(date: LocalDate): List<WorkoutSession> =
        workoutRepo.getSessionsBetween(startOfDayMillis(date), endOfDayMillisExclusive(date))

    fun saveWeight(date: LocalDate, kgText: String) {
        val kg = kgText.toDoubleOrNull() ?: return
        if (kg <= 0.0) return
        viewModelScope.launch {
            runCatching { weightRepo.saveWeight(date, kg) }
                .onFailure { Log.w("CalendarViewModel", "saveWeight failed", it) }
        }
    }

    suspend fun recentTitles(): List<String> = workoutRepo.recentTitles()

    fun addPastWorkout(
        date: LocalDate,
        title: String,
        startMillis: Long,
        endMillis: Long,
        onCreated: (Long) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching { workoutRepo.addPastSession(title.trim(), startMillis, endMillis) }
                .onSuccess { onCreated(it) }
                .onFailure { Log.w("CalendarViewModel", "addPastWorkout failed", it) }
        }
    }

    suspend fun sessionDetail(sessionId: Long): List<SetWithExercise> =
        workoutRepo.getSetsWithExerciseNames(sessionId)

    suspend fun deleteSession(id: Long) {
        runCatching { workoutRepo.abandonSession(id) }
            .onFailure { Log.w("CalendarViewModel", "deleteSession failed", it) }
    }
}
