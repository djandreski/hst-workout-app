package com.djand.hst.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.djand.hst.data.repository.CycleRepository
import com.djand.hst.data.repository.TemplateRepository
import com.djand.hst.domain.model.Equipment
import com.djand.hst.domain.model.ExerciseInput
import com.djand.hst.domain.model.WorkoutLetter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs the first-launch setup wizard: the user enters their current working
 * weight x reps for every exercise (NOT a 1RM) and the first cycle is generated
 * from those numbers.
 *
 * Pull-ups appear in both workout A and C but are shown (and entered) only once.
 */
@HiltViewModel
class SetupViewModel @Inject constructor(
    private val templateRepository: TemplateRepository,
    private val cycleRepository: CycleRepository,
) : ViewModel() {

    /** One wizard row: a seeded exercise plus the user's two text fields. */
    data class ExerciseEntry(
        val exerciseId: String,
        val name: String,
        val equipment: Equipment,
        val incrementKg: Double,
        val isCompound: Boolean,
        val sets: Int,
        val weightText: String = "",
        val repsText: String = "10",
    )

    /** The exercises of one workout template, in display order. */
    data class Group(
        val letter: WorkoutLetter,
        val entries: List<ExerciseEntry>,
    )

    data class UiState(
        val loading: Boolean = true,
        val groups: List<Group> = emptyList(),
        val saving: Boolean = false,
        val error: String? = null,
    ) {
        /** Every row has a parseable weight (>= 0) and reps (1..30). */
        val canSubmit: Boolean
            get() = groups.isNotEmpty() && groups.all { group ->
                group.entries.all { entry ->
                    (entry.weightText.toDoubleOrNull()?.let { it >= 0.0 } == true) &&
                        (entry.repsText.toIntOrNull()?.let { it in 1..30 } == true)
                }
            }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val templates = templateRepository.templates.first()
            val seen = mutableSetOf<String>()
            val groups = templates
                .sortedBy { it.template.letter.ordinal }
                .map { template ->
                    Group(
                        letter = template.template.letter,
                        entries = template.exercises.mapNotNull { line ->
                            // Pull-ups are shared between A and C: show them once.
                            if (!seen.add(line.exercise.id)) return@mapNotNull null
                            ExerciseEntry(
                                exerciseId = line.exercise.id,
                                name = line.exercise.name,
                                equipment = line.exercise.equipment,
                                incrementKg = line.exercise.incrementKg,
                                isCompound = line.exercise.isCompound,
                                sets = line.templateExercise.sets,
                                weightText = if (line.exercise.equipment == Equipment.BODYWEIGHT) "0" else "",
                            )
                        },
                    )
                }
            _uiState.update { it.copy(loading = false, groups = groups) }
        }
    }

    fun onWeightChange(exerciseId: String, text: String) {
        val sanitized = text.filter { it.isDigit() || it == '.' }
        updateEntry(exerciseId) { it.copy(weightText = sanitized) }
    }

    fun onRepsChange(exerciseId: String, text: String) {
        val sanitized = text.filter { it.isDigit() }.take(2)
        updateEntry(exerciseId) { it.copy(repsText = sanitized) }
    }

    /** Builds the engine inputs and generates cycle 1. Flips setup complete on success. */
    fun startProgram() {
        val state = _uiState.value
        if (!state.canSubmit || state.saving) return
        _uiState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val inputs = state.groups
                    .flatMap { it.entries }
                    .distinctBy { it.exerciseId }
                    .map { entry ->
                        ExerciseInput(
                            exerciseId = entry.exerciseId,
                            equipment = entry.equipment,
                            incrementKg = entry.incrementKg,
                            isCompound = entry.isCompound,
                            weightKg = entry.weightText.toDouble(),
                            reps = entry.repsText.toInt(),
                        )
                    }
                cycleRepository.startNewCycle(inputs)
            }.onFailure { e ->
                _uiState.update { it.copy(saving = false, error = e.message ?: "Could not start the cycle") }
            }
        }
    }

    private fun updateEntry(exerciseId: String, transform: (ExerciseEntry) -> ExerciseEntry) {
        _uiState.update { state ->
            state.copy(
                groups = state.groups.map { group ->
                    group.copy(
                        entries = group.entries.map { entry ->
                            if (entry.exerciseId == exerciseId) transform(entry) else entry
                        },
                    )
                },
            )
        }
    }
}
