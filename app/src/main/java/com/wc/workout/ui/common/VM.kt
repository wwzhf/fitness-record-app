package com.wc.workout.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wc.workout.WorkoutApp
import com.wc.workout.AppContainer

@Composable
fun appContainer(): AppContainer =
    (LocalContext.current.applicationContext as WorkoutApp).container

class VMFactory(private val creator: () -> ViewModel) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = creator() as T
}

@Composable
inline fun <reified VM : ViewModel> viewModelWith(noinline creator: () -> VM): VM =
    viewModel(factory = VMFactory(creator))
