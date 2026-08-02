package com.djand.hst.ui.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.djand.hst.data.local.entity.SessionStatus
import com.djand.hst.data.local.entity.SetStatus
import com.djand.hst.data.repository.ExerciseRepository
import com.djand.hst.data.repository.HistoryRepository
import com.djand.hst.data.repository.SessionRepository
import com.djand.hst.data.settings.SettingsRepository
import com.djand.hst.domain.model.Equipment
import com.djand.hst.domain.model.SetKind
import com.djand.hst.domain.model.WorkoutLetter
import com.djand.hst.ui.format.DisplayFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs the workout screen. The screen is driven entirely by the persisted set
 * logs: checking a set writes to Room and the updated state flows back, so the
 * workout survives process death at any point.
 *
 * The rest timer is the only purely transient state; everything else is durable.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkoutViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
    exerciseRepository: ExerciseRepository,
    private val historyRepository: HistoryRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val sessionId: Long = checkNotNull(savedStateHandle["sessionId"])

    /** One prescribed set rendered as a big check row. */
    data class SetUi(
        val id: Long,
        val setIndex: Int,
        val weightKg: Double,
        val targetReps: Int,
        val minReps: Int,
        val kind: SetKind,
        val status: SetStatus,
        val completedReps: Int?,
    )

    /** One exercise card. */
    data class ExerciseUi(
        val exerciseId: String,
        val name: String,
        val equipment: Equipment,
        val incrementKg: Double,
        val sets: List<SetUi>,
        val notes: String?,
        val skipped: Boolean,
        /** "Last: 70 kg × 10, 10" — the previous performance of this exercise. */
        val previousSummary: String?,
        /** True when the prescribed weight exceeds every previously completed weight. */
        val isPr: Boolean,
    ) {
        val resolved: Boolean get() = sets.all { it.status != SetStatus.PENDING }
        val firstWeightKg: Double get() = sets.first().weightKg
        val targetReps: Int get() = sets.first().targetReps
    }

    data class UiState(
        val loading: Boolean = true,
        val missing: Boolean = false,
        val status: SessionStatus = SessionStatus.PLANNED,
        val workout: WorkoutLetter = WorkoutLetter.A,
        val week: Int = 0,
        val sessionNumber: Int = 0,
        val isDeload: Boolean = false,
        val exercises: List<ExerciseUi> = emptyList(),
        val restSeconds: Int = 90,
        val barWeightKg: Double = 20.0,
    ) {
        /** Every set logged or skipped — the Finish button appears. */
        val allResolved: Boolean
            get() = exercises.isNotEmpty() && exercises.all { it.resolved }

        /** Index of the exercise the user should be looking at (auto-advance target). */
        val currentIndex: Int
            get() = exercises.indexOfFirst { !it.resolved }
    }

    /** Countdown shown after each checked set. Null = no active rest. */
    data class RestTimerUi(val totalSeconds: Int, val remainingSeconds: Int)

    val uiState: StateFlow<UiState> = combine(
        sessionRepository.observeSession(sessionId),
        exerciseRepository.exercises,
        settingsRepository.settings,
    ) { session, exercises, settings -> Triple(session, exercises, settings) }
        .mapLatest { (sessionWithSets, exercises, settings) ->
            currentRestSeconds = settings.restSeconds
            if (sessionWithSets == null) {
                return@mapLatest UiState(loading = false, missing = true)
            }
            val session = sessionWithSets.session
            val catalogue = exercises.associateBy { it.id }
            val sortedLogs = sessionWithSets.setLogs
                .sortedWith(compareBy({ it.exerciseIndex }, { it.setIndex }))

            val exerciseUis = sortedLogs
                .groupBy { it.exerciseId } // LinkedHashMap keeps template order
                .map { (exerciseId, logs) ->
                    val exercise = catalogue[exerciseId]
                    val history = historyRepository.exerciseHistory(exerciseId).first()
                        .filter { it.sessionId != sessionId }
                    val sets = logs.map { log ->
                        SetUi(
                            id = log.id,
                            setIndex = log.setIndex,
                            weightKg = log.prescribedWeightKg,
                            targetReps = log.prescribedTargetReps,
                            minReps = log.prescribedMinReps,
                            kind = log.kind,
                            status = log.status,
                            completedReps = log.completedReps,
                        )
                    }
                    val lastSessionRows = history.groupBy { it.sessionId }.values.lastOrNull()
                    ExerciseUi(
                        exerciseId = exerciseId,
                        name = exercise?.name ?: exerciseId,
                        equipment = exercise?.equipment ?: Equipment.BARBELL,
                        incrementKg = exercise?.incrementKg ?: 2.5,
                        sets = sets,
                        notes = logs.first().notes,
                        skipped = logs.all { it.status == SetStatus.SKIPPED },
                        previousSummary = lastSessionRows?.let { rows ->
                            val reps = rows.joinToString(", ") { (it.completedReps ?: 0).toString() }
                            "Last: ${DisplayFormat.weight(rows.first().prescribedWeightKg)} × $reps"
                        },
                        isPr = history.isNotEmpty() &&
                            sets.first().weightKg > history.maxOf { it.prescribedWeightKg },
                    )
                }

            UiState(
                loading = false,
                status = session.status,
                workout = session.workoutLetter,
                week = session.week,
                sessionNumber = session.sessionNumber,
                isDeload = session.isDeload,
                exercises = exerciseUis,
                restSeconds = settings.restSeconds,
                barWeightKg = settings.barWeightKg,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    private val _restTimer = MutableStateFlow<RestTimerUi?>(null)
    val restTimer: StateFlow<RestTimerUi?> = _restTimer.asStateFlow()

    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var restJob: Job? = null
    private var currentRestSeconds = 90
    private var sessionStarted = false

    // -------------------------------------------------------------- session start

    /** Marks the session IN_PROGRESS (once; a completed session is never resurrected). */
    fun start() {
        if (sessionStarted) return
        sessionStarted = true
        viewModelScope.launch {
            if (uiState.value.status != SessionStatus.COMPLETED) {
                sessionRepository.startSession(sessionId)
            }
        }
    }

    // -------------------------------------------------------------- set logging

    /** One-tap set completion: logs the prescribed target reps and starts the rest timer. */
    fun checkSet(set: SetUi) {
        if (set.status != SetStatus.PENDING) return
        viewModelScope.launch {
            runCatching { sessionRepository.logSet(set.id, set.targetReps) }
                .onFailure { _error.value = it.message }
            val pendingAfter = uiState.value.exercises.sumOf { ex ->
                ex.sets.count { it.status == SetStatus.PENDING && it.id != set.id }
            }
            if (pendingAfter > 0) startRest()
        }
    }

    /** Un-checks a set (mis-tap). */
    fun uncheckSet(set: SetUi) {
        if (set.status != SetStatus.DONE) return
        viewModelScope.launch {
            runCatching { sessionRepository.unlogSet(set.id) }
                .onFailure { _error.value = it.message }
        }
    }

    /** Corrects the completed reps of a checked set (missed reps / extra reps). */
    fun adjustReps(set: SetUi, delta: Int) {
        if (set.status != SetStatus.DONE) return
        val reps = ((set.completedReps ?: set.targetReps) + delta).coerceAtLeast(0)
        viewModelScope.launch {
            runCatching { sessionRepository.logSet(set.id, reps) }
                .onFailure { _error.value = it.message }
        }
    }

    // -------------------------------------------------------------- exercise level

    fun skipExercise(exerciseId: String) {
        viewModelScope.launch {
            runCatching { sessionRepository.skipExercise(sessionId, exerciseId) }
                .onFailure { _error.value = it.message }
        }
    }

    fun unskipExercise(exerciseId: String) {
        viewModelScope.launch {
            runCatching { sessionRepository.unskipExercise(sessionId, exerciseId) }
                .onFailure { _error.value = it.message }
        }
    }

    fun saveNotes(exerciseId: String, notes: String) {
        viewModelScope.launch {
            runCatching { sessionRepository.updateExerciseNotes(sessionId, exerciseId, notes) }
                .onFailure { _error.value = it.message }
        }
    }

    // -------------------------------------------------------------- session end

    /** Completes the session; progression rules run transactionally in the repository. */
    fun finish() {
        if (!uiState.value.allResolved || _finished.value) return
        viewModelScope.launch {
            runCatching { sessionRepository.completeSession(sessionId) }
                .onSuccess { _finished.value = true }
                .onFailure { _error.value = it.message ?: "Could not finish the session" }
        }
    }

    fun clearError() { _error.value = null }

    // -------------------------------------------------------------- rest timer

    fun startRest() {
        restJob?.cancel()
        val total = currentRestSeconds
        restJob = viewModelScope.launch {
            var remaining = total
            _restTimer.value = RestTimerUi(total, remaining)
            while (remaining > 0) {
                delay(1_000)
                remaining -= 1
                _restTimer.value = RestTimerUi(total, remaining)
            }
            _restTimer.value = null
        }
    }

    fun stopRest() {
        restJob?.cancel()
        _restTimer.value = null
    }

    fun restartRest() = startRest()
}
