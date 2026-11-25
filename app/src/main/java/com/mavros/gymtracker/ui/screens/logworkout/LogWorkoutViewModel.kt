package com.mavros.gymtracker.ui.screens.logworkout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mavros.gymtracker.data.Exercise
import com.mavros.gymtracker.data.ExerciseToSave
import com.mavros.gymtracker.data.SetToSave
import com.mavros.gymtracker.data.TargetArea
import com.mavros.gymtracker.data.WorkoutRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong

data class LoggedSetUi(
    /** Stable id for list keys; never persisted. */
    val clientId: Long,
    val weight: String = "",
    val reps: String = "",
)

data class LoggedExerciseUi(
    /** Stable id for list keys; never persisted. */
    val clientId: Long,
    val exerciseId: Long,
    val name: String,
    val targetArea: TargetArea?,
    val isTimed: Boolean = false,
    val supersetGroup: String? = null,
    val sectionName: String? = null,
    val sets: List<LoggedSetUi> = emptyList(),
)

data class LogWorkoutUiState(
    val date: LocalDate,
    /** Null until the first save of a new workout. */
    val sessionId: Long? = null,
    val templateDayId: Long? = null,
    val templateDayName: String? = null,
    val exercises: List<LoggedExerciseUi> = emptyList(),
    val selectedExerciseIds: Set<Long> = emptySet(),
    val showAddExerciseDialog: Boolean = false,
    val pendingNewExerciseName: String? = null,
)

/**
 * Logs (or edits) one workout. Changes auto-save 1.5 s after the last edit; [saveImmediately] runs
 * on the app scope so leaving the screen never drops the final edits.
 */
