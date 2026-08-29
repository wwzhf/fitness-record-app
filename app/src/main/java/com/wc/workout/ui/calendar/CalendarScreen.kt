package com.wc.workout.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.launch

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
    val hasWorkout = daySessions.isNotEmpty()
    val cellShape = RoundedCornerShape(8.dp)
    Column(
        modifier
            .clip(cellShape)
            .then(
                if (hasWorkout) {
                    Modifier.background(
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                        cellShape
                    )
                } else {
                    Modifier
                }
            )
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
        val titles = daySessions.sortedBy { it.startTime }.map { it.title }
        titles.take(2).forEach { t ->
            Text(
                t,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (titles.size > 2) {
            Text(
                "+${titles.size - 2}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayDetailSheet(
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
                detail.groupBy { it.set.exerciseOrder }.toSortedMap().forEach { (_, rows) ->
                    Text(
                        "${rows.first().exerciseName}：" +
                            rows.joinToString(", ") { "${it.set.weightKg.displayKg()}kg×${it.set.reps}" },
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

@Composable
private fun AddPastWorkoutDialog(
    date: LocalDate,
    vm: CalendarViewModel,
    onCreated: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var startText by remember {
        mutableStateOf(LocalTime.now().truncatedTo(ChronoUnit.MINUTES)
            .format(DateTimeFormatter.ofPattern("HH:mm")))
    }
    var endText by remember {
        mutableStateOf(LocalTime.now().plusMinutes(60).truncatedTo(ChronoUnit.MINUTES)
            .format(DateTimeFormatter.ofPattern("HH:mm")))
    }
    var error by remember { mutableStateOf<String?>(null) }
    var titles by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) { titles = vm.recentTitles() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${date.monthValue}月${date.dayOfMonth}日 补记健身") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; error = null },
                    label = { Text("标题（留空自动用日期）") },
                    singleLine = true
                )
                if (titles.isNotEmpty()) {
                    Text(
                        "最近使用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    titles.take(3).forEach { t ->
                        TextButton(onClick = { title = t }) { Text(t) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startText,
                        onValueChange = { startText = it; error = null },
                        label = { Text("开始 HH:mm") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endText,
                        onValueChange = { endText = it; error = null },
                        label = { Text("结束 HH:mm") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = parseHm(startText)
                val parsedEnd = parseHm(endText)
                when {
                    parsed == null -> error = "开始时间格式应为 HH:mm"
                    parsedEnd == null -> error = "结束时间格式应为 HH:mm"
                    else -> {
                        val startMillis = date.atTime(parsed.first, parsed.second)
                            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        val endMillis = date.atTime(parsedEnd.first, parsedEnd.second)
                            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        if (endMillis <= startMillis) {
                            error = "结束时间需晚于开始时间"
                        } else {
                            val finalTitle = title.ifBlank {
                                date.format(DateTimeFormatter.ISO_LOCAL_DATE) + " 训练"
                            }
                            vm.addPastWorkout(date, finalTitle, startMillis, endMillis, onCreated)
                        }
                    }
                }
            }) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun parseHm(text: String): Pair<Int, Int>? {
    val parts = text.trim().split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h to m
}
