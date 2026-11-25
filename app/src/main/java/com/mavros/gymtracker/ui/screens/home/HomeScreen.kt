package com.mavros.gymtracker.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mavros.gymtracker.ui.components.ConfirmDialog
import com.mavros.gymtracker.ui.components.Pill

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onStartWorkout: () -> Unit,
    onStartFromTemplate: () -> Unit,
    onOpenProgress: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSession: (Long) -> Unit,
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    var sessionPendingDelete by remember { mutableStateOf<HomeSessionUi?>(null) }

    Scaffold(topBar = { HomeHeader(onOpenSettings) }) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Button(onClick = onStartWorkout, modifier = Modifier.fillMaxWidth()) { Text("+ Start workout") }
            }
            item {
                OutlinedButton(onClick = onStartFromTemplate, modifier = Modifier.fillMaxWidth()) {
                    Text("Start from template")
                }
            }
            item {
                Text(
                    text = "Recent",
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            if (sessions.isEmpty()) {
                item { EmptyRecent() }
            } else {
                items(sessions, key = { it.id }) { session ->
                    SessionCard(
                        session = session,
                        onClick = { onOpenSession(session.id) },
                        onLongPress = { sessionPendingDelete = session },
                    )
                }
            }
            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    text = "📈 View exercise progress",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenProgress)
                        .padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    sessionPendingDelete?.let { session ->
        ConfirmDialog(
            title = "Delete workout?",
            message = "This permanently deletes the ${session.dateLabel} session and its sets.",
            onConfirm = {
                viewModel.deleteSession(session.id)
                sessionPendingDelete = null
            },
            onDismiss = { sessionPendingDelete = null },
        )
    }
}

@Composable
private fun HomeHeader(onOpenSettings: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("My Workouts", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "⚙",
                modifier = Modifier.clickable(onClick = onOpenSettings).padding(8.dp),
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionCard(session: HomeSessionUi, onClick: () -> Unit, onLongPress: () -> Unit) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(session.dateLabel, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                session.dayName?.let { Pill(it) }
            }
            Text(
                text = session.summary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun EmptyRecent() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No workouts yet", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Tap “Start workout” to log your first one.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
