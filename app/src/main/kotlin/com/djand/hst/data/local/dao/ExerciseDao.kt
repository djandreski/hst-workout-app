package com.djand.hst.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.djand.hst.data.local.entity.ExerciseEntity
import com.djand.hst.domain.model.Equipment
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {

    @Query("SELECT * FROM exercises ORDER BY name")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises")
    suspend fun getAll(): List<ExerciseEntity>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getById(id: String): ExerciseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(exercises: List<ExerciseEntity>)

    /** Applies a changed per-equipment increment to every exercise of that equipment. */
    @Query("UPDATE exercises SET incrementKg = :incrementKg WHERE equipment = :equipment")
    suspend fun updateIncrementForEquipment(equipment: Equipment, incrementKg: Double)

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    @Query("DELETE FROM exercises")
    suspend fun deleteAll()
}
