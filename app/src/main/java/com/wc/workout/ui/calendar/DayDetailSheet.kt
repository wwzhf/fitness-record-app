package com.wc.workout.ui.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.wc.workout.data.local.SetWithExercise
import com.wc.workout.data.local.WeightRecord
import com.wc.workout.data.local.WorkoutSession
import com.wc.workout.ui.common.WeightEditDialog
import com.wc.workout.ui.common.formatDuration
import com.wc.workout.ui.common.formatSetSummary
import com.wc.workout.ui.common.formatTime
import com.wc.workout.ui.common.kgLabel
import java.time.LocalDate
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DayDetailSheet(
    date: LocalDate,
    vm: CalendarViewModel,
    onDismiss: () -> Unit,
    onOpenSession: (Long) -> Unit
) {
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val dayWeight by produceState<WeightRecord?>(initialValue = null, date, refresh) {
        value = vm.weightFor(date)
    }
    val daySessions by produceState<List<WorkoutSession>>(initialValue = emptyList(), date, refresh) {
        value = vm.sessionsFor(date)
    }
    var showWeightDialog by remember { mutableStateOf(false) }
    var showDeleteWeightDialog by remember { mutableStateOf(false) }
    var showAddPastDialog by remember { mutableStateOf(false) }
    val canAdd = !date.isAfter(LocalDate.now())

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
                    TextButton(onClick = { showDeleteWeightDialog = true }) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // —— 健身记录 ——
            Text("健身记录", style = MaterialTheme.typography.titleMedium)
            if (canAdd) {
                TextButton(onClick = { showAddPastDialog = true }) { Text("+ 添加健身记录") }
            }
            if (daySessions.isEmpty()) {
                Text(
                    "这一天没有健身记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                daySessions.forEach { session ->
                    SessionCard(
                        session = session,
                        vm = vm,
                        refresh = refresh,
                        onOpenSession = onOpenSession,
                        onDeleted = { refresh++ }
                    )
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

    if (showDeleteWeightDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteWeightDialog = false },
            title = { Text("删除体重") },
            text = { Text("删除 ${date.monthValue}月${date.dayOfMonth}日 的体重记录？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteWeight(date)
                    showDeleteWeightDialog = false
                    refresh++
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteWeightDialog = false }) { Text("取消") } }
        )
    }

    if (showAddPastDialog) {
        AddPastWorkoutDialog(
            date = date,
            vm = vm,
            onCreated = { id ->
                showAddPastDialog = false
                onDismiss()
                onOpenSession(id)
            },
            onDismiss = { showAddPastDialog = false }
        )
    }
}

@Composable
private fun SessionCard(
    session: WorkoutSession,
    vm: CalendarViewModel,
    refresh: Int,
    onOpenSession: (Long) -> Unit,
    onDeleted: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<List<SetWithExercise>>(emptyList()) }
    var showDelete by remember { mutableStateOf(false) }

    LaunchedEffect(session.id, refresh, expanded) {
        if (expanded) detail = vm.sessionDetail(session.id)
    }

    ElevatedCard(
        Modifier.fillMaxWidth().clickable { expanded = !expanded }
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
                if (session.note.isNotBlank()) {
                    Text(session.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (detail.isNotEmpty()) {
                    val totalVolume = detail.sumOf { it.set.weightKg * it.set.reps }
                    // 全自重训练容量为 0，不展示该行
                    if (totalVolume > 0) {
                        Text(
                            "总容量 %,d kg".format(totalVolume.toLong()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                detail.groupBy { it.set.exerciseOrder }.toSortedMap().forEach { (_, rows) ->
                    Text(
                        "${rows.first().exerciseName}：" +
                            rows.joinToString(", ") { formatSetSummary(it.set.weightKg, it.set.reps) },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onOpenSession(session.id) }) { Text("编辑") }
                    TextButton(onClick = { showDelete = true }) {
                        Text("删除该次记录", color = MaterialTheme.colorScheme.error)
                    }
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
