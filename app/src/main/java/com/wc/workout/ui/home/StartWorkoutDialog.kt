package com.wc.workout.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wc.workout.data.local.WorkoutSession
import com.wc.workout.ui.workout.ElapsedTimer

@Composable
fun StartWorkoutDialog(
    onLoadTitles: suspend () -> List<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var titles by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) { titles = onLoadTitles() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("开始健身") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题（留空自动用日期）") },
                    singleLine = true
                )
                if (titles.isNotEmpty()) {
                    Text(
                        "最近使用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    titles.take(5).forEach { t ->
                        TextButton(onClick = { title = t }) { Text(t) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(title) }) { Text("开始") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun OngoingCard(session: WorkoutSession, onClick: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(session.title, style = MaterialTheme.typography.titleMedium)
                ElapsedTimer(session.startTime, style = MaterialTheme.typography.headlineSmall)
            }
            Text("继续 ›", color = MaterialTheme.colorScheme.primary)
        }
    }
}
