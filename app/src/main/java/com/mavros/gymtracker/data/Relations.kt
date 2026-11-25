package com.mavros.gymtracker.data

import androidx.room.Embedded
import androidx.room.Relation
import java.time.LocalDate

data class TemplateWithDays(
    @Embedded val template: Template,
    @Relation(parentColumn = "id", entityColumn = "templateId")
    val days: List<TemplateDay>,
)

data class TemplateExerciseWithName(
    @Embedded val templateExercise: TemplateExercise,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: Exercise,
)

data class TemplateDayWithExercises(
    @Embedded val day: TemplateDay,
    @Relation(entity = TemplateExercise::class, parentColumn = "id", entityColumn = "templateDayId")
    val exercises: List<TemplateExerciseWithName>,
    @Relation(parentColumn = "id", entityColumn = "templateDayId")
    val sections: List<TemplateSection>,
)

data class TemplateDetail(
    @Embedded val template: Template,
    @Relation(entity = TemplateDay::class, parentColumn = "id", entityColumn = "templateId")
    val days: List<TemplateDayWithExercises>,
)

data class SessionExerciseWithName(
    @Embedded val sessionExercise: SessionExercise,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: Exercise,
)

data class RecentSession(
    @Embedded val session: WorkoutSession,
    @Relation(parentColumn = "templateDayId", entityColumn = "id")
    val templateDay: TemplateDay?,
    @Relation(entity = SessionExercise::class, parentColumn = "id", entityColumn = "sessionId")
    val exercises: List<SessionExerciseWithName>,
)

data class SessionExerciseDetail(
    @Embedded val sessionExercise: SessionExercise,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: Exercise,
    @Relation(parentColumn = "id", entityColumn = "sessionExerciseId")
    val sets: List<SetEntry>,
)

data class SessionDetail(
    @Embedded val session: WorkoutSession,
    @Relation(parentColumn = "templateDayId", entityColumn = "id")
    val templateDay: TemplateDay?,
    @Relation(entity = SessionExercise::class, parentColumn = "id", entityColumn = "sessionId")
    val exercises: List<SessionExerciseDetail>,
)

data class SessionExerciseWithSets(
    @Embedded val sessionExercise: SessionExercise,
    @Relation(parentColumn = "id", entityColumn = "sessionExerciseId")
    val sets: List<SetEntry>,
)

data class SessionWithExercises(
    @Embedded val session: WorkoutSession,
    @Relation(entity = SessionExercise::class, parentColumn = "id", entityColumn = "sessionId")
    val exercises: List<SessionExerciseWithSets>,
)

data class ExerciseSessionHistory(
    @Embedded val sessionExercise: SessionExercise,
    val date: LocalDate,
    @Relation(parentColumn = "id", entityColumn = "sessionExerciseId")
    val sets: List<SetEntry>,
)

/** Total weight × reps of one session (timed exercises and bodyweight sets excluded). */
data class SessionVolume(
    val sessionId: Long,
    val volume: Double,
)

data class PersonalRecord(
    val weight: Double?,
    val reps: Int,
    val date: LocalDate,
)
