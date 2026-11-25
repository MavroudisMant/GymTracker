package com.mavros.gymtracker.ui.screens.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun TemplatesListScreen(
    viewModel: TemplatesListViewModel,
    onOpenTemplate: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val createdId by viewModel.createdTemplateId.collectAsStateWithLifecycle()
    var showNewDialog by remember { mutableStateOf(false) }

    // A freshly created template opens straight into the builder.
    LaunchedEffect(createdId) {
        createdId?.let { id ->
            viewModel.onCreatedTemplateHandled()
            onOpenTemplate(id)
        }
    }

    Scaffold(topBar = { TitleBar("Templates", onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (templates.isEmpty()) {
                item {
                    Text(
                        text = "No templates yet. Create one to pre-fill your workouts.",
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            items(templates, key = { it.id }) { template ->
                OutlinedCard(onClick = { onOpenTemplate(template.id) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(template.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = if (template.dayCount == 1) "1 day" else "${template.dayCount} days",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text(
                            text = template.daySummary,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            item {
                OutlinedButton(onClick = { showNewDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("+ New template")
                }
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

/** Top bar with a "←" back button and a title, shared by most screens. */
@Composable
fun TitleBar(title: String, onBack: () -> Unit) {
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
            Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleLarge)
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
