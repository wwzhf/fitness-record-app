package com.wc.workout.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wc.workout.AppContainer
import com.wc.workout.data.local.SetWithExercise
import com.wc.workout.data.local.WeightRecord
import com.wc.workout.data.local.WorkoutSession
import com.wc.workout.ui.common.WeightEditDialog
import com.wc.workout.ui.common.displayKg
import com.wc.workout.ui.common.formatDuration
import com.wc.workout.ui.common.formatTime
import com.wc.workout.ui.common.kgLabel
import com.wc.workout.ui.common.viewModelWith
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(container: AppContainer) {
    val vm: CalendarViewModel = viewModelWith {
        CalendarViewModel(container.weightRepository, container.workoutRepository)
    }
    val month by vm.month.collectAsState()
    val weights by vm.weightsForMonth.collectAsState()
    val sessions by vm.sessionsForMonth.collectAsState()
    val selected by vm.selectedDate.collectAsState()

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::prevMonth) {
                Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "上月")
            }
            Text(
                "${month.year}年${month.monthValue}月",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
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
        MonthGrid(month, weights, sessions, onDayClick = vm::selectDay, modifier = Modifier.fillMaxWidth())
    }

    selected?.let { date ->
        DayDetailSheet(date, vm, onDismiss = { vm.selectDay(null) })
    }
}

@Composable
fun MonthGrid(
    month: YearMonth,
    weights: Map<Long, WeightRecord>,
    sessions: List<WorkoutSession>,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val first = month.atDay(1)
    val offset = first.dayOfWeek.value - 1 // 周一起始：周一偏移 0，周日偏移 6
    val rows = (offset + month.lengthOfMonth() + 6) / 7
    val zone = ZoneId.systemDefault()
    val sessionsByDay = sessions.groupBy {
        Instant.ofEpochMilli(it.startTime).atZone(zone).toLocalDate()
    }

    Column(modifier) {
        repeat(rows) { r ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { c ->
                    val index = r * 7 + c
                    val date = first.minusDays(offset.toLong()).plusDays(index.toLong())
                    if (date.month == month.month) {
                        DayCell(
                            date = date,
                            weight = weights[date.toEpochDay()],
                            daySessions = sessionsByDay[date].orEmpty(),
                            modifier = Modifier.weight(1f),
                            onDayClick = onDayClick
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    weight: WeightRecord?,
    daySessions: List<WorkoutSession>,
    modifier: Modifier,
    onDayClick: (LocalDate) -> Unit
) {
    val isToday = date == LocalDate.now()
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onDayClick(date) }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isToday) {
                Box(
                    Modifier.size(24.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            Text(
                "${date.dayOfMonth}",
                style = MaterialTheme.typography.bodySmall,
                color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            weight?.weightKg?.kgLabel() ?: "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            daySessions.take(3).forEach {
                Box(Modifier.size(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayDetailSheet(date: LocalDate, vm: CalendarViewModel, onDismiss: () -> Unit) {
    var refresh by remember { mutableIntStateOf(0) }
    val dayWeight by produceState<WeightRecord?>(initialValue = null, date, refresh) {
        value = vm.weightFor(date)
    }
    val daySessions by produceState<List<WorkoutSession>>(initialValue = emptyList(), date, refresh) {
        value = vm.sessionsFor(date)
    }
    var showWeightDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("${date.monthValue}月${date.dayOfMonth}日", style = MaterialTheme.typography.titleLarge)

            // —— 体重 ——
            if (dayWeight == null) {
                TextButton(onClick = { showWeightDialog = true }) { Text("记录体重") }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "体重 ${dayWeight!!.weightKg.kgLabel()} kg（${formatTime(dayWeight!!.recordedAt)} 记录）",
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { showWeightDialog = true }) { Text("修改") }
                }
            }

            // —— 健身记录 ——
            Text("健身记录", style = MaterialTheme.typography.titleMedium)
            if (daySessions.isEmpty()) {
                Text(
                    "这一天没有健身记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                daySessions.forEach { session ->
                    SessionCard(session = session, vm = vm, onDeleted = { refresh++ })
                }
            }
        }
    }

    if (showWeightDialog) {
        WeightEditDialog(
            initialKg = dayWeight?.weightKg,
            onSaved = { vm.saveWeight(date, it); refresh++ },
            onDismiss = { showWeightDialog = false }
        )
    }
}

@Composable
private fun SessionCard(session: WorkoutSession, vm: CalendarViewModel, onDeleted: () -> Unit) {
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<List<SetWithExercise>>(emptyList()) }
    var showDelete by remember { mutableStateOf(false) }

    ElevatedCard(
        Modifier.fillMaxWidth().clickable {
            expanded = !expanded
            if (expanded && detail.isEmpty()) {
                scope.launch { detail = vm.sessionDetail(session.id) }
            }
        }
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(session.title, style = MaterialTheme.typography.titleMedium)
            val endText = session.endTime?.let { formatTime(it) } ?: "进行中"
            val durationSec = ((session.endTime ?: System.currentTimeMillis()) - session.startTime) / 1000
            Text(
                "${formatTime(session.startTime)} – $endText · ${formatDuration(durationSec)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (expanded) {
                detail.groupBy { it.set.exerciseOrder }.toSortedMap().forEach { (_, rows) ->
                    Text(
                        "${rows.first().exerciseName}：" +
                            rows.joinToString(", ") { "${it.set.weightKg.displayKg()}kg×${it.set.reps}" },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                TextButton(onClick = { showDelete = true }) {
                    Text("删除该次记录", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除记录") },
            text = { Text("删除「${session.title}」及其全部组记录？不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        vm.deleteSession(session.id)
                        showDelete = false
                        onDeleted()
                    }
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("取消") } }
        )
    }
}
