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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mavros.gymtracker.ui.components.LocalWeightUnit
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
        topBar = { ProgressHeader(title = state?.name ?: "", onEdit = { onEdit(exerciseId) }, onBack = onBack) },
    ) { padding ->
        val ui = state
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (ui == null) return@LazyColumn
            item {
                val unit = LocalWeightUnit.current.label
                val repsSuffix = if (ui.isTimed) "s" else ""
                val repsWord = if (ui.isTimed) "sec" else "reps"
                val pr = ui.pr
                val text = when {
                    pr == null -> "No personal record yet"
                    pr.weight != null ->
                        "🏆 PR: ${pr.weight.formatWeight()} $unit × ${pr.reps}$repsSuffix · ${pr.dateLabel}"
                    else -> "🏆 PR: ${pr.reps} $repsWord · ${pr.dateLabel}"
                }
                Text(text, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
            }
            item {
                if (ui.chartWeights.size >= 2) {
                    WeightChart(ui.chartWeights, Modifier.fillMaxWidth().height(160.dp))
                } else {
                    Text(
                        text = "Not enough data to chart yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item { HorizontalDivider() }
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
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(session.dateLabel, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = session.setsSummary,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressHeader(title: String, onEdit: () -> Unit, onBack: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 4.dp, end = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("←") }
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleLarge,
            )
            TextButton(onClick = onEdit) { Text("✎ Edit") }
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
