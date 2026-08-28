package com.wc.workout.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wc.workout.AppContainer
import com.wc.workout.data.local.Exercise
import com.wc.workout.data.repository.ExerciseNameResult
import com.wc.workout.ui.common.NameDialog
import com.wc.workout.ui.common.viewModelWith
import kotlinx.coroutines.launch

@Composable
fun ExerciseLibraryScreen(container: AppContainer) {
    val vm: ExerciseLibraryViewModel = viewModelWith { ExerciseLibraryViewModel(container.exerciseRepository) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val query by vm.query.collectAsState()
    val exercises by vm.exercises.collectAsState()
    val archived by vm.archived.collectAsState()
    var archivedExpanded by rememberSaveable { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Exercise?>(null) }
    var menuTarget by remember { mutableStateOf<Exercise?>(null) }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("动作库", style = MaterialTheme.typography.headlineMedium)
            OutlinedTextField(
                value = query,
                onValueChange = { vm.query.value = it },
                label = { Text("搜索动作") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) { Text("新建动作") }

            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (exercises.isEmpty() && query.isBlank()) {
                    item {
                        Text(
                            "还没有动作，点「新建动作」添加",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                }
                items(exercises, key = { it.id }) { ex ->
                    ListItem(
                        headlineContent = { Text(ex.name) },
                        trailingContent = {
                            IconButton(onClick = { menuTarget = ex }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                            }
                        },
                        modifier = Modifier.clickable { renameTarget = ex }
                    )
                }
                if (archived.isNotEmpty()) {
                    item {
                        TextButton(onClick = { archivedExpanded = !archivedExpanded }) {
                            Text(if (archivedExpanded) "收起已归档 (${archived.size})" else "已归档 (${archived.size})")
                        }
                    }
                }
                if (archivedExpanded) {
                    items(archived, key = { "archived-${it.id}" }) { ex ->
                        ListItem(
                            headlineContent = {
                                Text(ex.name, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            trailingContent = {
                                TextButton(onClick = { scope.launch { vm.unarchive(ex.id) } }) { Text("取消归档") }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        NameDialog(
            title = "新建动作",
            initial = query,
            confirmLabel = "创建",
            onConfirm = { name ->
                if (runCatching { vm.add(name) }.getOrElse { null } is ExerciseNameResult.Duplicate) "已存在同名动作" else null
            },
            onDismiss = { showAdd = false }
        )
    }

    renameTarget?.let { target ->
        NameDialog(
            title = "重命名动作",
            initial = target.name,
            confirmLabel = "保存",
            onConfirm = { name ->
                if (runCatching { vm.rename(target.id, name) }.getOrElse { null } is ExerciseNameResult.Duplicate) "已存在同名动作" else null
            },
            onDismiss = { renameTarget = null }
        )
    }

    menuTarget?.let { target ->
        val canDelete by produceState(initialValue = false, target.id) {
            value = !vm.isReferenced(target.id)
        }
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            DropdownMenu(expanded = true, onDismissRequest = { menuTarget = null }) {
                DropdownMenuItem(text = { Text("重命名") }, onClick = { renameTarget = target; menuTarget = null })
                DropdownMenuItem(text = { Text("归档") }, onClick = { scope.launch { vm.archive(target.id) }; menuTarget = null })
                if (canDelete) {
                    DropdownMenuItem(
                        text = { Text("删除") },
                        onClick = {
                            scope.launch {
                                if (vm.delete(target.id)) snackbar.showSnackbar("已删除「${target.name}」")
                            }
                            menuTarget = null
                        }
                    )
                }
            }
        }
    }
}
