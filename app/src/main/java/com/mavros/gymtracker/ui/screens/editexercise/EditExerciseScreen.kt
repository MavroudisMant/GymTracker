package com.mavros.gymtracker.ui.screens.editexercise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mavros.gymtracker.ui.components.ConfirmDialog
import com.mavros.gymtracker.ui.components.MeasureToggle
import com.mavros.gymtracker.ui.components.TargetAreaSelector
import com.mavros.gymtracker.ui.components.GymTopBar

@Composable
fun EditExerciseScreen(
    viewModel: EditExerciseViewModel,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDeleteBlocked by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) {
        if (state.saved) {
            viewModel.onSavedHandled()
            onSaved()
        }
    }
    LaunchedEffect(state.deleted) {
        if (state.deleted) {
            viewModel.onDeletedHandled()
            onDeleted()
        }
    }

    Scaffold(topBar = { GymTopBar("Edit exercise", onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Name") },
                supportingText = if (state.hasCollision) {
                    { Text("Another exercise already uses this name.") }
                } else {
                    null
                },
                isError = state.hasCollision,
                singleLine = true,
            )
            Text("Target area", modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.titleSmall)
            TargetAreaSelector(selected = state.targetArea, onSelect = viewModel::onAreaSelect)
            Text(
                text = "Tap the selected area again to clear it.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            MeasureToggle(isTimed = state.isTimed, onChange = viewModel::onTimedChange)
            Text(
                text = "Renaming updates this exercise across all past sessions.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(onClick = viewModel::save, modifier = Modifier.weight(1f), enabled = state.canSave) {
                    Text("Save")
                }
            }
            TextButton(
                onClick = { if (state.canDelete) showDeleteConfirm = true else showDeleteBlocked = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.loaded,
            ) {
                Text("Delete exercise", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "Delete exercise?",
            message = "“${state.name}” isn't used in any session or template, so it's safe to remove.",
            onConfirm = {
                showDeleteConfirm = false
                viewModel.deleteExercise()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
    if (showDeleteBlocked) {
        AlertDialog(
            onDismissRequest = { showDeleteBlocked = false },
            confirmButton = { TextButton(onClick = { showDeleteBlocked = false }) { Text("OK") } },
            title = { Text("Can't delete") },
            text = {
                Text(
                    "“${state.name}” is used in ${state.usageCount} logged session(s) or template(s). " +
                        "Remove those entries first to keep your history consistent.",
                )
            },
        )
    }
}
