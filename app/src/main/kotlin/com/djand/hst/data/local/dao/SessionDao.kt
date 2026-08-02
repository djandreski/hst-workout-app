package com.djand.hst.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.djand.hst.data.local.entity.SessionStatus
import com.djand.hst.data.local.entity.WorkoutSessionEntity
import com.djand.hst.data.local.relation.SessionWithSetLogs
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert
    suspend fun insertAll(sessions: List<WorkoutSessionEntity>): List<Long>

    // -------------------------------------------------------------- single session

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    fun observeSessionWithSets(id: Long): Flow<SessionWithSetLogs?>

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun getSessionWithSets(id: Long): SessionWithSetLogs?

    /** The next session to perform in a cycle: the lowest-numbered unfinished one. */
    @Transaction
    @Query(
        "SELECT * FROM workout_sessions WHERE cycleId = :cycleId AND status != :status " +
            "ORDER BY sessionNumber LIMIT 1",
    )
    fun observeNextSessionWithSets(cycleId: Long, status: SessionStatus): Flow<SessionWithSetLogs?>

    // -------------------------------------------------------------- cycle queries

    /** All sessions of a cycle (main cycle and deload), ascending by session number. */
    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE cycleId = :cycleId ORDER BY sessionNumber")
    suspend fun getSessionsWithSetsForCycle(cycleId: Long): List<SessionWithSetLogs>

    @Query("SELECT COUNT(*) FROM workout_sessions WHERE cycleId = :cycleId AND status != :status")
    suspend fun countSessionsNotInStatus(cycleId: Long, status: SessionStatus): Int

    // -------------------------------------------------------------- history

    /** Completed sessions, most recent first (completedAtEpochMs is non-null for these). */
    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE status = :status ORDER BY completedAtEpochMs DESC")
    fun observeSessionsWithSetsByStatus(status: SessionStatus): Flow<List<SessionWithSetLogs>>

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE status = :status ORDER BY completedAtEpochMs DESC LIMIT 1")
    fun observeLastSessionWithSetsByStatus(status: SessionStatus): Flow<SessionWithSetLogs?>

    // -------------------------------------------------------------- lifecycle

    /** Moves a session to IN_PROGRESS, keeping the first start timestamp on re-entry. */
    @Query(
        "UPDATE workout_sessions SET status = :status, " +
            "startedAtEpochMs = COALESCE(startedAtEpochMs, :startedAtEpochMs) WHERE id = :id",
    )
    suspend fun markStarted(id: Long, status: SessionStatus, startedAtEpochMs: Long)

    @Query("UPDATE workout_sessions SET status = :status, completedAtEpochMs = :completedAtEpochMs WHERE id = :id")
    suspend fun markCompleted(id: Long, status: SessionStatus, completedAtEpochMs: Long)

    // -------------------------------------------------------------- backup / reset

    @Query("SELECT * FROM workout_sessions ORDER BY id")
    suspend fun getAll(): List<WorkoutSessionEntity>

    @Query("DELETE FROM workout_sessions")
    suspend fun deleteAll()
}
