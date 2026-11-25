package com.mavros.gymtracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface WorkoutDao {
    @Insert
    suspend fun insertSession(session: WorkoutSession): Long

    @Insert
    suspend fun insertSessionExercise(sessionExercise: SessionExercise): Long

    @Insert
    suspend fun insertSet(set: SetEntry): Long

    @Insert
    suspend fun insertSets(sets: List<SetEntry>)

    @Delete
    suspend fun deleteSet(set: SetEntry)

    @Delete
    suspend fun deleteSession(session: WorkoutSession)

    @Update
    suspend fun updateSession(session: WorkoutSession)

    @Update
    suspend fun updateSet(set: SetEntry)

    @Transaction
    @Query("SELECT * FROM WorkoutSession ORDER BY date DESC, id DESC LIMIT :limit")
    fun observeRecentSessions(limit: Int): Flow<List<RecentSession>>

    @Query("SELECT * FROM WorkoutSession ORDER BY date DESC, id DESC")
    suspend fun getAllSessions(): List<WorkoutSession>

    @Transaction
    @Query("SELECT * FROM WorkoutSession WHERE id = :sessionId")
    suspend fun getSessionDetail(sessionId: Long): SessionDetail?

    @Transaction
    @Query("SELECT * FROM WorkoutSession WHERE id = :sessionId")
    suspend fun getSessionWithExercises(sessionId: Long): SessionWithExercises?

    @Transaction
    @Query("SELECT * FROM WorkoutSession WHERE id = :sessionId")
    fun observeSessionWithExercises(sessionId: Long): Flow<SessionWithExercises?>

    @Query("SELECT * FROM SetEntry WHERE sessionExerciseId = :sessionExerciseId ORDER BY setNumber")
    suspend fun getSets(sessionExerciseId: Long): List<SetEntry>

    @Transaction
    @Query(
        """
        SELECT se.*, ws.date AS date
        FROM SessionExercise se
        INNER JOIN WorkoutSession ws ON se.sessionId = ws.id
        WHERE se.exerciseId = :exerciseId
        ORDER BY ws.date DESC, ws.id DESC
        """,
    )
    fun observeExerciseHistory(exerciseId: Long): Flow<List<ExerciseSessionHistory>>

    @Transaction
    @Query(
        """
        SELECT se.*, ws.date AS date
        FROM SessionExercise se
        INNER JOIN WorkoutSession ws ON se.sessionId = ws.id
        WHERE se.exerciseId = :exerciseId
        ORDER BY ws.date DESC, ws.id DESC
        """,
    )
    suspend fun getExerciseHistory(exerciseId: Long): List<ExerciseSessionHistory>

    @Query(
        """
        SELECT s.weight AS weight, s.reps AS reps, ws.date AS date
        FROM SetEntry s
        INNER JOIN SessionExercise se ON s.sessionExerciseId = se.id
        INNER JOIN WorkoutSession ws ON se.sessionId = ws.id
        WHERE se.exerciseId = :exerciseId
        ORDER BY s.weight DESC, s.reps DESC
        LIMIT 1
        """,
    )
    suspend fun getPersonalRecord(exerciseId: Long): PersonalRecord?

    /**
     * The most recent time [exerciseId] was done before [excludeSessionId], on or before [onOrBefore].
     * Skips entries with no sets (e.g. an untouched session started from a template) unless flagged.
     */
    @Transaction
    @Query(
        """
        SELECT se.*, ws.date AS date
        FROM SessionExercise se
        INNER JOIN WorkoutSession ws ON se.sessionId = ws.id
        WHERE se.exerciseId = :exerciseId
          AND ws.id != :excludeSessionId
          AND ws.date <= :onOrBefore
          AND (se.increaseNextTime = 1 OR EXISTS (SELECT 1 FROM SetEntry s WHERE s.sessionExerciseId = se.id))
        ORDER BY ws.date DESC, ws.id DESC
        LIMIT 1
        """,
    )
    suspend fun getPreviousPerformance(exerciseId: Long, excludeSessionId: Long, onOrBefore: LocalDate): ExerciseSessionHistory?

    @Query(
        """
        SELECT se.sessionId AS sessionId, SUM(s.weight * s.reps) AS volume
        FROM SetEntry s
        INNER JOIN SessionExercise se ON s.sessionExerciseId = se.id
        INNER JOIN Exercise e ON se.exerciseId = e.id
        WHERE e.isTimed = 0 AND s.weight IS NOT NULL
        GROUP BY se.sessionId
        """,
    )
    fun observeSessionVolumes(): Flow<List<SessionVolume>>

    @Query("DELETE FROM WorkoutSession WHERE id = :id")
    suspend fun deleteSessionById(id: Long)

    @Query("DELETE FROM SessionExercise WHERE sessionId = :sessionId")
    suspend fun deleteSessionExercises(sessionId: Long)
}
