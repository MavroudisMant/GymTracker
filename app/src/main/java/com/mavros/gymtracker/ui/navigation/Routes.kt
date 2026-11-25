package com.mavros.gymtracker.ui.navigation

object Routes {
    const val HOME = "home"
    const val LOG_WORKOUT = "log_workout"
    const val TEMPLATES = "templates"
    const val TEMPLATE_BUILDER = "template_builder"
    const val TEMPLATE_IMPORT = "template_import"
    const val EXERCISE_PROGRESS = "exercise_progress"
    const val EXERCISE_DETAIL = "exercise_detail"
    const val EDIT_EXERCISE = "edit_exercise"
    const val SETTINGS = "settings"

    const val ARG_SESSION_ID = "sessionId"
    const val ARG_TEMPLATE_ID = "templateId"
    const val ARG_EXERCISE_ID = "exerciseId"

    const val LOG_WORKOUT_PATTERN = "$LOG_WORKOUT?$ARG_SESSION_ID={$ARG_SESSION_ID}"
    const val TEMPLATE_BUILDER_PATTERN = "$TEMPLATE_BUILDER/{$ARG_TEMPLATE_ID}"
    const val EXERCISE_DETAIL_PATTERN = "$EXERCISE_DETAIL/{$ARG_EXERCISE_ID}"
    const val EDIT_EXERCISE_PATTERN = "$EDIT_EXERCISE/{$ARG_EXERCISE_ID}"

    fun logWorkout(sessionId: Long? = null): String =
        if (sessionId == null) LOG_WORKOUT else "$LOG_WORKOUT?$ARG_SESSION_ID=$sessionId"

    fun templateBuilder(templateId: Long): String = "$TEMPLATE_BUILDER/$templateId"

    fun exerciseDetail(exerciseId: Long): String = "$EXERCISE_DETAIL/$exerciseId"

    fun editExercise(exerciseId: Long): String = "$EDIT_EXERCISE/$exerciseId"
}
