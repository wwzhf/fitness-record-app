package com.wc.workout.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.wc.workout.AppContainer
import com.wc.workout.ui.common.formatDuration
import com.wc.workout.ui.common.viewModelWith
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun WorkoutSessionScreen(container: AppContainer, sessionId: Long, onFinished: () -> Unit) {
    val vm: WorkoutSessionViewModel = viewModelWith {
        WorkoutSessionViewModel(container.workoutRepository, sessionId)
    }
    val scope = rememberCoroutineScope()
    val session by vm.session.collectAsState()
    val groups by vm.groups.collectAsState()
    var showEndDialog by remember { mutableStateOf(false) }
    var showAbandonDialog by remember { mutableStateOf(false) }

    val s = session
    if (s == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(s.title, style = MaterialTheme.typography.titleLarge)
        ElapsedTimer(startTime = s.startTime, style = MaterialTheme.typography.headlineMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showAbandonDialog = true }, modifier = Modifier.weight(1f)) { Text("放弃") }
            Button(onClick = { showEndDialog = true }, modifier = Modifier.weight(1f)) { Text("结束健身") }
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("还没有记录任何动作", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showEndDialog) {
        AlertDialog(
            onDismissRequest = { showEndDialog = false },
            title = { Text("结束健身") },
            text = { Text(if (groups.isEmpty()) "本次还没有记录任何组，确定结束吗？" else "结束并保存本次训练？") },
            confirmButton = {
                TextButton(onClick = { scope.launch { vm.endSession(); onFinished() } }) { Text("结束") }
            },
            dismissButton = { TextButton(onClick = { showEndDialog = false }) { Text("继续训练") } }
        )
    }
    if (showAbandonDialog) {
        AlertDialog(
            onDismissRequest = { showAbandonDialog = false },
            title = { Text("放弃本次训练") },
            text = { Text("将删除本次训练及其全部组记录，且不可恢复。") },
            confirmButton = {
                TextButton(onClick = { scope.launch { vm.abandon(); onFinished() } }) { Text("放弃") }
            },
            dismissButton = { TextButton(onClick = { showAbandonDialog = false }) { Text("取消") } }
        )
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
