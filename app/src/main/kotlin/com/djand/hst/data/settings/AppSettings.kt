package com.djand.hst.data.settings

import com.djand.hst.domain.model.Equipment
import kotlinx.serialization.Serializable

/** Theme preference; the app defaults to following the system. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * All user-configurable settings (metric only, fully offline). Persisted in
 * DataStore Preferences; also embedded in JSON backups.
 *
 * The per-equipment increments mirror [Equipment.defaultIncrementKg] initially.
 * They are the values shown in Settings; the effective increment used for cycle
 * generation lives on the exercise rows and is updated together with these.
 */
@Serializable
data class AppSettings(
    val restSeconds: Int = DEFAULT_REST_SECONDS,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val barbellIncrementKg: Double = Equipment.BARBELL.defaultIncrementKg,
    val machineIncrementKg: Double = Equipment.MACHINE.defaultIncrementKg,
    val dumbbellIncrementKg: Double = Equipment.DUMBBELL.defaultIncrementKg,
    val cableIncrementKg: Double = Equipment.CABLE.defaultIncrementKg,
    val bodyweightIncrementKg: Double = Equipment.BODYWEIGHT.defaultIncrementKg,
    val barWeightKg: Double = DEFAULT_BAR_WEIGHT_KG,
    val setupComplete: Boolean = false,
) {
    /** The configured increment for [equipment]. */
    fun incrementFor(equipment: Equipment): Double = when (equipment) {
        Equipment.BARBELL -> barbellIncrementKg
        Equipment.MACHINE -> machineIncrementKg
        Equipment.DUMBBELL -> dumbbellIncrementKg
        Equipment.CABLE -> cableIncrementKg
        Equipment.BODYWEIGHT -> bodyweightIncrementKg
    }

    companion object {
        const val DEFAULT_REST_SECONDS = 90
        const val DEFAULT_BAR_WEIGHT_KG = 20.0
    }
}
