package com.djand.hst.data.repository

import com.djand.hst.data.local.dao.SessionDao
import com.djand.hst.data.local.dao.SetLogDao
import com.djand.hst.data.local.entity.SessionStatus
import com.djand.hst.data.local.entity.SetStatus
import com.djand.hst.data.local.relation.ExerciseHistoryRow
import com.djand.hst.data.local.relation.SessionWithSetLogs
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Read-only access to completed sessions and per-exercise set history. */
@Singleton
class HistoryRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val setLogDao: SetLogDao,
) {

    /** Completed sessions, most recent first, each with its set logs. */
    val completedSessions: Flow<List<SessionWithSetLogs>> =
        sessionDao.observeSessionsWithSetsByStatus(SessionStatus.COMPLETED)

    /** The most recently completed session ("Last workout" on Home). */
    val lastCompletedSession: Flow<SessionWithSetLogs?> =
        sessionDao.observeLastSessionWithSetsByStatus(SessionStatus.COMPLETED)

    /** Every attempted set of [exerciseId], oldest session first. */
    fun exerciseHistory(exerciseId: String): Flow<List<ExerciseHistoryRow>> =
        setLogDao.observeExerciseHistory(exerciseId, SetStatus.DONE)
}
