package com.mavros.gymtracker.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.ListAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
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
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = TopLevelTab.entries.any { it.route == currentDestination?.route }

    Scaffold(
        // Each screen handles its own insets; this scaffold only adds the bottom bar.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelTab.entries.forEach { tab ->
                        val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTab(tab.route) },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { outerPadding ->
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = Modifier.padding(outerPadding).consumeWindowInsets(outerPadding),
    ) {
        composable(Routes.HOME) {
            val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(workoutRepository))
            HomeScreen(
                viewModel = viewModel,
                onStartWorkout = { navController.navigate(Routes.logWorkout()) },
                onStartFromTemplate = { navController.navigateToTab(Routes.TEMPLATES) },
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
            val viewModel: TemplatesListViewModel =
                viewModel(factory = TemplatesListViewModel.factory(templateRepository, workoutRepository))
            TemplatesListScreen(
                viewModel = viewModel,
                onOpenTemplate = { id -> navController.navigate(Routes.templateBuilder(id)) },
                onStartWorkout = { sessionId -> navController.navigate(Routes.logWorkout(sessionId)) },
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
                onManageExercises = { navController.navigateToTab(Routes.EXERCISE_PROGRESS) },
            )
        }
    }
    }
}

private enum class TopLevelTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Workouts(Routes.HOME, "Workouts", Icons.Outlined.FitnessCenter, Icons.Rounded.FitnessCenter),
    Templates(Routes.TEMPLATES, "Templates", Icons.Outlined.ListAlt, Icons.Rounded.ListAlt),
    Progress(Routes.EXERCISE_PROGRESS, "Progress", Icons.Outlined.Insights, Icons.Rounded.Insights),
    Settings(Routes.SETTINGS, "Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
}

/** Switches tabs the standard way: one copy of each tab, each keeping its own back stack. */
private fun NavController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
