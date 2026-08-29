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
    val maxWeight = allSets.maxOfOrNull { it.weightKg } ?: 0.0
    val maxVolume = allSets.maxOfOrNull { it.weightKg * it.reps } ?: 0.0
    val bestWeightSet = allSets.firstOrNull { it.weightKg == maxWeight }
    val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(exercise?.name ?: "", style = MaterialTheme.typography.headlineMedium)
        if (bestWeightSet != null) {
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
                    HistoryCard(entry, maxWeight, maxVolume, dateFmt)
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(
    entry: ExerciseHistoryEntry,
    maxWeight: Double,
    maxVolume: Double,
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
            Text(
                "${formatTime(session.startTime)} – $endText · ${formatDuration(durationSec)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            entry.sets.forEach { set ->
                SetRow(set, maxWeight, maxVolume)
            }
        }
    }
}

@Composable
private fun SetRow(set: WorkoutSet, maxWeight: Double, maxVolume: Double) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${set.weightKg.displayKg()}kg×${set.reps}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (set.weightKg == maxWeight) {
            Text(
                "重量PR",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (set.weightKg * set.reps == maxVolume) {
            Text(
                "容量PR",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}
