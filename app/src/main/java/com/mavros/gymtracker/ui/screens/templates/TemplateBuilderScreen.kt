package com.mavros.gymtracker.ui.screens.templates

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mavros.gymtracker.ui.components.AddExerciseDialog
import com.mavros.gymtracker.ui.components.ConfirmDialog
import com.mavros.gymtracker.ui.components.DragAutoScroller
import com.mavros.gymtracker.ui.components.DragHandle
import com.mavros.gymtracker.ui.components.EmptyState
import com.mavros.gymtracker.ui.components.GymTopBar
import com.mavros.gymtracker.ui.components.NewExerciseDialog
import com.mavros.gymtracker.ui.components.TargetAreaPill
import com.mavros.gymtracker.ui.components.rememberReorderState
import com.mavros.gymtracker.ui.components.rememberTextFileSaver
import com.mavros.gymtracker.ui.components.reorderItem
import com.mavros.gymtracker.ui.components.shareText
import com.mavros.gymtracker.ui.components.templateFileName
import kotlinx.coroutines.launch

@Composable
fun TemplateBuilderScreen(
    viewModel: TemplateBuilderViewModel,
    onStartWorkout: (sessionId: Long) -> Unit,
    onOpenTemplate: (templateId: Long) -> Unit,
    onBack: () -> Unit,
) {
    val template by viewModel.template.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val saveFile = rememberTextFileSaver()
    val allExercises by viewModel.allExercises.collectAsStateWithLifecycle()
    val editState by viewModel.editState.collectAsStateWithLifecycle()

    var showRenameTemplate by remember { mutableStateOf(false) }
    var renamingDayId by remember { mutableStateOf<Long?>(null) }
    var dayPendingDelete by remember { mutableStateOf<Long?>(null) }
    var showDeleteTemplate by remember { mutableStateOf(false) }
    var addSectionForDayId by remember { mutableStateOf<Long?>(null) }
    var renamingSectionId by remember { mutableStateOf<Long?>(null) }
    // Days the user opened. A template with a single day always shows it open.
    var expandedDayIds by rememberSaveable { mutableStateOf(listOf<Long>()) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val autoScroller = remember(listState, scope) { DragAutoScroller(listState, scope) }

    LaunchedEffect(editState.startedSessionId) {
        editState.startedSessionId?.let { sessionId ->
            viewModel.onStartedWorkoutHandled()
            onStartWorkout(sessionId)
        }
    }
    LaunchedEffect(editState.duplicatedTemplateId) {
        editState.duplicatedTemplateId?.let { id ->
            viewModel.onDuplicatedHandled()
            onOpenTemplate(id)
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
                onDelete = { showDeleteTemplate = true },
                onShare = { scope.launch { viewModel.exportText()?.let { context.shareText(template?.name ?: "Template", it) } } },
                onSaveFile = {
                    scope.launch {
                        viewModel.exportText()?.let { saveFile(templateFileName(template?.name ?: "template"), it) }
                    }
                },
                onDuplicate = viewModel::duplicate,
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (ui == null) return@LazyColumn
            if (ui.days.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.PlaylistAdd,
                        title = "No days yet",
                        message = "Add a day for each workout in this plan, e.g. Upper body, Lower body.",
                    )
                }
            }
            itemsIndexed(ui.days, key = { _, day -> day.id }) { dayIndex, day ->
                val expanded = ui.days.size == 1 || day.id in expandedDayIds
                DayCard(
                    day = day,
                    expanded = expanded,
                    canCollapse = ui.days.size > 1,
                    onToggleExpanded = {
                        expandedDayIds = if (day.id in expandedDayIds) expandedDayIds - day.id else expandedDayIds + day.id
                    },
                    autoScroller = autoScroller,
                    selectedIds = editState.selectedExerciseIds,
                    onToggleSelect = viewModel::toggleSelection,
                    onClearSelection = viewModel::clearSelection,
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
                    onMoveUp = if (dayIndex > 0) { { viewModel.moveDay(day.id, -1) } } else null,
                    onMoveDown = if (dayIndex < ui.days.lastIndex) { { viewModel.moveDay(day.id, +1) } } else null,
                    onStartWorkout = { viewModel.startWorkout(day.id) },
                )
            }
            item {
                OutlinedButton(
                    onClick = { viewModel.addDay() },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add day")
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
            message = "This permanently deletes the template and all its days. Logged workouts are kept.",
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
            initialValue = template?.days?.firstOrNull { it.id == dayId }?.name ?: "",
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
            initialValue = template?.days?.flatMap { it.sections }?.firstOrNull { it.id == sectionId }?.name ?: "",
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
private fun BuilderHeader(
    name: String,
    dayCount: Int,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onSaveFile: () -> Unit,
    onDuplicate: () -> Unit,
    onBack: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    GymTopBar(
        title = name,
        subtitle = if (dayCount == 1) "1 day" else "$dayCount days",
        onBack = onBack,
        onTitleClick = onRename,
        actions = {
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    MenuItem("Rename template", Icons.Outlined.Edit) {
                        menuOpen = false
                        onRename()
                    }
                    TemplateExportMenuItems(
                        onShare = onShare,
                        onSaveFile = onSaveFile,
                        onDuplicate = onDuplicate,
                        close = { menuOpen = false },
                    )
                    MenuItem("Delete template", Icons.Outlined.Delete, destructive = true) {
                        menuOpen = false
                        onDelete()
                    }
                }
            }
        },
    )
}

@Composable
private fun MenuItem(label: String, icon: ImageVector, destructive: Boolean = false, onClick: () -> Unit) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    DropdownMenuItem(
        text = { Text(label, color = color) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = color) },
        onClick = onClick,
    )
}

