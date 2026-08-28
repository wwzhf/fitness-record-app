package com.wc.workout.ui.library

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wc.workout.data.local.Exercise
import com.wc.workout.data.repository.ExerciseNameResult
import com.wc.workout.data.repository.ExerciseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class ExerciseLibraryViewModel(private val repo: ExerciseRepository) : ViewModel() {

    val query = MutableStateFlow("")

    val exercises: StateFlow<List<Exercise>> =
        combine(repo.observeActive(), query) { list, q ->
            if (q.isBlank()) list else list.filter { it.name.contains(q, ignoreCase = true) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val archived: StateFlow<List<Exercise>> = repo.observeArchived()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun add(name: String): ExerciseNameResult = repo.addExercise(name)
    suspend fun rename(id: Long, name: String): ExerciseNameResult = repo.rename(id, name)

    suspend fun archive(id: Long) {
        runCatching { repo.setArchived(id, true) }
            .onFailure { Log.w("ExerciseLibraryViewModel", "archive failed", it) }
    }

    suspend fun unarchive(id: Long) {
        runCatching { repo.setArchived(id, false) }
            .onFailure { Log.w("ExerciseLibraryViewModel", "unarchive failed", it) }
    }

    suspend fun delete(id: Long): Boolean =
        runCatching { repo.tryDelete(id) }
            .getOrElse { Log.w("ExerciseLibraryViewModel", "delete failed", it); false }

    suspend fun isReferenced(id: Long): Boolean = repo.isReferenced(id)
}
