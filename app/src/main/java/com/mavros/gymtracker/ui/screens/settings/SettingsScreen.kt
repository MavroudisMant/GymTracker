package com.mavros.gymtracker.ui.screens.settings

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mavros.gymtracker.BuildConfig
import com.mavros.gymtracker.MainActivity
import com.mavros.gymtracker.data.WeightUnit
import com.mavros.gymtracker.ui.components.ConfirmDialog
import com.mavros.gymtracker.ui.components.GymTopBar
import com.mavros.gymtracker.ui.components.SectionLabel
import java.io.File
import kotlin.system.exitProcess

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onManageExercises: () -> Unit,
) {
    val unit by viewModel.unit.collectAsStateWithLifecycle()
    val exportContent by viewModel.exportContent.collectAsStateWithLifecycle()
    val backupToSend by viewModel.backupToSend.collectAsStateWithLifecycle()
    val restoreStep by viewModel.restoreStep.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
        if (picked != null) viewModel.onBackupPicked(picked)
    }

    exportContent?.let { csv ->
        LaunchedEffect(csv) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "Gym Tracker export")
                putExtra(Intent.EXTRA_TEXT, csv)
            }
            context.startActivity(Intent.createChooser(intent, "Export data"))
            viewModel.onExportHandled()
        }
    }

    backupToSend?.let { backup ->
        LaunchedEffect(backup) {
            context.startActivity(Intent.createChooser(sendBackupIntent(context, backup), "Send backup"))
            viewModel.onBackupHandled()
        }
    }

    when (val step = restoreStep) {
        RestoreStep.Idle -> Unit
        is RestoreStep.Confirming -> ConfirmDialog(
            title = "Restore this backup?",
            message = "Every workout, exercise and template currently on this phone will be replaced by the ones " +
                "in the backup. This can't be undone — back up first if you want to keep what's here.",
            confirmLabel = "Replace",
            onConfirm = viewModel::confirmRestore,
            onDismiss = viewModel::dismissRestore,
        )
        RestoreStep.Working -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Restoring") },
            text = { Text("Replacing the database…") },
        )
        RestoreStep.NeedsRestart -> AlertDialog(
            onDismissRequest = {},
            confirmButton = { TextButton(onClick = { restart(context) }) { Text("Restart") } },
            title = { Text("Backup restored") },
            text = { Text("Gym Tracker needs to restart to load it.") },
        )
        is RestoreStep.Failed -> AlertDialog(
            onDismissRequest = viewModel::dismissRestore,
            confirmButton = { TextButton(onClick = viewModel::dismissRestore) { Text("OK") } },
            title = { Text("Couldn't restore") },
            text = { Text(step.reason) },
        )
    }

    Scaffold(topBar = { GymTopBar("Settings") }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionLabel("Preferences")
            SettingsGroup {
                ListItem(
                    headlineContent = { Text("Weight unit") },
                    supportingContent = { Text("Changing it doesn't convert logged weights") },
                    leadingContent = { Icon(Icons.Outlined.Scale, contentDescription = null) },
                    trailingContent = { UnitToggle(selected = unit, onSelect = viewModel::setUnit) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
            SectionLabel("Exercises")
            SettingsGroup {
                SettingsRow(
                    label = "Manage exercises",
                    description = "Rename, re-categorise or delete",
                    icon = Icons.Outlined.FitnessCenter,
                    onClick = onManageExercises,
                )
            }
            SectionLabel("Data")
            SettingsGroup {
                SettingsRow(
                    label = "Export as CSV",
                    description = "Every logged set, for spreadsheets",
                    icon = Icons.Outlined.TableChart,
                    onClick = viewModel::requestExport,
                )
                HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingsRow(
                    label = "Back up database",
                    description = "Save or send a full copy",
                    icon = Icons.Outlined.CloudUpload,
                    onClick = viewModel::requestBackup,
                )
                HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingsRow(
                    label = "Restore from backup",
                    description = "Replaces everything on this phone",
                    icon = Icons.Outlined.Restore,
                    onClick = { pickBackup.launch(arrayOf("*/*")) },
                )
            }
            Text(
                text = "Version ${BuildConfig.VERSION_NAME} · offline",
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun sendBackupIntent(context: Context, backup: File): Intent {
    val shareable = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", backup)
    return Intent(Intent.ACTION_SEND).apply {
        type = "application/octet-stream"
        putExtra(Intent.EXTRA_STREAM, shareable)
        putExtra(Intent.EXTRA_SUBJECT, backup.name)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

/** Relaunches the app in a fresh process so Room reopens the restored database. */
private fun restart(context: Context) {
    context.startActivity(
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
    )
    exitProcess(0)
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(content = content)
    }
}

@Composable
private fun SettingsRow(label: String, description: String, icon: ImageVector, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(description) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitToggle(selected: WeightUnit, onSelect: (WeightUnit) -> Unit) {
    SingleChoiceSegmentedButtonRow {
        WeightUnit.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = WeightUnit.entries.size),
                icon = {},
            ) { Text(option.label) }
        }
    }
}
