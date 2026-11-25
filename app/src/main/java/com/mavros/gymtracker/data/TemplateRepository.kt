package com.mavros.gymtracker.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

class TemplateRepository(private val db: GymDatabase) {
    private val templateDao = db.templateDao()

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

    suspend fun removeDay(dayId: Long) = templateDao.deleteDayById(dayId)

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
}

/** Blank -> null, otherwise trimmed. */
private fun String?.cleanOptional(): String? = this?.takeIf { it.isNotBlank() }?.trim()
