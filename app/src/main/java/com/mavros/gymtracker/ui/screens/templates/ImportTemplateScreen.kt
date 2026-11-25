package com.mavros.gymtracker.ui.screens.templates

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.FiberNew
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mavros.gymtracker.data.Exercise
import com.mavros.gymtracker.data.TemplateText
import com.mavros.gymtracker.data.normalizeExerciseName
import com.mavros.gymtracker.ui.components.GymTopBar
import com.mavros.gymtracker.ui.components.MAX_TEXT_FILE_BYTES
import com.mavros.gymtracker.ui.components.SectionLabel
import com.mavros.gymtracker.ui.components.TargetAreaPill
import com.mavros.gymtracker.ui.components.clipboardText
import com.mavros.gymtracker.ui.components.copyText
import com.mavros.gymtracker.ui.components.readTextUpTo
import com.mavros.gymtracker.ui.components.shareText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val FORMAT_EXAMPLE = """
    Template: Upper / Lower
    Day: Upper
    Section: Warm up (skip)
    Band Pull Apart 2x15 [shoulders]
    Section: Main
    A: Bench Press 4x8 [chest]
    A: Barbell Row 4x8 [back]
    Plank 3x45s [core]
    Day: Lower
    Squat 4x6 [legs]
""".trimIndent()

@Composable
fun ImportTemplateScreen(
    viewModel: ImportTemplateViewModel,
    pendingImportText: MutableStateFlow<String?>,
    onImported: (templateId: Long) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val allExercises by viewModel.allExercises.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    // Text shared from another app (also while this screen is already open).
    LaunchedEffect(Unit) {
        pendingImportText.filterNotNull().collect {
            pendingImportText.value = null
            viewModel.receiveSharedText(it)
        }
    }
    LaunchedEffect(state.importedTemplateId) {
        state.importedTemplateId?.let {
            viewModel.onImportedHandled()
            onImported(it)
        }
    }
    BackHandler(enabled = state.previewing) { viewModel.backToEdit() }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readTextUpTo(MAX_TEXT_FILE_BYTES) } }.getOrNull()
            }
            if (text.isNullOrBlank()) {
                snackbar.showSnackbar("Couldn't read that file as text.")
            } else {
                viewModel.onTextChange(text)
                viewModel.preview()
            }
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            GymTopBar(
                title = if (state.previewing) "Check import" else "Import template",
                onBack = if (state.previewing) viewModel::backToEdit else onBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.previewing) {
                        OutlinedButton(onClick = viewModel::backToEdit) { Text("Edit text") }
                        Button(
                            onClick = viewModel::import,
                            enabled = !state.importing,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                when (state.newCount) {
                                    0 -> "Import"
                                    1 -> "Import · 1 new exercise"
                                    else -> "Import · ${state.newCount} new exercises"
                                },
                            )
                        }
                    } else {
                        Button(
                            onClick = viewModel::preview,
                            enabled = state.text.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Preview") }
                    }
                }
            }
        },
    ) { padding ->
        if (state.previewing) {
            PreviewStep(
                state = state,
                modifier = Modifier.padding(padding),
                onNameChange = viewModel::onNameChange,
                onUseExisting = viewModel::useExisting,
                onCreateNew = viewModel::createNew,
                onChooseExisting = viewModel::openPicker,
            )
        } else {
            EditStep(
                text = state.text,
                error = state.error,
                exerciseCount = allExercises.size,
                modifier = Modifier.padding(padding),
                onTextChange = viewModel::onTextChange,
                onCopyPrompt = {
                    scope.launch {
                        val prompt = viewModel.aiPrompt()
                        if (context.copyText("AI prompt", prompt)) snackbar.showSnackbar("Prompt copied")
                    }
                },
                onSharePrompt = { scope.launch { context.shareText("Workout template request", viewModel.aiPrompt()) } },
                onPaste = {
                    val pasted = context.clipboardText()
                    if (pasted == null) {
                        scope.launch { snackbar.showSnackbar("The clipboard is empty.") }
                    } else {
                        viewModel.onTextChange(pasted)
                    }
                },
                onOpenFile = { openFile.launch(arrayOf("text/*", "application/octet-stream")) },
            )
        }
    }

    state.pickingFor?.let { key ->
        val item = state.exercises.firstOrNull { it.key == key }
        if (item != null) {
            ExercisePickerDialog(
                importedName = item.importedName,
                exercises = allExercises,
                onDismiss = viewModel::closePicker,
                onPick = { viewModel.useExisting(key, it) },
            )
        }
    }
}

