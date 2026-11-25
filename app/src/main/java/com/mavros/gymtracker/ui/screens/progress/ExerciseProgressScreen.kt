package com.mavros.gymtracker.ui.screens.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mavros.gymtracker.ui.components.LocalWeightUnit
import com.mavros.gymtracker.ui.components.GymTopBar
import com.mavros.gymtracker.ui.components.SectionLabel
import com.mavros.gymtracker.ui.components.formatWeight

@Composable
fun ExerciseProgressScreen(
    viewModel: ExerciseProgressViewModel,
    onEdit: (Long) -> Unit,
    onBack: () -> Unit,
    exerciseId: Long,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            GymTopBar(
                title = state?.name ?: "",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { onEdit(exerciseId) }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit exercise")
                    }
                },
            )
        },
    ) { padding ->
        val ui = state
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (ui == null) return@LazyColumn
            item {
                val unit = LocalWeightUnit.current.label
                val repsSuffix = if (ui.isTimed) "s" else ""
                val repsWord = if (ui.isTimed) "sec" else "reps"
                val pr = ui.pr
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.EmojiEvents, contentDescription = null, modifier = Modifier.size(32.dp))
                        Column {
                            Text("Personal record", style = MaterialTheme.typography.labelLarge)
                            Text(
                                text = when {
                                    pr == null -> "None yet"
                                    pr.weight != null -> "${pr.weight.formatWeight()} $unit × ${pr.reps}$repsSuffix"
                                    else -> "${pr.reps} $repsWord"
                                },
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            if (pr != null) Text(pr.dateLabel, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Top weight per session", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(12.dp))
                        if (ui.chartWeights.size >= 2) {
                            WeightChart(ui.chartWeights, Modifier.fillMaxWidth().height(160.dp))
                        } else {
                            Text(
                                text = "Log this exercise in two or more workouts to see a chart.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
            item { SectionLabel("History") }
            if (ui.sessions.isEmpty()) {
                item {
                    Text(
                        text = "No sessions logged for this exercise yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                items(ui.sessions) { session ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(session.dateLabel, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = session.setsSummary,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Line chart of [weights] in order, with a left and bottom axis. */
@Composable
private fun WeightChart(weights: List<Float>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            if (weights.isEmpty()) return@Canvas
            val min = weights.min()
            val range = (weights.max() - min).takeIf { it > 0f } ?: 1f
            val pad = 8.dp.toPx()
            val left = pad
            val top = pad
            val right = size.width - pad
            val bottom = size.height - pad
            val count = weights.size

            fun px(i: Int) = if (count == 1) (left + right) / 2f else left + (right - left) * i / (count - 1)
            fun py(w: Float) = bottom - (bottom - top) * ((w - min) / range)

            drawLine(axisColor, Offset(left, top), Offset(left, bottom), strokeWidth = 2f)
            drawLine(axisColor, Offset(left, bottom), Offset(right, bottom), strokeWidth = 2f)
            for (i in 0 until count - 1) {
                drawLine(
                    color = lineColor,
                    start = Offset(px(i), py(weights[i])),
                    end = Offset(px(i + 1), py(weights[i + 1])),
                    strokeWidth = 4f,
                )
            }
            weights.forEachIndexed { i, w -> drawCircle(lineColor, radius = 5f, center = Offset(px(i), py(w))) }
        }
    }
}
