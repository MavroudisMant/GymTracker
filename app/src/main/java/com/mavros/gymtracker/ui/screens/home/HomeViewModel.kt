package com.mavros.gymtracker.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mavros.gymtracker.data.RecentSession
import com.mavros.gymtracker.data.WorkoutRepository
import com.mavros.gymtracker.ui.components.toSessionLabel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeSessionUi(
    val id: Long,
    val dateLabel: String,
    val dayName: String?,
    val summary: String,
)

class HomeViewModel(private val repository: WorkoutRepository) : ViewModel() {
    val sessions: StateFlow<List<HomeSessionUi>> = repository.observeRecentSessions(50)
        .map { sessions -> sessions.map { it.toUi() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteSession(id: Long) {
        viewModelScope.launch { repository.deleteSession(id) }
    }

    private fun RecentSession.toUi(): HomeSessionUi {
        val names = exercises.sortedBy { it.sessionExercise.position }.map { it.exercise.name }
        return HomeSessionUi(
            id = session.id,
            dateLabel = session.date.toSessionLabel(),
            dayName = templateDay?.let { it.name ?: "Day ${it.dayNumber}" },
            summary = if (names.isEmpty()) "No exercises" else names.joinToString(" · "),
        )
    }

    companion object {
        fun factory(repository: WorkoutRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(repository) }
        }
    }
}
