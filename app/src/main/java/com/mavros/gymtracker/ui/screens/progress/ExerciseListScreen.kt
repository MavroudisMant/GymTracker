package com.mavros.gymtracker.ui.screens.progress

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
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mavros.gymtracker.data.TargetArea
import com.mavros.gymtracker.ui.components.TargetAreaPill
import com.mavros.gymtracker.ui.components.label
import com.mavros.gymtracker.ui.components.EmptyState
import com.mavros.gymtracker.ui.components.GymTopBar

@Composable
fun ExerciseListScreen(
    viewModel: ExerciseListViewModel,
    onOpenExercise: (Long) -> Unit,
) {
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()

    Scaffold(topBar = { GymTopBar("Progress") }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            FilterChipsRow(selected = filter, onSelect = viewModel::setFilter)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (exercises.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.Insights,
                            title = if (filter == null) "No exercises yet" else "Nothing in ${filter?.label()}",
                            message = "Exercises appear here once you add them to a workout or template.",
                        )
                    }
                }
                items(exercises, key = { it.id }) { exercise ->
                    Card(
                        onClick = { onOpenExercise(exercise.id) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(exercise.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            exercise.targetArea?.let { TargetAreaPill(it) }
                            Icon(
                                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
            .padding(horizontal = 16.dp),
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
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else {
            null
        },
    )
}
