package com.djand.hst.ui.format

import com.djand.hst.domain.model.Equipment
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Display formatting for weights, dates and durations. Centralised so every screen
 * renders the same numbers the same way (metric only).
 */
object DisplayFormat {

    /** "70 kg", "72.5 kg" — trailing zeros stripped. */
    fun weight(kg: Double): String = "${trimZeros(kg)} kg"

    /** Load shown on an exercise card; bodyweight exercises show their added weight. */
    fun exerciseLoad(equipment: Equipment, kg: Double): String =
        if (equipment == Equipment.BODYWEIGHT) {
            if (kg <= 0.0) "Bodyweight" else "+${trimZeros(kg)} kg"
        } else {
            weight(kg)
        }

    /** "2 × 15" (sets x reps). */
    fun setsAndReps(sets: Int, reps: Int): String = "$sets × $reps"

    /** Short date for history rows: "Sat, 2 Aug". */
    fun shortDate(epochMs: Long): String =
        Instant.ofEpochMilli(epochMs)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(SHORT_DATE)

    /** ISO week label used as an x-axis tick for volume charts: "2 Aug". */
    fun dayAndMonth(epochDay: Long): String =
        LocalDate.ofEpochDay(epochDay).format(DAY_MONTH)

    /** "1 h 5 min" / "42 min" between two timestamps; null when not computable. */
    fun durationMinutes(startEpochMs: Long?, endEpochMs: Long?): String? {
        if (startEpochMs == null || endEpochMs == null || endEpochMs <= startEpochMs) return null
        val minutes = ((endEpochMs - startEpochMs) / 60_000.0).roundToLong()
        val hours = minutes / 60
        val rest = minutes % 60
        return if (hours > 0) "$hours h $rest min" else "$rest min"
    }

    /** "80 kg × 10" — a weight/reps pair for previous-performance summaries. */
    fun weightTimesReps(kg: Double, reps: Int): String = "${trimZeros(kg)} kg × $reps"

    private fun trimZeros(value: Double): String =
        if (abs(value - value.roundToLong()) < 1e-9) {
            value.roundToLong().toString()
        } else {
            String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
        }

    private val SHORT_DATE: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())

    private val DAY_MONTH: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
}
