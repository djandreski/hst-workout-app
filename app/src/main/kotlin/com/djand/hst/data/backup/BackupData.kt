package com.djand.hst.data.backup

import com.djand.hst.data.local.entity.BodyweightEntryEntity
import com.djand.hst.data.local.entity.CycleEntity
import com.djand.hst.data.local.entity.ExerciseEntity
import com.djand.hst.data.local.entity.ExerciseProgressionEntity
import com.djand.hst.data.local.entity.SetLogEntity
import com.djand.hst.data.local.entity.TemplateExerciseEntity
import com.djand.hst.data.local.entity.WorkoutSessionEntity
import com.djand.hst.data.local.entity.WorkoutTemplateEntity
import com.djand.hst.data.settings.AppSettings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Full-database JSON backup. Entities double as the wire format (stable ids and
 * enum names), so a backup restores byte-for-byte identical rows including
 * auto-generated ids, which keeps foreign keys intact.
 *
 * [version] guards the format: only [CURRENT_VERSION] backups are accepted.
 */
@Serializable
data class BackupData(
    val version: Int = CURRENT_VERSION,
    val exportedAtEpochMs: Long,
    val exercises: List<ExerciseEntity>,
    val workoutTemplates: List<WorkoutTemplateEntity>,
    val templateExercises: List<TemplateExerciseEntity>,
    val cycles: List<CycleEntity>,
    val exerciseProgressions: List<ExerciseProgressionEntity>,
    val workoutSessions: List<WorkoutSessionEntity>,
    val setLogs: List<SetLogEntity>,
    val bodyweightEntries: List<BodyweightEntryEntity>,
    val settings: AppSettings? = null,
) {
    companion object {
        const val CURRENT_VERSION = 1

        val json: Json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
