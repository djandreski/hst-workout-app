package com.djand.hst.data.repository

import androidx.room.withTransaction
import com.djand.hst.data.local.HstDatabase
import com.djand.hst.data.local.dao.CycleDao
import com.djand.hst.data.local.dao.ExerciseDao
import com.djand.hst.data.local.dao.ExerciseProgressionDao
import com.djand.hst.data.local.dao.SessionDao
import com.djand.hst.data.local.dao.SetLogDao
import com.djand.hst.data.local.entity.CycleEntity
import com.djand.hst.data.local.entity.ExerciseEntity
import com.djand.hst.data.local.entity.ExerciseProgressionEntity
import com.djand.hst.data.local.entity.SessionStatus
import com.djand.hst.data.local.entity.SetLogEntity
import com.djand.hst.data.local.entity.SetStatus
import com.djand.hst.data.local.entity.WorkoutSessionEntity
import com.djand.hst.data.local.relation.SessionWithSetLogs
import com.djand.hst.data.settings.SettingsRepository
import com.djand.hst.domain.model.CyclePlan
import com.djand.hst.domain.model.ExerciseInput
import com.djand.hst.domain.model.ExercisePrescription
import com.djand.hst.domain.model.ExerciseResult
import com.djand.hst.domain.model.SessionPrescription
import com.djand.hst.domain.model.SetPrescription
import com.djand.hst.domain.progression.ProgressionEngine
import com.djand.hst.domain.progression.RmMath
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Owns the cycle lifecycle and is the single bridge between the pure
 * [ProgressionEngine] and the database.
 *
 * The engine works on an immutable [CyclePlan]; the database stores that plan
 * materialised as `workout_sessions` + `set_logs` rows. This repository
 * reconstructs the plan from those rows, lets the engine evaluate completed
 * sessions, and persists the outcomes. The plan itself is never rewritten
 * mid-cycle — the HST spreadsheet schedule is always followed as prescribed.
 */
