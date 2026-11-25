package com.mavros.gymtracker.ui.screens.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mavros.gymtracker.data.Exercise
import com.mavros.gymtracker.data.TargetArea
import com.mavros.gymtracker.data.TemplateDayWithExercises
import com.mavros.gymtracker.data.TemplateDetail
import com.mavros.gymtracker.data.TemplateRepository
import com.mavros.gymtracker.data.WorkoutRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TemplateBuilderUi(
    val id: Long,
    val name: String,
    val days: List<BuilderDayUi>,
)

data class BuilderDayUi(
    val id: Long,
    val title: String,
    /** The user-given name, without the "Day N" prefix; null if unnamed. */
    val name: String?,
    /** The first entry is always the unsectioned group (id = null). */
    val sections: List<BuilderSectionUi>,
) {
    val allExercises: List<BuilderExerciseUi> get() = sections.flatMap { it.exercises }
}

data class BuilderSectionUi(
    /** Null for exercises that aren't in a section. */
    val id: Long?,
    val name: String?,
    val position: Int,
    val includeInWorkout: Boolean,
    val exercises: List<BuilderExerciseUi>,
)

data class BuilderExerciseUi(
    /** TemplateExercise id. */
    val id: Long,
    val exerciseId: Long,
    val name: String,
    val position: Int,
    val isTimed: Boolean,
    val targetArea: TargetArea?,
    val supersetGroup: String?,
    /** e.g. "A1", "A2". */
    val supersetLabel: String?,
    val targetSets: Int?,
    val targetReps: Int?,
    /** e.g. "3 × 10", "3 sets", "45s". */
    val targetLabel: String?,
)

/** A row in a section: a lone exercise or a run of exercises sharing a superset group. */
sealed interface BuilderBlock {
    val key: String
    val exerciseIds: List<Long>

    data class Single(val exercise: BuilderExerciseUi) : BuilderBlock {
        override val key: String get() = "ex-${exercise.id}"
        override val exerciseIds: List<Long> get() = listOf(exercise.id)
    }

    data class Superset(val group: String, val members: List<BuilderExerciseUi>) : BuilderBlock {
        override val key: String get() = "ss-$group-${members.first().id}"
        override val exerciseIds: List<Long> get() = members.map { it.id }
    }
}

/** Transient UI state for the builder's dialogs, selection and one-shot events. */
data class BuilderEditState(
    /** Add-exercise dialog is open for this day (and section). */
    val addExerciseForDayId: Long? = null,
    val addExerciseForSectionId: Long? = null,
    /** A typed name that doesn't exist yet, waiting for the new-exercise dialog. */
    val pendingNewExerciseName: String? = null,
    val pendingNewExerciseDayId: Long? = null,
    val pendingNewExerciseSectionId: Long? = null,
    /** Template exercises selected for superset grouping. */
    val selectedExerciseIds: Set<Long> = emptySet(),
    /** Set once a workout was started from a day; the screen navigates to it. */
    val startedSessionId: Long? = null,
    val deleted: Boolean = false,
    /** Set after duplicating; the screen opens the copy. */
    val duplicatedTemplateId: Long? = null,
    /** Target (sets × reps) dialog is open for this template exercise. */
    val targetEditFor: Long? = null,
    val targetEditInitialSets: String = "",
    val targetEditInitialReps: String = "",
    val targetEditIsTimed: Boolean = false,
)

