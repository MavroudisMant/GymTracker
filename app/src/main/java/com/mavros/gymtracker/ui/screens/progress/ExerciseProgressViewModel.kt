package com.mavros.gymtracker.ui.screens.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mavros.gymtracker.data.Exercise
import com.mavros.gymtracker.data.ExerciseSessionHistory
import com.mavros.gymtracker.data.TargetArea
import com.mavros.gymtracker.data.WorkoutRepository
import com.mavros.gymtracker.ui.components.formatWeight
import com.mavros.gymtracker.ui.components.toSessionLabel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class PrUi(
    val weight: Double?,
    val reps: Int,
    val dateLabel: String,
)

data class ProgressSessionUi(
    val dateLabel: String,
    val setsSummary: String,
)

data class ExerciseProgressUiState(
    val name: String,
    val targetArea: TargetArea?,
    val isTimed: Boolean,
    val pr: PrUi?,
    /** Heaviest set per session, oldest first. */
    val chartWeights: List<Float>,
    /** Newest first. */
    val sessions: List<ProgressSessionUi>,
)

class ExerciseProgressViewModel(
    repository: WorkoutRepository,
    private val exerciseId: Long,
) : ViewModel() {
    /** Null while loading, or if the exercise was deleted. */
    val uiState: StateFlow<ExerciseProgressUiState?> =
        combine(repository.observeExercise(exerciseId), repository.observeExerciseHistory(exerciseId)) { exercise, history ->
            exercise?.let { buildState(it, history) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun buildState(exercise: Exercise, history: List<ExerciseSessionHistory>): ExerciseProgressUiState {
        val pr = history
            .flatMap { session -> session.sets.map { Triple(it.weight, it.reps, session.date) } }
            .maxWithOrNull(compareBy({ it.first }, { it.second }))
            ?.let { (weight, reps, date) -> PrUi(weight, reps, date.toSessionLabel()) }

        val chartWeights = history
            .sortedBy { it.date }
            .mapNotNull { session -> session.sets.mapNotNull { it.weight }.maxOrNull()?.toFloat() }

        val repsSuffix = if (exercise.isTimed) "s" else ""
        val sessions = history.map { session ->
            ProgressSessionUi(
                dateLabel = session.date.toSessionLabel(),
                setsSummary = session.sets.sortedBy { it.setNumber }.joinToString(" · ") { set ->
                    val weight = set.weight
                    if (weight == null) "${set.reps}$repsSuffix" else "${weight.formatWeight()}×${set.reps}$repsSuffix"
                },
            )
        }

        return ExerciseProgressUiState(
            name = exercise.name,
            targetArea = exercise.targetArea,
            isTimed = exercise.isTimed,
            pr = pr,
            chartWeights = chartWeights,
            sessions = sessions,
        )
    }

    companion object {
        fun factory(repository: WorkoutRepository, exerciseId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { ExerciseProgressViewModel(repository, exerciseId) }
        }
    }
}
