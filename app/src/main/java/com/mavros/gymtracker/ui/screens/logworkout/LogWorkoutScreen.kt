package com.mavros.gymtracker.ui.screens.logworkout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mavros.gymtracker.ui.components.AddExerciseDialog
import com.mavros.gymtracker.ui.components.ConfirmDialog
import com.mavros.gymtracker.ui.components.EmptyState
import com.mavros.gymtracker.ui.components.GymTopBar
import com.mavros.gymtracker.ui.components.LocalWeightUnit
import com.mavros.gymtracker.ui.components.NewExerciseDialog
import com.mavros.gymtracker.ui.components.TargetAreaPill
import com.mavros.gymtracker.ui.components.formatVolume
import com.mavros.gymtracker.ui.components.formatWeight
import com.mavros.gymtracker.ui.components.toSessionLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

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
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // Compact view folds every exercise to a one-line summary; tapped cards open individually.
    var compact by rememberSaveable { mutableStateOf(false) }
    var openedInCompact by remember { mutableStateOf(setOf<Long>()) }

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
    val selecting = state.selectedExerciseIds.isNotEmpty()

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            WorkoutTopBar(
                date = state.date,
                dayName = state.templateDayName,
                onPickDate = { showDatePicker = true },
                compact = compact,
                onToggleCompact = {
                    compact = !compact
                    openedInCompact = emptySet()
                },
                onDelete = { showDeleteConfirm = true },
                onDone = onDone,
            )
        },
        bottomBar = {
            BottomActions(
                totalVolume = state.totalVolume,
                exerciseCount = state.exercises.size,
                setCount = state.exercises.sumOf { it.sets.size },
                onAddExercise = viewModel::openAddExerciseDialog,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.exercises.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Add,
                        title = "No exercises yet",
                        message = "Add your first exercise. Last time's sets are filled in for you.",
                        actionLabel = "Add exercise",
                        onAction = viewModel::openAddExerciseDialog,
                    )
                }
            }
            if (selecting) {
                item {
                    val selected = state.exercises.filter { it.clientId in state.selectedExerciseIds }
                    SupersetActionBar(
                        count = selected.size,
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
                            modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    when (block) {
                        is LogBlock.Single -> LoggedExerciseCard(
                            exercise = block.exercise,
                            collapsed = compact && block.exercise.clientId !in openedInCompact,
                            onToggleCollapsed = if (compact) { { openedInCompact = openedInCompact.toggle(block.exercise.clientId) } } else null,
                            previous = state.previous[block.exercise.exerciseId],
                            selecting = selecting,
                            selected = block.exercise.clientId in state.selectedExerciseIds,
                            supersetLabel = supersetLabels[block.exercise.clientId],
                            viewModel = viewModel,
                        )
                        is LogBlock.Superset -> SupersetPanel {
                            block.members.forEach { member ->
                                LoggedExerciseCard(
                                    exercise = member,
                                    collapsed = compact && member.clientId !in openedInCompact,
                                    onToggleCollapsed = if (compact) { { openedInCompact = openedInCompact.toggle(member.clientId) } } else null,
                                    previous = state.previous[member.exerciseId],
                                    selecting = selecting,
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

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "Delete workout?",
            message = "This permanently deletes this workout and all its sets.",
            onConfirm = {
                showDeleteConfirm = false
                viewModel.deleteWorkout()
                onDone()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

@Composable
private fun WorkoutTopBar(
    date: LocalDate,
    dayName: String?,
    onPickDate: () -> Unit,
    compact: Boolean,
    onToggleCompact: () -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    GymTopBar(
        title = if (date == LocalDate.now()) "Today" else date.toSessionLabel(),
        subtitle = dayName,
        onBack = onDone,
        onTitleClick = onPickDate,
        titleTrailing = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = "Change date") },
        actions = {
            IconButton(onClick = onToggleCompact) {
                Icon(
                    if (compact) Icons.Rounded.UnfoldMore else Icons.Rounded.UnfoldLess,
                    contentDescription = if (compact) "Show all sets" else "Compact view",
                )
            }
            TextButton(onClick = onDone) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Done")
            }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Change date") },
                        leadingIcon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onPickDate()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete workout", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        },
    )
}

/** An exercise card wired to the view model. */
@Composable
private fun LoggedExerciseCard(
    exercise: LoggedExerciseUi,
    collapsed: Boolean,
    onToggleCollapsed: (() -> Unit)?,
    previous: PreviousPerformanceUi?,
    selecting: Boolean,
    selected: Boolean,
    supersetLabel: String?,
    viewModel: LogWorkoutViewModel,
) {
    ExerciseCard(
        exercise = exercise,
        collapsed = collapsed,
        onToggleCollapsed = onToggleCollapsed,
        previous = previous,
        selecting = selecting,
        selected = selected,
        supersetLabel = supersetLabel,
        onToggleSelect = { viewModel.toggleSelection(exercise.clientId) },
        onToggleIncrease = { viewModel.toggleIncreaseNextTime(exercise.clientId) },
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
    collapsed: Boolean,
    onToggleCollapsed: (() -> Unit)?,
    previous: PreviousPerformanceUi?,
    selecting: Boolean,
    selected: Boolean,
    supersetLabel: String?,
    onToggleSelect: () -> Unit,
    onToggleIncrease: () -> Unit,
    onAddSet: () -> Unit,
    onWeightChange: (setClientId: Long, String) -> Unit,
    onRepsChange: (setClientId: Long, String) -> Unit,
    onRemoveSet: (setClientId: Long) -> Unit,
    onRemoveExercise: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 8.dp)) {
            Row(
                modifier = if (onToggleCollapsed != null) Modifier.clickable(onClick = onToggleCollapsed) else Modifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selecting) {
                    Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
                }
                Row(
                    modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (supersetLabel != null) {
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ) {
                            Text(
                                text = supersetLabel,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                    Text(
                        text = exercise.name,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    exercise.targetArea?.let { TargetAreaPill(it) }
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = "Exercise options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (selected) "Deselect" else "Select for superset") },
                            leadingIcon = { Icon(Icons.Outlined.CheckBox, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onToggleSelect()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Remove exercise", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                menuOpen = false
                                confirmRemove = true
                            },
                        )
                    }
                }
            }
            if (collapsed) {
                CollapsedSummary(
                    exercise = exercise,
                    goHeavier = previous?.increaseFlagged == true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (onToggleCollapsed != null) Modifier.clickable(onClick = onToggleCollapsed) else Modifier)
                        .padding(end = 8.dp, bottom = 6.dp),
                )
                return@Column
            }
            if (previous != null) {
                PreviousPerformance(previous, isTimed = exercise.isTimed, modifier = Modifier.padding(end = 8.dp))
            }
            Spacer(Modifier.height(6.dp))
            SetsHeaderRow(repsLabel = if (exercise.isTimed) "SEC" else "REPS")
            exercise.sets.forEachIndexed { index, set ->
                val hint = previous?.sets?.getOrNull(index)
                SetRow(
                    setNumber = index + 1,
                    set = set,
                    weightHint = hint?.weight?.formatWeight() ?: "–",
                    repsHint = hint?.reps?.toString() ?: "–",
                    onWeightChange = { onWeightChange(set.clientId, it) },
                    onRepsChange = { onRepsChange(set.clientId, it) },
                    onRemove = { onRemoveSet(set.clientId) },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 8.dp, top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onAddSet, contentPadding = ButtonDefaults.TextButtonWithIconContentPadding) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add set")
                }
                FilterChip(
                    selected = exercise.increaseNextTime,
                    onClick = onToggleIncrease,
                    label = { Text("Increase next time") },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.TrendingUp,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ),
                )
            }
        }
    }

    if (confirmRemove) {
        ConfirmDialog(
            title = "Remove ${exercise.name}?",
            message = "Its sets in this workout will be removed too.",
            confirmLabel = "Remove",
            onConfirm = {
                confirmRemove = false
                onRemoveExercise()
            },
            onDismiss = { confirmRemove = false },
        )
    }
}

/** One line for compact view: "3 sets · 20×12 · 20×10 · 20×9 kg", plus flag icons. */
@Composable
private fun CollapsedSummary(exercise: LoggedExerciseUi, goHeavier: Boolean, modifier: Modifier = Modifier) {
    val unit = LocalWeightUnit.current.label
    val filled = exercise.sets.filter { it.reps.isNotBlank() || it.weight.isNotBlank() }
    val setsText = filled.joinToString(" · ") { set ->
        val reps = set.reps.ifBlank { "–" } + if (exercise.isTimed) "s" else ""
        if (set.weight.isBlank()) reps else "${set.weight}×$reps"
    }
    val count = exercise.sets.size
    val text = buildString {
        append(if (count == 1) "1 set" else "$count sets")
        if (setsText.isNotEmpty()) {
            append("  ·  ").append(setsText)
            if (filled.any { it.weight.isNotBlank() }) append(" ").append(unit)
        }
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (goHeavier || exercise.increaseNextTime) {
            Icon(
                Icons.Rounded.TrendingUp,
                contentDescription = if (goHeavier) "Go heavier this time" else "Flagged to increase next time",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

/** "Last · Tue 18 Nov: 80×8 · 80×8 · 82.5×6 kg", plus a banner if it was flagged for an increase. */
@Composable
private fun PreviousPerformance(previous: PreviousPerformanceUi, isTimed: Boolean, modifier: Modifier = Modifier) {
    val unit = LocalWeightUnit.current.label
    val summary = previous.sets.joinToString(" · ") { set ->
        val reps = if (isTimed) "${set.reps}s" else set.reps.toString()
        if (set.weight == null) reps else "${set.weight.formatWeight()}×$reps"
    }
    val hasWeights = previous.sets.any { it.weight != null }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (previous.increaseFlagged) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.TrendingUp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Go heavier — you flagged this last time", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        if (previous.sets.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.History,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${previous.date.toSessionLabel()}:  $summary" + if (hasWeights) " $unit" else "",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private val SetNumberWidth = 32.dp
private val RemoveButtonWidth = 40.dp

@Composable
private fun SetsHeaderRow(repsLabel: String) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("SET", modifier = Modifier.width(SetNumberWidth), color = labelColor, style = labelStyle, textAlign = TextAlign.Center)
        Text(
            text = LocalWeightUnit.current.label.uppercase(),
            modifier = Modifier.weight(1f),
            color = labelColor,
            style = labelStyle,
            textAlign = TextAlign.Center,
        )
        Text(repsLabel, modifier = Modifier.weight(1f), color = labelColor, style = labelStyle, textAlign = TextAlign.Center)
        Spacer(Modifier.width(RemoveButtonWidth))
    }
}

@Composable
private fun SetRow(
    setNumber: Int,
    set: LoggedSetUi,
    weightHint: String,
    repsHint: String,
    onWeightChange: (String) -> Unit,
    onRepsChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(SetNumberWidth), contentAlignment = Alignment.Center) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(28.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(setNumber.toString(), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        NumberField(
            value = set.weight,
            onValueChange = { onWeightChange(sanitizeWeightInput(it)) },
            hint = weightHint,
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Next,
            modifier = Modifier.weight(1f),
        )
        NumberField(
            value = set.reps,
            onValueChange = { value -> onRepsChange(value.filter { it.isDigit() }) },
            hint = repsHint,
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Next,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(RemoveButtonWidth)) {
            Icon(
                Icons.Outlined.RemoveCircleOutline,
                contentDescription = "Remove set $setNumber",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/** Compact, centered numeric input; [hint] (last time's value) shows while empty. */
@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val textStyle = MaterialTheme.typography.titleMedium.copy(color = colors.onSurface, textAlign = TextAlign.Center)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.height(44.dp),
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        decorationBox = { inner ->
            Surface(shape = MaterialTheme.shapes.small, color = colors.surfaceContainerHigh) {
                Box(Modifier.fillMaxSize().padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                    if (value.isEmpty()) {
                        Text(hint, style = textStyle.copy(color = colors.outline), maxLines = 1)
                    }
                    inner()
                }
            }
        },
    )
}

@Composable
private fun BottomActions(totalVolume: Double, exerciseCount: Int, setCount: Int, onAddExercise: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${totalVolume.formatVolume()} ${LocalWeightUnit.current.label}",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "volume · $exerciseCount ex · $setCount sets",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(
                onClick = onAddExercise,
                modifier = Modifier.height(48.dp),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Exercise")
            }
        }
    }
}

private fun Set<Long>.toggle(id: Long): Set<Long> = if (id in this) this - id else this + id

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
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        contentColor = MaterialTheme.colorScheme.primary,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                Text("Superset", style = MaterialTheme.typography.labelLarge)
            }
            content()
        }
    }
}

@Composable
private fun SupersetActionBar(
    count: Int,
    canMakeSuperset: Boolean,
    canUngroup: Boolean,
    onMakeSuperset: () -> Unit,
    onUngroup: () -> Unit,
    onClear: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClear) { Icon(Icons.Rounded.Close, contentDescription = "Clear selection") }
                Text(
                    text = "$count selected",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (canUngroup) {
                    TextButton(onClick = onUngroup) {
                        Icon(Icons.Outlined.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Ungroup")
                    }
                }
                TextButton(onClick = onMakeSuperset, enabled = canMakeSuperset) {
                    Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Superset")
                }
            }
            if (!canMakeSuperset) {
                Text(
                    text = "Select one more exercise to make a superset.",
                    modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
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
