package com.mavros.gymtracker.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mavros.gymtracker.data.RecentSession
import com.mavros.gymtracker.data.WorkoutRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

data class HomeSessionUi(
    val id: Long,
    val date: LocalDate,
    val dayName: String?,
    val summary: String,
    val exerciseCount: Int,
    /** Weight × reps over the session; 0 when nothing weighted was logged. */
    val volume: Double,
)

data class WeekStats(
    val workouts: Int = 0,
    val volume: Double = 0.0,
    /** Days since the most recent workout; null if there's none. */
    val daysSinceLast: Long? = null,
)

data class HomeUiState(
    val sessions: List<HomeSessionUi> = emptyList(),
    val week: WeekStats = WeekStats(),
    val loaded: Boolean = false,
)

class HomeViewModel(private val repository: WorkoutRepository) : ViewModel() {
    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeRecentSessions(50),
        repository.observeSessionVolumes(),
    ) { sessions, volumes ->
        val volumeBySession = volumes.associate { it.sessionId to it.volume }
        val items = sessions.map { it.toUi(volumeBySession[it.session.id] ?: 0.0) }
        HomeUiState(sessions = items, week = weekStats(items, LocalDate.now()), loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun deleteSession(id: Long) {
        viewModelScope.launch { repository.deleteSession(id) }
    }

    private fun weekStats(sessions: List<HomeSessionUi>, today: LocalDate): WeekStats {
        val firstDay: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        val weekStart = today.with(TemporalAdjusters.previousOrSame(firstDay))
        val thisWeek = sessions.filter { !it.date.isBefore(weekStart) && !it.date.isAfter(today) }
        val last = sessions.filter { !it.date.isAfter(today) }.maxOfOrNull { it.date }
        return WeekStats(
            workouts = thisWeek.size,
            volume = thisWeek.sumOf { it.volume },
            daysSinceLast = last?.let { ChronoUnit.DAYS.between(it, today) },
        )
    }

    private fun RecentSession.toUi(volume: Double): HomeSessionUi {
        val names = exercises.sortedBy { it.sessionExercise.position }.map { it.exercise.name }
        return HomeSessionUi(
            id = session.id,
            date = session.date,
            dayName = templateDay?.let { it.name ?: "Day ${it.dayNumber}" },
            summary = if (names.isEmpty()) "No exercises" else names.joinToString(" · "),
            exerciseCount = names.size,
            volume = volume,
        )
    }

    companion object {
        fun factory(repository: WorkoutRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(repository) }
        }
    }
}
