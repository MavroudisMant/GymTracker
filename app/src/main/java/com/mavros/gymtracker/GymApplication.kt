package com.mavros.gymtracker

import android.app.Application
import com.mavros.gymtracker.data.BackupRepository
import com.mavros.gymtracker.data.GymDatabase
import com.mavros.gymtracker.data.SettingsRepository
import com.mavros.gymtracker.data.TemplateRepository
import com.mavros.gymtracker.data.WorkoutRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Holds the app-wide singletons (manual dependency injection). */
class GymApplication : Application() {
    val database: GymDatabase by lazy { GymDatabase.getInstance(this) }
    val workoutRepository: WorkoutRepository by lazy { WorkoutRepository(database) }
    val backupRepository: BackupRepository by lazy { BackupRepository(this, database) }
    val templateRepository: TemplateRepository by lazy { TemplateRepository(database) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }

    /** For work that must outlive a screen, e.g. saving a workout as the user navigates away. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
