package com.djand.hst.data.local.seed

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.djand.hst.data.local.dao.ExerciseDao
import com.djand.hst.data.local.dao.TemplateDao
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

/**
 * Seeds the static program catalogue (23 exercises + A/B/C templates) into a fresh
 * database. Idempotent: does nothing if exercises already exist.
 */
@Singleton
class DatabaseSeeder @Inject constructor(
    private val exerciseDao: ExerciseDao,
    private val templateDao: TemplateDao,
) {
    suspend fun seedIfEmpty() {
        if (exerciseDao.count() > 0) return
        exerciseDao.upsertAll(ProgramSeed.exercises)
        templateDao.upsertTemplates(ProgramSeed.templates)
        templateDao.upsertTemplateExercises(ProgramSeed.templateExercises)
    }
}

/**
 * Room callback that runs the seeder on database creation. Wired into the
 * database builder by the Hilt module (Phase 4); the [Provider] breaks the
 * construction cycle database -> DAOs -> seeder -> database.
 */
class SeedDatabaseCallback @Inject constructor(
    private val seeder: Provider<DatabaseSeeder>,
) : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        runBlocking { seeder.get().seedIfEmpty() }
    }
}
