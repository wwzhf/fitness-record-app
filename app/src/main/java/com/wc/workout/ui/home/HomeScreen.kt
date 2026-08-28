package com.wc.workout.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wc.workout.AppContainer
import com.wc.workout.data.local.WeightRecord
import com.wc.workout.ui.common.WeightEditDialog
import com.wc.workout.ui.common.displayKg
import com.wc.workout.ui.common.formatTime
import com.wc.workout.ui.common.kgLabel
import com.wc.workout.ui.common.viewModelWith

@Composable
fun HomeScreen(container: AppContainer) {
    val vm: HomeViewModel = viewModelWith {
        HomeViewModel(container.weightRepository, container.workoutRepository)
    }
    val weight by vm.todayWeight.collectAsState()

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("训练", style = MaterialTheme.typography.headlineMedium)
        TodayWeightCard(weight = weight, onSave = vm::saveWeight)
    }
}

@Composable
private fun TodayWeightCard(weight: WeightRecord?, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("今日体重", style = MaterialTheme.typography.titleMedium)
            if (weight == null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it; showError = false },
                        label = { Text("kg") },
                        isError = showError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = {
                        val kg = text.toDoubleOrNull()
                        if (kg == null || kg <= 0.0) showError = true else onSave(text)
                    }) { Text("记录") }
                }
                if (showError) Text(
                    "请输入大于 0 的数字",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Text("${weight.weightKg.kgLabel()} kg", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "记录于 ${formatTime(weight.recordedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { editing = true }) { Text("修改") }
            }
        }
    }

    if (editing && weight != null) {
        WeightEditDialog(
            initialKg = weight!!.weightKg,
            onSaved = { onSave(it) },
            onDismiss = { editing = false }
        )
    }
}
