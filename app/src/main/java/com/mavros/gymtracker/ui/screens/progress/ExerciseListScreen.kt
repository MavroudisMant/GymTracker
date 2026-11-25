package com.mavros.gymtracker.ui.screens.progress

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mavros.gymtracker.data.TargetArea
import com.mavros.gymtracker.ui.components.TargetAreaPill
import com.mavros.gymtracker.ui.components.label
import com.mavros.gymtracker.ui.screens.templates.TitleBar

@Composable
fun ExerciseListScreen(
    viewModel: ExerciseListViewModel,
    onOpenExercise: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()

    Scaffold(topBar = { TitleBar("Exercise progress", onBack) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            FilterChipsRow(selected = filter, onSelect = viewModel::setFilter)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (exercises.isEmpty()) {
                    item {
                        Text(
                            text = "No exercises yet. Log a workout first.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                items(exercises, key = { it.id }) { exercise ->
                    OutlinedCard(onClick = { onOpenExercise(exercise.id) }, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                            exercise.targetArea?.let { TargetAreaPill(it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChipsRow(selected: TargetArea?, onSelect: (TargetArea?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip("All", selected = selected == null, onClick = { onSelect(null) })
        TargetArea.entries.forEach { area ->
            FilterChip(area.label(), selected = selected == area, onClick = { onSelect(area) })
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (selected) colors.primaryContainer else colors.surface,
        contentColor = if (selected) colors.onPrimaryFixed else colors.onSurface,
        border = if (selected) null else BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
