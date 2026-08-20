package com.djand.hst.domain.progression

import com.djand.hst.domain.model.CyclePlan
import com.djand.hst.domain.model.Equipment
import com.djand.hst.domain.model.ExerciseInput
import com.djand.hst.domain.model.ExercisePrescription
import com.djand.hst.domain.model.ExerciseResult
import com.djand.hst.domain.model.ProgressionEvent
import com.djand.hst.domain.model.ProgressionOutcome
import com.djand.hst.domain.model.SessionEvaluation
import com.djand.hst.domain.model.SessionPrescription
import com.djand.hst.domain.model.SetKind
import com.djand.hst.domain.model.SetPrescription
import com.djand.hst.domain.model.TemplateExercise
import com.djand.hst.domain.model.WorkoutLetter
import kotlin.math.max

/**
 * The HST progression engine: pure Kotlin, no Android dependencies, fully deterministic.
 *
 * Reproduces the Lift Vault HST spreadsheet logic:
 *
 * ## Cycle structure
 *
 * 4 phases of 6 workouts each (24 total), workouts A/B/C rotating continuously.
 *
 * | Workouts | Phase | Target reps |
 * |----------|-------|-------------|
 * | 1-6      | 1     | 15          |
 * | 7-12     | 2     | 10          |
 * | 13-18    | 3     | 5           |
 * | 19-24    | 4     | 5 / neg     |
 *
 * ## Ramp formula (reproduces Excel)
 *
 * Within each phase, the weight ramps linearly by the per-exercise increment:
 *
 *     w_n = phaseRM - (6 - pos_in_phase) * increment
 *
 * where pos_in_phase = (sessionNumber - 1) % 6 (0-based), and phaseRM is the
 * appropriate rep-max (15RM, 10RM, or 5RM). Phase 4 continues beyond 5RM:
 *
 *     w_19 = 5RM + 1*inc, w_20 = 5RM + 2*inc, ..., w_24 = 5RM + 6*inc
 *
 * with workouts 22-24 prescribed as negative-only sets (NEGATIVE kind).
 *
 * No percentage ladders, no top-set/back-off structure, no auto weight reset on
 * failure — the spreadsheet's linear-increment model is reproduced as-is.
 *
 * ## Bodyweight pull-ups
 *
 * Exercises with [Equipment.BODYWEIGHT] and 0 kg added weight are prescribed as
 * bodyweight-only across all phases. The suggestion to start adding weight triggers
 * at 3 sets of 8+ bodyweight reps.
 */
class ProgressionEngine {

    fun generateCycle(
        cycleNumber: Int,
        inputs: List<ExerciseInput>,
        templates: Map<WorkoutLetter, List<TemplateExercise>>,
    ): CyclePlan {
        require(cycleNumber >= 1) { "Cycle number must be >= 1, got $cycleNumber" }
        require(templates.keys.containsAll(WorkoutLetter.entries.toList())) {
            "Templates for all of A, B and C are required"
        }
        val byId = inputs.associateBy { it.exerciseId }

        val occurrencesByExercise = linkedMapOf<String, MutableList<Occurrence>>()
        for ((letter, template) in templates) {
            require(template.map { it.exerciseId }.distinct().size == template.size) {
                "Duplicate exercise in template $letter"
            }
            for (te in template) {
                requireNotNull(byId[te.exerciseId]) { "No ExerciseInput for '${te.exerciseId}'" }
                val list = occurrencesByExercise.getOrPut(te.exerciseId) { mutableListOf() }
                repeat(SESSIONS_PER_CYCLE / WORKOUTS_PER_ROTATION) { k ->
                    list += Occurrence(
                        sessionNumber = letter.ordinal + 1 + WORKOUTS_PER_ROTATION * k,
                        sets = te.sets,
                    )
                }
            }
        }

        val bySession = mutableMapOf<Pair<Int, String>, ExercisePrescription>()
        for ((exerciseId, occurrences) in occurrencesByExercise) {
            val input = byId.getValue(exerciseId)
            val sorted = occurrences.sortedBy { it.sessionNumber }
            val sequence = prescribeSequence(input, sorted)
            sorted.zip(sequence).forEach { (occ, prescription) ->
                bySession[occ.sessionNumber to exerciseId] = prescription
            }
        }

        val sessions = (1..SESSIONS_PER_CYCLE).map { sessionNumber ->
            val letter = workoutForSession(sessionNumber)
            SessionPrescription(
                sessionNumber = sessionNumber,
                phase = phaseForSession(sessionNumber),
                workout = letter,
                exercises = templates.getValue(letter).map { te ->
                    bySession.getValue(sessionNumber to te.exerciseId)
                },
            )
        }
        return CyclePlan(cycleNumber, sessions)
    }

