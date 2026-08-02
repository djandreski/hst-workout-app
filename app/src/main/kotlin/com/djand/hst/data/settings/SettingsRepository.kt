package com.djand.hst.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.djand.hst.domain.model.Equipment
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
)

/**
 * Wrapper around DataStore Preferences exposing the whole [AppSettings] as a single
 * immutable flow plus small setters. Unknown/invalid persisted values fall back to
 * defaults, so a corrupted preference can never crash the app.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private object Keys {
        val REST_SECONDS = intPreferencesKey("rest_seconds")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val BARBELL_INCREMENT = doublePreferencesKey("barbell_increment_kg")
        val MACHINE_INCREMENT = doublePreferencesKey("machine_increment_kg")
        val DUMBBELL_INCREMENT = doublePreferencesKey("dumbbell_increment_kg")
        val CABLE_INCREMENT = doublePreferencesKey("cable_increment_kg")
        val BODYWEIGHT_INCREMENT = doublePreferencesKey("bodyweight_increment_kg")
        val BAR_WEIGHT = doublePreferencesKey("bar_weight_kg")
        val SETUP_COMPLETE = booleanPreferencesKey("setup_complete")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        val defaults = AppSettings()
        AppSettings(
            restSeconds = prefs[Keys.REST_SECONDS] ?: defaults.restSeconds,
            themeMode = prefs[Keys.THEME_MODE]
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: defaults.themeMode,
            barbellIncrementKg = prefs[Keys.BARBELL_INCREMENT] ?: defaults.barbellIncrementKg,
            machineIncrementKg = prefs[Keys.MACHINE_INCREMENT] ?: defaults.machineIncrementKg,
            dumbbellIncrementKg = prefs[Keys.DUMBBELL_INCREMENT] ?: defaults.dumbbellIncrementKg,
            cableIncrementKg = prefs[Keys.CABLE_INCREMENT] ?: defaults.cableIncrementKg,
            bodyweightIncrementKg = prefs[Keys.BODYWEIGHT_INCREMENT] ?: defaults.bodyweightIncrementKg,
            barWeightKg = prefs[Keys.BAR_WEIGHT] ?: defaults.barWeightKg,
            setupComplete = prefs[Keys.SETUP_COMPLETE] ?: defaults.setupComplete,
        )
    }

    suspend fun setRestSeconds(seconds: Int) {
        require(seconds > 0) { "Rest must be positive, got $seconds" }
        context.settingsDataStore.edit { it[Keys.REST_SECONDS] = seconds }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    /** Persists the increment for [equipment]. Callers must also update the exercise rows. */
    suspend fun setEquipmentIncrement(equipment: Equipment, incrementKg: Double) {
        require(incrementKg > 0.0) { "Increment must be positive, got $incrementKg" }
        context.settingsDataStore.edit {
            it[keyFor(equipment)] = incrementKg
        }
    }

    suspend fun setBarWeight(kg: Double) {
        require(kg >= 0.0) { "Bar weight must be >= 0, got $kg" }
        context.settingsDataStore.edit { it[Keys.BAR_WEIGHT] = kg }
    }

    suspend fun setSetupComplete(complete: Boolean) {
        context.settingsDataStore.edit { it[Keys.SETUP_COMPLETE] = complete }
    }

    /** Overwrites every setting at once (used by backup import). */
    suspend fun restore(settings: AppSettings) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.REST_SECONDS] = settings.restSeconds
            prefs[Keys.THEME_MODE] = settings.themeMode.name
            prefs[Keys.BARBELL_INCREMENT] = settings.barbellIncrementKg
            prefs[Keys.MACHINE_INCREMENT] = settings.machineIncrementKg
            prefs[Keys.DUMBBELL_INCREMENT] = settings.dumbbellIncrementKg
            prefs[Keys.CABLE_INCREMENT] = settings.cableIncrementKg
            prefs[Keys.BODYWEIGHT_INCREMENT] = settings.bodyweightIncrementKg
            prefs[Keys.BAR_WEIGHT] = settings.barWeightKg
            prefs[Keys.SETUP_COMPLETE] = settings.setupComplete
        }
    }

    private fun keyFor(equipment: Equipment) = when (equipment) {
        Equipment.BARBELL -> Keys.BARBELL_INCREMENT
        Equipment.MACHINE -> Keys.MACHINE_INCREMENT
        Equipment.DUMBBELL -> Keys.DUMBBELL_INCREMENT
        Equipment.CABLE -> Keys.CABLE_INCREMENT
        Equipment.BODYWEIGHT -> Keys.BODYWEIGHT_INCREMENT
    }
}
