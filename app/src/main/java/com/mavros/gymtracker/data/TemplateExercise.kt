package com.mavros.gymtracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = TemplateDay::class,
            parentColumns = ["id"],
            childColumns = ["templateDayId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [Index("templateDayId"), Index("exerciseId")],
)
data class TemplateExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateDayId: Long,
    val exerciseId: Long,
    val position: Int,
    val supersetGroup: String? = null,
    val targetSets: Int? = null,
    val targetReps: Int? = null,
    val sectionId: Long? = null,
)
