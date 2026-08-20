package com.djand.hst.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.djand.hst.data.local.entity.SetStatus
import com.djand.hst.data.repository.ExerciseRepository
import com.djand.hst.data.repository.HistoryRepository
import com.djand.hst.domain.model.Equipment
import com.djand.hst.ui.format.DisplayFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Backs the History screen: completed sessions, most recent first, each carrying
 * the per-exercise set detail for the expandable card (DESIGN.md §10.8).
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    historyRepository: HistoryRepository,
    exerciseRepository: ExerciseRepository,
) : ViewModel() {

    /** One set rendered inside the reps line ("15", "–" when skipped). */
    data class SetRepUi(
        val repsText: String,
        val missed: Boolean,
        val skipped: Boolean,
        /** "55 kg × " prefix, only when this set's weight differs from the exercise's first set. */
        val weightPrefix: String?,
    )

    data class ExerciseUi(
        val name: String,
        val skipped: Boolean,
        /** "70 kg" / "Bodyweight" / "+5 kg" — the load of the first (top) set. */
        val weightLabel: String,
        val sets: List<SetRepUi>,
    )

    data class SessionUi(
        val id: Long,
        val title: String,
        val subtitle: String,
        val exercises: List<ExerciseUi>,
    )

    data class UiState(
        val loading: Boolean = true,
        val sessions: List<SessionUi> = emptyList(),
    )

    val uiState: StateFlow<UiState> = combine(
        historyRepository.completedSessions,
        exerciseRepository.exercises,
    ) { sessions, catalogue ->
        val names = catalogue.associate { it.id to it.name }
        val equipment = catalogue.associate { it.id to it.equipment }
        UiState(
            loading = false,
            sessions = sessions.map { sessionWithSets ->
                val session = sessionWithSets.session
                val sorted = sessionWithSets.setLogs
                    .sortedWith(compareBy({ it.exerciseIndex }, { it.setIndex }))
                val exercises = sorted.groupBy { it.exerciseId }.map { (exerciseId, logs) ->
                    val firstWeight = logs.first().prescribedWeightKg
                    ExerciseUi(
                        name = names[exerciseId] ?: exerciseId,
                        skipped = logs.all { it.status == SetStatus.SKIPPED },
                        weightLabel = DisplayFormat.exerciseLoad(
                            equipment[exerciseId] ?: Equipment.BARBELL,
                            firstWeight,
                        ),
                        sets = logs.map { log ->
                            SetRepUi(
                                repsText = (log.completedReps ?: log.prescribedTargetReps).toString(),
                                missed = log.status == SetStatus.DONE &&
                                    (log.completedReps ?: 0) < log.prescribedMinReps,
                                skipped = log.status == SetStatus.SKIPPED,
                                weightPrefix = if (log.prescribedWeightKg != firstWeight) {
                                    DisplayFormat.weight(log.prescribedWeightKg)
                                } else {
                                    null
                                },
                            )
                        },
                    )
                }
                SessionUi(
                    id = session.id,
                        title = "Workout ${session.workoutLetter}",
                        subtitle = listOfNotNull(
                            "Session ${session.sessionNumber}",
                            if (session.phase in 1..4) com.djand.hst.domain.progression.ProgressionEngine.phaseName(session.phase) else null,
                            session.completedAtEpochMs?.let(DisplayFormat::shortDate),
                            DisplayFormat.durationMinutes(
                                session.startedAtEpochMs,
                                session.completedAtEpochMs,
                            ),
                        ).joinToString(" · "),
                    exercises = exercises,
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())
}
