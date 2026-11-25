package com.mavros.gymtracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = Template::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("templateId")],
)
data class TemplateDay(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val dayNumber: Int,
    val name: String? = null,
)
