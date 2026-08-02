package com.djand.hst.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.djand.hst.data.local.entity.BodyweightEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BodyweightDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: BodyweightEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<BodyweightEntryEntity>)

    @Query("SELECT * FROM bodyweight_entries ORDER BY epochDay")
    fun observeAll(): Flow<List<BodyweightEntryEntity>>

    @Query("SELECT * FROM bodyweight_entries ORDER BY epochDay DESC LIMIT 1")
    fun observeLatest(): Flow<BodyweightEntryEntity?>

    @Query("SELECT * FROM bodyweight_entries ORDER BY epochDay")
    suspend fun getAll(): List<BodyweightEntryEntity>

    @Query("DELETE FROM bodyweight_entries WHERE epochDay = :epochDay")
    suspend fun delete(epochDay: Long)

    @Query("DELETE FROM bodyweight_entries")
    suspend fun deleteAll()
}
