package com.mavros.gymtracker.ui.screens.templates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mavros.gymtracker.ui.components.AddExerciseDialog
import com.mavros.gymtracker.ui.components.ConfirmDialog
import com.mavros.gymtracker.ui.components.DragAutoScroller
import com.mavros.gymtracker.ui.components.DragHandle
import com.mavros.gymtracker.ui.components.NewExerciseDialog
import com.mavros.gymtracker.ui.components.TargetAreaPill
import com.mavros.gymtracker.ui.components.rememberReorderState
import com.mavros.gymtracker.ui.components.reorderItem

@Composable
fun TemplateBuilderScreen(
    viewModel: TemplateBuilderViewModel,
    onStartWorkout: (sessionId: Long) -> Unit,
    onBack: () -> Unit,
) {
    val template by viewModel.template.collectAsStateWithLifecycle()
    val allExercises by viewModel.allExercises.collectAsStateWithLifecycle()
    val editState by viewModel.editState.collectAsStateWithLifecycle()

    var showRenameTemplate by remember { mutableStateOf(false) }
    var renamingDayId by remember { mutableStateOf<Long?>(null) }
    var dayPendingDelete by remember { mutableStateOf<Long?>(null) }
    var showDeleteTemplate by remember { mutableStateOf(false) }
    var addSectionForDayId by remember { mutableStateOf<Long?>(null) }
    var renamingSectionId by remember { mutableStateOf<Long?>(null) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val autoScroller = remember(listState, scope) { DragAutoScroller(listState, scope) }

    LaunchedEffect(editState.startedSessionId) {
        editState.startedSessionId?.let { sessionId ->
            viewModel.onStartedWorkoutHandled()
            onStartWorkout(sessionId)
        }
    }
    LaunchedEffect(editState.deleted) {
        if (editState.deleted) {
            viewModel.onDeletedHandled()
            onBack()
        }
    }

    Scaffold(
        topBar = {
            BuilderHeader(
                name = template?.name ?: "",
                dayCount = template?.days?.size ?: 0,
                onRename = { showRenameTemplate = true },
                onBack = onBack,
            )
        },
    ) { padding ->
        val ui = template
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .onGloballyPositioned {
                    val top = it.positionInWindow().y
                    autoScroller.viewportTop = top
                    autoScroller.viewportBottom = top + it.size.height
                },
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (ui == null) return@LazyColumn
            items(ui.days, key = { it.id }) { day ->
                DayCard(
                    day = day,
                    autoScroller = autoScroller,
                    selectedIds = editState.selectedExerciseIds,
                    onToggleSelect = viewModel::toggleSelection,
                    onAddExercise = { sectionId -> viewModel.openAddExercise(day.id, sectionId) },
                    onAddSection = { addSectionForDayId = day.id },
                    onRenameSection = { renamingSectionId = it },
                    onRemoveSection = { viewModel.removeSection(it) },
                    onSetSectionIncluded = { id, include -> viewModel.setSectionIncluded(id, include) },
                    onMoveExercise = { id, sectionId -> viewModel.moveExerciseToSection(id, sectionId) },
                    onReorderSections = { from, to -> viewModel.reorderSections(day.id, from, to) },
                    onReorderExerciseIds = viewModel::applyExerciseOrder,
                    onEditTarget = viewModel::openTargetEdit,
                    onRemoveExercise = viewModel::removeExercise,
                    onMakeSuperset = { viewModel.makeSuperset(day.id) },
                    onUngroup = { viewModel.ungroupSuperset(day.id) },
                    onRenameDay = { renamingDayId = day.id },
                    onRemoveDay = { dayPendingDelete = day.id },
                    onStartWorkout = { viewModel.startWorkout(day.id) },
                )
            }
            item {
                OutlinedButton(onClick = { viewModel.addDay() }, modifier = Modifier.fillMaxWidth()) { Text("+ Add day") }
            }
            item {
                TextButton(onClick = { showDeleteTemplate = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete template", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    dayPendingDelete?.let { dayId ->
        ConfirmDialog(
            title = "Remove day?",
            message = "This removes the day and its exercises from the template.",
            confirmLabel = "Remove",
            onConfirm = {
                viewModel.removeDay(dayId)
                dayPendingDelete = null
            },
            onDismiss = { dayPendingDelete = null },
        )
    }

    if (showDeleteTemplate) {
        ConfirmDialog(
            title = "Delete template?",
            message = "This permanently deletes the template and all its days.",
            onConfirm = {
                showDeleteTemplate = false
                viewModel.deleteTemplate()
            },
            onDismiss = { showDeleteTemplate = false },
        )
    }

    editState.addExerciseForDayId?.let { dayId ->
        val sectionId = editState.addExerciseForSectionId
        AddExerciseDialog(
            allExercises = allExercises,
            onDismiss = viewModel::closeAddExercise,
            onPickExisting = { viewModel.addExistingExercise(dayId, sectionId, it) },
            onSubmitTyped = { viewModel.submitTypedExercise(dayId, sectionId, it) },
        )
    }

    editState.pendingNewExerciseName?.let { name ->
        NewExerciseDialog(
            name = name,
            onDismiss = viewModel::cancelNewExercise,
            onConfirm = viewModel::confirmNewExercise,
        )
    }

    if (showRenameTemplate) {
        NameDialog(
            title = "Rename template",
            label = "Template name",
            initialValue = template?.name ?: "",
            onDismiss = { showRenameTemplate = false },
            onConfirm = {
                viewModel.renameTemplate(it)
                showRenameTemplate = false
            },
        )
    }

    renamingDayId?.let { dayId ->
        NameDialog(
            title = "Rename day",
            label = "Day name (optional)",
            onDismiss = { renamingDayId = null },
            onConfirm = {
                viewModel.renameDay(dayId, it)
                renamingDayId = null
            },
        )
    }

    addSectionForDayId?.let { dayId ->
        NameDialog(
            title = "Add section",
            label = "Section name (e.g. Warm up)",
            onDismiss = { addSectionForDayId = null },
            onConfirm = {
                viewModel.addSection(dayId, it)
                addSectionForDayId = null
            },
        )
    }

    renamingSectionId?.let { sectionId ->
        NameDialog(
            title = "Rename section",
            label = "Section name",
            onDismiss = { renamingSectionId = null },
            onConfirm = {
                viewModel.renameSection(sectionId, it)
                renamingSectionId = null
            },
        )
    }

    editState.targetEditFor?.let { templateExerciseId ->
        key(templateExerciseId) {
            TargetDialog(
                initialSets = editState.targetEditInitialSets,
                initialReps = editState.targetEditInitialReps,
                isTimed = editState.targetEditIsTimed,
                onDismiss = viewModel::closeTargetEdit,
                onSave = { sets, reps -> viewModel.saveTarget(templateExerciseId, sets, reps) },
            )
        }
    }
}

/** Optional sets × reps (or seconds) prescription for a template exercise. */
@Composable
private fun TargetDialog(
    initialSets: String,
    initialReps: String,
    isTimed: Boolean,
    onDismiss: () -> Unit,
    onSave: (sets: Int?, reps: Int?) -> Unit,
) {
    var sets by remember { mutableStateOf(initialSets) }
    var reps by remember { mutableStateOf(initialReps) }
    val repsLabel = if (isTimed) "Seconds" else "Reps"
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onSave(sets.toIntOrNull(), reps.toIntOrNull()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text(if (isTimed) "Target sets & seconds" else "Target sets & reps") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Optional prescription used to pre-fill a workout started from this template.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = sets,
                        onValueChange = { value -> sets = value.filter { it.isDigit() } },
                        modifier = Modifier.weight(1f),
                        label = { Text("Sets") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = reps,
                        onValueChange = { value -> reps = value.filter { it.isDigit() } },
                        modifier = Modifier.weight(1f),
                        label = { Text(repsLabel) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                }
            }
        },
    )
}

@Composable
private fun BuilderHeader(name: String, dayCount: Int, onRename: () -> Unit, onBack: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 4.dp, end = 16.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("←") }
            Text(
                text = name,
                modifier = Modifier.weight(1f).clickable(onClick = onRename),
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = if (dayCount == 1) "1 day" else "$dayCount days",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun DayCard(
    day: BuilderDayUi,
    autoScroller: DragAutoScroller,
    selectedIds: Set<Long>,
    onToggleSelect: (Long) -> Unit,
    onAddExercise: (sectionId: Long?) -> Unit,
    onAddSection: () -> Unit,
    onRenameSection: (Long) -> Unit,
    onRemoveSection: (Long) -> Unit,
    onSetSectionIncluded: (Long, Boolean) -> Unit,
    onMoveExercise: (templateExerciseId: Long, sectionId: Long?) -> Unit,
    onReorderSections: (from: Int, to: Int) -> Unit,
    onReorderExerciseIds: (List<Long>) -> Unit,
    onEditTarget: (BuilderExerciseUi) -> Unit,
    onRemoveExercise: (Long) -> Unit,
    onMakeSuperset: () -> Unit,
    onUngroup: () -> Unit,
    onRenameDay: () -> Unit,
    onRemoveDay: () -> Unit,
    onStartWorkout: () -> Unit,
) {
    val selected = day.allExercises.filter { it.id in selectedIds }
    val canMakeSuperset = selected.size >= 2
    val canUngroup = selected.any { it.supersetGroup != null }
    val namedSections = day.sections.filter { it.id != null }
    val ungrouped = day.sections.firstOrNull { it.id == null }
    var movingExerciseId by remember { mutableStateOf<Long?>(null) }
    val sectionReorder = rememberReorderState(day.id)

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = day.title,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = onRenameDay) { Text("Rename") }
                TextButton(onClick = onRemoveDay) { Text("Remove") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onAddSection, modifier = Modifier.weight(1f)) { Text("+ Add section") }
                Button(onClick = onStartWorkout, modifier = Modifier.weight(1f)) { Text("Start workout") }
            }

            if (ungrouped != null) {
                ExerciseList(
                    exercises = ungrouped.exercises,
                    reorderKey = "ungrouped-${day.id}",
                    autoScroller = autoScroller,
                    selectedIds = selectedIds,
                    canMove = namedSections.isNotEmpty(),
                    onReorderIds = onReorderExerciseIds,
                    onToggleSelect = onToggleSelect,
                    onEditTarget = onEditTarget,
                    onMoveToSection = { movingExerciseId = it },
                    onRemove = onRemoveExercise,
                )
                if (namedSections.isEmpty()) {
                    TextButton(onClick = { onAddExercise(null) }) { Text("+ Add exercise") }
                }
            }

            namedSections.forEachIndexed { index, section ->
                val sectionId = section.id ?: return@forEachIndexed
                Column(Modifier.reorderItem(sectionReorder, index)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DragHandle(sectionReorder, index, namedSections.size, autoScroller, onReorderSections)
                        Text(
                            text = section.name ?: "",
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        TextButton(onClick = { onRenameSection(sectionId) }) { Text("Rename") }
                        TextButton(onClick = { onRemoveSection(sectionId) }) { Text("Remove") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = section.includeInWorkout,
                            onCheckedChange = { onSetSectionIncluded(sectionId, it) },
                        )
                        Text("Show in workout", style = MaterialTheme.typography.bodySmall)
                    }
                    ExerciseList(
                        exercises = section.exercises,
                        reorderKey = "section-$sectionId",
                        autoScroller = autoScroller,
                        selectedIds = selectedIds,
                        canMove = namedSections.isNotEmpty(),
                        onReorderIds = onReorderExerciseIds,
                        onToggleSelect = onToggleSelect,
                        onEditTarget = onEditTarget,
                        onMoveToSection = { movingExerciseId = it },
                        onRemove = onRemoveExercise,
                    )
                    TextButton(onClick = { onAddExercise(sectionId) }) { Text("+ Add exercise") }
                }
            }

            if (canMakeSuperset || canUngroup) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (canMakeSuperset) TextButton(onClick = onMakeSuperset) { Text("⛓ Make superset") }
                    if (canUngroup) TextButton(onClick = onUngroup) { Text("Ungroup") }
                }
            }
        }
    }

    movingExerciseId?.let { exerciseId ->
        MoveToSectionDialog(
            sections = namedSections,
            onDismiss = { movingExerciseId = null },
            onPick = { sectionId ->
                onMoveExercise(exerciseId, sectionId)
                movingExerciseId = null
            },
        )
    }
}

