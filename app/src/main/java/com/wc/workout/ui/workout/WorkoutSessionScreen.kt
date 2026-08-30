package com.wc.workout.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wc.workout.AppContainer
import com.wc.workout.data.local.Exercise
import com.wc.workout.data.local.WorkoutSet
import com.wc.workout.ui.common.displayKg
import com.wc.workout.ui.common.formatDuration
import com.wc.workout.ui.common.formatSetRow
import com.wc.workout.ui.common.formatSetSummary
import com.wc.workout.ui.common.viewModelWith
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 一张动作卡片：持久化的组 + 或尚无组的 pending 动作 */
data class ExerciseCardUi(
    val exercise: Exercise,
    val sets: List<WorkoutSet>,
    val pending: Boolean
)

@Composable
fun WorkoutSessionScreen(container: AppContainer, sessionId: Long, onFinished: () -> Unit) {
    val vm: WorkoutSessionViewModel = viewModelWith {
        WorkoutSessionViewModel(container.workoutRepository, container.exerciseRepository, sessionId)
    }
    val scope = rememberCoroutineScope()
    val session by vm.session.collectAsState()
    val groups by vm.groups.collectAsState()
    val exercises by vm.exercises.collectAsState()
    val pending by vm.pendingExerciseIds.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarMessage by vm.snackbarMessage.collectAsState()
    LaunchedEffect(snackbarMessage?.id) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it.text)
            vm.onSnackbarShown()
        }
    }
    var showEndDialog by remember { mutableStateOf(false) }
    var showAbandonDialog by remember { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showTitleDialog by remember { mutableStateOf(false) }
    var showDurationDialog by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var editingSet by remember { mutableStateOf<WorkoutSet?>(null) }
    var removingCard by remember { mutableStateOf<ExerciseCardUi?>(null) }

    val s = session
    if (s == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val ended = s.endTime != null

    val cards = remember(groups, exercises, pending) {
        val persisted = groups.groupBy { it.set.exerciseId }.map { (exId, rows) ->
            ExerciseCardUi(
                exercise = Exercise(id = exId, name = rows.first().exerciseName, createdAt = 0),
                sets = rows.map { it.set },
                pending = false
            )
        }.sortedBy { card -> card.sets.minOf { it.exerciseOrder } }
        val persistedIds = persisted.map { it.exercise.id }.toSet()
        val pendingCards = pending.filter { it !in persistedIds }.mapNotNull { id ->
            exercises.firstOrNull { it.id == id }?.let { ExerciseCardUi(it, emptyList(), pending = true) }
        }
        persisted + pendingCards
    }

    // 拖拽排序期间用本地列表驱动 UI，结束后写回 DB，再由 DB 流同步回来；
    // 挂起动作（还没录组）没有 exerciseOrder，固定在末尾不参与排序
    var displayCards by remember { mutableStateOf(cards) }
    var isReordering by remember { mutableStateOf(false) }
    LaunchedEffect(cards) { if (!isReordering) displayCards = cards }

    val lazyListState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        // 挂起动作固定在末尾，拒绝涉及挂起卡片的移动
        if (!displayCards[from.index].pending && !displayCards[to.index].pending) {
            displayCards = displayCards.toMutableList().apply { add(to.index, removeAt(from.index)) }
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { _ ->
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { showTitleDialog = true }) { Icon(Icons.Filled.Edit, contentDescription = "修改标题") }
            }
            if (ended) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "时长 " + formatDuration((s.endTime!! - s.startTime) / 1000),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showDurationDialog = true }) { Icon(Icons.Filled.Edit, contentDescription = "修改时长") }
                }
            } else {
                ElapsedTimer(startTime = s.startTime, style = MaterialTheme.typography.headlineMedium)
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { showNoteDialog = true }) {
                Text(
                    if (s.note.isBlank()) "添加备注" else "备注：${s.note}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (s.note.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showNoteDialog = true }) { Icon(Icons.Filled.Edit, contentDescription = "修改备注") }
            }

            if (cards.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("点下方按钮添加第一个动作", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f),
                    state = lazyListState,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(displayCards, key = { it.exercise.id }) { card ->
                        ReorderableItem(reorderableState, key = card.exercise.id) { itemDragging ->
                            ExerciseCard(
                                card = card,
                                isDragging = itemDragging,
                                dragHandle = Modifier.draggableHandle(
                                    onDragStarted = {
                                        isReordering = true
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDragStopped = {
                                        isReordering = false
                                        // 先同步捕获当前顺序再发起写回，避免协程启动前被重同步抢先；
                                        // 无实际移动则跳过；写回失败时回退本地顺序并提示
                                        val orderedIds = displayCards.filter { !it.pending }.map { it.exercise.id }
                                        if (orderedIds != cards.filter { !it.pending }.map { it.exercise.id }) {
                                            scope.launch {
                                                if (!vm.reorderExercises(orderedIds)) displayCards = cards
                                            }
                                        }
                                    }
                                ),
                                onLoadLast = { vm.lastPerformance(card.exercise.id) },
                                onAddSet = { w, r -> scope.launch { vm.addSet(card.exercise.id, w, r) } },
                                onEditSet = { editingSet = it },
                                onRemove = { removingCard = card }
                            )
                        }
                    }
                }
            }

            Button(onClick = { showAddSheet = true }, modifier = Modifier.fillMaxWidth()) { Text("添加动作") }
            if (!ended) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showAbandonDialog = true }, modifier = Modifier.weight(1f)) { Text("放弃") }
                    Button(onClick = { showEndDialog = true }, modifier = Modifier.weight(1f)) { Text("结束健身") }
                }
            }
        }
    }

    if (showAddSheet) {
        AddExerciseSheet(vm = vm, onDismiss = { showAddSheet = false; vm.exerciseQuery.value = "" })
    }

    editingSet?.let { set ->
        SetEditDialog(
            set = set,
            onSaved = { w, r ->
                scope.launch { vm.updateSet(set.copy(weightKg = w, reps = r)) }
                editingSet = null
            },
            onDeleted = {
                scope.launch { vm.deleteSet(set.id) }
                editingSet = null
            },
            onDismiss = { editingSet = null }
        )
    }

    removingCard?.let { card ->
        AlertDialog(
            onDismissRequest = { removingCard = null },
            title = { Text("移除动作") },
            text = { Text("删除「${card.exercise.name}」下的全部组记录？") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { vm.removeExercise(card.exercise.id) }
                    removingCard = null
                }) { Text("移除") }
            },
            dismissButton = { TextButton(onClick = { removingCard = null }) { Text("取消") } }
        )
    }

    if (showTitleDialog) {
        EditTitleDialog(
            initial = s.title,
            onSaved = { vm.setSessionTitle(it); showTitleDialog = false },
            onDismiss = { showTitleDialog = false }
        )
    }

    if (showNoteDialog) {
        NoteEditDialog(
            initial = s.note,
            onSaved = { vm.setNote(it); showNoteDialog = false },
            onDismiss = { showNoteDialog = false }
        )
    }

    if (showDurationDialog && ended) {
        EditDurationDialog(
            initialMinutes = (((s.endTime!! - s.startTime) / 1000).toInt() + 59) / 60,   // 向上取整到分钟，至少 1
            onSaved = { vm.setDurationMinutes(it); showDurationDialog = false },
            onDismiss = { showDurationDialog = false }
        )
    }

    if (!ended) {
        EndAndAbandonDialogs(
            groupsEmpty = groups.isEmpty(),
            showEnd = showEndDialog,
            onDismissEnd = { showEndDialog = false },
            onEnd = { scope.launch { vm.endSession(); onFinished() } },
            showAbandon = showAbandonDialog,
            onDismissAbandon = { showAbandonDialog = false },
            onAbandon = { scope.launch { vm.abandon(); onFinished() } }
        )
    }
}

