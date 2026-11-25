package com.mavros.gymtracker.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ListAlt
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mavros.gymtracker.ui.components.ConfirmDialog
import com.mavros.gymtracker.ui.components.EmptyState
import com.mavros.gymtracker.ui.components.LocalWeightUnit
import com.mavros.gymtracker.ui.components.SectionLabel
import com.mavros.gymtracker.ui.components.formatVolume
import com.mavros.gymtracker.ui.components.toSessionLabel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val todayFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM")
private val monthFormatter = DateTimeFormatter.ofPattern("MMM")
private val weekdayFormatter = DateTimeFormatter.ofPattern("EEEE")

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onStartWorkout: () -> Unit,
    onStartFromTemplate: () -> Unit,
    onOpenSession: (Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var sessionPendingDelete by remember { mutableStateOf<HomeSessionUi?>(null) }

    Scaffold(topBar = { HomeHeader() }) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { WeekCard(state.week) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onStartWorkout,
                        modifier = Modifier.weight(1f).height(52.dp),
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Start workout")
                    }
                    FilledTonalButton(
                        onClick = onStartFromTemplate,
                        modifier = Modifier.weight(1f).height(52.dp),
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    ) {
                        Icon(Icons.Rounded.ListAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("From template")
                    }
                }
            }
            if (state.loaded && state.sessions.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.FitnessCenter,
                        title = "No workouts yet",
                        message = "Start an empty workout or pick a template to log your first session.",
                    )
                }
            } else if (state.sessions.isNotEmpty()) {
                item { SectionLabel("Recent workouts") }
                items(state.sessions, key = { it.id }) { session ->
                    SessionCard(
                        session = session,
                        onClick = { onOpenSession(session.id) },
                        onDelete = { sessionPendingDelete = session },
                    )
                }
            }
        }
    }

    sessionPendingDelete?.let { session ->
        ConfirmDialog(
            title = "Delete workout?",
            message = "This permanently deletes the ${session.date.toSessionLabel()} workout and its sets.",
            onConfirm = {
                viewModel.deleteSession(session.id)
                sessionPendingDelete = null
            },
            onDismiss = { sessionPendingDelete = null },
        )
    }
}

@Composable
private fun HomeHeader() {
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
    ) {
        Text(
            text = LocalDate.now().format(todayFormatter),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
        Text("My Workouts", style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun WeekCard(week: WeekStats) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("This week", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat(
                    value = week.workouts.toString(),
                    label = if (week.workouts == 1) "workout" else "workouts",
                    modifier = Modifier.weight(1f),
                )
                Stat(
                    value = week.volume.formatVolume(),
                    label = "${LocalWeightUnit.current.label} lifted",
                    modifier = Modifier.weight(1.3f),
                )
                Stat(
                    value = when (val days = week.daysSinceLast) {
                        null -> "—"
                        0L -> "Today"
                        1L -> "1 day"
                        else -> "$days days"
                    },
                    label = if (week.daysSinceLast == null || week.daysSinceLast == 0L) "last workout" else "since last",
                    modifier = Modifier.weight(1.2f),
                )
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, maxLines = 1, style = MaterialTheme.typography.headlineSmall)
        Text(label, maxLines = 1, style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionCard(session: HomeSessionUi, onClick: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onDelete),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DateBadge(session.date)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = session.dayName ?: "Workout",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = session.summary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodyMedium,
                )
                val details = buildList {
                    add(session.date.format(weekdayFormatter))
                    if (session.exerciseCount > 0) {
                        add(if (session.exerciseCount == 1) "1 exercise" else "${session.exerciseCount} exercises")
                    }
                    if (session.volume > 0.0) add("${session.volume.formatVolume()} ${LocalWeightUnit.current.label}")
                }
                if (details.isNotEmpty()) {
                    Text(
                        text = details.joinToString("  ·  "),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = "More",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Delete workout") },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

/** Calendar-style square: day of month over the abbreviated month. */
@Composable
private fun DateBadge(date: LocalDate) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.size(52.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleLarge)
            Text(date.format(monthFormatter).uppercase(), style = MaterialTheme.typography.labelSmall)
        }
    }
}
