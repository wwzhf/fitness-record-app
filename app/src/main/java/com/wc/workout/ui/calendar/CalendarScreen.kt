package com.wc.workout.ui.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wc.workout.AppContainer
import com.wc.workout.ui.common.viewModelWith

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(container: AppContainer, onOpenSession: (Long) -> Unit) {
    val vm: CalendarViewModel = viewModelWith {
        CalendarViewModel(container.weightRepository, container.workoutRepository)
    }
    val month by vm.month.collectAsState()
    val weights by vm.weightsForMonth.collectAsState()
    val sessions by vm.sessionsForMonth.collectAsState()
    val selected by vm.selectedDate.collectAsState()
    var showMonthPicker by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::prevMonth) {
                Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "上月")
            }
            Text(
                "${month.year}年${month.monthValue}月 ▾",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).clickable { showMonthPicker = true }
            )
            IconButton(onClick = vm::nextMonth) {
                Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "下月")
            }
            TextButton(onClick = vm::goToday) { Text("今") }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                Text(
                    it,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        var dragAccum by remember { mutableFloatStateOf(0f) }
        MonthGrid(
            month, weights, sessions,
            onDayClick = vm::selectDay,
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragAccum = 0f },
                        onDragEnd = {
                            if (dragAccum < -120f) vm.nextMonth()
                            else if (dragAccum > 120f) vm.prevMonth()
                            dragAccum = 0f
                        }
                    ) { change, dragAmount ->
                        dragAccum += dragAmount
                        change.consume()
                    }
                }
        )
    }

    selected?.let { date ->
        DayDetailSheet(date, vm, onDismiss = { vm.selectDay(null) }, onOpenSession = onOpenSession)
    }

    if (showMonthPicker) {
        var pickYear by remember { mutableStateOf(month.year) }
        AlertDialog(
            onDismissRequest = { showMonthPicker = false },
            title = { Text("选择年月") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { pickYear-- }) { Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "上一年") }
                        Text("$pickYear", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                        IconButton(onClick = { pickYear++ }) { Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "下一年") }
                    }
                    for (r in 0..2) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (c in 0..3) {
                                val m = r * 4 + c + 1
                                val selected = pickYear == month.year && m == month.monthValue
                                TextButton(
                                    onClick = {
                                        vm.goToMonth(pickYear, m)
                                        showMonthPicker = false
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("${m}月", color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showMonthPicker = false }) { Text("取消") } }
        )
    }
}
