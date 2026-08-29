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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wc.workout.AppContainer
import com.wc.workout.data.local.Exercise
import com.wc.workout.data.local.WorkoutSet
import com.wc.workout.ui.common.displayKg
import com.wc.workout.ui.common.formatDuration
import com.wc.workout.ui.common.viewModelWith
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
    var showEndDialog by remember { mutableStateOf(false) }
    var showAbandonDialog by remember { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showTitleDialog by remember { mutableStateOf(false) }
    var showDurationDialog by remember { mutableStateOf(false) }
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

        if (cards.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("点下方按钮添加第一个动作", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(cards, key = { it.exercise.id }) { card ->
                    ExerciseCard(
                        card = card,
                        onLoadLast = { vm.lastPerformance(card.exercise.id) },
                        onAddSet = { w, r -> scope.launch { vm.addSet(card.exercise.id, w, r) } },
                        onEditSet = { editingSet = it },
                        onRemove = { removingCard = card }
                    )
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
    val valid = (weight.toDoubleOrNull()?.takeIf { it > 0.0 } != null) &&
        (reps.toIntOrNull()?.takeIf { it > 0 } != null)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(card.exercise.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onRemove) { Text("移除") }
            }
            if (last.isNotEmpty()) {
                Text(
                    "上次：" + last.joinToString(", ") { "${it.weightKg.displayKg()}kg×${it.reps}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            card.sets.forEach { set ->
                Row(
                    Modifier.fillMaxWidth().clickable { onEditSet(set) }.padding(vertical = 4.dp)
                ) {
                    Text("第 ${set.setOrder} 组", modifier = Modifier.weight(1f))
                    Text("${set.weightKg.displayKg()}kg × ${set.reps} 次")
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
