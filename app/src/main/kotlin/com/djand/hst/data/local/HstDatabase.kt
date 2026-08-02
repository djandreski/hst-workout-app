package com.djand.hst.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.djand.hst.data.local.dao.BodyweightDao
import com.djand.hst.data.local.dao.CycleDao
import com.djand.hst.data.local.dao.ExerciseDao
import com.djand.hst.data.local.dao.ExerciseProgressionDao
import com.djand.hst.data.local.dao.SessionDao
import com.djand.hst.data.local.dao.SetLogDao
import com.djand.hst.data.local.dao.TemplateDao
import com.djand.hst.data.local.entity.BodyweightEntryEntity
import com.djand.hst.data.local.entity.CycleEntity
import com.djand.hst.data.local.entity.ExerciseEntity
import com.djand.hst.data.local.entity.ExerciseProgressionEntity
import com.djand.hst.data.local.entity.SetLogEntity
import com.djand.hst.data.local.entity.TemplateExerciseEntity
import com.djand.hst.data.local.entity.WorkoutSessionEntity
import com.djand.hst.data.local.entity.WorkoutTemplateEntity

/**
 * Offline Room database for the HST tracker. Schema shape:
 *
 * - Catalogue (seeded once): `exercises`, `workout_templates`, `template_exercises`.
 * - Progress: `cycles` -> `workout_sessions` -> `set_logs`, plus
 *   `exercise_progressions` (per-cycle per-exercise engine state).
 * - Optional log: `bodyweight_entries`.
 *
 * Enums (Equipment, WorkoutLetter, SetKind, SessionStatus, SetStatus) are stored
 * as their names via Room's built-in enum support.
 */
@Database(
    entities = [
        ExerciseEntity::class,
        WorkoutTemplateEntity::class,
        TemplateExerciseEntity::class,
        CycleEntity::class,
        ExerciseProgressionEntity::class,
        WorkoutSessionEntity::class,
        SetLogEntity::class,
        BodyweightEntryEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class HstDatabase : RoomDatabase() {

    abstract fun exerciseDao(): ExerciseDao
    abstract fun templateDao(): TemplateDao
    abstract fun cycleDao(): CycleDao
    abstract fun exerciseProgressionDao(): ExerciseProgressionDao
    abstract fun sessionDao(): SessionDao
    abstract fun setLogDao(): SetLogDao
    abstract fun bodyweightDao(): BodyweightDao

    companion object {
        const val NAME = "hst.db"
    }
}
