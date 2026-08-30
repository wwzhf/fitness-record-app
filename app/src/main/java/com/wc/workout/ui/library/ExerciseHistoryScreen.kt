package com.wc.workout.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wc.workout.AppContainer
import com.wc.workout.data.local.WorkoutSet
import com.wc.workout.data.repository.ExerciseHistoryEntry
import com.wc.workout.ui.common.displayKg
import com.wc.workout.ui.common.formatDuration
import com.wc.workout.ui.common.formatSetSummary
import com.wc.workout.ui.common.formatTime
import com.wc.workout.ui.common.viewModelWith
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ExerciseHistoryScreen(container: AppContainer, exerciseId: Long) {
    val vm: ExerciseHistoryViewModel = viewModelWith {
        ExerciseHistoryViewModel(container.workoutRepository, container.exerciseRepository, exerciseId)
    }
    val exercise by vm.exercise.collectAsState()
    val entries by vm.entries.collectAsState()

    val allSets = entries.flatMap { it.sets }
    val allBodyweight = allSets.isNotEmpty() && allSets.all { it.weightKg == 0.0 }
    val maxVolume = allSets.maxOfOrNull { it.weightKg * it.reps } ?: 0.0
    val bestWeightSet = bestWeightSetOf(allSets)
    val maxReps = if (allBodyweight) allSets.maxOf { it.reps } else 0
    val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(exercise?.name ?: "", style = MaterialTheme.typography.headlineMedium)
        if (allBodyweight) {
            Text(
                "最多 $maxReps 次",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (bestWeightSet != null) {
            Text(
                "最大重量 ${bestWeightSet.weightKg.displayKg()}kg×${bestWeightSet.reps}" +
                    " · 最大单组容量 %,.0f kg".format(maxVolume),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (entries.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("这个动作还没有训练记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(entries, key = { it.session.id }) { entry ->
                    HistoryCard(entry, bestWeightSet, maxVolume, allBodyweight, maxReps, dateFmt)
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(
    entry: ExerciseHistoryEntry,
    bestWeightSet: WorkoutSet?,
    maxVolume: Double,
    allBodyweight: Boolean,
    maxReps: Int,
    dateFmt: DateTimeFormatter
) {
    val session = entry.session
    val endText = session.endTime?.let { formatTime(it) } ?: "进行中"
    val durationSec = ((session.endTime ?: System.currentTimeMillis()) - session.startTime) / 1000
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                Instant.ofEpochMilli(session.startTime).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFmt),
                style = MaterialTheme.typography.titleMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${formatTime(session.startTime)} – $endText · ${formatDuration(durationSec)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            entry.sets.forEach { set ->
                SetRow(set, bestWeightSet, maxVolume, maxReps, showPrChips = !allBodyweight)
            }
        }
    }
}

@Composable
private fun SetRow(set: WorkoutSet, bestWeightSet: WorkoutSet?, maxVolume: Double, maxReps: Int, showPrChips: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            formatSetSummary(set.weightKg, set.reps),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f, fill = false)
        )
        // 重量PR 只标最大重量并列中最优的那组（与头部展示一致），容量PR 维持并列都标
        if (showPrChips && bestWeightSet != null && set.id == bestWeightSet.id) {
            Text(
                "重量PR",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (showPrChips && set.weightKg * set.reps == maxVolume) {
            Text(
                "容量PR",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
        if (!showPrChips && set.reps == maxReps) {
            Text(
                "次数PR",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** 头部"最大重量"与重量PR 标记共用的代表组：最大重量并列时取次数最多的一组（如 35kg×5 与 35kg×6 取 ×6） */
fun bestWeightSetOf(sets: List<WorkoutSet>): WorkoutSet? {
    val maxWeight = sets.maxOfOrNull { it.weightKg } ?: return null
    return sets.filter { it.weightKg == maxWeight }.maxByOrNull { it.reps }
}
