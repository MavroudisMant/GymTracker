package com.mavros.gymtracker.ui.screens.logworkout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mavros.gymtracker.ui.components.AddExerciseDialog
import com.mavros.gymtracker.ui.components.LocalWeightUnit
import com.mavros.gymtracker.ui.components.NewExerciseDialog
import com.mavros.gymtracker.ui.components.TargetAreaPill
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val dateFormatter = DateTimeFormatter.ofPattern("EEE d MMM")

/** A row in the workout: a lone exercise or a run of exercises sharing a superset group. */
sealed interface LogBlock {
    val key: String
    val first: LoggedExerciseUi
    val last: LoggedExerciseUi

    data class Single(val exercise: LoggedExerciseUi) : LogBlock {
        override val key: String get() = "ex-${exercise.clientId}"
        override val first: LoggedExerciseUi get() = exercise
        override val last: LoggedExerciseUi get() = exercise
    }

    data class Superset(val group: String, val members: List<LoggedExerciseUi>) : LogBlock {
        override val key: String get() = "ss-$group-${members.first().clientId}"
        override val first: LoggedExerciseUi get() = members.first()
        override val last: LoggedExerciseUi get() = members.last()
    }
}

@Composable
fun LogWorkoutScreen(
    viewModel: LogWorkoutViewModel,
    onDone: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val allExercises by viewModel.allExercises.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }

    // Flush pending edits when leaving the screen (the debounced auto-save may not have run yet).
    DisposableEffect(Unit) {
        onDispose { viewModel.saveImmediately() }
    }

    val supersetLabels = remember(state.exercises) { computeSupersetLabels(state.exercises) }
    // Section headers only make sense when the workout spans at least two sections.
    val showSections = remember(state.exercises) {
        state.exercises.mapNotNull { it.sectionName }.distinct().size >= 2
    }
    val blocks = remember(state.exercises) { buildLogBlocks(state.exercises) }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            WorkoutHeader(
                date = state.date,
                dayName = state.templateDayName,
                onPickDate = { showDatePicker = true },
                onNavigateBack = onDone,
            )
        },
        bottomBar = { BottomActions(onAddExercise = viewModel::openAddExerciseDialog) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.exercises.isEmpty()) {
                item { EmptyState() }
            }
            if (state.selectedExerciseIds.isNotEmpty()) {
                item {
                    val selected = state.exercises.filter { it.clientId in state.selectedExerciseIds }
                    SupersetActionBar(
                        canMakeSuperset = selected.size >= 2,
                        canUngroup = selected.any { it.supersetGroup != null },
                        onMakeSuperset = viewModel::makeSuperset,
                        onUngroup = viewModel::ungroupSuperset,
                        onClear = viewModel::clearSelection,
                    )
                }
            }
            itemsIndexed(blocks, key = { _, block -> block.key }) { index, block ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val previousSection = blocks.getOrNull(index - 1)?.last?.sectionName
                    if (showSections && block.first.sectionName != previousSection) {
                        Text(
                            text = block.first.sectionName ?: "Other",
                            modifier = Modifier.padding(top = 4.dp),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    when (block) {
                        is LogBlock.Single -> LoggedExerciseCard(
                            exercise = block.exercise,
                            selected = block.exercise.clientId in state.selectedExerciseIds,
                            supersetLabel = supersetLabels[block.exercise.clientId],
                            viewModel = viewModel,
                        )
                        is LogBlock.Superset -> SupersetPanel {
                            block.members.forEach { member ->
                                LoggedExerciseCard(
                                    exercise = member,
                                    selected = member.clientId in state.selectedExerciseIds,
                                    supersetLabel = supersetLabels[member.clientId],
                                    viewModel = viewModel,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (state.showAddExerciseDialog) {
        AddExerciseDialog(
            allExercises = allExercises,
            onDismiss = viewModel::closeAddExerciseDialog,
            onPickExisting = viewModel::addExistingExercise,
            onSubmitTyped = viewModel::submitTypedExercise,
        )
    }

    state.pendingNewExerciseName?.let { name ->
        NewExerciseDialog(
            name = name,
            onDismiss = viewModel::cancelNewExercise,
            onConfirm = viewModel::confirmNewExercise,
        )
    }

    if (showDatePicker) {
        WorkoutDatePicker(
            initialDate = state.date,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                viewModel.onDateSelected(it)
                showDatePicker = false
            },
        )
    }
}

@Composable
private fun WorkoutHeader(
    date: LocalDate,
    dayName: String?,
    onPickDate: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 4.dp, end = 16.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNavigateBack) { Text("←", style = MaterialTheme.typography.titleLarge) }
                Row(
                    modifier = Modifier.clickable(onClick = onPickDate),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("📅", style = MaterialTheme.typography.titleLarge)
                    Text(date.format(dateFormatter), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleLarge)
                }
            }
            if (dayName != null) {
                Text(dayName, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** An exercise card wired to the view model. */
@Composable
private fun LoggedExerciseCard(
    exercise: LoggedExerciseUi,
    selected: Boolean,
    supersetLabel: String?,
    viewModel: LogWorkoutViewModel,
) {
    ExerciseCard(
        exercise = exercise,
        selected = selected,
        supersetLabel = supersetLabel,
        onToggleSelect = { viewModel.toggleSelection(exercise.clientId) },
        onAddSet = { viewModel.addSet(exercise.clientId) },
        onWeightChange = { setId, value -> viewModel.updateSetWeight(exercise.clientId, setId, value) },
        onRepsChange = { setId, value -> viewModel.updateSetReps(exercise.clientId, setId, value) },
        onRemoveSet = { setId -> viewModel.removeSet(exercise.clientId, setId) },
        onRemoveExercise = { viewModel.removeExercise(exercise.clientId) },
    )
}

@Composable
private fun ExerciseCard(
    exercise: LoggedExerciseUi,
    selected: Boolean,
    supersetLabel: String?,
    onToggleSelect: () -> Unit,
    onAddSet: () -> Unit,
    onWeightChange: (setClientId: Long, String) -> Unit,
    onRepsChange: (setClientId: Long, String) -> Unit,
    onRemoveSet: (setClientId: Long) -> Unit,
    onRemoveExercise: () -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (supersetLabel != null) {
                        Text(
                            text = supersetLabel,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                    exercise.targetArea?.let { TargetAreaPill(it) }
                }
                IconButton(onClick = onRemoveExercise) {
                    Text("✕", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(4.dp))
            SetsHeaderRow(repsLabel = if (exercise.isTimed) "sec" else "reps")
            exercise.sets.forEachIndexed { index, set ->
                SetRow(
                    setNumber = index + 1,
                    set = set,
                    onWeightChange = { onWeightChange(set.clientId, it) },
                    onRepsChange = { onRepsChange(set.clientId, it) },
                    onRemove = { onRemoveSet(set.clientId) },
                )
            }
            TextButton(onClick = onAddSet) { Text("+ Add set") }
        }
    }
}

@Composable
private fun SetsHeaderRow(repsLabel: String) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelMedium
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Set", modifier = Modifier.width(36.dp), color = labelColor, style = labelStyle)
        Spacer(Modifier.width(8.dp))
        Text(LocalWeightUnit.current.label, modifier = Modifier.weight(1f), color = labelColor, style = labelStyle)
        Spacer(Modifier.width(8.dp))
        Text(repsLabel, modifier = Modifier.weight(1f), color = labelColor, style = labelStyle)
        Spacer(Modifier.width(48.dp))
    }
}

@Composable
private fun SetRow(
    setNumber: Int,
    set: LoggedSetUi,
    onWeightChange: (String) -> Unit,
    onRepsChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(setNumber.toString(), modifier = Modifier.width(36.dp), style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = set.weight,
            onValueChange = { onWeightChange(sanitizeWeightInput(it)) },
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
        )
        OutlinedTextField(
            value = set.reps,
            onValueChange = { value -> onRepsChange(value.filter { it.isDigit() }) },
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )
        IconButton(onClick = onRemove, modifier = Modifier.width(40.dp)) {
            Text("✕", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BottomActions(onAddExercise: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
        ) {
            OutlinedButton(onClick = onAddExercise, modifier = Modifier.fillMaxWidth()) { Text("+ Exercise") }
        }
    }
}

/** Keeps digits and a single decimal separator; a comma becomes a dot. */
private fun sanitizeWeightInput(input: String): String {
    var separatorSeen = false
    val sb = StringBuilder()
    for (c in input) {
        if (c.isDigit()) {
            sb.append(c)
        } else if ((c == '.' || c == ',') && !separatorSeen) {
            sb.append('.')
            separatorSeen = true
        }
    }
    return sb.toString()
}

/** Exercise clientId -> "A1", "A2", "B1"… for exercises in a superset. */
private fun computeSupersetLabels(exercises: List<LoggedExerciseUi>): Map<Long, String> {
    val counts = mutableMapOf<String, Int>()
    val labels = mutableMapOf<Long, String>()
    for (exercise in exercises) {
        val group = exercise.supersetGroup ?: continue
        val count = (counts[group] ?: 0) + 1
        counts[group] = count
        labels[exercise.clientId] = "$group$count"
    }
    return labels
}

/** Groups consecutive exercises with the same superset group; a lone member stays a Single. */
private fun buildLogBlocks(exercises: List<LoggedExerciseUi>): List<LogBlock> {
    val blocks = mutableListOf<LogBlock>()
    var i = 0
    while (i < exercises.size) {
        val group = exercises[i].supersetGroup
        if (group == null) {
            blocks += LogBlock.Single(exercises[i])
            i++
        } else {
            val members = mutableListOf<LoggedExerciseUi>()
            while (i < exercises.size && exercises[i].supersetGroup == group) {
                members += exercises[i]
                i++
            }
            blocks += if (members.size == 1) LogBlock.Single(members[0]) else LogBlock.Superset(group, members)
        }
    }
    return blocks
}

@Composable
private fun SupersetPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryFixed,
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("⛓ SUPERSET", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
            content()
        }
    }
}

@Composable
private fun SupersetActionBar(
    canMakeSuperset: Boolean,
    canUngroup: Boolean,
    onMakeSuperset: () -> Unit,
    onUngroup: () -> Unit,
    onClear: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Selected", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            if (canMakeSuperset) TextButton(onClick = onMakeSuperset) { Text("⛓ Make superset") }
            if (canUngroup) TextButton(onClick = onUngroup) { Text("Ungroup") }
            TextButton(onClick = onClear) { Text("Clear") }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No exercises yet", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Tap “+ Exercise” to add your first one.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutDatePicker(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    // DatePicker works in UTC millis.
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        onConfirm(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    } else {
                        onDismiss()
                    }
                },
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = datePickerState)
    }
}