@Composable
private fun EditStep(
    text: String,
    error: String?,
    exerciseCount: Int,
    modifier: Modifier,
    onTextChange: (String) -> Unit,
    onCopyPrompt: () -> Unit,
    onSharePrompt: () -> Unit,
    onPaste: () -> Unit,
    onOpenFile: () -> Unit,
) {
    var showFormat by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text("Let an AI write it", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = "The prompt explains the format and lists your $exerciseCount exercises with their exact " +
                            "names, so the AI reuses them. Paste it into ChatGPT, Gemini or Claude, describe the plan " +
                            "you want at the end, then paste the answer here.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onCopyPrompt) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copy prompt")
                        }
                        TextButton(onClick = onSharePrompt) {
                            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Send to app")
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onPaste, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Paste")
                }
                OutlinedButton(onClick = onOpenFile, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open file")
                }
            }
        }
        item {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
                placeholder = {
                    Text(
                        text = "Paste or type a template. For example:\n\n$FORMAT_EXAMPLE",
                        color = MaterialTheme.colorScheme.outline,
                        fontFamily = FontFamily.Monospace,
                    )
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
            )
        }
        item {
            val rotation = if (showFormat) 180f else 0f
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showFormat = !showFormat }.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Format", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Icon(Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.rotate(rotation))
                }
                if (showFormat) FormatHelp()
            }
        }
    }
}

