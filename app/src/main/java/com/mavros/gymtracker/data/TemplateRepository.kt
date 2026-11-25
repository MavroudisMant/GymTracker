package com.mavros.gymtracker.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

/** What an imported exercise name becomes. */
sealed interface ExerciseChoice {
    data class UseExisting(val exerciseId: Long) : ExerciseChoice

    /** Create it, or reuse it if an exercise with this exact name exists by then. */
    data class CreateNew(val name: String, val targetArea: TargetArea?, val isTimed: Boolean) : ExerciseChoice
}

class TemplateRepository(private val db: GymDatabase) {
    private val templateDao = db.templateDao()
    private val exerciseDao = db.exerciseDao()

    fun observeTemplates(): Flow<List<TemplateWithDays>> = templateDao.observeTemplatesWithDays()

    fun observeTemplate(templateId: Long): Flow<TemplateDetail?> = templateDao.observeTemplateDetail(templateId)

    suspend fun createTemplate(name: String): Long = templateDao.insertTemplate(Template(name = name.trim()))

    suspend fun renameTemplate(id: Long, name: String) = templateDao.renameTemplate(id, name.trim())

    suspend fun deleteTemplate(id: Long) = templateDao.deleteTemplateById(id)

    suspend fun addDay(templateId: Long, name: String?) {
        val nextNumber = templateDao.maxDayNumber(templateId) + 1
        templateDao.insertDay(TemplateDay(templateId = templateId, dayNumber = nextNumber, name = name.cleanOptional()))
    }

    suspend fun renameDay(dayId: Long, name: String?) = templateDao.renameDay(dayId, name.cleanOptional())

    /** Removes a day and renumbers the rest, so there's no gap (Day 1, Day 2, … stay consecutive). */
    suspend fun removeDay(templateId: Long, dayId: Long) = db.withTransaction {
        templateDao.deleteDayById(dayId)
        renumberDays(templateDao.getDays(templateId))
    }

    /** Swaps a day with the one before ([offset] = -1) or after (+1) it. */
    suspend fun moveDay(templateId: Long, dayId: Long, offset: Int) = db.withTransaction {
        val days = templateDao.getDays(templateId).toMutableList()
        val from = days.indexOfFirst { it.id == dayId }
        val to = from + offset
        if (from < 0 || to !in days.indices) return@withTransaction
        days.add(to, days.removeAt(from))
        renumberDays(days)
    }

    private suspend fun renumberDays(ordered: List<TemplateDay>) {
        ordered.forEachIndexed { index, day ->
            if (day.dayNumber != index + 1) templateDao.setDayNumber(day.id, index + 1)
        }
    }

    suspend fun addExerciseToDay(dayId: Long, exerciseId: Long, sectionId: Long? = null): Long {
        val nextPosition = templateDao.maxPosition(dayId) + 1
        return templateDao.insertExercise(
            TemplateExercise(templateDayId = dayId, exerciseId = exerciseId, position = nextPosition, sectionId = sectionId),
        )
    }

    suspend fun addSection(dayId: Long, name: String) {
        val nextPosition = templateDao.maxSectionPosition(dayId) + 1
        templateDao.insertSection(TemplateSection(templateDayId = dayId, name = name.trim(), position = nextPosition))
    }

    suspend fun renameSection(sectionId: Long, name: String) = templateDao.renameSection(sectionId, name.trim())

    /** Deletes a section; its exercises stay in the day, just without a section. */
    suspend fun removeSection(sectionId: Long) {
        templateDao.clearSection(sectionId)
        templateDao.deleteSectionById(sectionId)
    }

    suspend fun setSectionIncluded(sectionId: Long, include: Boolean) = templateDao.setSectionIncluded(sectionId, include)

    suspend fun moveExerciseToSection(templateExerciseId: Long, sectionId: Long?) =
        templateDao.setExerciseSection(templateExerciseId, sectionId)

    suspend fun reorderSections(orderedIds: List<Long>) = db.withTransaction {
        orderedIds.forEachIndexed { index, id -> templateDao.setSectionPosition(id, index) }
    }

    suspend fun reorderExercises(orderedIds: List<Long>) = db.withTransaction {
        orderedIds.forEachIndexed { index, id -> templateDao.setExercisePosition(id, index) }
    }

    suspend fun removeExercise(templateExerciseId: Long) = templateDao.deleteTemplateExerciseById(templateExerciseId)

    suspend fun setTarget(templateExerciseId: Long, sets: Int?, reps: Int?) =
        templateDao.setTarget(templateExerciseId, sets, reps)

