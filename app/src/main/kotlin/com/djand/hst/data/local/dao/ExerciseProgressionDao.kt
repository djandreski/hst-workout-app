package com.djand.hst.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.djand.hst.data.local.entity.ExerciseProgressionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseProgressionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(progressions: List<ExerciseProgressionEntity>)

    @Query("SELECT * FROM exercise_progressions WHERE cycleId = :cycleId")
    suspend fun getForCycle(cycleId: Long): List<ExerciseProgressionEntity>

    @Query("SELECT * FROM exercise_progressions WHERE cycleId = :cycleId")
    fun observeForCycle(cycleId: Long): Flow<List<ExerciseProgressionEntity>>

    @Query("SELECT * FROM exercise_progressions WHERE cycleId = :cycleId AND exerciseId = :exerciseId")
    suspend fun get(cycleId: Long, exerciseId: String): ExerciseProgressionEntity?

    @Query(
        "UPDATE exercise_progressions SET consecutiveMisses = :misses " +
            "WHERE cycleId = :cycleId AND exerciseId = :exerciseId",
    )
    suspend fun updateConsecutiveMisses(cycleId: Long, exerciseId: String, misses: Int)

    @Query(
        "UPDATE exercise_progressions SET pullUpSuggestAddingWeight = 1 " +
            "WHERE cycleId = :cycleId AND exerciseId = :exerciseId",
    )
    suspend fun setPullUpSuggestion(cycleId: Long, exerciseId: String)

    @Query("SELECT * FROM exercise_progressions")
    suspend fun getAll(): List<ExerciseProgressionEntity>

    @Query("DELETE FROM exercise_progressions")
    suspend fun deleteAll()
}
