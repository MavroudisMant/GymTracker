package com.mavros.gymtracker.ui.components

import androidx.compose.runtime.compositionLocalOf
import com.mavros.gymtracker.data.WeightUnit

/** The user's chosen weight unit, provided at the root in MainActivity. */
val LocalWeightUnit = compositionLocalOf { WeightUnit.KG }
