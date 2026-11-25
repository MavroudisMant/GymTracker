package com.mavros.gymtracker.ui.screens.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mavros.gymtracker.data.Exercise
import com.mavros.gymtracker.data.TargetArea
import com.mavros.gymtracker.data.WorkoutRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

class ExerciseListViewModel(private val repository: WorkoutRepository) : ViewModel() {
    private val _filter = MutableStateFlow<TargetArea?>(null)
    val filter: StateFlow<TargetArea?> = _filter.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val exercises: StateFlow<List<Exercise>> = _filter
        .flatMapLatest { area ->
            if (area == null) repository.observeAllExercises() else repository.observeExercisesByArea(area)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFilter(area: TargetArea?) {
        _filter.value = area
    }

    companion object {
        fun factory(repository: WorkoutRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { ExerciseListViewModel(repository) }
        }
    }
}
