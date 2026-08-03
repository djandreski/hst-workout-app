package com.djand.hst.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.djand.hst.data.local.entity.SetStatus
import com.djand.hst.data.local.relation.ExerciseHistoryRow
import com.djand.hst.data.local.relation.SessionWithSetLogs
import com.djand.hst.data.repository.BodyweightRepository
import com.djand.hst.data.repository.ExerciseRepository
import com.djand.hst.data.repository.HistoryRepository
import com.djand.hst.ui.format.DisplayFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * Backs the Statistics screen: ONE metric at a time (DESIGN.md §10.7), selected
 * via a dropdown — per-exercise working weight, bodyweight, or weekly tonnage.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    historyRepository: HistoryRepository,
    exerciseRepository: ExerciseRepository,
    bodyweightRepository: BodyweightRepository,
) : ViewModel() {

    /** One (label, value) point of the selected series, oldest first. */
    data class ChartPoint(val label: String, val value: Float)

    data class MetricOption(val key: String, val label: String)

    data class UiState(
        val loading: Boolean = true,
        val options: List<MetricOption> = emptyList(),
        val selectedKey: String? = null,
        val points: List<ChartPoint> = emptyList(),
    )

    private val selection = MutableStateFlow<String?>(null)

    fun select(key: String) {
        selection.value = key
    }

    private val exerciseHistory = selection.flatMapLatest { key ->
        if (key == null || key == KEY_BODYWEIGHT || key == KEY_TONNAGE) {
            flowOf(emptyList())
        } else {
            historyRepository.exerciseHistory(key)
        }
    }

    val uiState: StateFlow<UiState> = combine(
        exerciseRepository.exercises,
        historyRepository.completedSessions,
        bodyweightRepository.entries,
        exerciseHistory,
        selection,
    ) { exercises, sessions, bodyweight, history, selected ->
        val options = exercises.map { MetricOption(it.id, it.name) } +
            MetricOption(KEY_BODYWEIGHT, "Bodyweight") +
            MetricOption(KEY_TONNAGE, "Weekly tonnage")
        val key = selected ?: exercises.firstOrNull()?.id ?: KEY_TONNAGE
        val points = when (key) {
            KEY_BODYWEIGHT -> bodyweight.map {
                ChartPoint(DisplayFormat.dayAndMonth(it.epochDay), it.weightKg.toFloat())
            }

            KEY_TONNAGE -> weeklyTonnage(sessions)
            else -> exerciseWeightPoints(history)
        }
        UiState(
            loading = false,
            options = options,
            selectedKey = key,
            points = points,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    /** Working weight per session (the heaviest set of the exercise that day). */
    private fun exerciseWeightPoints(history: List<ExerciseHistoryRow>): List<ChartPoint> =
        history.groupBy { it.sessionId }.values.mapNotNull { rows ->
            val completedAt = rows.first().completedAtEpochMs ?: return@mapNotNull null
            ChartPoint(
                label = DisplayFormat.shortDate(completedAt),
                value = rows.maxOf { it.prescribedWeightKg }.toFloat(),
            )
        }

    /** Total lifted weight (kg × reps) per ISO week, oldest week first. */
    private fun weeklyTonnage(sessions: List<SessionWithSetLogs>): List<ChartPoint> {
        val zone = ZoneId.systemDefault()
        return sessions
            .mapNotNull { sessionWithSets ->
                val completedAt = sessionWithSets.session.completedAtEpochMs ?: return@mapNotNull null
                val date = Instant.ofEpochMilli(completedAt).atZone(zone).toLocalDate()
                val weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val tonnage = sessionWithSets.setLogs
                    .filter { it.status == SetStatus.DONE }
                    .sumOf { it.prescribedWeightKg * (it.completedReps ?: 0) }
                weekStart to tonnage
            }
            .groupBy({ it.first }, { it.second })
            .toSortedMap()
            .map { (weekStart, tonnages) ->
                ChartPoint(
                    label = DisplayFormat.dayAndMonth(weekStart.toEpochDay()),
                    value = tonnages.sum().toFloat(),
                )
            }
    }

    companion object {
        private const val KEY_BODYWEIGHT = "bodyweight"
        private const val KEY_TONNAGE = "tonnage"
    }
}
