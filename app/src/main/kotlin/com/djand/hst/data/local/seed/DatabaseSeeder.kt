package com.djand.hst.data.local.seed

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Seeds the static program catalogue (23 exercises + A/B/C templates) into a fresh
 * database. Runs only from [RoomDatabase.Callback.onCreate], so the database is
 * guaranteed empty and no idempotency check is needed.
 *
 * The inserts go through the [SupportSQLiteDatabase] that Room is currently
 * opening, synchronously, on the same thread and connection. Do NOT call suspend
 * DAO functions here: Room holds the connection pool lock while onCreate runs, so
 * re-entering the pool from a coroutine deadlocks the app (the setup screen would
 * show a spinner forever).
 */
@Singleton
class DatabaseSeeder @Inject constructor() {

    fun seedNow(db: SupportSQLiteDatabase) {
        ProgramSeed.exercises.forEach { exercise ->
            db.insert(
                "exercises",
                SQLiteDatabase.CONFLICT_REPLACE,
                ContentValues().apply {
                    put("id", exercise.id)
                    put("name", exercise.name)
                    // Room stores enums as their name and booleans as 0/1.
                    put("equipment", exercise.equipment.name)
                    put("incrementKg", exercise.incrementKg)
                    put("isCompound", if (exercise.isCompound) 1 else 0)
                },
            )
        }
        ProgramSeed.templates.forEach { template ->
            db.insert(
                "workout_templates",
                SQLiteDatabase.CONFLICT_REPLACE,
                ContentValues().apply {
                    put("letter", template.letter.name)
                },
            )
        }
        ProgramSeed.templateExercises.forEach { line ->
            db.insert(
                "template_exercises",
                SQLiteDatabase.CONFLICT_REPLACE,
                ContentValues().apply {
                    put("templateLetter", line.templateLetter.name)
                    put("orderIndex", line.orderIndex)
                    put("exerciseId", line.exerciseId)
                    put("sets", line.sets)
                },
            )
        }
    }
}

/**
 * Room callback that runs the seeder on database creation. Wired into the
 * database builder by the Hilt module; the [Provider] keeps the callback
 * constructible without the database instance.
 */
class SeedDatabaseCallback @Inject constructor(
    private val seeder: Provider<DatabaseSeeder>,
) : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        seeder.get().seedNow(db)
    }
}
