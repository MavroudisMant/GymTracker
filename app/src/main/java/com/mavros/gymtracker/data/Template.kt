package com.mavros.gymtracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Template(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)
