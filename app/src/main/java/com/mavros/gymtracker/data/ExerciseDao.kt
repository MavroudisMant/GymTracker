package com.mavros.gymtracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Insert
    suspend fun insert(exercise: Exercise): Long

    @Update
    suspend fun update(exercise: Exercise)

    @Query("SELECT * FROM Exercise WHERE id = :id")
    suspend fun getById(id: Long): Exercise?

    @Query("SELECT * FROM Exercise WHERE id = :id")
    fun observeById(id: Long): Flow<Exercise?>

    @Query(
        "SELECT (SELECT COUNT(*) FROM SessionExercise WHERE exerciseId = :id) + " +
            "(SELECT COUNT(*) FROM TemplateExercise WHERE exerciseId = :id)",
    )
    suspend fun usageCount(id: Long): Int

    @Query("SELECT * FROM Exercise WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun findByNormalizedName(normalizedName: String): Exercise?

    @Query("SELECT * FROM Exercise ORDER BY name")
    fun observeAll(): Flow<List<Exercise>>

    @Query("SELECT * FROM Exercise WHERE targetArea = :area ORDER BY name")
    fun observeByTargetArea(area: TargetArea): Flow<List<Exercise>>

    @Query("SELECT * FROM Exercise WHERE normalizedName LIKE '%' || :query || '%' ORDER BY name")
    fun autocomplete(query: String): Flow<List<Exercise>>

    @Query("DELETE FROM Exercise WHERE id = :id")
    suspend fun deleteById(id: Long)
}
