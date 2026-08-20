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
    version = 2,
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

        val MIGRATION_1_2 = androidx.room.migration.Migration(1, 2) { db ->
            db.execSQL(
                "ALTER TABLE workout_sessions ADD COLUMN phase INTEGER NOT NULL DEFAULT 0",
            )
            db.execSQL(
                "UPDATE workout_sessions SET phase = block WHERE isDeload = 0",
            )
            db.execSQL(
                "UPDATE workout_sessions SET phase = (sessionNumber - 1) / 6 + 1 WHERE isDeload != 0 AND sessionNumber <= 24",
            )
        }
    }
}