class LogWorkoutViewModel(
    private val repository: WorkoutRepository,
    private val appScope: CoroutineScope,
    private val sessionId: Long? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LogWorkoutUiState(date = LocalDate.now()))
    val uiState: StateFlow<LogWorkoutUiState> = _uiState.asStateFlow()

    val allExercises: StateFlow<List<Exercise>> = repository.observeAllExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val clientIds = AtomicLong(0)
    private val saveMutex = Mutex()

    init {
        if (sessionId != null) loadSession(sessionId)
        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            uiState
                .map { it.date to it.exercises }
                .distinctUntilChanged()
                .drop(1)
                .debounce(1_500)
                .collect { saveNow() }
        }
    }

    private fun nextId(): Long = clientIds.incrementAndGet()

    private fun loadSession(id: Long) {
        viewModelScope.launch {
            val detail = repository.getSessionDetail(id) ?: return@launch
            val targets = detail.session.templateDayId?.let { repository.templateTargetsForDay(it) } ?: emptyMap()
            _uiState.value = LogWorkoutUiState(
                date = detail.session.date,
                sessionId = detail.session.id,
                templateDayId = detail.session.templateDayId,
                templateDayName = detail.templateDay?.let { it.name ?: "Day ${it.dayNumber}" },
                exercises = detail.exercises.sortedBy { it.sessionExercise.position }.map { item ->
                    val sets = item.sets.sortedBy { it.setNumber }
                    LoggedExerciseUi(
                        clientId = nextId(),
                        exerciseId = item.exercise.id,
                        name = item.exercise.name,
                        targetArea = item.exercise.targetArea,
                        isTimed = item.exercise.isTimed,
                        supersetGroup = item.sessionExercise.supersetGroup,
                        sectionName = item.sessionExercise.sectionName,
                        // A session just started from a template has no sets yet: pre-fill its targets.
                        sets = if (sets.isEmpty()) {
                            suggestedSets(targets[item.exercise.id])
                        } else {
                            sets.map { LoggedSetUi(nextId(), it.weight.toCleanString(), it.reps.toString()) }
                        },
                    )
                },
            )
        }
    }

    /** Empty rows for a template target (sets × reps); one empty row if there's no target. */
    private fun suggestedSets(target: Pair<Int?, Int?>?): List<LoggedSetUi> {
        val targetSets = target?.first
        val targetReps = target?.second
        if (targetSets == null && targetReps == null) return listOf(LoggedSetUi(nextId()))
        return List(targetSets ?: 1) { LoggedSetUi(nextId(), "", targetReps?.toString() ?: "") }
    }

    fun onDateSelected(date: LocalDate) {
        _uiState.update { it.copy(date = date) }
    }

    fun openAddExerciseDialog() {
        _uiState.update { it.copy(showAddExerciseDialog = true) }
    }

    fun closeAddExerciseDialog() {
        _uiState.update { it.copy(showAddExerciseDialog = false) }
    }

    /** Adds the exercise pre-filled with the sets from the last time it was logged. */
    fun addExistingExercise(exercise: Exercise) {
        viewModelScope.launch {
            val lastSets = repository.lastLoggedSets(exercise.id)
            val sets = if (lastSets.isEmpty()) {
                listOf(LoggedSetUi(nextId()))
            } else {
                lastSets.map { LoggedSetUi(nextId(), it.weight.toCleanString(), it.reps.toString()) }
            }
            _uiState.update {
                it.copy(
                    exercises = it.exercises + LoggedExerciseUi(
                        clientId = nextId(),
                        exerciseId = exercise.id,
                        name = exercise.name,
                        targetArea = exercise.targetArea,
                        isTimed = exercise.isTimed,
                        sets = sets,
                    ),
                    showAddExerciseDialog = false,
                )
            }
        }
    }

    fun submitTypedExercise(rawName: String) {
        val name = rawName.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            val existing = repository.findExerciseByName(name)
            if (existing != null) {
                addExistingExercise(existing)
            } else {
                _uiState.update { it.copy(showAddExerciseDialog = false, pendingNewExerciseName = name) }
            }
        }
    }

    fun confirmNewExercise(targetArea: TargetArea?, isTimed: Boolean) {
        val name = _uiState.value.pendingNewExerciseName ?: return
        viewModelScope.launch {
            val exercise = repository.getOrCreateExercise(name, targetArea, isTimed)
            _uiState.update { it.copy(pendingNewExerciseName = null) }
            addExistingExercise(exercise)
        }
    }

    fun cancelNewExercise() {
        _uiState.update { it.copy(pendingNewExerciseName = null) }
    }

    /** Appends a set that copies the previous one. */
    fun addSet(exerciseClientId: Long) {
        updateExercise(exerciseClientId) { exercise ->
            val last = exercise.sets.lastOrNull()
            exercise.copy(sets = exercise.sets + LoggedSetUi(nextId(), last?.weight ?: "", last?.reps ?: ""))
        }
    }

    fun updateSetWeight(exerciseClientId: Long, setClientId: Long, weight: String) {
        updateSet(exerciseClientId, setClientId) { it.copy(weight = weight) }
    }

    fun updateSetReps(exerciseClientId: Long, setClientId: Long, reps: String) {
        updateSet(exerciseClientId, setClientId) { it.copy(reps = reps) }
    }

    fun removeSet(exerciseClientId: Long, setClientId: Long) {
        updateExercise(exerciseClientId) { exercise ->
            exercise.copy(sets = exercise.sets.filter { it.clientId != setClientId })
        }
    }

    fun removeExercise(exerciseClientId: Long) {
        _uiState.update {
            it.copy(
                exercises = it.exercises.filter { e -> e.clientId != exerciseClientId },
                selectedExerciseIds = it.selectedExerciseIds - exerciseClientId,
            )
        }
    }

    fun toggleSelection(exerciseClientId: Long) {
        _uiState.update {
            val selected = it.selectedExerciseIds
            it.copy(
                selectedExerciseIds = if (exerciseClientId in selected) {
                    selected - exerciseClientId
                } else {
                    selected + exerciseClientId
                },
            )
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedExerciseIds = emptySet()) }
    }

    /** Puts the selected exercises in a new superset with the next free letter. */
    fun makeSuperset() {
        _uiState.update { state ->
            if (state.selectedExerciseIds.size < 2) return@update state
            val used = state.exercises.mapNotNull { it.supersetGroup }.toSet()
            val label = ('A'..'Z').first { it.toString() !in used }.toString()
            state.copy(
                exercises = state.exercises.map {
                    if (it.clientId in state.selectedExerciseIds) it.copy(supersetGroup = label) else it
                },
                selectedExerciseIds = emptySet(),
            )
        }
    }

    /** Dissolves every superset containing a selected exercise. */
    fun ungroupSuperset() {
        _uiState.update { state ->
            val groups = state.exercises
                .filter { it.clientId in state.selectedExerciseIds }
                .mapNotNull { it.supersetGroup }
                .toSet()
            if (groups.isEmpty()) return@update state
            state.copy(
                exercises = state.exercises.map { if (it.supersetGroup in groups) it.copy(supersetGroup = null) else it },
                selectedExerciseIds = emptySet(),
            )
        }
    }

    /** Saves on the app scope, so it completes even after the screen (and this ViewModel) is gone. */
    fun saveImmediately() {
        appScope.launch { saveNow() }
    }

    private suspend fun saveNow() = saveMutex.withLock {
        val state = _uiState.value
        if (state.exercises.isEmpty()) return@withLock
        val toSave = state.exercises.map { exercise ->
            ExerciseToSave(
                exerciseId = exercise.exerciseId,
                supersetGroup = exercise.supersetGroup,
                sectionName = exercise.sectionName,
                sets = exercise.sets.mapNotNull { it.toDomainOrNull() },
            )
        }
        val savedId = repository.saveSession(state.sessionId, state.date, state.templateDayId, null, toSave)
        if (state.sessionId == null) {
            _uiState.update { it.copy(sessionId = savedId) }
        }
    }

    private fun updateExercise(exerciseClientId: Long, transform: (LoggedExerciseUi) -> LoggedExerciseUi) {
        _uiState.update {
            it.copy(exercises = it.exercises.map { e -> if (e.clientId == exerciseClientId) transform(e) else e })
        }
    }

    private fun updateSet(exerciseClientId: Long, setClientId: Long, transform: (LoggedSetUi) -> LoggedSetUi) {
        updateExercise(exerciseClientId) { exercise ->
            exercise.copy(sets = exercise.sets.map { if (it.clientId == setClientId) transform(it) else it })
        }
    }

    /** Rows without valid reps (or with a negative weight) are skipped. Accepts "82,5" as 82.5. */
    private fun LoggedSetUi.toDomainOrNull(): SetToSave? {
        val parsedReps = reps.trim().toIntOrNull()?.takeIf { it > 0 } ?: return null
        val parsedWeight = weight.trim().replace(',', '.').toDoubleOrNull()
        if (parsedWeight != null && parsedWeight < 0.0) return null
        return SetToSave(parsedWeight, parsedReps)
    }

    private fun Double?.toCleanString(): String = when {
        this == null -> ""
        this % 1.0 == 0.0 -> toLong().toString()
        else -> toString()
    }

    companion object {
        fun factory(
            repository: WorkoutRepository,
            appScope: CoroutineScope,
            sessionId: Long? = null,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { LogWorkoutViewModel(repository, appScope, sessionId) }
        }
    }
}
