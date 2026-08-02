package com.djand.hst.di

import android.content.Context
import androidx.room.Room
import com.djand.hst.data.local.HstDatabase
import com.djand.hst.data.local.dao.BodyweightDao
import com.djand.hst.data.local.dao.CycleDao
import com.djand.hst.data.local.dao.ExerciseDao
import com.djand.hst.data.local.dao.ExerciseProgressionDao
import com.djand.hst.data.local.dao.SessionDao
import com.djand.hst.data.local.dao.SetLogDao
import com.djand.hst.data.local.dao.TemplateDao
import com.djand.hst.data.local.seed.SeedDatabaseCallback
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the Room database and its DAOs. The database is a process-wide
 * singleton; [SeedDatabaseCallback] seeds the static program catalogue
 * (23 exercises + A/B/C templates) on first creation.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        seedCallback: SeedDatabaseCallback,
    ): HstDatabase =
        Room.databaseBuilder(context, HstDatabase::class.java, HstDatabase.NAME)
            .addCallback(seedCallback)
            .build()

    @Provides
    fun provideExerciseDao(db: HstDatabase): ExerciseDao = db.exerciseDao()

    @Provides
    fun provideTemplateDao(db: HstDatabase): TemplateDao = db.templateDao()

    @Provides
    fun provideCycleDao(db: HstDatabase): CycleDao = db.cycleDao()

    @Provides
    fun provideExerciseProgressionDao(db: HstDatabase): ExerciseProgressionDao =
        db.exerciseProgressionDao()

    @Provides
    fun provideSessionDao(db: HstDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideSetLogDao(db: HstDatabase): SetLogDao = db.setLogDao()

    @Provides
    fun provideBodyweightDao(db: HstDatabase): BodyweightDao = db.bodyweightDao()
}
