package com.mavros.gymtracker.data

import androidx.room.ColumnInfo
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
    ],
    indices = [Index("templateDayId")],
)
data class TemplateSection(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateDayId: Long,
    val name: String,
    val position: Int,
    @ColumnInfo(defaultValue = "1") val includeInWorkout: Boolean = true,
)
