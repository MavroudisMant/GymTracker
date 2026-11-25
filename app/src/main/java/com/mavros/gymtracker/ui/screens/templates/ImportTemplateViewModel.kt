package com.mavros.gymtracker.ui.screens.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mavros.gymtracker.data.Exercise
import com.mavros.gymtracker.data.ExerciseChoice
import com.mavros.gymtracker.data.ExerciseMatch
import com.mavros.gymtracker.data.ExerciseMatcher
import com.mavros.gymtracker.data.TargetArea
import com.mavros.gymtracker.data.TemplateDraft
import com.mavros.gymtracker.data.TemplateRepository
import com.mavros.gymtracker.data.TemplateText
import com.mavros.gymtracker.data.WorkoutRepository
import com.mavros.gymtracker.data.normalizeExerciseName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MatchKind { EXACT, SIMILAR, NEW }

/** One distinct exercise name from the imported text and what it will map to. */
data class ImportExerciseUi(
    /** Normalized imported name; the key for [ExerciseChoice]s. */
    val key: String,
    val importedName: String,
    val kind: MatchKind,
    /** The exact or near match among the user's exercises, if any. */
    val suggestion: Exercise?,
    /** The existing exercise it will use; null means "create a new exercise". */
    val chosen: Exercise?,
    /** Used when created. */
    val targetArea: TargetArea?,
    val isTimed: Boolean,
)

data class ImportUiState(
    val text: String = "",
    val previewing: Boolean = false,
    val name: String = "",
    val draft: TemplateDraft? = null,
    val warnings: List<String> = emptyList(),
    val error: String? = null,
    /** Distinct names in order of first appearance. */
    val exercises: List<ImportExerciseUi> = emptyList(),
    /** The exercise key whose "choose existing" picker is open. */
    val pickingFor: String? = null,
    val importing: Boolean = false,
    /** Set once imported; the screen opens the new template. */
    val importedTemplateId: Long? = null,
) {
    val newCount: Int get() = exercises.count { it.chosen == null }
}

class ImportTemplateViewModel(
    private val templateRepository: TemplateRepository,
    private val workoutRepository: WorkoutRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ImportUiState())
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    val allExercises: StateFlow<List<Exercise>> = workoutRepository.observeAllExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onTextChange(text: String) {
        _state.update { it.copy(text = text, error = null) }
    }

    /** Text shared in from another app: show its preview straight away. */
    fun receiveSharedText(text: String) {
        _state.update { ImportUiState(text = text) }
        preview()
    }

    fun preview() {
        val text = _state.value.text
        viewModelScope.launch {
            val result = TemplateText.parse(text)
            val draft = result.draft
            if (draft == null) {
                _state.update { it.copy(error = result.error, warnings = result.warnings, previewing = false) }
                return@launch
            }
            val existing = workoutRepository.observeAllExercises().first()
            val exercises = draft.allExercises
                .distinctBy { normalizeExerciseName(it.name) }
                .map { exercise ->
                    val match = ExerciseMatcher.match(exercise.name, existing)
                    val suggestion = when (match) {
                        is ExerciseMatch.Exact -> match.exercise
                        is ExerciseMatch.Similar -> match.exercise
                        ExerciseMatch.None -> null
                    }
                    ImportExerciseUi(
                        key = normalizeExerciseName(exercise.name),
                        importedName = exercise.name,
                        kind = when (match) {
                            is ExerciseMatch.Exact -> MatchKind.EXACT
                            is ExerciseMatch.Similar -> MatchKind.SIMILAR
                            ExerciseMatch.None -> MatchKind.NEW
                        },
                        suggestion = suggestion,
                        // Near matches default to the user's exercise, so no near-duplicate is created.
                        chosen = suggestion,
                        targetArea = exercise.targetArea,
                        isTimed = exercise.isTimed,
                    )
                }
            _state.update {
                it.copy(
                    previewing = true,
                    name = draft.name,
                    draft = draft,
                    warnings = result.warnings,
                    error = null,
                    exercises = exercises,
                )
            }
        }
    }

    fun backToEdit() {
        _state.update { it.copy(previewing = false, pickingFor = null) }
    }

    fun onNameChange(name: String) {
        _state.update { it.copy(name = name) }
    }

    fun useExisting(key: String, exercise: Exercise) {
        updateExercise(key) { it.copy(chosen = exercise) }
        closePicker()
    }

    fun createNew(key: String) {
        updateExercise(key) { it.copy(chosen = null) }
    }

    fun openPicker(key: String) {
        _state.update { it.copy(pickingFor = key) }
    }

    fun closePicker() {
        _state.update { it.copy(pickingFor = null) }
    }

    fun import() {
        val current = _state.value
        val draft = current.draft ?: return
        if (current.importing) return
        _state.update { it.copy(importing = true) }
        viewModelScope.launch {
            val choices = current.exercises.associate { exercise ->
                exercise.key to (
                    exercise.chosen?.let { ExerciseChoice.UseExisting(it.id) }
                        ?: ExerciseChoice.CreateNew(exercise.importedName, exercise.targetArea, exercise.isTimed)
                    )
            }
            val id = templateRepository.importDraft(draft.copy(name = current.name.trim().ifEmpty { draft.name }), choices)
            _state.update { it.copy(importing = false, importedTemplateId = id) }
        }
    }

    fun onImportedHandled() {
        _state.update { it.copy(importedTemplateId = null) }
    }

    /** The chatbot prompt, listing the user's current exercises. */
    suspend fun aiPrompt(): String = TemplateText.aiPrompt(workoutRepository.observeAllExercises().first())

    private fun updateExercise(key: String, transform: (ImportExerciseUi) -> ImportExerciseUi) {
        _state.update { state ->
            state.copy(exercises = state.exercises.map { if (it.key == key) transform(it) else it })
        }
    }

    companion object {
        fun factory(templateRepository: TemplateRepository, workoutRepository: WorkoutRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { ImportTemplateViewModel(templateRepository, workoutRepository) }
            }
    }
}
