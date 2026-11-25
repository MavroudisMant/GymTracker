package com.mavros.gymtracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {
    @Insert
    suspend fun insertTemplate(template: Template): Long

    @Insert
    suspend fun insertDay(day: TemplateDay): Long

    @Insert
    suspend fun insertExercise(templateExercise: TemplateExercise): Long

    @Insert
    suspend fun insertSection(section: TemplateSection): Long

    @Transaction
    @Query("SELECT * FROM Template ORDER BY name")
    fun observeTemplatesWithDays(): Flow<List<TemplateWithDays>>

    @Transaction
    @Query("SELECT * FROM Template WHERE id = :templateId")
    fun observeTemplateDetail(templateId: Long): Flow<TemplateDetail?>

    @Transaction
    @Query("SELECT * FROM TemplateDay WHERE id = :dayId")
    suspend fun getDayWithExercises(dayId: Long): TemplateDayWithExercises?

    @Query("SELECT COALESCE(MAX(dayNumber), 0) FROM TemplateDay WHERE templateId = :templateId")
    suspend fun maxDayNumber(templateId: Long): Int

    @Query("SELECT COALESCE(MAX(position), -1) FROM TemplateExercise WHERE templateDayId = :dayId")
    suspend fun maxPosition(dayId: Long): Int

    @Query("SELECT COALESCE(MAX(position), -1) FROM TemplateSection WHERE templateDayId = :dayId")
    suspend fun maxSectionPosition(dayId: Long): Int

    @Query("UPDATE Template SET name = :name WHERE id = :id")
    suspend fun renameTemplate(id: Long, name: String)

    @Query("DELETE FROM Template WHERE id = :id")
    suspend fun deleteTemplateById(id: Long)

    @Query("UPDATE TemplateDay SET name = :name WHERE id = :id")
    suspend fun renameDay(id: Long, name: String?)

    @Query("DELETE FROM TemplateDay WHERE id = :id")
    suspend fun deleteDayById(id: Long)

    @Query("DELETE FROM TemplateExercise WHERE id = :id")
    suspend fun deleteTemplateExerciseById(id: Long)

    @Query("UPDATE TemplateExercise SET supersetGroup = :group WHERE id IN (:ids)")
    suspend fun setSupersetGroup(ids: List<Long>, group: String?)

    @Query("UPDATE TemplateExercise SET targetSets = :sets, targetReps = :reps WHERE id = :id")
    suspend fun setTarget(id: Long, sets: Int?, reps: Int?)

    @Query("UPDATE TemplateSection SET name = :name WHERE id = :id")
    suspend fun renameSection(id: Long, name: String)

    @Query("DELETE FROM TemplateSection WHERE id = :id")
    suspend fun deleteSectionById(id: Long)

    @Query("UPDATE TemplateExercise SET sectionId = NULL WHERE sectionId = :sectionId")
    suspend fun clearSection(sectionId: Long)

    @Query("UPDATE TemplateSection SET includeInWorkout = :include WHERE id = :id")
    suspend fun setSectionIncluded(id: Long, include: Boolean)

    @Query("UPDATE TemplateExercise SET sectionId = :sectionId WHERE id = :id")
    suspend fun setExerciseSection(id: Long, sectionId: Long?)

    @Query("UPDATE TemplateSection SET position = :position WHERE id = :id")
    suspend fun setSectionPosition(id: Long, position: Int)

    @Query("UPDATE TemplateExercise SET position = :position WHERE id = :id")
    suspend fun setExercisePosition(id: Long, position: Int)
}
