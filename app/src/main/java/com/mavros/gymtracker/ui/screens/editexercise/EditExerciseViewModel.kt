package com.mavros.gymtracker.ui.screens.editexercise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mavros.gymtracker.data.TargetArea
import com.mavros.gymtracker.data.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditExerciseUiState(
    val loaded: Boolean = false,
    val name: String = "",
    val targetArea: TargetArea? = null,
    val isTimed: Boolean = false,
    /** Another exercise already has this name. */
    val hasCollision: Boolean = false,
    /** Sessions + templates that reference this exercise. */
    val usageCount: Int = 0,
    val saved: Boolean = false,
    val deleted: Boolean = false,
) {
    val canSave: Boolean get() = loaded && name.isNotBlank() && !hasCollision

    /** Only unused exercises can be deleted, so history stays consistent. */
    val canDelete: Boolean get() = loaded && usageCount == 0
}

class EditExerciseViewModel(
    private val repository: WorkoutRepository,
    private val exerciseId: Long,
) : ViewModel() {
    private val _state = MutableStateFlow(EditExerciseUiState())
    val state: StateFlow<EditExerciseUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val exercise = repository.getExercise(exerciseId) ?: return@launch
            _state.value = EditExerciseUiState(
                loaded = true,
                name = exercise.name,
                targetArea = exercise.targetArea,
                isTimed = exercise.isTimed,
                hasCollision = false,
                usageCount = repository.exerciseUsageCount(exerciseId),
            )
        }
    }

    fun onNameChange(name: String) {
        _state.update { it.copy(name = name) }
        viewModelScope.launch {
            val taken = if (name.isBlank()) false else repository.isNameTaken(name, exerciseId)
            _state.update { it.copy(hasCollision = taken) }
        }
    }

    fun onAreaSelect(area: TargetArea?) {
        _state.update { it.copy(targetArea = area) }
    }

    fun onTimedChange(isTimed: Boolean) {
        _state.update { it.copy(isTimed = isTimed) }
    }

    fun save() {
        val current = _state.value
        if (!current.canSave) return
        viewModelScope.launch {
            repository.updateExercise(exerciseId, current.name, current.targetArea, current.isTimed)
            _state.update { it.copy(saved = true) }
        }
    }

    fun onSavedHandled() {
        _state.update { it.copy(saved = false) }
    }

    fun deleteExercise() {
        if (!_state.value.canDelete) return
        viewModelScope.launch {
            repository.deleteExercise(exerciseId)
            _state.update { it.copy(deleted = true) }
        }
    }

    fun onDeletedHandled() {
        _state.update { it.copy(deleted = false) }
    }

    companion object {
        fun factory(repository: WorkoutRepository, exerciseId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { EditExerciseViewModel(repository, exerciseId) }
        }
    }
}