class TemplateBuilderViewModel(
    private val templateRepository: TemplateRepository,
    private val workoutRepository: WorkoutRepository,
    private val templateId: Long,
) : ViewModel() {
    val template: StateFlow<TemplateBuilderUi?> = templateRepository.observeTemplate(templateId)
        .map { it?.toUi() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val allExercises: StateFlow<List<Exercise>> = workoutRepository.observeAllExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _editState = MutableStateFlow(BuilderEditState())
    val editState: StateFlow<BuilderEditState> = _editState.asStateFlow()

    fun renameTemplate(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { templateRepository.renameTemplate(templateId, name) }
    }

    fun addDay(): Job = viewModelScope.launch { templateRepository.addDay(templateId, null) }

    fun renameDay(dayId: Long, name: String?): Job = viewModelScope.launch { templateRepository.renameDay(dayId, name) }

    fun removeDay(dayId: Long): Job = viewModelScope.launch { templateRepository.removeDay(templateId, dayId) }

    fun moveDay(dayId: Long, offset: Int): Job = viewModelScope.launch { templateRepository.moveDay(templateId, dayId, offset) }

    fun addSection(dayId: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { templateRepository.addSection(dayId, name) }
    }

    fun renameSection(sectionId: Long, name: String): Job =
        viewModelScope.launch { templateRepository.renameSection(sectionId, name) }

    fun removeSection(sectionId: Long): Job = viewModelScope.launch { templateRepository.removeSection(sectionId) }

    fun setSectionIncluded(sectionId: Long, include: Boolean): Job =
        viewModelScope.launch { templateRepository.setSectionIncluded(sectionId, include) }

    fun moveExerciseToSection(templateExerciseId: Long, sectionId: Long?): Job =
        viewModelScope.launch { templateRepository.moveExerciseToSection(templateExerciseId, sectionId) }

    /** Moves a named section from index [from] to [to] (indices exclude the unsectioned group). */
    fun reorderSections(dayId: Long, from: Int, to: Int) {
        val day = template.value?.days?.firstOrNull { it.id == dayId } ?: return
        val ids = day.sections.mapNotNull { it.id }.toMutableList()
        if (from !in ids.indices) return
        val target = to.coerceIn(0, ids.size - 1)
        ids.add(target, ids.removeAt(from))
        viewModelScope.launch { templateRepository.reorderSections(ids) }
    }

    fun applyExerciseOrder(orderedIds: List<Long>) {
        viewModelScope.launch { templateRepository.reorderExercises(orderedIds) }
    }

    fun deleteTemplate(): Job = viewModelScope.launch {
        templateRepository.deleteTemplate(templateId)
        _editState.update { it.copy(deleted = true) }
    }

    fun onDeletedHandled() {
        _editState.update { it.copy(deleted = false) }
    }

    fun openAddExercise(dayId: Long, sectionId: Long?) {
        _editState.update { it.copy(addExerciseForDayId = dayId, addExerciseForSectionId = sectionId) }
    }

    fun closeAddExercise() {
        _editState.update { it.copy(addExerciseForDayId = null, addExerciseForSectionId = null) }
    }

    /** Adds the exercise, then opens the target dialog for it. */
    fun addExistingExercise(dayId: Long, sectionId: Long?, exercise: Exercise) {
        viewModelScope.launch {
            val newId = templateRepository.addExerciseToDay(dayId, exercise.id, sectionId)
            _editState.update {
                it.copy(
                    addExerciseForDayId = null,
                    addExerciseForSectionId = null,
                    targetEditFor = newId,
                    targetEditInitialSets = "",
                    targetEditInitialReps = "",
                    targetEditIsTimed = exercise.isTimed,
                )
            }
        }
    }

    /** Adds the typed exercise if it exists; otherwise asks for its details first. */
    fun submitTypedExercise(dayId: Long, sectionId: Long?, rawName: String) {
        val name = rawName.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            val existing = workoutRepository.findExerciseByName(name)
            if (existing != null) {
                addExistingExercise(dayId, sectionId, existing)
            } else {
                _editState.update {
                    it.copy(
                        addExerciseForDayId = null,
                        addExerciseForSectionId = null,
                        pendingNewExerciseName = name,
                        pendingNewExerciseDayId = dayId,
                        pendingNewExerciseSectionId = sectionId,
                    )
                }
            }
        }
    }

    fun confirmNewExercise(targetArea: TargetArea?, isTimed: Boolean) {
        val state = _editState.value
        val name = state.pendingNewExerciseName ?: return
        val dayId = state.pendingNewExerciseDayId ?: return
        val sectionId = state.pendingNewExerciseSectionId
        viewModelScope.launch {
            val exercise = workoutRepository.getOrCreateExercise(name, targetArea, isTimed)
            val newId = templateRepository.addExerciseToDay(dayId, exercise.id, sectionId)
            _editState.update {
                it.copy(
                    pendingNewExerciseName = null,
                    pendingNewExerciseDayId = null,
                    pendingNewExerciseSectionId = null,
                    targetEditFor = newId,
                    targetEditInitialSets = "",
                    targetEditInitialReps = "",
                    targetEditIsTimed = exercise.isTimed,
                )
            }
        }
    }

    fun cancelNewExercise() {
        _editState.update {
            it.copy(pendingNewExerciseName = null, pendingNewExerciseDayId = null, pendingNewExerciseSectionId = null)
        }
    }

    fun openTargetEdit(exercise: BuilderExerciseUi) {
        _editState.update {
            it.copy(
                targetEditFor = exercise.id,
                targetEditInitialSets = exercise.targetSets?.toString() ?: "",
                targetEditInitialReps = exercise.targetReps?.toString() ?: "",
                targetEditIsTimed = exercise.isTimed,
            )
        }
    }

    fun closeTargetEdit() {
        _editState.update { it.copy(targetEditFor = null) }
    }

    fun saveTarget(templateExerciseId: Long, sets: Int?, reps: Int?) {
        viewModelScope.launch {
            templateRepository.setTarget(templateExerciseId, sets, reps)
            _editState.update { it.copy(targetEditFor = null) }
        }
    }

    fun removeExercise(templateExerciseId: Long) {
        viewModelScope.launch { templateRepository.removeExercise(templateExerciseId) }
        _editState.update { it.copy(selectedExerciseIds = it.selectedExerciseIds - templateExerciseId) }
    }

    fun toggleSelection(templateExerciseId: Long) {
        _editState.update {
            val selected = it.selectedExerciseIds
            it.copy(
                selectedExerciseIds = if (templateExerciseId in selected) {
                    selected - templateExerciseId
                } else {
                    selected + templateExerciseId
                },
            )
        }
    }

    fun clearSelection() {
        _editState.update { it.copy(selectedExerciseIds = emptySet()) }
    }

    /** Groups the selected exercises of a day into a superset with the next free letter (A, B, …). */
    fun makeSuperset(dayId: Long) {
        val day = template.value?.days?.firstOrNull { it.id == dayId } ?: return
        val selectedInDay = day.allExercises.filter { it.id in _editState.value.selectedExerciseIds }
        if (selectedInDay.size < 2) return
        val usedLabels = day.allExercises.mapNotNull { it.supersetGroup }.toSet()
        val nextLabel = ('A'..'Z').first { it.toString() !in usedLabels }.toString()
        viewModelScope.launch {
            templateRepository.setSuperset(selectedInDay.map { it.id }, nextLabel)
            _editState.update { it.copy(selectedExerciseIds = emptySet()) }
        }
    }

    /** Dissolves every superset that contains a selected exercise. */
    fun ungroupSuperset(dayId: Long) {
        val day = template.value?.days?.firstOrNull { it.id == dayId } ?: return
        val selected = _editState.value.selectedExerciseIds
        val groups = day.allExercises.filter { it.id in selected }.mapNotNull { it.supersetGroup }.toSet()
        if (groups.isEmpty()) return
        val idsToClear = day.allExercises.filter { it.supersetGroup in groups }.map { it.id }
        viewModelScope.launch {
            templateRepository.setSuperset(idsToClear, null)
            _editState.update { it.copy(selectedExerciseIds = emptySet()) }
        }
    }

    fun startWorkout(dayId: Long) {
        viewModelScope.launch {
            val sessionId = workoutRepository.startSessionFromTemplateDay(dayId, LocalDate.now())
            _editState.update { it.copy(startedSessionId = sessionId) }
        }
    }

    suspend fun exportText(): String? = templateRepository.exportText(templateId)

    fun duplicate() {
        viewModelScope.launch {
            val id = templateRepository.duplicate(templateId) ?: return@launch
            _editState.update { it.copy(duplicatedTemplateId = id) }
        }
    }

    fun onDuplicatedHandled() {
        _editState.update { it.copy(duplicatedTemplateId = null) }
    }

    fun onStartedWorkoutHandled() {
        _editState.update { it.copy(startedSessionId = null) }
    }

    private fun TemplateDetail.toUi(): TemplateBuilderUi =
        TemplateBuilderUi(
            id = template.id,
            name = template.name,
            days = days.sortedBy { it.day.dayNumber }.map { it.toUi() },
        )

    private fun TemplateDayWithExercises.toUi(): BuilderDayUi {
        val sectionPosition = sections.associate { it.id to it.position }
        val ordered = exercises.sortedWith(
            compareBy(
                { sectionPosition[it.templateExercise.sectionId] ?: Int.MAX_VALUE },
                { it.templateExercise.position },
            ),
        )

        // Number superset members in order: A1, A2, B1…
        val groupCounts = mutableMapOf<String, Int>()
        val uis = ordered.map { item ->
            val te = item.templateExercise
            val group = te.supersetGroup
            val supersetLabel = group?.let {
                val count = (groupCounts[it] ?: 0) + 1
                groupCounts[it] = count
                "$it$count"
            }
            te.sectionId to BuilderExerciseUi(
                id = te.id,
                exerciseId = item.exercise.id,
                name = item.exercise.name,
                position = te.position,
                isTimed = item.exercise.isTimed,
                targetArea = item.exercise.targetArea,
                supersetGroup = group,
                supersetLabel = supersetLabel,
                targetSets = te.targetSets,
                targetReps = te.targetReps,
                targetLabel = targetLabel(te.targetSets, te.targetReps, item.exercise.isTimed),
            )
        }
        val bySection = uis.groupBy({ it.first }, { it.second })

        val sectionUis = buildList {
            add(BuilderSectionUi(id = null, name = null, position = -1, includeInWorkout = true, exercises = bySection[null] ?: emptyList()))
            sections.sortedBy { it.position }.forEach { section ->
                add(
                    BuilderSectionUi(
                        id = section.id,
                        name = section.name,
                        position = section.position,
                        includeInWorkout = section.includeInWorkout,
                        exercises = bySection[section.id] ?: emptyList(),
                    ),
                )
            }
        }

        return BuilderDayUi(
            id = day.id,
            title = "Day ${day.dayNumber}" + (day.name?.let { " · $it" } ?: ""),
            name = day.name,
            sections = sectionUis,
        )
    }

    private fun targetLabel(sets: Int?, reps: Int?, isTimed: Boolean): String? {
        if (sets == null && reps == null) return null
        val repsPart = reps?.let { "$it" + if (isTimed) "s" else "" }
        return when {
            sets != null && repsPart != null -> "$sets × $repsPart"
            sets != null -> "$sets sets"
            else -> repsPart
        }
    }

    companion object {
        fun factory(
            templateRepository: TemplateRepository,
            workoutRepository: WorkoutRepository,
            templateId: Long,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { TemplateBuilderViewModel(templateRepository, workoutRepository, templateId) }
        }
    }
}
