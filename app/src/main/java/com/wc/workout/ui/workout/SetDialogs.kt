package com.wc.workout.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wc.workout.data.local.WorkoutSet
import com.wc.workout.ui.common.displayKg

@Composable
fun SetEditDialog(
    set: WorkoutSet,
    onSaved: (Double, Int) -> Unit,
    onDeleted: () -> Unit,
    onDismiss: () -> Unit
) {
    var weight by remember { mutableStateOf(set.weightKg.displayKg()) }
    var reps by remember { mutableStateOf(set.reps.toString()) }
    val valid = (weight.toDoubleOrNull()?.takeIf { it > 0.0 } != null) &&
        (reps.toIntOrNull()?.takeIf { it > 0 } != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑第 ${set.setOrder} 组") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSaved(weight.toDouble(), reps.toInt()) }) { Text("保存") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDeleted) { Text("删除", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

@Composable
fun EditTitleDialog(initial: String, onSaved: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改标题") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("标题") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onSaved(text.trim()) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