/** ⋮ button with a dropdown; [content] receives a callback that closes the menu. */
@Composable
private fun OverflowMenu(description: String, content: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = description, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            content { open = false }
        }
    }
}

@Composable
private fun DayCard(
    day: BuilderDayUi,
    expanded: Boolean,
    canCollapse: Boolean,
    onToggleExpanded: () -> Unit,
    autoScroller: DragAutoScroller,
    selectedIds: Set<Long>,
    onToggleSelect: (Long) -> Unit,
    onClearSelection: () -> Unit,
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
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onStartWorkout: () -> Unit,
) {
    val exercises = day.allExercises
    val selectedInDay = exercises.filter { it.id in selectedIds }
    val selecting = selectedIds.isNotEmpty()
    val namedSections = day.sections.filter { it.id != null }
    val ungrouped = day.sections.firstOrNull { it.id == null }
    var movingExerciseId by remember { mutableStateOf<Long?>(null) }
    val sectionReorder = rememberReorderState(day.id)
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        // Header: tap to open/close. Start is always reachable without opening the day.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = canCollapse, onClick = onToggleExpanded)
                .padding(start = if (canCollapse) 8.dp else 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (canCollapse) {
                Icon(
                    Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.padding(end = 6.dp).rotate(chevronRotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(day.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (exercises.size == 1) "1 exercise" else "${exercises.size} exercises",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            FilledTonalButton(
                onClick = onStartWorkout,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Start")
            }
            OverflowMenu("Day options") { close ->
                MenuItem("Rename day", Icons.Outlined.Edit) { close(); onRenameDay() }
                onMoveUp?.let { move -> MenuItem("Move up", Icons.Rounded.ArrowUpward) { close(); move() } }
                onMoveDown?.let { move -> MenuItem("Move down", Icons.Rounded.ArrowDownward) { close(); move() } }
                MenuItem("Add section", Icons.Outlined.PlaylistAdd) { close(); onAddSection() }
                MenuItem("Remove day", Icons.Outlined.Delete, destructive = true) { close(); onRemoveDay() }
            }
        }

        if (!expanded && exercises.isNotEmpty()) {
            Text(
                text = exercises.joinToString(" · ") { it.name },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp)) {
                HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                if (selectedInDay.isNotEmpty()) {
                    SelectionBar(
                        count = selectedInDay.size,
                        canMakeSuperset = selectedInDay.size >= 2,
                        canUngroup = selectedInDay.any { it.supersetGroup != null },
                        onMakeSuperset = onMakeSuperset,
                        onUngroup = onUngroup,
                        onClear = onClearSelection,
                    )
                }

                if (ungrouped != null && (ungrouped.exercises.isNotEmpty() || namedSections.isEmpty())) {
                    ExerciseList(
                        exercises = ungrouped.exercises,
                        reorderKey = "ungrouped-${day.id}",
                        autoScroller = autoScroller,
                        selectedIds = selectedIds,
                        selecting = selecting,
                        canMove = namedSections.isNotEmpty(),
                        onReorderIds = onReorderExerciseIds,
                        onToggleSelect = onToggleSelect,
                        onEditTarget = onEditTarget,
                        onMoveToSection = { movingExerciseId = it },
                        onRemove = onRemoveExercise,
                    )
                    if (namedSections.isEmpty()) AddExerciseButton { onAddExercise(null) }
                }

                namedSections.forEachIndexed { index, section ->
                    val sectionId = section.id ?: return@forEachIndexed
                    Column(Modifier.reorderItem(sectionReorder, index)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DragHandle(sectionReorder, index, namedSections.size, autoScroller, onReorderSections)
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = section.name ?: "",
                                modifier = Modifier.weight(1f, fill = false),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            if (!section.includeInWorkout) {
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    shape = MaterialTheme.shapes.extraSmall,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(Icons.Outlined.VisibilityOff, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Text("Not in workout", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            }
                            OverflowMenu("Section options") { close ->
                                MenuItem("Rename section", Icons.Outlined.Edit) { close(); onRenameSection(sectionId) }
                                if (section.includeInWorkout) {
                                    MenuItem("Leave out of workouts", Icons.Outlined.VisibilityOff) {
                                        close(); onSetSectionIncluded(sectionId, false)
                                    }
                                } else {
                                    MenuItem("Include in workouts", Icons.Outlined.Visibility) {
                                        close(); onSetSectionIncluded(sectionId, true)
                                    }
                                }
                                MenuItem("Remove section", Icons.Outlined.Delete, destructive = true) {
                                    close(); onRemoveSection(sectionId)
                                }
                            }
                        }
                        ExerciseList(
                            exercises = section.exercises,
                            reorderKey = "section-$sectionId",
                            autoScroller = autoScroller,
                            selectedIds = selectedIds,
                            selecting = selecting,
                            canMove = true,
                            onReorderIds = onReorderExerciseIds,
                            onToggleSelect = onToggleSelect,
                            onEditTarget = onEditTarget,
                            onMoveToSection = { movingExerciseId = it },
                            onRemove = onRemoveExercise,
                        )
                        AddExerciseButton { onAddExercise(sectionId) }
                    }
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

@Composable
private fun AddExerciseButton(onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.padding(start = 4.dp),
        contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text("Add exercise")
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    canMakeSuperset: Boolean,
    canUngroup: Boolean,
    onMakeSuperset: () -> Unit,
    onUngroup: () -> Unit,
    onClear: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClear) { Icon(Icons.Rounded.Close, contentDescription = "Clear selection") }
            Text("$count selected", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
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
    selecting: Boolean,
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
        val row: @Composable (BuilderExerciseUi, @Composable () -> Unit, Modifier) -> Unit = { exercise, rowHandle, modifier ->
            ExerciseRow(
                exercise = exercise,
                selecting = selecting,
                selected = exercise.id in selectedIds,
                canMove = canMove,
                modifier = modifier,
                handle = rowHandle,
                onToggleSelect = { onToggleSelect(exercise.id) },
                onEditTarget = { onEditTarget(exercise) },
                onMoveToSection = { onMoveToSection(exercise.id) },
                onRemove = { onRemove(exercise.id) },
            )
        }
        when (block) {
            is BuilderBlock.Single -> row(block.exercise, handle, Modifier.reorderItem(reorder, index))
            is BuilderBlock.Superset -> {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).reorderItem(reorder, index),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                ) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            handle()
                            Icon(
                                Icons.Outlined.Link,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Superset", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        }
                        block.members.forEach { member -> row(member, { Spacer(Modifier.width(32.dp)) }, Modifier) }
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
    selecting: Boolean,
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
        if (selecting) Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
        Column(Modifier.weight(1f).padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                exercise.supersetLabel?.let {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Text(it, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
                Text(
                    text = exercise.name,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                TargetChip(label = exercise.targetLabel, onClick = onEditTarget)
                exercise.targetArea?.let { TargetAreaPill(it) }
            }
        }
        OverflowMenu("Exercise options") { close ->
            MenuItem("Set target", Icons.Outlined.Tune) { close(); onEditTarget() }
            MenuItem(if (selected) "Deselect" else "Select for superset", Icons.Outlined.CheckBox) {
                close(); onToggleSelect()
            }
            if (canMove) {
                MenuItem("Move to section", Icons.AutoMirrored.Outlined.DriveFileMove) { close(); onMoveToSection() }
            }
            MenuItem("Remove", Icons.Outlined.Delete, destructive = true) { close(); onRemove() }
        }
    }
}

/** "3 × 10" target, or a "+ Target" prompt when none is set. */
@Composable
private fun TargetChip(label: String?, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (label != null) colors.primaryContainer else colors.surface,
        contentColor = if (label != null) colors.onPrimaryContainer else colors.primary,
        border = if (label != null) null else BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Text(
            text = label ?: "+ Target",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
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
