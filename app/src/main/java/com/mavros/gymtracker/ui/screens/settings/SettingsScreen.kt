package com.mavros.gymtracker.ui.screens.settings

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mavros.gymtracker.BuildConfig
import com.mavros.gymtracker.MainActivity
import com.mavros.gymtracker.data.WeightUnit
import com.mavros.gymtracker.ui.components.ConfirmDialog
import com.mavros.gymtracker.ui.screens.templates.TitleBar
import java.io.File
import kotlin.system.exitProcess

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onManageExercises: () -> Unit,
    onBack: () -> Unit,
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

    Scaffold(topBar = { TitleBar("Settings", onBack) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Units", style = MaterialTheme.typography.bodyLarge)
                UnitToggle(selected = unit, onSelect = viewModel::setUnit)
            }
            HorizontalDivider()
            SettingsRow("Export data", "⤓", onClick = viewModel::requestExport)
            HorizontalDivider()
            SettingsRow("Back up database", "⤓", onClick = viewModel::requestBackup)
            HorizontalDivider()
            SettingsRow("Restore from backup", "⤒", onClick = { pickBackup.launch(arrayOf("*/*")) })
            HorizontalDivider()
            SettingsRow("Manage exercises", "›", onClick = onManageExercises)
            HorizontalDivider()
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
private fun SettingsRow(label: String, trailing: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(trailing, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun UnitToggle(selected: WeightUnit, onSelect: (WeightUnit) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WeightUnit.entries.forEach { option ->
            val isSelected = option == selected
            val colors = MaterialTheme.colorScheme
            Surface(
                onClick = { onSelect(option) },
                shape = MaterialTheme.shapes.small,
                color = if (isSelected) colors.primaryContainer else colors.surface,
                contentColor = if (isSelected) colors.onPrimaryFixed else colors.onSurface,
                border = if (isSelected) null else BorderStroke(1.dp, colors.outlineVariant),
            ) {
                Text(
                    text = option.label,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
