package com.mavros.gymtracker.ui.screens.templates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mavros.gymtracker.ui.components.EmptyState
import com.mavros.gymtracker.ui.components.GymTopBar

@Composable
fun TemplatesListScreen(
    viewModel: TemplatesListViewModel,
    onOpenTemplate: (Long) -> Unit,
    onStartWorkout: (sessionId: Long) -> Unit,
) {
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val createdId by viewModel.createdTemplateId.collectAsStateWithLifecycle()
    val startedSessionId by viewModel.startedSessionId.collectAsStateWithLifecycle()
    var showNewDialog by remember { mutableStateOf(false) }

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
        topBar = { GymTopBar("Templates") },
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
                        message = "Templates hold your training days, so a workout starts pre-filled with the right exercises.",
                        actionLabel = "Create a template",
                        onAction = { showNewDialog = true },
                    )
                }
            }
            items(templates, key = { it.id }) { template ->
                TemplateCard(
                    template = template,
                    onEdit = { onOpenTemplate(template.id) },
                    onStartDay = viewModel::startWorkout,
                )
            }
        }
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
private fun TemplateCard(template: TemplateListItemUi, onEdit: () -> Unit, onStartDay: (Long) -> Unit) {
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