    fun evaluateSession(
        plan: CyclePlan,
        sessionNumber: Int,
        results: List<ExerciseResult>,
        consecutiveMisses: Map<String, Int> = emptyMap(),
    ): SessionEvaluation {
        val session = plan.sessions.firstOrNull { it.sessionNumber == sessionNumber }
            ?: throw IllegalArgumentException("Session $sessionNumber is not in the plan")

        val outcomes = results.map { result ->
            if (result.skipped) {
                val misses = consecutiveMisses[result.exerciseId] ?: 0
                ProgressionOutcome(result.exerciseId, ProgressionEvent.SKIPPED, misses)
            } else {
                val current = session.exercises.firstOrNull { it.exerciseId == result.exerciseId }
                    ?: throw IllegalArgumentException("Exercise '${result.exerciseId}' is not in session $sessionNumber")
                val achieved = isAchieved(current, result)
                if (achieved) {
                    ProgressionOutcome(result.exerciseId, ProgressionEvent.ACHIEVED, 0)
                } else {
                    val misses = (consecutiveMisses[result.exerciseId] ?: 0) + 1
                    ProgressionOutcome(result.exerciseId, ProgressionEvent.MISSED, misses)
                }
            }
        }
        return SessionEvaluation(plan, outcomes)
    }

    fun nextCycleInputs(
        inputs: List<ExerciseInput>,
        plan: CyclePlan,
        results: Map<Int, List<ExerciseResult>>,
    ): List<ExerciseInput> = inputs.map { input ->
        val lastSuccess = plan.sessions
            .sortedByDescending { it.sessionNumber }
            .firstNotNullOfOrNull { s ->
                val p = s.exercises.firstOrNull { it.exerciseId == input.exerciseId }
                    ?: return@firstNotNullOfOrNull null
                val r = results[s.sessionNumber]
                    ?.firstOrNull { it.exerciseId == input.exerciseId }
                    ?: return@firstNotNullOfOrNull null
                if (r.skipped || !isAchieved(p, r)) null else p to r
            }
        if (lastSuccess == null) {
            input
        } else {
            val (prescription, _) = lastSuccess
            input.copy(
                weightKg = prescription.sets.first().weightKg,
                reps = prescription.sets.first().targetReps,
            )
        }
    }

    fun shouldSuggestAddingWeight(
        equipment: Equipment,
        addedWeightKg: Double,
        completedReps: List<Int>,
    ): Boolean =
        equipment == Equipment.BODYWEIGHT &&
            addedWeightKg <= 0.0 &&
            completedReps.size >= PULL_UP_SUGGESTION_SETS &&
            completedReps.all { it >= PULL_UP_SUGGESTION_REPS }

    // ------------------------------------------------------------ internals

    private data class Occurrence(val sessionNumber: Int, val sets: Int)

    private data class Rms(val rm15: Double, val rm10: Double, val rm5: Double) {
        fun forPhase(phase: Int): Double = when (phase) {
            1 -> rm15
            2 -> rm10
            else -> rm5
        }

