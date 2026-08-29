package com.wc.workout.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
internal fun AddPastWorkoutDialog(
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
