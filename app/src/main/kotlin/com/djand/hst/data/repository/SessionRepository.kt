package com.djand.hst.data.repository

import com.djand.hst.data.local.dao.CycleDao
import com.djand.hst.data.local.dao.SessionDao
import com.djand.hst.data.local.dao.SetLogDao
import com.djand.hst.data.local.entity.SessionStatus
import com.djand.hst.data.local.entity.SetStatus
import com.djand.hst.data.local.relation.SessionWithSetLogs
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * Drives the workout screen: the upcoming session, set logging, skipping and
 * notes. Session completion is delegated to [CycleRepository], which applies the
 * progression rules transactionally.
 */
@Singleton
class SessionRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val setLogDao: SetLogDao,
    private val cycleDao: CycleDao,
    private val cycleRepository: CycleRepository,
) {

    /**
     * The next session to perform in the active cycle (PLANNED or IN_PROGRESS),
     * with its set logs. Null when no cycle is active or the active cycle is done.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val upcomingSession: Flow<SessionWithSetLogs?> =
        cycleDao.observeActive().flatMapLatest { cycle ->
            if (cycle == null) {
                flowOf(null)
            } else {
                sessionDao.observeNextSessionWithSets(cycle.id, SessionStatus.COMPLETED)
            }
        }

    fun observeSession(sessionId: Long): Flow<SessionWithSetLogs?> =
        sessionDao.observeSessionWithSets(sessionId)

    /** Marks a session IN_PROGRESS; the first start timestamp is kept on re-entry. */
    suspend fun startSession(sessionId: Long, now: Long = System.currentTimeMillis()) {
        sessionDao.markStarted(sessionId, SessionStatus.IN_PROGRESS, now)
    }

    /** Records [reps] completed reps for a set. */
    suspend fun logSet(setLogId: Long, reps: Int) {
        require(reps >= 0) { "Reps must be >= 0, got $reps" }
        val log = setLogDao.getById(setLogId)
            ?: throw IllegalArgumentException("SetLog $setLogId does not exist")
        setLogDao.update(log.copy(status = SetStatus.DONE, completedReps = reps))
    }

    /** Un-checks a set (mis-tap), returning it to PENDING. */
    suspend fun unlogSet(setLogId: Long) {
        val log = setLogDao.getById(setLogId)
            ?: throw IllegalArgumentException("SetLog $setLogId does not exist")
        setLogDao.update(log.copy(status = SetStatus.PENDING, completedReps = null))
    }

    /** Skips an exercise: all its not-yet-logged sets become SKIPPED (no progression impact). */
    suspend fun skipExercise(sessionId: Long, exerciseId: String) {
        setLogDao.setStatusForExercise(sessionId, exerciseId, from = SetStatus.PENDING, to = SetStatus.SKIPPED)
    }

    /** Reverts a skip: SKIPPED sets become PENDING again. */
    suspend fun unskipExercise(sessionId: Long, exerciseId: String) {
        setLogDao.setStatusForExercise(sessionId, exerciseId, from = SetStatus.SKIPPED, to = SetStatus.PENDING)
    }

    /** Stores exercise-level notes (on the exercise's first set row by convention). */
    suspend fun updateExerciseNotes(sessionId: Long, exerciseId: String, notes: String?) {
        setLogDao.updateNotesForFirstSet(sessionId, exerciseId, notes?.trim()?.ifBlank { null })
    }

    /**
     * Completes the session and applies the progression rules. Requires every set
     * to be logged or skipped first — see [CycleRepository.completeSession].
     */
    suspend fun completeSession(sessionId: Long) = cycleRepository.completeSession(sessionId)
}