        companion object {
            fun fromWorkingWeight(weightKg: Double, reps: Int): Rms {
                val oneRm = RmMath.oneRepMax(weightKg, reps)
                return Rms(
                    rm15 = RmMath.repMax(oneRm, 15),
                    rm10 = RmMath.repMax(oneRm, 10),
                    rm5 = RmMath.repMax(oneRm, 5),
                )
            }
        }
    }

    private fun prescribeSequence(
        input: ExerciseInput,
        occurrences: List<Occurrence>,
    ): List<ExercisePrescription> {
        val rms = Rms.fromWorkingWeight(input.weightKg, input.reps)
        val isBodyweightOnly = input.equipment == Equipment.BODYWEIGHT && input.weightKg <= 0.0

        val result = ArrayList<ExercisePrescription>(occurrences.size)
        var previousWeight = 0.0
        var currentPhase = 0

        for (occ in occurrences) {
            val phase = phaseForSession(occ.sessionNumber)
            if (phase != currentPhase) {
                currentPhase = phase
                previousWeight = 0.0
            }
            val posInPhase = (occ.sessionNumber - 1) % SESSIONS_PER_PHASE  // 0..5
            val targetReps = targetRepsForPhase(phase)
            val isNegative = phase == PHASE_4 && posInPhase >= 3

            val weight = if (isBodyweightOnly) {
                0.0
            } else {
                val raw = if (phase <= PHASE_3) {
                    rms.forPhase(phase) - (SESSIONS_PER_PHASE - 1 - posInPhase) * input.incrementKg
                } else {
                    rms.rm5 + (posInPhase + 1) * input.incrementKg
                }
                val w = RmMath.roundToIncrement(raw, input.incrementKg)
                max(w, previousWeight)
            }
            previousWeight = weight

            val sets = List(occ.sets) {
                SetPrescription(
                    weightKg = weight,
                    targetReps = targetReps,
                    minReps = targetReps,
                    kind = if (isNegative) SetKind.NEGATIVE else SetKind.NORMAL,
                )
            }
            result += ExercisePrescription(
                exerciseId = input.exerciseId,
                incrementKg = input.incrementKg,
                isCompound = input.isCompound,
                sets = sets,
            )
        }
        return result
    }

    private fun isAchieved(prescription: ExercisePrescription, result: ExerciseResult): Boolean =
        prescription.sets.indices.all { i ->
            result.completedReps.getOrElse(i) { 0 } >= prescription.sets[i].minReps
        }

    companion object {
        const val SESSIONS_PER_CYCLE = 24
        const val SESSIONS_PER_PHASE = 6
        const val WORKOUTS_PER_ROTATION = 3

        const val PHASE_1 = 1
        const val PHASE_2 = 2
        const val PHASE_3 = 3
        const val PHASE_4 = 4

        const val PHASE1_REPS = 15
        const val PHASE2_REPS = 10
        const val PHASE3_REPS = 5
        const val PHASE4_REPS = 5

        const val PULL_UP_SUGGESTION_SETS = 3
        const val PULL_UP_SUGGESTION_REPS = 8

        fun workoutForSession(sessionNumber: Int): WorkoutLetter =
            WorkoutLetter.entries[(sessionNumber - 1) % WORKOUTS_PER_ROTATION]

        fun phaseForSession(sessionNumber: Int): Int =
            (sessionNumber - 1) / SESSIONS_PER_PHASE + 1

        fun targetRepsForPhase(phase: Int): Int = when (phase) {
            1 -> PHASE1_REPS
            2 -> PHASE2_REPS
            3 -> PHASE3_REPS
            else -> PHASE4_REPS
        }

        fun phaseName(phase: Int): String = when (phase) {
            1 -> "15 RM Phase"
            2 -> "10 RM Phase"
            3 -> "5 RM Phase"
            else -> "Post-5RM Phase"
        }
    }
}
