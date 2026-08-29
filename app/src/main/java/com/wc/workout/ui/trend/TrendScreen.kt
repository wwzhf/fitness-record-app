package com.wc.workout.ui.trend

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wc.workout.AppContainer
import com.wc.workout.ui.common.kgLabel
import com.wc.workout.ui.common.readUriText
import com.wc.workout.ui.common.viewModelWith
import com.wc.workout.ui.common.writeUriText
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TrendScreen(container: AppContainer) {
    val vm: TrendViewModel = viewModelWith { TrendViewModel(container.weightRepository, container.workoutRepository) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    val all by vm.weights.collectAsState()
    val volumes by vm.volumes.collectAsState()
    val range by vm.range.collectAsState()
    val metric by vm.metric.collectAsState()
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) scope.launch {
            try {
                writeUriText(context, uri, container.backupRepository.export())
                snackbar.showSnackbar("备份已导出")
            } catch (e: Exception) {
                snackbar.showSnackbar("导出失败：${e.message}")
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pendingImportUri = uri
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("体重趋势", style = MaterialTheme.typography.headlineMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TrendRange.entries.forEach { r ->
                    FilterChip(selected = range == r, onClick = { vm.range.value = r }, label = { Text(r.label) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TrendMetric.entries.forEach { m ->
                    FilterChip(selected = metric == m, onClick = { vm.metric.value = m }, label = { Text(m.label) })
                }
            }

            val today = LocalDate.now().toEpochDay()
            val shownWeights = remember(all, range, today) {
                when (val r = range) {
                    TrendRange.ALL -> all
                    else -> all.filter { it.dateEpochDay >= today - (r.days ?: 0) + 1 }
                }
            }
            val shownVolumes = remember(volumes, range) {
                when (val r = range) {
                    TrendRange.ALL -> volumes
                    else -> {
                        val rangeStartMillis = LocalDate.now()
                            .minusDays((r.days ?: 0) - 1L)
                            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        volumes.filter { it.startTime >= rangeStartMillis }
                    }
                }
            }
            val recordCount = if (metric == TrendMetric.WEIGHT) shownWeights.size else shownVolumes.size
            Text(
                "该范围共 $recordCount 条记录",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (metric == TrendMetric.WEIGHT) {
                if (shownWeights.isEmpty()) {
                    Box(
                        Modifier.fillMaxWidth().height(220.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("这个范围内还没有体重记录", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    val values = shownWeights.map { it.weightKg }
                    val zone = ZoneId.systemDefault()
                    val fmt = DateTimeFormatter.ofPattern("MM-dd")
                    WeightLineChart(
                        points = shownWeights.map { it.dateEpochDay to it.weightKg },
                        modifier = Modifier.fillMaxWidth().height(220.dp)
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(Instant.ofEpochMilli(shownWeights.first().dateEpochDay * 86_400_000).atZone(zone).toLocalDate().format(fmt))
                        Text(Instant.ofEpochMilli(shownWeights.last().dateEpochDay * 86_400_000).atZone(zone).toLocalDate().format(fmt))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("最高 ${values.max().kgLabel()}")
                        Text("最低 ${values.min().kgLabel()}")
                        Text("平均 ${values.average().kgLabel()}")
                    }
                }
            } else {
                if (shownVolumes.isEmpty()) {
                    Box(
                        Modifier.fillMaxWidth().height(220.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("这个范围内还没有训练记录", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    val vols = shownVolumes.map { it.volume }
                    val fmt = DateTimeFormatter.ofPattern("MM-dd")
                    WeightLineChart(
                        points = shownVolumes.map { it.startTime to it.volume },
                        xIsMillis = true,
                        modifier = Modifier.fillMaxWidth().height(220.dp)
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(Instant.ofEpochMilli(shownVolumes.first().startTime).atZone(ZoneId.systemDefault()).toLocalDate().format(fmt))
                        Text(Instant.ofEpochMilli(shownVolumes.last().startTime).atZone(ZoneId.systemDefault()).toLocalDate().format(fmt))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("最高 %,.0f kg".format(vols.max()))
                        Text("最低 %,.0f kg".format(vols.min()))
                        Text("平均 %,.0f kg".format(vols.average()))
                    }
                }
            }

            Text("数据备份", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").format(LocalDateTime.now())
                    exportLauncher.launch("workout-backup-$stamp.json")
                }) { Text("导出备份") }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Text("导入备份") }
            }
        }
    }

    pendingImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text("导入备份") },
            text = { Text("导入将按日期合并：文件中出现的日期，当天训练与体重以文件为准覆盖；文件未涉及的日期与动作，App 现有数据保留。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingImportUri = null
                    scope.launch {
                        try {
                            val s = container.backupRepository.import(readUriText(context, uri))
                            snackbar.showSnackbar("导入完成：${s.weights} 天体重、${s.sessions} 次训练、${s.sets} 组记录")
                        } catch (e: Exception) {
                            snackbar.showSnackbar("导入失败：${e.message}")
                        }
                    }
                }) { Text("导入") }
            },
            dismissButton = { TextButton(onClick = { pendingImportUri = null }) { Text("取消") } }
        )
    }
}