    suspend fun setSuperset(templateExerciseIds: List<Long>, group: String?) =
        templateDao.setSupersetGroup(templateExerciseIds, group)

    /** The template in the plain-text import format, or null if it no longer exists. */
    suspend fun exportText(templateId: Long): String? = getDraft(templateId)?.let(TemplateText::format)

    /**
     * Creates a template from [draft] in one transaction. [choices] maps each exercise's
     * normalized name (see [normalizeExerciseName]) to what it becomes. Returns the template id.
     */
    suspend fun importDraft(draft: TemplateDraft, choices: Map<String, ExerciseChoice>): Long = db.withTransaction {
        val resolved = mutableMapOf<String, Long>()
        suspend fun exerciseIdFor(exercise: ExerciseDraft): Long {
            val key = normalizeExerciseName(exercise.name)
            resolved[key]?.let { return it }
            val id = when (val choice = choices[key] ?: ExerciseChoice.CreateNew(exercise.name, exercise.targetArea, exercise.isTimed)) {
                is ExerciseChoice.UseExisting -> choice.exerciseId
                is ExerciseChoice.CreateNew -> exerciseDao.findByNormalizedName(normalizeExerciseName(choice.name))?.id
                    ?: exerciseDao.insert(Exercise.create(choice.name, choice.targetArea, choice.isTimed))
            }
            resolved[key] = id
            return id
        }

        val templateId = templateDao.insertTemplate(Template(name = draft.name.trim().ifEmpty { TemplateText.DEFAULT_NAME }))
        draft.days.forEachIndexed { dayIndex, day ->
            val dayId = templateDao.insertDay(
                TemplateDay(templateId = templateId, dayNumber = dayIndex + 1, name = day.name.cleanOptional()),
            )
            var position = 0
            var sectionPosition = 0
            day.sections.forEach { section ->
                val sectionId = section.name?.let { name ->
                    templateDao.insertSection(
                        TemplateSection(
                            templateDayId = dayId,
                            name = name.trim(),
                            position = sectionPosition++,
                            includeInWorkout = section.includeInWorkout,
                        ),
                    )
                }
                section.exercises.forEach { exercise ->
                    templateDao.insertExercise(
                        TemplateExercise(
                            templateDayId = dayId,
                            exerciseId = exerciseIdFor(exercise),
                            position = position++,
                            supersetGroup = exercise.supersetGroup,
                            targetSets = exercise.targetSets,
                            targetReps = exercise.targetReps,
                            sectionId = sectionId,
                        ),
                    )
                }
            }
        }
        templateId
    }

    /** Copies a template (days, sections, targets, supersets) as "<name> (copy)". Returns the new id. */
    suspend fun duplicate(templateId: Long): Long? {
        val detail = templateDao.getTemplateDetail(templateId) ?: return null
        val draft = detail.toDraft().let { it.copy(name = "${it.name} (copy)") }
        val choices = detail.days.flatMap { it.exercises }.associate {
            it.exercise.normalizedName to ExerciseChoice.UseExisting(it.exercise.id)
        }
        return importDraft(draft, choices)
    }

    private suspend fun getDraft(templateId: Long): TemplateDraft? = templateDao.getTemplateDetail(templateId)?.toDraft()

    /** Unsectioned exercises first, then each section in order, as the builder shows them. */
    private fun TemplateDetail.toDraft(): TemplateDraft = TemplateDraft(
        name = template.name,
        days = days.sortedBy { it.day.dayNumber }.map { day ->
            val sections = day.sections.sortedBy { it.position }
            val sectionIds = sections.map { it.id }.toSet()
            fun exercisesIn(sectionId: Long?) = day.exercises
                .filter { (it.templateExercise.sectionId?.takeIf { id -> id in sectionIds }) == sectionId }
                .sortedBy { it.templateExercise.position }
                .map { item ->
                    ExerciseDraft(
                        name = item.exercise.name,
                        supersetGroup = item.templateExercise.supersetGroup,
                        targetSets = item.templateExercise.targetSets,
                        targetReps = item.templateExercise.targetReps,
                        isTimed = item.exercise.isTimed,
                        targetArea = item.exercise.targetArea,
                    )
                }
            val unsectioned = exercisesIn(null)
            DayDraft(
                name = day.day.name,
                sections = listOfNotNull(unsectioned.takeIf { it.isNotEmpty() }?.let { SectionDraft(null, true, it) }) +
                    sections.map { SectionDraft(it.name, it.includeInWorkout, exercisesIn(it.id)) },
            )
        },
    )
}

/** Blank -> null, otherwise trimmed. */
private fun String?.cleanOptional(): String? = this?.takeIf { it.isNotBlank() }?.trim()
