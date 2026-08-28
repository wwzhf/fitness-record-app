package com.wc.workout.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExerciseSheet(vm: WorkoutSessionViewModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val query by vm.exerciseQuery.collectAsState()
    val list by vm.filteredExercises.collectAsState()
    var createError by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("添加动作", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = query,
                onValueChange = { vm.exerciseQuery.value = it; createError = false },
                label = { Text("搜索动作，或输入新名字新建") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            val trimmed = query.trim()
            val exactExists = trimmed.isNotEmpty() && list.any { it.name.equals(trimmed, ignoreCase = true) }
            if (trimmed.isNotEmpty() && !exactExists) {
                if (createError) {
                    Text(
                        "已存在同名动作",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                TextButton(onClick = {
                    scope.launch {
                        val created = vm.createExercise(trimmed)
                        if (created == null) {
                            createError = true
                        } else if (vm.addPendingExercise(created.id)) {
                            onDismiss()
                        }
                    }
                }) { Text("新建“$trimmed”并加入动作库") }
            }
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(list, key = { it.id }) { ex ->
                    ListItem(
                        headlineContent = { Text(ex.name) },
                        modifier = Modifier.clickable {
                            scope.launch { vm.addPendingExercise(ex.id); onDismiss() }
                        }
                    )
                }
            }
        }
    }
}