/**
 * The exercises of one section as drag-reorderable blocks; a superset moves as one block.
 * Reordering reports the full new id order for the section.
 */
@Composable
private fun ColumnScope.ExerciseList(
    exercises: List<BuilderExerciseUi>,
    reorderKey: Any,
    autoScroller: DragAutoScroller,
    selectedIds: Set<Long>,
    canMove: Boolean,
    onReorderIds: (List<Long>) -> Unit,
    onToggleSelect: (Long) -> Unit,
    onEditTarget: (BuilderExerciseUi) -> Unit,
    onMoveToSection: (Long) -> Unit,
    onRemove: (Long) -> Unit,
) {
    val blocks = buildBuilderBlocks(exercises)
    val reorder = rememberReorderState(reorderKey)
    val onMoveBlock: (Int, Int) -> Unit = remember(blocks, onReorderIds) {
        { from, to ->
            val moved = blocks.toMutableList()
            moved.add(to.coerceIn(0, moved.size - 1), moved.removeAt(from))
            onReorderIds(moved.flatMap { it.exerciseIds })
        }
    }

    blocks.forEachIndexed { index, block ->
        val handle: @Composable () -> Unit = {
            DragHandle(reorder, index, blocks.size, autoScroller, onMoveBlock)
        }
        when (block) {
            is BuilderBlock.Single -> {
                val exercise = block.exercise
                ExerciseRow(
                    exercise = exercise,
                    selected = exercise.id in selectedIds,
                    canMove = canMove,
                    modifier = Modifier.reorderItem(reorder, index),
                    handle = handle,
                    onToggleSelect = { onToggleSelect(exercise.id) },
                    onEditTarget = { onEditTarget(exercise) },
                    onMoveToSection = { onMoveToSection(exercise.id) },
                    onRemove = { onRemove(exercise.id) },
                )
            }
            is BuilderBlock.Superset -> {
                Surface(
                    modifier = Modifier.fillMaxWidth().reorderItem(reorder, index),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryFixed,
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            handle()
                            Text("⛓ SUPERSET", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                        }
                        block.members.forEach { member ->
                            ExerciseRow(
                                exercise = member,
                                selected = member.id in selectedIds,
                                canMove = canMove,
                                handle = {},
                                onToggleSelect = { onToggleSelect(member.id) },
                                onEditTarget = { onEditTarget(member) },
                                onMoveToSection = { onMoveToSection(member.id) },
                                onRemove = { onRemove(member.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Groups consecutive exercises with the same superset group; a lone member stays a Single. */
private fun buildBuilderBlocks(exercises: List<BuilderExerciseUi>): List<BuilderBlock> {
    val blocks = mutableListOf<BuilderBlock>()
    var i = 0
    while (i < exercises.size) {
        val group = exercises[i].supersetGroup
        if (group == null) {
            blocks += BuilderBlock.Single(exercises[i])
            i++
        } else {
            val members = mutableListOf<BuilderExerciseUi>()
            while (i < exercises.size && exercises[i].supersetGroup == group) {
                members += exercises[i]
                i++
            }
            blocks += if (members.size == 1) BuilderBlock.Single(members[0]) else BuilderBlock.Superset(group, members)
        }
    }
    return blocks
}

@Composable
private fun ExerciseRow(
    exercise: BuilderExerciseUi,
    selected: Boolean,
    canMove: Boolean,
    modifier: Modifier = Modifier,
    handle: @Composable () -> Unit,
    onToggleSelect: () -> Unit,
    onEditTarget: () -> Unit,
    onMoveToSection: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        handle()
        Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
        exercise.supersetLabel?.let {
            Text(
                text = it,
                modifier = Modifier.padding(end = 4.dp),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(exercise.name, style = MaterialTheme.typography.bodyLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = exercise.targetLabel ?: "Set target",
                    modifier = Modifier.clickable(onClick = onEditTarget).padding(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                )
                if (canMove) {
                    Text(
                        text = "· Move",
                        modifier = Modifier.clickable(onClick = onMoveToSection).padding(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        exercise.targetArea?.let { TargetAreaPill(it) }
        Text(
            text = "✕",
            modifier = Modifier.clickable(onClick = onRemove).padding(6.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun MoveToSectionDialog(
    sections: List<BuilderSectionUi>,
    onDismiss: () -> Unit,
    onPick: (sectionId: Long?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Move to section") },
        text = {
            Column {
                Text(
                    text = "No section",
                    modifier = Modifier.fillMaxWidth().clickable { onPick(null) }.padding(vertical = 12.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
                sections.forEach { section ->
                    Text(
                        text = section.name ?: "",
                        modifier = Modifier.fillMaxWidth().clickable { onPick(section.id) }.padding(vertical = 12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        },
    )
}
