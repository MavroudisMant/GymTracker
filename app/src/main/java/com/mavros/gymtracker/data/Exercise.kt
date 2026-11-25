package com.mavros.gymtracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Locale

@Entity(indices = [Index(value = ["normalizedName"], unique = true)])
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val normalizedName: String,
    val targetArea: TargetArea? = null,
    val isTimed: Boolean = false,
) {
    companion object {
        fun create(name: String, targetArea: TargetArea? = null, isTimed: Boolean = false): Exercise =
            Exercise(
                name = name.trim(),
                normalizedName = normalizeExerciseName(name),
                targetArea = targetArea,
                isTimed = isTimed,
            )
    }
}

fun normalizeExerciseName(name: String): String = name.trim().lowercase(Locale.ROOT)
