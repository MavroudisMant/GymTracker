package com.mavros.gymtracker.ui.screens.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mavros.gymtracker.data.BackupRepository
import com.mavros.gymtracker.data.RestoreOutcome
import com.mavros.gymtracker.data.SettingsRepository
import com.mavros.gymtracker.data.WeightUnit
import com.mavros.gymtracker.data.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface RestoreStep {
    data object Idle : RestoreStep
    data class Confirming(val backup: Uri) : RestoreStep
    data object Working : RestoreStep
    data object NeedsRestart : RestoreStep
    data class Failed(val reason: String) : RestoreStep
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val workoutRepository: WorkoutRepository,
    private val backupRepository: BackupRepository,
) : ViewModel() {
    val unit: StateFlow<WeightUnit> = settingsRepository.unit

    /** CSV waiting to be shared; the screen clears it with [onExportHandled]. */
    private val _exportContent = MutableStateFlow<String?>(null)
    val exportContent: StateFlow<String?> = _exportContent.asStateFlow()

    /** Backup file waiting to be shared; the screen clears it with [onBackupHandled]. */
    private val _backupToSend = MutableStateFlow<File?>(null)
    val backupToSend: StateFlow<File?> = _backupToSend.asStateFlow()

    private val _restoreStep = MutableStateFlow<RestoreStep>(RestoreStep.Idle)
    val restoreStep: StateFlow<RestoreStep> = _restoreStep.asStateFlow()

    fun setUnit(unit: WeightUnit) = settingsRepository.setUnit(unit)

    fun requestExport() {
        viewModelScope.launch { _exportContent.value = workoutRepository.exportCsv() }
    }

    fun onExportHandled() {
        _exportContent.value = null
    }

    fun requestBackup() {
        viewModelScope.launch { _backupToSend.value = backupRepository.createBackup() }
    }

    fun onBackupHandled() {
        _backupToSend.value = null
    }

    fun onBackupPicked(backup: Uri) {
        _restoreStep.value = RestoreStep.Confirming(backup)
    }

    fun confirmRestore() {
        val step = _restoreStep.value as? RestoreStep.Confirming ?: return
        _restoreStep.value = RestoreStep.Working
        viewModelScope.launch {
            _restoreStep.value = when (val outcome = backupRepository.restoreFrom(step.backup)) {
                RestoreOutcome.Restored -> RestoreStep.NeedsRestart
                is RestoreOutcome.Rejected -> RestoreStep.Failed(outcome.reason)
            }
        }
    }

    fun dismissRestore() {
        _restoreStep.value = RestoreStep.Idle
    }

    companion object {
        fun factory(
            settingsRepository: SettingsRepository,
            workoutRepository: WorkoutRepository,
            backupRepository: BackupRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(settingsRepository, workoutRepository, backupRepository) }
        }
    }
}
