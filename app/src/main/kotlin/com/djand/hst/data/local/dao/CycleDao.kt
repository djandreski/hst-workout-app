package com.djand.hst.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.djand.hst.data.local.entity.CycleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CycleDao {

    @Insert
    suspend fun insert(cycle: CycleEntity): Long

    @Insert
    suspend fun insertAll(cycles: List<CycleEntity>)

    /** The unfinished cycle, if any (at most one can exist by construction). */
    @Query("SELECT * FROM cycles WHERE completedAtEpochMs IS NULL ORDER BY cycleNumber DESC LIMIT 1")
    fun observeActive(): Flow<CycleEntity?>

    @Query("SELECT * FROM cycles WHERE completedAtEpochMs IS NULL ORDER BY cycleNumber DESC LIMIT 1")
    suspend fun getActive(): CycleEntity?

    /** The most recent cycle, finished or not. */
    @Query("SELECT * FROM cycles ORDER BY cycleNumber DESC LIMIT 1")
    suspend fun getLatest(): CycleEntity?

    @Query("SELECT * FROM cycles WHERE id = :id")
    suspend fun getById(id: Long): CycleEntity?

    @Query("UPDATE cycles SET completedAtEpochMs = :completedAtEpochMs WHERE id = :id")
    suspend fun markCompleted(id: Long, completedAtEpochMs: Long)

    @Query("SELECT * FROM cycles ORDER BY cycleNumber")
    suspend fun getAll(): List<CycleEntity>

    @Query("DELETE FROM cycles")
    suspend fun deleteAll()
}
