package com.mavros.gymtracker.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class WeightUnit(val label: String) {
    KG("kg"),
    LB("lb"),
}

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _unit = MutableStateFlow(loadUnit())
    val unit: StateFlow<WeightUnit> = _unit.asStateFlow()

    fun setUnit(unit: WeightUnit) {
        prefs.edit { putString(KEY_UNIT, unit.name) }
        _unit.value = unit
    }

    private fun loadUnit(): WeightUnit =
        runCatching { WeightUnit.valueOf(prefs.getString(KEY_UNIT, "KG")!!) }
            .getOrDefault(WeightUnit.KG)

    private companion object {
        const val KEY_UNIT = "weight_unit"
    }
}
