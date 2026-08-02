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
 * sessions, and writes the resulting rewrites (repeat / -10% reset / isolation
 * bump) back to the future PENDING set rows. Because prescriptions only ever live
 * in the database, progression is deterministic across process restarts.
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

    /** The unfinished cycle, if any. */
    val activeCycle: Flow<CycleEntity?> = cycleDao.observeActive()

    /** Progression state (miss counters, pull-up suggestion) of the active cycle. */
    fun observeProgressions(cycleId: Long): Flow<List<ExerciseProgressionEntity>> =
        progressionDao.observeForCycle(cycleId)

    // ------------------------------------------------------------- cycle start

    /**
     * Generates and persists a full new cycle from [inputs] (working weight x reps
     * per exercise): the cycle row, the per-exercise progression snapshots, and all
     * 24 sessions with their prescribed PENDING set rows. Marks setup complete.
     *
     * Deload sessions are NOT created here; they are generated from the final plan
     * state when session 24 completes.
     *
     * @return the id of the new cycle.
     */
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

    /**
     * Computes the setup inputs of the next cycle from what was actually achieved
     * in the most recent cycle (engine `nextCycleInputs`). Increments are taken
     * from the current exercise catalogue, so Settings changes made during the
     * finished cycle are picked up.
     */
    suspend fun computeNextCycleInputs(): List<ExerciseInput> {
        val cycle = cycleDao.getLatest() ?: error("No cycle exists yet")
        val sessions = sessionDao.getSessionsWithSetsForCycle(cycle.id)
        val progressions = progressionDao.getForCycle(cycle.id)
        val exercises = exerciseDao.getAll().associateBy { it.id }
        val plan = reconstructPlan(cycle.cycleNumber, sessions, progressions, exercises)
        val resultsBySession = sessions
            .filter { it.session.status == SessionStatus.COMPLETED && !it.session.isDeload }
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

    // ------------------------------------------------------------- session end

    /**
     * Completes a session whose sets have all been logged or skipped. For a main
     * cycle session this evaluates the results with the engine, persists the new
     * miss counters, rewrites future PENDING set rows, sets the pull-up suggestion
     * flag when earned, and generates the deload week after session 24. For a
     * deload session it only marks progress (no failure, no progression); once the
     * last deload session completes, the cycle is closed.
     *
     * The whole operation is a single database transaction.
     */
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

            if (sessionWithSets.session.isDeload) {
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
            val evaluation = engine.evaluateSession(plan, sessionWithSets.session.sessionNumber, results, misses)

            // Persist the new miss counters.
            for (outcome in evaluation.outcomes) {
                progressionDao.updateConsecutiveMisses(cycleId, outcome.exerciseId, outcome.consecutiveMisses)
            }

            // Rewrite the prescriptions of all future sessions (repeat / reset / bump).
            syncFutureSetLogs(sessions, evaluation.plan, afterSessionNumber = sessionWithSets.session.sessionNumber)

            // Pull-up suggestion: "Start adding weight" at 3x8 bodyweight reps.
            val completedPrescription = plan.sessions.first {
                it.sessionNumber == sessionWithSets.session.sessionNumber
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

            // Last session of the main cycle: generate the deload week from the
            // final plan state (weights x 0.85, sets halved, no failure).
            if (sessionWithSets.session.sessionNumber == ProgressionEngine.SESSIONS_PER_CYCLE) {
                insertPlanSessions(cycleId, engine.generateDeload(evaluation.plan))
            }
        }
    }

    // ------------------------------------------------------------- reset

    /**
     * "Reset cycle" from Settings: wipes every cycle with its sessions, set logs
     * and progression state, and marks setup incomplete so the wizard runs again.
     * The exercise/template catalogue is untouched.
     */
    suspend fun resetAllProgress() {
        db.withTransaction {
            setLogDao.deleteAll()
            sessionDao.deleteAll()
            progressionDao.deleteAll()
            cycleDao.deleteAll()
        }
        settingsRepository.setSetupComplete(false)
    }

    // ------------------------------------------------------------- plan <-> rows

    /** Rebuilds the engine's immutable plan from the persisted session/set rows. */
    private fun reconstructPlan(
        cycleNumber: Int,
        sessions: List<SessionWithSetLogs>,
        progressions: List<ExerciseProgressionEntity>,
        exercises: Map<String, ExerciseEntity>,
    ): CyclePlan {
        val progressionByExercise = progressions.associateBy { it.exerciseId }
        return CyclePlan(
            cycleNumber = cycleNumber,
            sessions = sessions.map { sessionWithSets ->
                val session = sessionWithSets.session
                SessionPrescription(
                    sessionNumber = session.sessionNumber,
                    week = session.week,
                    block = session.block,
                    workout = session.workoutLetter,
                    isDeload = session.isDeload,
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

    /** Inserts planned sessions and their prescribed PENDING set rows. */
    private suspend fun insertPlanSessions(cycleId: Long, sessions: List<SessionPrescription>) {
        val sessionIds = sessionDao.insertAll(
            sessions.map { session ->
                WorkoutSessionEntity(
                    cycleId = cycleId,
                    sessionNumber = session.sessionNumber,
                    week = session.week,
                    block = session.block,
                    workoutLetter = session.workout,
                    isDeload = session.isDeload,
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

    /**
     * Copies the prescriptions of [updatedPlan] for sessions after
     * [afterSessionNumber] onto the persisted PENDING set rows. Set structure never
     * changes during rewrites (weights and rep targets only), so rows and
     * prescriptions match one-to-one.
     */
    private suspend fun syncFutureSetLogs(
        persistedSessions: List<SessionWithSetLogs>,
        updatedPlan: CyclePlan,
        afterSessionNumber: Int,
    ) {
        val planBySessionNumber = updatedPlan.sessions.associateBy { it.sessionNumber }
        val updates = ArrayList<SetLogEntity>()
        for (sessionWithSets in persistedSessions) {
            val sessionNumber = sessionWithSets.session.sessionNumber
            if (sessionNumber <= afterSessionNumber) continue
            val prescription = planBySessionNumber[sessionNumber] ?: continue
            val prescriptionByExercise = prescription.exercises.associateBy { it.exerciseId }
            for (log in sessionWithSets.setLogs) {
                if (log.status != SetStatus.PENDING) continue
                val set = prescriptionByExercise[log.exerciseId]?.sets?.getOrNull(log.setIndex) ?: continue
                val updated = log.copy(
                    kind = set.kind,
                    prescribedWeightKg = set.weightKg,
                    prescribedTargetReps = set.targetReps,
                    prescribedMinReps = set.minReps,
                )
                if (updated != log) updates += updated
            }
        }
        if (updates.isNotEmpty()) setLogDao.updateAll(updates)
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
