package com.mavros.gymtracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = TemplateDay::class,
            parentColumns = ["id"],
            childColumns = ["templateDayId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("templateDayId")],
)
data class WorkoutSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val templateDayId: Long? = null,
    val note: String? = null,
)
