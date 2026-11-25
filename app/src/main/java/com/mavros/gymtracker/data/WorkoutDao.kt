package com.mavros.gymtracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

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

    @Query("DELETE FROM WorkoutSession WHERE id = :id")
    suspend fun deleteSessionById(id: Long)

    @Query("DELETE FROM SessionExercise WHERE sessionId = :sessionId")
    suspend fun deleteSessionExercises(sessionId: Long)
}
