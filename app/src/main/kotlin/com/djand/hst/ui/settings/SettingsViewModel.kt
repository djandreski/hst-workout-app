package com.djand.hst.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.djand.hst.data.repository.BackupRepository
import com.djand.hst.data.repository.CycleRepository
import com.djand.hst.data.repository.ExerciseRepository
import com.djand.hst.data.settings.AppSettings
import com.djand.hst.data.settings.SettingsRepository
import com.djand.hst.data.settings.ThemeMode
import com.djand.hst.domain.model.Equipment
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs the Settings screen (DESIGN.md §10.9): rest timer, bar weight, per-equipment
 * increments, theme, JSON export/import, and reset cycle. Read state is the
 * persisted [AppSettings]; actions report through [actionState] for the snackbar.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val exerciseRepository: ExerciseRepository,
    private val backupRepository: BackupRepository,
    private val cycleRepository: CycleRepository,
) : ViewModel() {

    /** `null` while DataStore has not emitted yet, so the screen never flashes defaults. */
    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .map { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Transient action feedback (busy spinner, snackbar text). */
    data class ActionState(
        val busy: Boolean = false,
        val message: String? = null,
        val error: String? = null,
    )

    private val _actionState = MutableStateFlow(ActionState())
    val actionState: StateFlow<ActionState> = _actionState.asStateFlow()

    fun setRestSeconds(seconds: Int) = run { settingsRepository.setRestSeconds(seconds) }

    fun setBarWeight(kg: Double) = run { settingsRepository.setBarWeight(kg) }

    /** Writes the increment to Settings AND every exercise row of that equipment. */
    fun setIncrement(equipment: Equipment, kg: Double) = run {
        exerciseRepository.updateEquipmentIncrement(equipment, kg)
    }

    fun setThemeMode(mode: ThemeMode) = run { settingsRepository.setThemeMode(mode) }

    fun exportBackup(uri: Uri) = runBusy(
        action = { backupRepository.exportTo(uri) },
        message = "Backup exported",
    )

    fun importBackup(uri: Uri) = runBusy(
        action = { backupRepository.importFrom(uri) },
        message = "Backup imported",
    )

    /** Wipes all cycles/progress; the app then swaps back to the setup wizard. */
    fun resetAllProgress() = runBusy(action = { cycleRepository.resetAllProgress() })

    fun clearFeedback() = _actionState.update { it.copy(message = null, error = null) }

    private fun run(action: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { action() }
                .onFailure { e ->
                    _actionState.update { it.copy(error = e.message ?: "Something went wrong") }
                }
        }
    }

    private fun runBusy(action: suspend () -> Unit, message: String? = null) {
        if (_actionState.value.busy) return
        _actionState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            runCatching { action() }
                .onSuccess {
                    _actionState.update { ActionState(message = message) }
                }
                .onFailure { e ->
                    _actionState.update {
                        ActionState(error = e.message ?: "Something went wrong")
                    }
                }
        }
    }
}
