package com.mavros.gymtracker.ui.screens.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mavros.gymtracker.data.TemplateRepository
import com.mavros.gymtracker.data.TemplateWithDays
import com.mavros.gymtracker.data.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TemplateListItemUi(
    val id: Long,
    val name: String,
    val days: List<TemplateDayItemUi>,
)

data class TemplateDayItemUi(val id: Long, val title: String)

class TemplatesListViewModel(
    private val repository: TemplateRepository,
    private val workoutRepository: WorkoutRepository,
) : ViewModel() {
    val templates: StateFlow<List<TemplateListItemUi>> = repository.observeTemplates()
        .map { templates -> templates.map { it.toUi() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Set after creating a template so the screen can open it; cleared by [onCreatedTemplateHandled]. */
    private val _createdTemplateId = MutableStateFlow<Long?>(null)
    val createdTemplateId: StateFlow<Long?> = _createdTemplateId.asStateFlow()

    fun createTemplate(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { _createdTemplateId.value = repository.createTemplate(name) }
    }

    fun onCreatedTemplateHandled() {
        _createdTemplateId.value = null
    }

    /** Set after starting a workout from a day so the screen can open it. */
    private val _startedSessionId = MutableStateFlow<Long?>(null)
    val startedSessionId: StateFlow<Long?> = _startedSessionId.asStateFlow()

    fun startWorkout(dayId: Long) {
        viewModelScope.launch {
            _startedSessionId.value = workoutRepository.startSessionFromTemplateDay(dayId, LocalDate.now())
        }
    }

    fun onStartedWorkoutHandled() {
        _startedSessionId.value = null
    }

    /** The template as importable text, for sharing or saving. */
    suspend fun exportText(id: Long): String? = repository.exportText(id)

    fun duplicate(id: Long) {
        viewModelScope.launch { repository.duplicate(id) }
    }

    fun deleteTemplate(id: Long) {
        viewModelScope.launch { repository.deleteTemplate(id) }
    }

    private fun TemplateWithDays.toUi(): TemplateListItemUi {
        return TemplateListItemUi(
            id = template.id,
            name = template.name,
            days = days.sortedBy { it.dayNumber }.map { TemplateDayItemUi(it.id, it.name ?: "Day ${it.dayNumber}") },
        )
    }

    companion object {
        fun factory(repository: TemplateRepository, workoutRepository: WorkoutRepository): ViewModelProvider.Factory =
            viewModelFactory {
            initializer { TemplatesListViewModel(repository, workoutRepository) }
        }
    }
}
