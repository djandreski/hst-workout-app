package com.djand.hst.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.djand.hst.data.local.entity.ExerciseProgressionEntity
import com.djand.hst.data.local.entity.SessionStatus
import com.djand.hst.data.repository.BodyweightRepository
import com.djand.hst.data.repository.CycleRepository
import com.djand.hst.data.repository.HistoryRepository
import com.djand.hst.data.repository.SessionRepository
import com.djand.hst.domain.model.WorkoutLetter
import com.djand.hst.ui.format.DisplayFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs the Home screen: today's workout, cycle progress, banners, last workout
 * and the bodyweight quick-log. Read-only state is a single combined flow; user
 * actions (start next cycle, log bodyweight) report through [actionState].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val cycleRepository: CycleRepository,
    sessionRepository: SessionRepository,
    historyRepository: HistoryRepository,
    private val bodyweightRepository: BodyweightRepository,
) : ViewModel() {

    /** The upcoming session rendered on the "Today's Workout" card. */
    data class UpcomingUi(
        val sessionId: Long,
        val workout: WorkoutLetter,
        val week: Int,
        val sessionNumber: Int,
        val isDeload: Boolean,
        val inProgress: Boolean,
    )

    /** The "Last workout" line. */
    data class LastWorkoutUi(
        val workout: WorkoutLetter,
        val week: Int,
        val date: String,
        val duration: String?,
    )

    data class UiState(
        val loading: Boolean = true,
        val cycleNumber: Int? = null,
        val upcoming: UpcomingUi? = null,
        val completedSessions: Int = 0,
        val lastWorkout: LastWorkoutUi? = null,
        val latestBodyweightKg: Double? = null,
        val pullUpSuggestion: Boolean = false,
        /** True once the whole cycle (incl. deload) is done and no cycle is active. */
        val cycleFinished: Boolean = false,
    )

    /** Transient action feedback (starting the next cycle, logging bodyweight). */
    data class ActionState(
        val busy: Boolean = false,
        val error: String? = null,
    )

    private val progressions: Flow<List<ExerciseProgressionEntity>> =
        cycleRepository.activeCycle.flatMapLatest { cycle ->
            if (cycle == null) flowOf(emptyList()) else cycleRepository.observeProgressions(cycle.id)
        }

    val uiState: StateFlow<UiState> = combine(
        cycleRepository.activeCycle,
        sessionRepository.upcomingSession,
        progressions,
        historyRepository.lastCompletedSession,
        bodyweightRepository.latest,
    ) { cycle, upcoming, progressions, last, bodyweight ->
        UiState(
            loading = false,
            cycleNumber = cycle?.cycleNumber,
            upcoming = upcoming?.let {
                UpcomingUi(
                    sessionId = it.session.id,
                    workout = it.session.workoutLetter,
                    week = it.session.week,
                    sessionNumber = it.session.sessionNumber,
                    isDeload = it.session.isDeload,
                    inProgress = it.session.status == SessionStatus.IN_PROGRESS,
                )
            },
            completedSessions = (upcoming?.session?.sessionNumber ?: 1) - 1,
            lastWorkout = last?.let {
                LastWorkoutUi(
                    workout = it.session.workoutLetter,
                    week = it.session.week,
                    date = it.session.completedAtEpochMs?.let(DisplayFormat::shortDate).orEmpty(),
                    duration = DisplayFormat.durationMinutes(
                        it.session.startedAtEpochMs,
                        it.session.completedAtEpochMs,
                    ),
                )
            },
            latestBodyweightKg = bodyweight?.weightKg,
            pullUpSuggestion = progressions.any { it.pullUpSuggestAddingWeight },
            cycleFinished = cycle == null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    private val _actionState = MutableStateFlow(ActionState())
    val actionState: StateFlow<ActionState> = _actionState.asStateFlow()

    /**
     * Starts the cycle after a finished one: the engine derives the new working
     * weights from what was actually achieved (see
     * [com.djand.hst.domain.progression.ProgressionEngine.nextCycleInputs]).
     */
    fun startNextCycle() {
        if (_actionState.value.busy) return
        _actionState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val inputs = cycleRepository.computeNextCycleInputs()
                cycleRepository.startNewCycle(inputs)
            }.onFailure { e ->
                _actionState.update { it.copy(busy = false, error = e.message ?: "Could not start the next cycle") }
            }.onSuccess {
                _actionState.update { ActionState() }
            }
        }
    }

    /** Logs today's bodyweight (re-logging the same day overwrites). */
    fun logBodyweight(text: String) {
        val kg = text.toDoubleOrNull()
        if (kg == null || kg <= 0.0) {
            _actionState.update { it.copy(error = "Enter a valid bodyweight in kg") }
            return
        }
        viewModelScope.launch {
            runCatching { bodyweightRepository.log(kg) }
                .onFailure { e -> _actionState.update { it.copy(error = e.message) } }
        }
    }

    fun clearError() = _actionState.update { it.copy(error = null) }
}
