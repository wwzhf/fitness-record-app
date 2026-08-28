package com.wc.workout.ui.calendar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.wc.workout.AppContainer

@Composable
fun CalendarScreen(container: AppContainer) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("日历") }
}
