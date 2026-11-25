package com.mavros.gymtracker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mavros.gymtracker.data.BackupRepository
import com.mavros.gymtracker.data.SettingsRepository
import com.mavros.gymtracker.data.TemplateRepository
import com.mavros.gymtracker.data.WorkoutRepository
import com.mavros.gymtracker.ui.screens.editexercise.EditExerciseScreen
import com.mavros.gymtracker.ui.screens.editexercise.EditExerciseViewModel
import com.mavros.gymtracker.ui.screens.home.HomeScreen
import com.mavros.gymtracker.ui.screens.home.HomeViewModel
import com.mavros.gymtracker.ui.screens.logworkout.LogWorkoutScreen
import com.mavros.gymtracker.ui.screens.logworkout.LogWorkoutViewModel
import com.mavros.gymtracker.ui.screens.progress.ExerciseListScreen
import com.mavros.gymtracker.ui.screens.progress.ExerciseListViewModel
import com.mavros.gymtracker.ui.screens.progress.ExerciseProgressScreen
import com.mavros.gymtracker.ui.screens.progress.ExerciseProgressViewModel
import com.mavros.gymtracker.ui.screens.settings.SettingsScreen
import com.mavros.gymtracker.ui.screens.settings.SettingsViewModel
import com.mavros.gymtracker.ui.screens.templates.TemplateBuilderScreen
import com.mavros.gymtracker.ui.screens.templates.TemplateBuilderViewModel
import com.mavros.gymtracker.ui.screens.templates.TemplatesListScreen
import com.mavros.gymtracker.ui.screens.templates.TemplatesListViewModel
import kotlinx.coroutines.CoroutineScope

@Composable
fun GymNavGraph(
    workoutRepository: WorkoutRepository,
    templateRepository: TemplateRepository,
    settingsRepository: SettingsRepository,
    backupRepository: BackupRepository,
    appScope: CoroutineScope,
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(workoutRepository))
            HomeScreen(
                viewModel = viewModel,
                onStartWorkout = { navController.navigate(Routes.logWorkout()) },
                onStartFromTemplate = { navController.navigate(Routes.TEMPLATES) },
                onOpenProgress = { navController.navigate(Routes.EXERCISE_PROGRESS) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenSession = { id -> navController.navigate(Routes.logWorkout(id)) },
            )
        }

        composable(
            route = Routes.LOG_WORKOUT_PATTERN,
            arguments = listOf(
                navArgument(Routes.ARG_SESSION_ID) {
                    type = NavType.LongType
                    defaultValue = -1L
                },
            ),
        ) { backStackEntry ->
            // -1 means "new workout".
            val sessionId = backStackEntry.arguments?.getLong(Routes.ARG_SESSION_ID)?.takeIf { it >= 0 }
            val viewModel: LogWorkoutViewModel =
                viewModel(factory = LogWorkoutViewModel.factory(workoutRepository, appScope, sessionId))
            LogWorkoutScreen(viewModel = viewModel, onDone = { navController.popBackStack() })
        }

        composable(Routes.TEMPLATES) {
            val viewModel: TemplatesListViewModel = viewModel(factory = TemplatesListViewModel.factory(templateRepository))
            TemplatesListScreen(
                viewModel = viewModel,
                onOpenTemplate = { id -> navController.navigate(Routes.templateBuilder(id)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.TEMPLATE_BUILDER_PATTERN,
            arguments = listOf(navArgument(Routes.ARG_TEMPLATE_ID) { type = NavType.LongType }),
        ) { backStackEntry ->
            val templateId = requireNotNull(backStackEntry.arguments).getLong(Routes.ARG_TEMPLATE_ID)
            val viewModel: TemplateBuilderViewModel = viewModel(
                factory = TemplateBuilderViewModel.factory(templateRepository, workoutRepository, templateId),
            )
            TemplateBuilderScreen(
                viewModel = viewModel,
                onStartWorkout = { sessionId -> navController.navigate(Routes.logWorkout(sessionId)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.EXERCISE_PROGRESS) {
            val viewModel: ExerciseListViewModel = viewModel(factory = ExerciseListViewModel.factory(workoutRepository))
            ExerciseListScreen(
                viewModel = viewModel,
                onOpenExercise = { id -> navController.navigate(Routes.exerciseDetail(id)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.EXERCISE_DETAIL_PATTERN,
            arguments = listOf(navArgument(Routes.ARG_EXERCISE_ID) { type = NavType.LongType }),
        ) { backStackEntry ->
            val exerciseId = requireNotNull(backStackEntry.arguments).getLong(Routes.ARG_EXERCISE_ID)
            val viewModel: ExerciseProgressViewModel =
                viewModel(factory = ExerciseProgressViewModel.factory(workoutRepository, exerciseId))
            ExerciseProgressScreen(
                viewModel = viewModel,
                onEdit = { id -> navController.navigate(Routes.editExercise(id)) },
                onBack = { navController.popBackStack() },
                exerciseId = exerciseId,
            )
        }

        composable(
            route = Routes.EDIT_EXERCISE_PATTERN,
            arguments = listOf(navArgument(Routes.ARG_EXERCISE_ID) { type = NavType.LongType }),
        ) { backStackEntry ->
            val exerciseId = requireNotNull(backStackEntry.arguments).getLong(Routes.ARG_EXERCISE_ID)
            val viewModel: EditExerciseViewModel =
                viewModel(factory = EditExerciseViewModel.factory(workoutRepository, exerciseId))
            EditExerciseScreen(
                viewModel = viewModel,
                onSaved = { navController.popBackStack() },
                // The detail screen for a deleted exercise is gone too, so go back to the list.
                onDeleted = { navController.popBackStack(Routes.EXERCISE_PROGRESS, inclusive = false) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            val viewModel: SettingsViewModel =
                viewModel(factory = SettingsViewModel.factory(settingsRepository, workoutRepository, backupRepository))
            SettingsScreen(
                viewModel = viewModel,
                onManageExercises = { navController.navigate(Routes.EXERCISE_PROGRESS) },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
