package com.mavros.gymtracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mavros.gymtracker.data.TargetArea
import java.util.Locale

/** "LEGS" -> "Legs". */
fun TargetArea.label(): String =
    name.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }

@Composable
fun Pill(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
fun TargetAreaPill(area: TargetArea, modifier: Modifier = Modifier) {
    Pill(area.label().lowercase(Locale.ROOT), modifier)
}

/** Two-column grid of target areas. Tapping the selected area clears the selection. */
@Composable
fun TargetAreaSelector(
    selected: TargetArea?,
    onSelect: (TargetArea?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TargetArea.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { area ->
                    AreaOption(
                        label = area.label(),
                        selected = selected == area,
                        onClick = { onSelect(if (selected == area) null else area) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AreaOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = if (selected) colors.primaryContainer else colors.surface,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface,
        border = if (selected) null else BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