@Composable
private fun EndAndAbandonDialogs(
    groupsEmpty: Boolean,
    showEnd: Boolean,
    onDismissEnd: () -> Unit,
    onEnd: () -> Unit,
    showAbandon: Boolean,
    onDismissAbandon: () -> Unit,
    onAbandon: () -> Unit
) {
    if (showEnd) {
        AlertDialog(
            onDismissRequest = onDismissEnd,
            title = { Text("结束健身") },
            text = { Text(if (groupsEmpty) "本次还没有记录任何组，确定结束吗？" else "结束并保存本次训练？") },
            confirmButton = { TextButton(onClick = onEnd) { Text("结束") } },
            dismissButton = { TextButton(onClick = onDismissEnd) { Text("继续训练") } }
        )
    }
    if (showAbandon) {
        AlertDialog(
            onDismissRequest = onDismissAbandon,
            title = { Text("放弃本次训练") },
            text = { Text("将删除本次训练及其全部组记录，且不可恢复。") },
            confirmButton = { TextButton(onClick = onAbandon) { Text("放弃") } },
            dismissButton = { TextButton(onClick = onDismissAbandon) { Text("取消") } }
        )
    }
}

@Composable
private fun ExerciseCard(
    card: ExerciseCardUi,
    isDragging: Boolean,
    dragHandle: Modifier,
    onLoadLast: suspend () -> List<WorkoutSet>,
    onAddSet: (Double, Int) -> Unit,
    onEditSet: (WorkoutSet) -> Unit,
    onRemove: () -> Unit
) {
    val last by produceState<List<WorkoutSet>>(emptyList(), card.exercise.id) {
        value = onLoadLast()
    }
    var weight by remember(card.exercise.id) { mutableStateOf("") }
    var reps by remember(card.exercise.id) { mutableStateOf("") }
    var entryVisible by remember(card.exercise.id) { mutableStateOf(false) }
    LaunchedEffect(last) {
        val first = last.firstOrNull()
        if (first != null && weight.isBlank() && reps.isBlank()) {
            weight = first.weightKg.displayKg()
            reps = first.reps.toString()
        }
    }
    // 重量 0 表示自重动作（引体向上、俯卧撑等）
    val valid = (weight.toDoubleOrNull()?.takeIf { it >= 0.0 } != null) &&
        (reps.toIntOrNull()?.takeIf { it > 0 } != null)

    Card(
        Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 8.dp else 1.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!card.pending) {
                    IconButton(onClick = {}, modifier = dragHandle) {
                        Icon(
                            Icons.Filled.DragHandle,
                            contentDescription = "拖动调整顺序",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(card.exercise.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onRemove) { Text("移除") }
            }
            if (last.isNotEmpty()) {
                Text(
                    "上次：" + last.joinToString(", ") { formatSetSummary(it.weightKg, it.reps) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            card.sets.forEach { set ->
                Row(
                    Modifier.fillMaxWidth().clickable { onEditSet(set) }.padding(vertical = 4.dp)
                ) {
                    Text("第 ${set.setOrder} 组", modifier = Modifier.weight(1f))
                    Text(formatSetRow(set.weightKg, set.reps))
                }
            }
            if (!entryVisible) {
                if (card.pending) {
                    Text(
                        "将从第一组开始记录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = { entryVisible = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("+ 添加一组")
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = weight, onValueChange = { weight = it },
                        label = { Text("重量 kg") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = reps, onValueChange = { reps = it },
                        label = { Text("次数") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            onAddSet(weight.toDouble(), reps.toInt())
                            reps = ""
                            entryVisible = false
                        },
                        enabled = valid
                    ) { Text("添加") }
                    TextButton(onClick = { entryVisible = false }) { Text("收起") }
                }
            }
        }
    }
}

@Composable
fun ElapsedTimer(
    startTime: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineMedium
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startTime) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    Text(
        formatDuration((now - startTime).coerceAtLeast(0) / 1000),
        style = style,
        modifier = modifier
    )
}
