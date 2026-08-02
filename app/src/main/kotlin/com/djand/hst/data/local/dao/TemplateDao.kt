package com.djand.hst.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.djand.hst.data.local.entity.TemplateExerciseEntity
import com.djand.hst.data.local.entity.WorkoutTemplateEntity
import com.djand.hst.data.local.relation.TemplateWithExercises
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {

    /** Templates are unordered; sort exercises by orderIndex after loading. */
    @Transaction
    @Query("SELECT * FROM workout_templates")
    fun observeTemplatesWithExercises(): Flow<List<TemplateWithExercises>>

    @Transaction
    @Query("SELECT * FROM workout_templates")
    suspend fun getTemplatesWithExercises(): List<TemplateWithExercises>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTemplates(templates: List<WorkoutTemplateEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTemplateExercises(exercises: List<TemplateExerciseEntity>)

    @Query("SELECT * FROM workout_templates")
    suspend fun getAllTemplates(): List<WorkoutTemplateEntity>

    @Query("SELECT * FROM template_exercises")
    suspend fun getAllTemplateExercises(): List<TemplateExerciseEntity>

    @Query("SELECT COUNT(*) FROM template_exercises")
    suspend fun templateExerciseCount(): Int

    @Query("DELETE FROM template_exercises")
    suspend fun deleteAllTemplateExercises()

    @Query("DELETE FROM workout_templates")
    suspend fun deleteAllTemplates()
}