@Singleton
class CycleRepository @Inject constructor(
    private val db: HstDatabase,
    private val engine: ProgressionEngine,
    private val cycleDao: CycleDao,
    private val progressionDao: ExerciseProgressionDao,
    private val sessionDao: SessionDao,
    private val setLogDao: SetLogDao,
    private val exerciseDao: ExerciseDao,
    private val templateRepository: TemplateRepository,
    private val settingsRepository: SettingsRepository,
) {

    val activeCycle: Flow<CycleEntity?> = cycleDao.observeActive()

    fun observeProgressions(cycleId: Long): Flow<List<ExerciseProgressionEntity>> =
        progressionDao.observeForCycle(cycleId)

    suspend fun startNewCycle(
        inputs: List<ExerciseInput>,
        now: Long = System.currentTimeMillis(),
    ): Long {
        val templates = templateRepository.getDomainTemplates()
        val cycleId = db.withTransaction {
            check(cycleDao.getActive() == null) { "Cannot start a new cycle: another cycle is still unfinished" }
            val cycleNumber = (cycleDao.getLatest()?.cycleNumber ?: 0) + 1
            val plan = engine.generateCycle(cycleNumber, inputs, templates)
            val id = cycleDao.insert(CycleEntity(cycleNumber = cycleNumber, startedAtEpochMs = now))
            progressionDao.upsertAll(inputs.map { it.toProgressionEntity(id) })
            insertPlanSessions(id, plan.sessions)
            id
        }
        settingsRepository.setSetupComplete(true)
        return cycleId
    }

    suspend fun computeNextCycleInputs(): List<ExerciseInput> {
        val cycle = cycleDao.getLatest() ?: error("No cycle exists yet")
        val sessions = sessionDao.getSessionsWithSetsForCycle(cycle.id)
        val progressions = progressionDao.getForCycle(cycle.id)
        val exercises = exerciseDao.getAll().associateBy { it.id }
        val plan = reconstructPlan(cycle.cycleNumber, sessions, progressions, exercises)
        val resultsBySession = sessions
            .filter { it.session.status == SessionStatus.COMPLETED && it.session.sessionNumber <= ProgressionEngine.SESSIONS_PER_CYCLE }
            .associate { it.session.sessionNumber to toExerciseResults(it.setLogs) }
        val inputs = progressions.map { p ->
            val exercise = exercises.getValue(p.exerciseId)
            ExerciseInput(
                exerciseId = p.exerciseId,
                equipment = exercise.equipment,
                incrementKg = exercise.incrementKg,
                isCompound = exercise.isCompound,
                weightKg = p.baseWeightKg,
                reps = p.baseReps,
            )
        }
        return engine.nextCycleInputs(inputs, plan, resultsBySession)
    }

    suspend fun completeSession(sessionId: Long, now: Long = System.currentTimeMillis()) {
        db.withTransaction {
            val sessionWithSets = sessionDao.getSessionWithSets(sessionId)
                ?: throw IllegalArgumentException("Session $sessionId does not exist")
            require(sessionWithSets.session.status != SessionStatus.COMPLETED) {
                "Session $sessionId is already completed"
            }
            require(
                sessionWithSets.setLogs.isNotEmpty() &&
                    sessionWithSets.setLogs.all { it.status != SetStatus.PENDING },
            ) { "Session $sessionId still has unresolved (PENDING) sets" }

            sessionDao.markCompleted(sessionId, SessionStatus.COMPLETED, now)
            val cycleId = sessionWithSets.session.cycleId
            val sessionNumber = sessionWithSets.session.sessionNumber

            if (sessionNumber > ProgressionEngine.SESSIONS_PER_CYCLE) {
                if (sessionDao.countSessionsNotInStatus(cycleId, SessionStatus.COMPLETED) == 0) {
                    cycleDao.markCompleted(cycleId, now)
                }
                return@withTransaction
            }

            val cycle = cycleDao.getById(cycleId) ?: error("Session $sessionId has no cycle")
            val sessions = sessionDao.getSessionsWithSetsForCycle(cycleId)
            val progressions = progressionDao.getForCycle(cycleId)
            val exercises = exerciseDao.getAll().associateBy { it.id }
            val plan = reconstructPlan(cycle.cycleNumber, sessions, progressions, exercises)

            val results = toExerciseResults(sessionWithSets.setLogs)
            val misses = progressions.associate { it.exerciseId to it.consecutiveMisses }
            val evaluation = engine.evaluateSession(plan, sessionNumber, results, misses)

            for (outcome in evaluation.outcomes) {
                progressionDao.updateConsecutiveMisses(cycleId, outcome.exerciseId, outcome.consecutiveMisses)
            }

            val completedPrescription = plan.sessions.first {
                it.sessionNumber == sessionNumber
            }
            for (result in results) {
                if (result.skipped) continue
                val exercise = exercises[result.exerciseId] ?: continue
                val prescribedWeight = completedPrescription.exercises
                    .firstOrNull { it.exerciseId == result.exerciseId }
                    ?.sets?.firstOrNull()?.weightKg ?: continue
                if (engine.shouldSuggestAddingWeight(exercise.equipment, prescribedWeight, result.completedReps)) {
                    progressionDao.setPullUpSuggestion(cycleId, result.exerciseId)
                }
            }

            if (sessionNumber == ProgressionEngine.SESSIONS_PER_CYCLE) {
                cycleDao.markCompleted(cycleId, now)
            }
        }
    }

    suspend fun resetAllProgress() {
        db.withTransaction {
            setLogDao.deleteAll()
            sessionDao.deleteAll()
            progressionDao.deleteAll()
            cycleDao.deleteAll()
        }
        settingsRepository.setSetupComplete(false)
    }

    private fun reconstructPlan(
        cycleNumber: Int,
        sessions: List<SessionWithSetLogs>,
        progressions: List<ExerciseProgressionEntity>,
        exercises: Map<String, ExerciseEntity>,
    ): CyclePlan {
        val progressionByExercise = progressions.associateBy { it.exerciseId }
        val mainSessions = sessions
            .filter { it.session.sessionNumber <= ProgressionEngine.SESSIONS_PER_CYCLE }
        return CyclePlan(
            cycleNumber = cycleNumber,
            sessions = mainSessions.map { sessionWithSets ->
                val session = sessionWithSets.session
                SessionPrescription(
                    sessionNumber = session.sessionNumber,
                    phase = session.phase,
                    workout = session.workoutLetter,
                    exercises = sessionWithSets.setLogs
                        .groupBy { it.exerciseId }
                        .entries
                        .sortedBy { it.value.first().exerciseIndex }
                        .map { (exerciseId, logs) ->
                            ExercisePrescription(
                                exerciseId = exerciseId,
                                incrementKg = progressionByExercise.getValue(exerciseId).incrementKg,
                                isCompound = exercises.getValue(exerciseId).isCompound,
                                sets = logs.sortedBy { it.setIndex }.map { log ->
                                    SetPrescription(
                                        weightKg = log.prescribedWeightKg,
                                        targetReps = log.prescribedTargetReps,
                                        minReps = log.prescribedMinReps,
                                        kind = log.kind,
                                    )
                                },
                            )
                        },
                )
            },
        )
    }

    private suspend fun insertPlanSessions(cycleId: Long, sessions: List<SessionPrescription>) {
        val sessionIds = sessionDao.insertAll(
            sessions.map { session ->
                WorkoutSessionEntity(
                    cycleId = cycleId,
                    sessionNumber = session.sessionNumber,
                    phase = session.phase,
                    workoutLetter = session.workout,
                )
            },
        )
        val logs = ArrayList<SetLogEntity>()
        sessions.forEachIndexed { index, session ->
            session.exercises.forEachIndexed { exerciseIndex, exercise ->
                exercise.sets.forEachIndexed { setIndex, set ->
                    logs += SetLogEntity(
                        sessionId = sessionIds[index],
                        exerciseId = exercise.exerciseId,
                        exerciseIndex = exerciseIndex,
                        setIndex = setIndex,
                        kind = set.kind,
                        prescribedWeightKg = set.weightKg,
                        prescribedTargetReps = set.targetReps,
                        prescribedMinReps = set.minReps,
                    )
                }
            }
        }
        setLogDao.insertAll(logs)
    }

    private fun toExerciseResults(setLogs: List<SetLogEntity>): List<ExerciseResult> =
        setLogs
            .groupBy { it.exerciseId }
            .map { (exerciseId, logs) ->
                val sorted = logs.sortedBy { it.setIndex }
                ExerciseResult(
                    exerciseId = exerciseId,
                    completedReps = sorted.map { log ->
                        if (log.status == SetStatus.DONE) log.completedReps ?: 0 else 0
                    },
                    skipped = sorted.all { it.status == SetStatus.SKIPPED },
                )
            }

    private fun ExerciseInput.toProgressionEntity(cycleId: Long): ExerciseProgressionEntity {
        val oneRm = RmMath.oneRepMax(weightKg, reps)
        return ExerciseProgressionEntity(
            cycleId = cycleId,
            exerciseId = exerciseId,
            baseWeightKg = weightKg,
            baseReps = reps,
            rm15Kg = RmMath.repMax(oneRm, 15),
            rm10Kg = RmMath.repMax(oneRm, 10),
            rm5Kg = RmMath.repMax(oneRm, 5),
            incrementKg = incrementKg,
        )
    }
}
