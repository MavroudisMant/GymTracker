package com.mavros.gymtracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mavros.gymtracker.data.Exercise
import com.mavros.gymtracker.data.TargetArea
import com.mavros.gymtracker.data.normalizeExerciseName

/**
 * Search-as-you-type exercise picker. Tapping a suggestion picks that exercise; "Add" submits the
 * typed name (the caller decides whether that's an existing exercise or a new one).
 */
@Composable
fun AddExerciseDialog(
    allExercises: List<Exercise>,
    onDismiss: () -> Unit,
    onPickExisting: (Exercise) -> Unit,
    onSubmitTyped: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val suggestions = remember(query, allExercises) {
        val q = normalizeExerciseName(query)
        if (q.isBlank()) allExercises else allExercises.filter { it.normalizedName.contains(q) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onSubmitTyped(query) }, enabled = query.isNotBlank()) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Add exercise") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Exercise name") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                if (suggestions.isEmpty()) {
                    if (query.isNotBlank()) {
                        Text(
                            text = "No match — tap Add to create “${query.trim()}”.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                        items(suggestions, key = { it.id }) { exercise ->
                            SuggestionRow(exercise, onClick = { onPickExisting(exercise) })
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun SuggestionRow(exercise: Exercise, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(exercise.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        exercise.targetArea?.let { TargetAreaPill(it) }
    }
}

/** Shown after typing a name that doesn't exist yet: optionally pick a target area and measure. */
@Composable
fun NewExerciseDialog(
    name: String,
    onDismiss: () -> Unit,
    onConfirm: (TargetArea?, isTimed: Boolean) -> Unit,
) {
    var selected by remember { mutableStateOf<TargetArea?>(null) }
    var isTimed by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(selected, isTimed) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = { onConfirm(null, false) }) { Text("Skip") } },
        title = { Text("New exercise") },
        text = {
            Column {
                Text(
                    text = "“$name” — set a target area? (optional)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                TargetAreaSelector(selected = selected, onSelect = { selected = it })
                Spacer(Modifier.height(12.dp))
                MeasureToggle(isTimed = isTimed, onChange = { isTimed = it })
            }
        },
    )
}

/** "Measured in [reps] [seconds]". */
@Composable
fun MeasureToggle(isTimed: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Measured in", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        MeasureOption("reps", selected = !isTimed, onClick = { onChange(false) })
        MeasureOption("seconds", selected = isTimed, onClick = { onChange(true) })
    }
}

@Composable
private fun MeasureOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (selected) colors.primaryContainer else colors.surface,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface,
        border = if (selected) null else BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
