package com.mavros.gymtracker

import android.content.Intent
import android.net.Uri
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
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mavros.gymtracker.ui.components.LocalWeightUnit
import com.mavros.gymtracker.ui.components.MAX_TEXT_FILE_BYTES
import com.mavros.gymtracker.ui.components.readTextUpTo
import com.mavros.gymtracker.ui.navigation.GymNavGraph
import com.mavros.gymtracker.ui.theme.GymTrackerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as GymApplication
        if (savedInstanceState == null) receiveSharedText(intent)
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
                            pendingImportText = app.pendingImportText,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receiveSharedText(intent)
    }

    /** Text (or a small text file) shared from another app goes to the template import screen. */
    private fun receiveSharedText(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val app = application as GymApplication
        intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let {
            app.pendingImportText.value = it
            return
        }
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: return
        lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openInputStream(uri)?.use { it.readTextUpTo(MAX_TEXT_FILE_BYTES) }
                }.getOrNull()
            }
            if (!text.isNullOrBlank()) app.pendingImportText.value = text
        }
    }
}
