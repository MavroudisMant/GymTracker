package com.mavros.gymtracker.ui.screens.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mavros.gymtracker.data.TemplateRepository
import com.mavros.gymtracker.data.TemplateWithDays
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TemplateListItemUi(
    val id: Long,
    val name: String,
    val dayCount: Int,
    val daySummary: String,
)

class TemplatesListViewModel(private val repository: TemplateRepository) : ViewModel() {
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

    fun deleteTemplate(id: Long) {
        viewModelScope.launch { repository.deleteTemplate(id) }
    }

    private fun TemplateWithDays.toUi(): TemplateListItemUi {
        val dayNames = days.sortedBy { it.dayNumber }.map { it.name ?: "Day ${it.dayNumber}" }
        return TemplateListItemUi(
            id = template.id,
            name = template.name,
            dayCount = days.size,
            daySummary = if (dayNames.isEmpty()) "No days yet" else dayNames.joinToString(" · "),
        )
    }

    companion object {
        fun factory(repository: TemplateRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { TemplatesListViewModel(repository) }
        }
    }
}
