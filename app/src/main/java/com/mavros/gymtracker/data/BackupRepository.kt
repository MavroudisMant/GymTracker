package com.mavros.gymtracker.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

sealed interface RestoreOutcome {
    data object Restored : RestoreOutcome
    data class Rejected(val reason: String) : RestoreOutcome
}

/**
 * Backs up the database by snapshotting it (VACUUM INTO) into the cache directory, where the
 * FileProvider can share it, and restores by validating a picked file and swapping it in for the
 * live database. After a restore the database is closed, so the app must restart.
 */
class BackupRepository(
    private val context: Context,
    private val database: GymDatabase,
) {
    suspend fun createBackup(now: LocalDateTime = LocalDateTime.now()): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, BACKUP_DIRECTORY)
        directory.mkdirs()
        directory.listFiles()?.forEach { it.delete() }
        val snapshot = File(directory, "gym-tracker-${TIMESTAMP.format(now)}.db")
        database.openHelper.writableDatabase.execSQL("VACUUM INTO ?", arrayOf(snapshot.path))
        snapshot
    }

    suspend fun restoreFrom(uri: Uri): RestoreOutcome = withContext(Dispatchers.IO) {
        val staged = File(context.cacheDir, STAGED_NAME)
        val live = context.getDatabasePath(GymDatabase.DB_NAME)
        val incoming = File(live.path + ".incoming")
        try {
            val read = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    staged.outputStream().use { output -> input.copyTo(output) }
                }
            }.getOrNull()
            if (read == null) return@withContext RestoreOutcome.Rejected("Couldn't read that file.")

            rejectionReason(staged)?.let { return@withContext RestoreOutcome.Rejected(it) }

            staged.copyTo(incoming, overwrite = true)
            database.close()
            if (!incoming.renameTo(live)) {
                return@withContext RestoreOutcome.Rejected("Couldn't replace the database. Nothing was changed.")
            }
            File(live.path + "-wal").delete()
            File(live.path + "-shm").delete()
            RestoreOutcome.Restored
        } catch (e: IOException) {
            RestoreOutcome.Rejected("Couldn't read that file: ${e.message}")
        } finally {
            staged.delete()
            incoming.delete()
        }
    }

    private fun rejectionReason(candidate: File): String? {
        if (candidate.length() < SMALLEST_POSSIBLE_DATABASE) return "That file is too small to be a backup."
        return try {
            SQLiteDatabase.openDatabase(candidate.path, null, SQLiteDatabase.OPEN_READONLY).use { snapshot ->
                when {
                    snapshot.version > DATABASE_VERSION ->
                        "That backup was made by a newer version of the app (database v${snapshot.version}; " +
                            "this build reads up to v$DATABASE_VERSION). Update the app, then restore."
                    else -> missingTable(snapshot)?.let { "That isn't a Gym Tracker backup — no $it table." }
                        ?: if (isDamaged(snapshot)) "That backup file is damaged." else null
                }
            }
        } catch (e: SQLiteException) {
            "That file isn't a database Gym Tracker can read."
        }
    }

    private fun missingTable(snapshot: SQLiteDatabase): String? {
        val present = mutableSetOf<String>()
        snapshot.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { cursor ->
            while (cursor.moveToNext()) present += cursor.getString(0)
        }
        return REQUIRED_TABLES.firstOrNull { it !in present }
    }

    private fun isDamaged(snapshot: SQLiteDatabase): Boolean =
        snapshot.rawQuery("PRAGMA integrity_check", null).use { cursor ->
            !(cursor.moveToFirst() && cursor.getString(0) == "ok")
        }

    private companion object {
        const val BACKUP_DIRECTORY = "backups"
        const val STAGED_NAME = "staged-restore.db"
        const val SMALLEST_POSSIBLE_DATABASE = 512L

        /** Keep in sync with the version in [GymDatabase]. */
        const val DATABASE_VERSION = 7

        val TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")
        val REQUIRED_TABLES = listOf(
            "Exercise", "Template", "TemplateDay", "TemplateExercise",
            "TemplateSection", "WorkoutSession", "SessionExercise", "SetEntry",
        )
    }
}
