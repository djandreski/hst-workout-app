package com.djand.hst.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.djand.hst.data.local.entity.SetLogEntity
import com.djand.hst.data.local.entity.SetStatus
import com.djand.hst.data.local.relation.ExerciseHistoryRow
import kotlinx.coroutines.flow.Flow

@Dao
interface SetLogDao {

    @Insert
    suspend fun insertAll(logs: List<SetLogEntity>)

    @Update
    suspend fun update(log: SetLogEntity)

    @Update
    suspend fun updateAll(logs: List<SetLogEntity>)

    @Query("SELECT * FROM set_logs WHERE id = :id")
    suspend fun getById(id: Long): SetLogEntity?

    @Query("SELECT * FROM set_logs WHERE sessionId = :sessionId ORDER BY exerciseIndex, setIndex")
    suspend fun getForSession(sessionId: Long): List<SetLogEntity>

    /** Moves every set of an exercise in a session from one status to another (skip / unskip). */
    @Query(
        "UPDATE set_logs SET status = :to " +
            "WHERE sessionId = :sessionId AND exerciseId = :exerciseId AND status = :from",
    )
    suspend fun setStatusForExercise(sessionId: Long, exerciseId: String, from: SetStatus, to: SetStatus)

    /** Exercise notes live on the first set row by convention. */
    @Query(
        "UPDATE set_logs SET notes = :notes " +
            "WHERE sessionId = :sessionId AND exerciseId = :exerciseId AND setIndex = 0",
    )
    suspend fun updateNotesForFirstSet(sessionId: Long, exerciseId: String, notes: String?)

    /**
     * Per-exercise history for Statistics: every attempted set of [exerciseId],
     * oldest session first, sets in order.
     */
    @Query(
        "SELECT workout_sessions.id AS sessionId, workout_sessions.cycleId AS cycleId, " +
            "workout_sessions.sessionNumber AS sessionNumber, " +
            "workout_sessions.completedAtEpochMs AS completedAtEpochMs, " +
            "set_logs.exerciseId AS exerciseId, set_logs.setIndex AS setIndex, set_logs.kind AS kind, " +
            "set_logs.prescribedWeightKg AS prescribedWeightKg, " +
            "set_logs.prescribedTargetReps AS prescribedTargetReps, " +
            "set_logs.completedReps AS completedReps " +
            "FROM set_logs JOIN workout_sessions ON workout_sessions.id = set_logs.sessionId " +
            "WHERE set_logs.exerciseId = :exerciseId AND set_logs.status = :status " +
            "ORDER BY workout_sessions.completedAtEpochMs, set_logs.setIndex",
    )
    fun observeExerciseHistory(exerciseId: String, status: SetStatus): Flow<List<ExerciseHistoryRow>>

    // -------------------------------------------------------------- backup / reset

    @Query("SELECT * FROM set_logs ORDER BY id")
    suspend fun getAll(): List<SetLogEntity>

    @Query("DELETE FROM set_logs")
    suspend fun deleteAll()
}
