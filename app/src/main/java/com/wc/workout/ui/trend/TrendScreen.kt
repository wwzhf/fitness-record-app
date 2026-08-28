package com.wc.workout.ui.trend

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wc.workout.AppContainer
import com.wc.workout.ui.common.kgLabel
import com.wc.workout.ui.common.viewModelWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun TrendScreen(container: AppContainer) {
    val vm: TrendViewModel = viewModelWith { TrendViewModel(container.weightRepository) }
    val all by vm.weights.collectAsState()
    val range by vm.range.collectAsState()

    val today = LocalDate.now().toEpochDay()
    val shown = remember(all, range, today) {
        when (val r = range) {
            TrendRange.ALL -> all
            else -> all.filter { it.dateEpochDay >= today - (r.days ?: 0) + 1 }
        }
    }
    val values = shown.map { it.weightKg }
    val zone = ZoneId.systemDefault()
    val fmt = java.time.format.DateTimeFormatter.ofPattern("MM-dd")

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("体重趋势", style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrendRange.entries.forEach { r ->
                FilterChip(selected = range == r, onClick = { vm.range.value = r }, label = { Text(r.label) })
            }
        }
        if (shown.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().height(220.dp),
                contentAlignment = Alignment.Center
            ) { Text("这个范围内还没有体重记录", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            WeightLineChart(
                points = shown.map { it.dateEpochDay to it.weightKg },
                modifier = Modifier.fillMaxWidth().height(220.dp)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(Instant.ofEpochMilli(shown.first().dateEpochDay * 86_400_000).atZone(zone).toLocalDate().format(fmt))
                Text(Instant.ofEpochMilli(shown.last().dateEpochDay * 86_400_000).atZone(zone).toLocalDate().format(fmt))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("最高 ${values.max().kgLabel()}")
                Text("最低 ${values.min().kgLabel()}")
                Text("平均 ${values.average().kgLabel()}")
            }
        }
    }
}
