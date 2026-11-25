package com.mavros.gymtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mavros.gymtracker.ui.components.LocalWeightUnit
import com.mavros.gymtracker.ui.navigation.GymNavGraph
import com.mavros.gymtracker.ui.theme.GymTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as GymApplication
        setContent {
            GymTrackerTheme {
                val unit by app.settingsRepository.unit.collectAsStateWithLifecycle()
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    CompositionLocalProvider(LocalWeightUnit provides unit) {
                        GymNavGraph(
                            workoutRepository = app.workoutRepository,
                            templateRepository = app.templateRepository,
                            settingsRepository = app.settingsRepository,
                            backupRepository = app.backupRepository,
                            appScope = app.applicationScope,
                        )
                    }
                }
            }
        }
    }
}