@Composable
private fun FormatHelp() {
    val rows = listOf(
        "Template: <name>" to "The template's name (optional).",
        "Day: <name>" to "Starts a day. “Day 2” on its own works too.",
        "Section: <name>" to "Optional group inside a day. Add “(skip)” to leave it out of logged workouts.",
        "Squat 4x6" to "One exercise per line, with an optional sets × reps target.",
        "Plank 3x45s" to "Seconds instead of reps, for timed exercises.",
        "A: Bench Press" to "Consecutive lines with the same letter form a superset.",
        "[legs]" to "Optional area for new exercises: legs, chest, back, shoulders, arms, core, other.",
    )
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { (syntax, meaning) ->
                Column {
                    Text(syntax, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                    Text(meaning, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(
                text = "Bullets, numbering and markdown from chat apps are ignored.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PreviewStep(
    state: ImportUiState,
    modifier: Modifier,
    onNameChange: (String) -> Unit,
    onUseExisting: (String, Exercise) -> Unit,
    onCreateNew: (String) -> Unit,
    onChooseExisting: (String) -> Unit,
) {
    val draft = state.draft ?: return
    val toCheck = state.exercises.filter { it.kind != MatchKind.EXACT || it.chosen?.id != it.suggestion?.id }
    val exact = state.exercises.filter { it !in toCheck }
    var showExact by remember { mutableStateOf(false) }
    val newKeys = state.exercises.filter { it.chosen == null }.map { it.key }.toSet()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Template name") },
                singleLine = true,
            )
        }
        item {
            val dayCount = draft.days.size
            val exerciseCount = draft.allExercises.size
            Text(
                text = "${if (dayCount == 1) "1 day" else "$dayCount days"} · $exerciseCount exercises · " +
                    "${exact.size} matched · ${state.newCount} new",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (state.warnings.isNotEmpty()) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Outlined.WarningAmber, contentDescription = null, modifier = Modifier.size(20.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            state.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }

        if (toCheck.isNotEmpty()) {
            item {
                SectionLabel("Check these exercises")
                Text(
                    text = "Close matches use your existing exercise. Anything else becomes a new exercise unless you pick one of yours.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            items(toCheck, key = { it.key }) { item ->
                MatchCard(
                    item = item,
                    onUseSuggestion = { item.suggestion?.let { onUseExisting(item.key, it) } },
                    onCreateNew = { onCreateNew(item.key) },
                    onChooseExisting = { onChooseExisting(item.key) },
                )
            }
        }
        if (exact.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showExact = !showExact }.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(
                        text = "${exact.size} matched your exercises exactly",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Icon(Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.rotate(if (showExact) 180f else 0f))
                }
                if (showExact) {
                    Text(
                        text = exact.joinToString(" · ") { it.importedName },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        item { SectionLabel("Days") }
        items(draft.days.size) { index ->
            val day = draft.days[index]
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Day ${index + 1}" + (day.name?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (day.sections.isEmpty()) {
                        Text("No exercises", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    day.sections.forEach { section ->
                        if (section.name != null) {
                            Text(
                                text = section.name + if (!section.includeInWorkout) "  (not in workout)" else "",
                                modifier = Modifier.padding(top = 6.dp),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        section.exercises.forEach { exercise ->
                            val chosenName = state.exercises
                                .firstOrNull { it.key == normalizeExerciseName(exercise.name) }?.chosen?.name
                            DraftExerciseLine(
                                text = TemplateText.formatExercise(exercise.copy(name = chosenName ?: exercise.name, targetArea = null)),
                                isNew = normalizeExerciseName(exercise.name) in newKeys,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DraftExerciseLine(text: String, isNew: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, modifier = Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyMedium)
        if (isNew) StatusBadge("new", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
    }
}

@Composable
private fun StatusBadge(text: String, container: Color, content: Color) {
    Surface(shape = MaterialTheme.shapes.extraSmall, color = container, contentColor = content) {
        Text(text, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall)
    }
}

/** One imported name that isn't an exact match (or was re-pointed): what it becomes, and how to change it. */
@Composable
private fun MatchCard(
    item: ImportExerciseUi,
    onUseSuggestion: () -> Unit,
    onCreateNew: () -> Unit,
    onChooseExisting: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val isNew = item.chosen == null
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLowest),
        border = if (isNew) BorderStroke(1.dp, colors.tertiary.copy(alpha = 0.5f)) else null,
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.importedName,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (isNew) {
                    item.targetArea?.let { TargetAreaPill(it) }
                    if (item.isTimed) StatusBadge("timed", colors.secondaryContainer, colors.onSecondaryContainer)
                }
            }
            Spacer(Modifier.size(4.dp))
            StatusLine(
                icon = if (isNew) Icons.Outlined.FiberNew else Icons.Outlined.CheckCircle,
                tint = if (isNew) colors.tertiary else colors.primary,
                text = if (isNew) "Will be created as a new exercise" else "Uses your “${item.chosen?.name}”",
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val suggestion = item.suggestion
                if (suggestion != null && item.chosen?.id != suggestion.id) {
                    TextButton(onClick = onUseSuggestion) { Text("Use “${suggestion.name}”") }
                }
                if (!isNew) TextButton(onClick = onCreateNew) { Text("Create new instead") }
                TextButton(onClick = onChooseExisting) { Text(if (isNew) "Use one of mine…" else "Pick another…") }
            }
        }
    }
}

@Composable
private fun StatusLine(icon: ImageVector, tint: Color, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Searchable list of the user's exercises; picks one (never creates). */
@Composable
private fun ExercisePickerDialog(
    importedName: String,
    exercises: List<Exercise>,
    onDismiss: () -> Unit,
    onPick: (Exercise) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, exercises) {
        val q = normalizeExerciseName(query)
        exercises.filter { q.isEmpty() || it.normalizedName.contains(q) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Use one of your exercises") },
        text = {
            Column {
                Text(
                    text = "For “$importedName”",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search") },
                    singleLine = true,
                )
                Spacer(Modifier.size(4.dp))
                PickerList(filtered, onPick)
            }
        },
    )
}

@Composable
private fun ColumnScope.PickerList(exercises: List<Exercise>, onPick: (Exercise) -> Unit) {
    if (exercises.isEmpty()) {
        Text("No matches", modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    LazyColumn(Modifier.heightIn(max = 300.dp)) {
        items(exercises, key = { it.id }) { exercise ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onPick(exercise) }.padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(exercise.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                exercise.targetArea?.let { TargetAreaPill(it) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
