package com.mavros.gymtracker.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class ExerciseToSave(
    val exerciseId: Long,
    val supersetGroup: String?,
    val sectionName: String?,
    val increaseNextTime: Boolean,
    val sets: List<SetToSave>,
)

data class SetToSave(
    val weight: Double?,
    val reps: Int,
)

class WorkoutRepository(private val db: GymDatabase) {
    private val exerciseDao = db.exerciseDao()
    private val workoutDao = db.workoutDao()

    fun observeAllExercises(): Flow<List<Exercise>> = exerciseDao.observeAll()

    fun observeExercisesByArea(area: TargetArea): Flow<List<Exercise>> = exerciseDao.observeByTargetArea(area)

    fun observeExercise(id: Long): Flow<Exercise?> = exerciseDao.observeById(id)

    fun observeExerciseHistory(exerciseId: Long): Flow<List<ExerciseSessionHistory>> =
        workoutDao.observeExerciseHistory(exerciseId)

    fun observeRecentSessions(limit: Int): Flow<List<RecentSession>> = workoutDao.observeRecentSessions(limit)

    suspend fun getSessionDetail(sessionId: Long): SessionDetail? = workoutDao.getSessionDetail(sessionId)

    /** Exercise id -> (target sets, target reps) for every exercise in a template day. */
    suspend fun templateTargetsForDay(dayId: Long): Map<Long, Pair<Int?, Int?>> {
        val exercises = db.templateDao().getDayWithExercises(dayId)?.exercises ?: return emptyMap()
        return exercises.associate {
            it.templateExercise.exerciseId to (it.templateExercise.targetSets to it.templateExercise.targetReps)
        }
    }

    /**
     * Creates a session for [date] pre-filled with the exercises of a template day. Exercises in
     * sections marked "don't include in workout" are skipped; the rest are ordered by section, then
     * by their position within the day.
     */
    suspend fun startSessionFromTemplateDay(dayId: Long, date: LocalDate): Long = db.withTransaction {
        val day = db.templateDao().getDayWithExercises(dayId)
        val sessionId = workoutDao.insertSession(WorkoutSession(date = date, templateDayId = dayId))
        val sectionsById = day?.sections?.associateBy { it.id } ?: emptyMap()
        day?.exercises
            ?.filter { sectionsById[it.templateExercise.sectionId]?.includeInWorkout ?: true }
            ?.sortedWith(
                compareBy(
                    { sectionsById[it.templateExercise.sectionId]?.position ?: Int.MAX_VALUE },
                    { it.templateExercise.position },
                ),
            )
            ?.forEachIndexed { index, item ->
                val templateExercise = item.templateExercise
                workoutDao.insertSessionExercise(
                    SessionExercise(
                        sessionId = sessionId,
                        exerciseId = templateExercise.exerciseId,
                        position = index,
                        supersetGroup = templateExercise.supersetGroup,
                        sectionName = sectionsById[templateExercise.sectionId]?.name,
                    ),
                )
            }
        sessionId
    }

    suspend fun findExerciseByName(name: String): Exercise? =
        exerciseDao.findByNormalizedName(normalizeExerciseName(name))

    suspend fun getOrCreateExercise(name: String, targetArea: TargetArea?, isTimed: Boolean = false): Exercise {
        findExerciseByName(name)?.let { return it }
        val id = exerciseDao.insert(Exercise.create(name, targetArea, isTimed))
        return requireNotNull(exerciseDao.getById(id))
    }

    /**
     * The last time this exercise was logged before [date], ignoring [excludeSessionId] (the session
     * being edited). Its sets are unsorted; sort by setNumber.
     */
    suspend fun previousPerformance(exerciseId: Long, excludeSessionId: Long?, date: LocalDate): ExerciseSessionHistory? =
        workoutDao.getPreviousPerformance(exerciseId, excludeSessionId ?: -1, date)

    fun observeSessionVolumes(): Flow<List<SessionVolume>> = workoutDao.observeSessionVolumes()

    suspend fun deleteSession(id: Long) = workoutDao.deleteSessionById(id)

    suspend fun getExercise(id: Long): Exercise? = exerciseDao.getById(id)

    suspend fun exerciseUsageCount(id: Long): Int = exerciseDao.usageCount(id)

    suspend fun deleteExercise(id: Long) = exerciseDao.deleteById(id)

    suspend fun isNameTaken(name: String, excludingId: Long): Boolean {
        val existing = findExerciseByName(name) ?: return false
        return existing.id != excludingId
    }

    suspend fun updateExercise(id: Long, name: String, targetArea: TargetArea?, isTimed: Boolean) =
        exerciseDao.update(Exercise.create(name, targetArea, isTimed).copy(id = id))

    /** Every logged set as CSV: date,exercise,set,weight,reps,superset. */
    suspend fun exportCsv(): String {
        val builder = StringBuilder("date,exercise,set,weight,reps,superset\n")
        for (session in workoutDao.getAllSessions()) {
            val detail = workoutDao.getSessionDetail(session.id) ?: continue
            for (exercise in detail.exercises.sortedBy { it.sessionExercise.position }) {
                val name = "\"" + exercise.exercise.name.replace("\"", "\"\"") + "\""
                val superset = exercise.sessionExercise.supersetGroup ?: ""
                for (set in exercise.sets.sortedBy { it.setNumber }) {
                    val weight = set.weight?.toString() ?: ""
                    builder.append("${session.date},$name,${set.setNumber},$weight,${set.reps},$superset\n")
                }
            }
        }
        return builder.toString()
    }

    /**
     * Inserts a new session, or overwrites [existingId] (its exercises and sets are replaced).
     * Returns the session id.
     */
    suspend fun saveSession(
        existingId: Long?,
        date: LocalDate,
        templateDayId: Long?,
        note: String?,
        exercises: List<ExerciseToSave>,
    ): Long = db.withTransaction {
        val sessionId = if (existingId == null) {
            workoutDao.insertSession(WorkoutSession(date = date, templateDayId = templateDayId, note = note))
        } else {
            workoutDao.updateSession(WorkoutSession(existingId, date, templateDayId, note))
            workoutDao.deleteSessionExercises(existingId)
            existingId
        }
        exercises.forEachIndexed { index, item ->
            val sessionExerciseId = workoutDao.insertSessionExercise(
                SessionExercise(
                    sessionId = sessionId,
                    exerciseId = item.exerciseId,
                    position = index,
                    supersetGroup = item.supersetGroup,
                    sectionName = item.sectionName,
                    increaseNextTime = item.increaseNextTime,
                ),
            )
            if (item.sets.isNotEmpty()) {
                workoutDao.insertSets(
                    item.sets.mapIndexed { setIndex, set ->
                        SetEntry(
                            sessionExerciseId = sessionExerciseId,
                            setNumber = setIndex + 1,
                            weight = set.weight,
                            reps = set.reps,
                        )
                    },
                )
            }
        }
        sessionId
    }
}
