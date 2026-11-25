package com.mavros.gymtracker.ui.screens.templates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mavros.gymtracker.ui.components.ConfirmDialog
import com.mavros.gymtracker.ui.components.EmptyState
import com.mavros.gymtracker.ui.components.GymTopBar
import com.mavros.gymtracker.ui.components.rememberTextFileSaver
import com.mavros.gymtracker.ui.components.shareText
import com.mavros.gymtracker.ui.components.templateFileName
import kotlinx.coroutines.launch

@Composable
fun TemplatesListScreen(
    viewModel: TemplatesListViewModel,
    onOpenTemplate: (Long) -> Unit,
    onStartWorkout: (sessionId: Long) -> Unit,
    onImport: () -> Unit,
) {
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val createdId by viewModel.createdTemplateId.collectAsStateWithLifecycle()
    val startedSessionId by viewModel.startedSessionId.collectAsStateWithLifecycle()
    var showNewDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<TemplateListItemUi?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val saveFile = rememberTextFileSaver { saved ->
        scope.launch { snackbar.showSnackbar(if (saved) "Template saved" else "Couldn't save the file") }
    }

    // A freshly created template opens straight into the builder.
    LaunchedEffect(createdId) {
        createdId?.let { id ->
            viewModel.onCreatedTemplateHandled()
            onOpenTemplate(id)
        }
    }
    LaunchedEffect(startedSessionId) {
        startedSessionId?.let { id ->
            viewModel.onStartedWorkoutHandled()
            onStartWorkout(id)
        }
    }

    Scaffold(
        topBar = {
            GymTopBar(
                title = "Templates",
                actions = {
                    TextButton(onClick = onImport) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Import")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNewDialog = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("New template") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (templates.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.ListAlt,
                        title = "No templates yet",
                        message = "Templates hold your training days, so a workout starts pre-filled with the right " +
                            "exercises. Create one, or import one written by you or an AI.",
                        actionLabel = "Import a template",
                        onAction = onImport,
                    )
                }
            }
            items(templates, key = { it.id }) { template ->
                TemplateCard(
                    template = template,
                    onEdit = { onOpenTemplate(template.id) },
                    onStartDay = viewModel::startWorkout,
                    onShare = {
                        scope.launch {
                            viewModel.exportText(template.id)?.let { context.shareText(template.name, it) }
                        }
                    },
                    onSaveFile = {
                        scope.launch {
                            viewModel.exportText(template.id)?.let { saveFile(templateFileName(template.name), it) }
                        }
                    },
                    onDuplicate = {
                        viewModel.duplicate(template.id)
                        scope.launch { snackbar.showSnackbar("Duplicated “${template.name}”") }
                    },
                    onDelete = { pendingDelete = template },
                )
            }
        }
    }

    pendingDelete?.let { template ->
        ConfirmDialog(
            title = "Delete “${template.name}”?",
            message = "This permanently deletes the template and all its days. Logged workouts are kept.",
            onConfirm = {
                viewModel.deleteTemplate(template.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }

    if (showNewDialog) {
        NameDialog(
            title = "New template",
            label = "Template name",
            onDismiss = { showNewDialog = false },
            onConfirm = { name ->
                viewModel.createTemplate(name)
                showNewDialog = false
            },
        )
    }
}

/** A template with one row per day, each with its own Start button. */
@Composable
private fun TemplateCard(
    template: TemplateListItemUi,
    onEdit: () -> Unit,
    onStartDay: (Long) -> Unit,
    onShare: () -> Unit,
    onSaveFile: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(bottom = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(template.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = when (template.days.size) {
                            0 -> "No days yet"
                            1 -> "1 day"
                            else -> "${template.days.size} days"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Edit")
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Template options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        TemplateExportMenuItems(
                            onShare = onShare,
                            onSaveFile = onSaveFile,
                            onDuplicate = onDuplicate,
                            close = { menuOpen = false },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
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
            }
            template.days.forEach { day ->
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = day.title,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    FilledTonalButton(
                        onClick = { onStartDay(day.id) },
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Start")
                    }
                }
            }
        }
    }
}

/** Share / Save as file / Duplicate, shared by the templates list and the template editor menus. */
@Composable
fun TemplateExportMenuItems(onShare: () -> Unit, onSaveFile: () -> Unit, onDuplicate: () -> Unit, close: () -> Unit) {
    DropdownMenuItem(
        text = { Text("Share as text") },
        leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
        onClick = { close(); onShare() },
    )
    DropdownMenuItem(
        text = { Text("Save as file") },
        leadingIcon = { Icon(Icons.Outlined.SaveAlt, contentDescription = null) },
        onClick = { close(); onSaveFile() },
    )
    DropdownMenuItem(
        text = { Text("Duplicate") },
        leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
        onClick = { close(); onDuplicate() },
    )
}

/** Single text-field dialog used for naming templates, days and sections. */
@Composable
fun NameDialog(
    title: String,
    label: String,
    initialValue: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(label) },
                singleLine = true,
            )
        },
    )
}
