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
import kotlin.math.ceil
import kotlin.math.max

/**
 * The HST progression engine: pure Kotlin, no Android dependencies, fully deterministic.
 *
 * ## Cycle structure
 *
 * A cycle is 8 weeks = 24 sessions. Workouts A/B/C rotate continuously
 * (session 1 = A, 2 = B, 3 = C, 4 = A, ...). Weeks 1-2 form block 1, weeks 3-4
 * block 2, etc., so each 2-week block spans exactly 6 sessions and each workout
 * occurs exactly twice per block.
 *
 * ## Block ladders (classic HST spreadsheet zig-zag)
 *
 * Blocks 1-3 prescribe [BLOCK_LADDER] (`75% .. 100%`) of the block's rep max, where
 * the ladder rung is selected by the session's position inside the block:
 * rung index = `(sessionNumber - 1) % 6`. Each rung is rounded to the exercise's
 * equipment increment and the sequence is kept monotonically non-decreasing, which
 * scales safely from light dumbbell isolations (where several rungs can round to the
 * same weight) to heavy barbell compounds. When a new block starts, the weight drops
 * back (a lower percentage of a higher rep max) — the intentional zig-zag.
 *
 * ## Rep schemes
 *
 * - Block 1 (weeks 1-2): 15 reps, ladder on 15RM.
 * - Block 2 (weeks 3-4): 10 reps, ladder on 10RM.
 * - Block 3 (weeks 5-6): target 8, accept >= 5, ladder on 5RM.
 * - Block 4 (weeks 7-8): first set is the TOP set of 5 at
 *   `5RM * [1.00, 1.00, 1.025, 1.025, 1.05, 1.05]`; the remaining sets are BACK-OFF
 *   sets at 80% of the (rounded) top-set weight, target 10, accept >= 8. Template set
 *   counts are preserved.
 *
 * ## Isolation exercises
 *
 * Isolations follow the block ladders in blocks 1-2, then stay at 10-15 reps in
 * blocks 3-4 at the weight of their last block-2 occurrence. Progression is reactive
 * and reps-first: the weight increases by one increment only after every set reaches
 * the top of the range (15); on a miss (< 10 on any set) the weight is repeated;
 * two consecutive misses reduce it by 10%.
 *
 * ## Miss rules (compounds, and isolations in blocks 1-2)
 *
 * A prescription is achieved when every set reaches its minimum reps. On a miss, the
 * next occurrence repeats the same weight (with that occurrence's own rep targets);
 * the original ladder resumes afterwards. On the second consecutive miss the working
 * weight is reduced by 10% (rounded to the increment), that reduced weight becomes
 * the exercise's new rep-max reference for the current block, and the remaining
 * progression is regenerated from it via Epley. Deterministic in all cases.
 *
 * ## Worked example (Hack Squat, machine 2.5 kg, input 80 kg x 10)
 *
 * 1RM = 106.67; 15RM = 71.11, 10RM = 80, 5RM = 91.43. Hack Squat is in workout A,
 * so its rungs per block are indices 0 and 3 (75%/90%, 80%/95% is workout B, etc.):
 *
 * | Session | Block | Rung  | Weight | Reps        |
 * |---------|-------|-------|--------|-------------|
 * | 1       | 1     | 75%   | 52.5   | 2 x 15      |
 * | 4       | 1     | 90%   | 65.0   | 2 x 15      |
 * | 7       | 2     | 75%   | 60.0   | 2 x 10      |
 * | 10      | 2     | 90%   | 72.5   | 2 x 10      |
 * | 13      | 3     | 75%   | 67.5   | 2 x 8 (>=5) |
 * | 16      | 3     | 90%   | 82.5   | 2 x 8 (>=5) |
 * | 19      | 4     | 100%  | 92.5   | 5 + back-off 75.0 x 10 (>=8) |
 * | 22      | 4     | 102.5%| 92.5   | 5 + back-off 75.0 x 10 (>=8) |
 */
class ProgressionEngine {

    // ------------------------------------------------------------------ generation

    /**
     * Generates the full 24-session prescription of cycle [cycleNumber] from the
     * setup inputs and the workout templates. Every exercise referenced by the
     * templates must have an [ExerciseInput].
     */
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

        // Collect every occurrence (session number + set count) of each exercise.
        // An exercise may appear in more than one workout (e.g. pull-ups in A and C).
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

        // Prescribe each exercise's full sequence, then index it by session.
        val bySession = mutableMapOf<Pair<Int, String>, ExercisePrescription>()
        for ((exerciseId, occurrences) in occurrencesByExercise) {
            val input = byId.getValue(exerciseId)
            val sorted = occurrences.sortedBy { it.sessionNumber }
            val rms = Rms.fromWorkingWeight(input.weightKg, input.reps)
            val sequence = prescribeSequence(
                exerciseId = exerciseId,
                rms = rms,
                incrementKg = input.incrementKg,
                isCompound = input.isCompound,
                occurrences = sorted,
            )
            sorted.zip(sequence).forEach { (occ, prescription) ->
                bySession[occ.sessionNumber to exerciseId] = prescription
            }
        }

        val sessions = (1..SESSIONS_PER_CYCLE).map { sessionNumber ->
            val letter = workoutForSession(sessionNumber)
            SessionPrescription(
                sessionNumber = sessionNumber,
                week = weekForSession(sessionNumber),
                block = blockForSession(sessionNumber),
                workout = letter,
                isDeload = false,
                exercises = templates.getValue(letter).map { te ->
                    bySession.getValue(sessionNumber to te.exerciseId)
                },
            )
        }
        return CyclePlan(cycleNumber, sessions)
    }

    /**
     * Generates the deload week (sessions 25-27, one per workout) after a completed
     * cycle: weights at 85% of each exercise's last session (rounded to the
     * increment), sets reduced by half (`ceil(sets / 2)`), no failure. Deload
     * sessions never drive progression.
     */
    fun generateDeload(plan: CyclePlan): List<SessionPrescription> =
        WorkoutLetter.entries.map { letter ->
            val source = plan.sessions.filter { !it.isDeload }.last { it.workout == letter }
            SessionPrescription(
                sessionNumber = SESSIONS_PER_CYCLE + 1 + letter.ordinal,
                week = DELOAD_WEEK,
                block = 0,
                workout = letter,
                isDeload = true,
                exercises = source.exercises.map { ep ->
                    ep.copy(
                        sets = ep.sets
                            .take(ceil(ep.sets.size / 2.0).toInt())
                            .map { s ->
                                s.copy(
                                    weightKg = RmMath.roundToIncrement(
                                        s.weightKg * DELOAD_WEIGHT_FRACTION,
                                        ep.incrementKg,
                                    ),
                                )
                            },
                    )
                },
            )
        }

    // ------------------------------------------------------------------ evaluation

    /**
     * Evaluates one finished session and returns the updated plan plus per-exercise
     * outcomes. Future sessions of the plan may be rewritten (repeat / reset / bump);
     * the evaluated session itself is never modified.
     *
     * [consecutiveMisses] holds the persisted per-exercise miss counters (exercises
     * missing from the map are treated as 0). Persist each
     * [ProgressionOutcome.consecutiveMisses] back after the call.
     *
     * Exercises of the session that have no [ExerciseResult] are simply not
     * evaluated. Deload sessions must not be evaluated.
     */
    fun evaluateSession(
        plan: CyclePlan,
        sessionNumber: Int,
        results: List<ExerciseResult>,
        consecutiveMisses: Map<String, Int> = emptyMap(),
    ): SessionEvaluation {
        val session = plan.sessions.firstOrNull { it.sessionNumber == sessionNumber }
            ?: throw IllegalArgumentException("Session $sessionNumber is not in the plan")
        require(!session.isDeload) { "Deload sessions do not drive progression" }

        val replacements = mutableMapOf<Pair<Int, String>, ExercisePrescription>()
        val outcomes = ArrayList<ProgressionOutcome>(results.size)

        for (result in results) {
            val exerciseId = result.exerciseId
            val current = session.exercises.firstOrNull { it.exerciseId == exerciseId }
                ?: throw IllegalArgumentException("Exercise '$exerciseId' is not in session $sessionNumber")
            val misses = consecutiveMisses[exerciseId] ?: 0

            if (result.skipped) {
                outcomes += ProgressionOutcome(exerciseId, ProgressionEvent.SKIPPED, misses)
                continue
            }

            val future = futureOccurrences(plan, sessionNumber, exerciseId)
            val achieved = isAchieved(current, result)
            val reactiveIsolation = !current.isCompound && session.block >= FIRST_REACTIVE_ISOLATION_BLOCK

            when {
                achieved && reactiveIsolation && hitTopOfRange(current, result) -> {
                    // Reps-first progression earned: +1 increment on all remaining sessions.
                    for ((num, occ) in future) {
                        replacements[num to exerciseId] = occ.copy(
                            sets = occ.sets.map { s -> s.copy(weightKg = s.weightKg + current.incrementKg) },
                        )
                    }
                    outcomes += ProgressionOutcome(exerciseId, ProgressionEvent.BUMP_SCHEDULED, 0)
                }

                achieved -> {
                    outcomes += ProgressionOutcome(exerciseId, ProgressionEvent.ACHIEVED, 0)
                }

                misses >= 1 -> {
                    // Second consecutive miss: -10% and regenerate / flatten the rest.
                    val reduced = RmMath.roundToIncrement(
                        current.sets.first().weightKg * RESET_FRACTION,
                        current.incrementKg,
                    )
                    if (reactiveIsolation) {
                        // Blocks 3-4 isolations are flat: just lower the flat weight.
                        for ((num, occ) in future) {
                            replacements[num to exerciseId] = occ.copy(
                                sets = occ.sets.map { s -> s.copy(weightKg = reduced) },
                            )
                        }
                    } else {
                        // The reduced weight becomes the new rep-max reference for the
                        // current block; regenerate the remaining ladder from it.
                        val newRms = Rms.fromWorkingWeight(reduced, referenceRepsForBlock(session.block))
                        val occurrences = future.map { (num, occ) -> Occurrence(num, occ.sets.size) }
                        val regenerated = prescribeSequence(
                            exerciseId = exerciseId,
                            rms = newRms,
                            incrementKg = current.incrementKg,
                            isCompound = current.isCompound,
                            occurrences = occurrences,
                        )
                        occurrences.zip(regenerated).forEach { (occ, ep) ->
                            replacements[occ.sessionNumber to exerciseId] = ep
                        }
                    }
                    outcomes += ProgressionOutcome(exerciseId, ProgressionEvent.RESET, 0)
                }

                else -> {
                    // First miss: next occurrence repeats the same weight (keeping its
                    // own rep targets); the original ladder resumes after that.
                    future.firstOrNull()?.let { (num, occ) ->
                        replacements[num to exerciseId] = reweight(
                            occ = occ,
                            block = blockForSession(num),
                            weightKg = current.sets.first().weightKg,
                            incrementKg = current.incrementKg,
                        )
                    }
                    outcomes += ProgressionOutcome(exerciseId, ProgressionEvent.REPEAT_SCHEDULED, misses + 1)
                }
            }
        }

        if (replacements.isEmpty()) return SessionEvaluation(plan, outcomes)

        val newSessions = plan.sessions.map { s ->
            if (s.sessionNumber <= sessionNumber) {
                s
            } else {
                s.copy(
                    exercises = s.exercises.map { ep ->
                        replacements[s.sessionNumber to ep.exerciseId] ?: ep
                    },
                )
            }
        }
        return SessionEvaluation(plan.copy(sessions = newSessions), outcomes)
    }

    // ------------------------------------------------------------------ next cycle

    /**
     * Derives the setup inputs of the next cycle from what was actually achieved in
     * [plan]: for each exercise, the weight of its last successful session (top set
     * for block-4 compounds) together with the minimum reps achieved across the sets
     * at that weight becomes the new rep-max input. Exercises with no successful
     * session keep their previous input.
     */
    fun nextCycleInputs(
        inputs: List<ExerciseInput>,
        plan: CyclePlan,
        results: Map<Int, List<ExerciseResult>>,
    ): List<ExerciseInput> = inputs.map { input ->
        val lastSuccess = plan.sessions
            .filter { !it.isDeload }
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
            val (prescription, result) = lastSuccess
            val workingSetIndices = prescription.sets.indices.filter { prescription.sets[it].kind != SetKind.BACK_OFF }
            input.copy(
                weightKg = prescription.sets.first().weightKg,
                reps = workingSetIndices.minOf { result.completedReps.getOrElse(it) { 0 } },
            )
        }
    }

    // ------------------------------------------------------------------ pull-ups

    /**
     * Pull-ups are tracked as ADDED weight only (0 kg = bodyweight). When bodyweight
     * pull-ups reach 3 sets of 8 reps, the app suggests "Start adding weight."
     */
    fun shouldSuggestAddingWeight(
        equipment: Equipment,
        addedWeightKg: Double,
        completedReps: List<Int>,
    ): Boolean =
        equipment == Equipment.BODYWEIGHT &&
            addedWeightKg <= 0.0 &&
            completedReps.size >= PULL_UP_SUGGESTION_SETS &&
            completedReps.all { it >= PULL_UP_SUGGESTION_REPS }

    // ------------------------------------------------------------------ internals

    /** One scheduled performance of an exercise: where it happens and with how many sets. */
    private data class Occurrence(val sessionNumber: Int, val sets: Int) {
        val block: Int get() = blockForSession(sessionNumber)
        val ladderIndex: Int get() = (sessionNumber - 1) % SESSIONS_PER_BLOCK
    }

    /** The three rep maxes a cycle is built on, derived from the working weight via Epley. */
    private data class Rms(val rm15: Double, val rm10: Double, val rm5: Double) {
        fun forBlock(block: Int): Double = when (block) {
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

    /**
     * Prescribes one exercise across [occurrences] (must be sorted by session number).
     * Rounded weights are kept monotonically non-decreasing inside each block; block
     * boundaries intentionally drop the weight (the HST zig-zag).
     */
    private fun prescribeSequence(
        exerciseId: String,
        rms: Rms,
        incrementKg: Double,
        isCompound: Boolean,
        occurrences: List<Occurrence>,
    ): List<ExercisePrescription> {
        val result = ArrayList<ExercisePrescription>(occurrences.size)
        var previousBlock = 0
        var previousWeightInBlock = 0.0

        for (occ in occurrences) {
            if (occ.block != previousBlock) {
                previousBlock = occ.block
                previousWeightInBlock = 0.0
            }

            val sets: List<SetPrescription> = if (isCompound || occ.block <= 2) {
                val ladder = if (occ.block == HEAVY_BLOCK) BLOCK4_TOP_LADDER else BLOCK_LADDER
                val weight = max(
                    RmMath.roundToIncrement(ladder[occ.ladderIndex] * rms.forBlock(occ.block), incrementKg),
                    previousWeightInBlock,
                )
                previousWeightInBlock = weight
                when (occ.block) {
                    1 -> straightSets(occ.sets, weight, target = BLOCK1_REPS, min = BLOCK1_REPS)
                    2 -> straightSets(occ.sets, weight, target = BLOCK2_REPS, min = BLOCK2_REPS)
                    3 -> straightSets(occ.sets, weight, target = BLOCK3_TARGET_REPS, min = BLOCK3_MIN_REPS)
                    else -> {
                        val backOff = RmMath.roundToIncrement(weight * BACK_OFF_FRACTION, incrementKg)
                        listOf(SetPrescription(weight, TOP_SET_REPS, TOP_SET_REPS, SetKind.TOP)) +
                            List(occ.sets - 1) {
                                SetPrescription(backOff, BACK_OFF_TARGET_REPS, BACK_OFF_MIN_REPS, SetKind.BACK_OFF)
                            }
                    }
                }
            } else {
                // Isolation, blocks 3-4: flat at the weight of the exercise's last
                // block-2 occurrence (rung index = workout ordinal + 3), 10-15 reps.
                val flatRungIndex = (occ.sessionNumber - 1) % WORKOUTS_PER_ROTATION + 3
                val flat = RmMath.roundToIncrement(BLOCK_LADDER[flatRungIndex] * rms.rm10, incrementKg)
                straightSets(occ.sets, flat, target = ISOLATION_MAX_REPS, min = ISOLATION_MIN_REPS)
            }

            result += ExercisePrescription(
                exerciseId = exerciseId,
                incrementKg = incrementKg,
                isCompound = isCompound,
                sets = sets,
            )
        }
        return result
    }

    private fun straightSets(count: Int, weightKg: Double, target: Int, min: Int): List<SetPrescription> =
        List(count) { SetPrescription(weightKg, target, min, SetKind.NORMAL) }

    /** All occurrences of [exerciseId] scheduled after [sessionNumber] (deload excluded). */
    private fun futureOccurrences(
        plan: CyclePlan,
        sessionNumber: Int,
        exerciseId: String,
    ): List<Pair<Int, ExercisePrescription>> =
        plan.sessions
            .filter { it.sessionNumber > sessionNumber && !it.isDeload }
            .mapNotNull { s ->
                s.exercises.firstOrNull { it.exerciseId == exerciseId }
                    ?.let { s.sessionNumber to it }
            }

    /** Achieved = every prescribed set reached at least its minimum reps. */
    private fun isAchieved(prescription: ExercisePrescription, result: ExerciseResult): Boolean =
        prescription.sets.indices.all { i ->
            result.completedReps.getOrElse(i) { 0 } >= prescription.sets[i].minReps
        }

    /** Every set reached the top of the isolation rep range. */
    private fun hitTopOfRange(prescription: ExercisePrescription, result: ExerciseResult): Boolean =
        prescription.sets.indices.all { i ->
            result.completedReps.getOrElse(i) { 0 } >= ISOLATION_MAX_REPS
        }

    /**
     * Replaces the weights of an occurrence while keeping its set structure and rep
     * targets. Used for the first-miss repeat rule: in blocks 1-3 every set gets the
     * repeated weight; in block 4 the top set gets it and back-offs stay at 80%.
     */
    private fun reweight(
        occ: ExercisePrescription,
        block: Int,
        weightKg: Double,
        incrementKg: Double,
    ): ExercisePrescription {
        if (block != HEAVY_BLOCK) {
            return occ.copy(sets = occ.sets.map { s -> s.copy(weightKg = weightKg) })
        }
        val backOff = RmMath.roundToIncrement(weightKg * BACK_OFF_FRACTION, incrementKg)
        return occ.copy(
            sets = occ.sets.map { s ->
                s.copy(weightKg = if (s.kind == SetKind.TOP) weightKg else backOff)
            },
        )
    }

    companion object {
        /** Sessions of the main cycle: 8 weeks x 3 workouts. */
        const val SESSIONS_PER_CYCLE = 24

        /** Sessions per 2-week block. */
        const val SESSIONS_PER_BLOCK = 6

        /** A, B and C rotate continuously. */
        const val WORKOUTS_PER_ROTATION = 3

        /** Classic spreadsheet ladder for blocks 1-3: 75% .. 100% of the block's rep max. */
        val BLOCK_LADDER: List<Double> = listOf(0.75, 0.80, 0.85, 0.90, 0.95, 1.00)

        /** Block-4 top-set ladder over 5RM (post-5RM overreaching). */
        val BLOCK4_TOP_LADDER: List<Double> = listOf(1.00, 1.00, 1.025, 1.025, 1.05, 1.05)

        const val HEAVY_BLOCK = 4
        const val BLOCK1_REPS = 15
        const val BLOCK2_REPS = 10
        const val BLOCK3_TARGET_REPS = 8
        const val BLOCK3_MIN_REPS = 5
        const val TOP_SET_REPS = 5
        const val BACK_OFF_TARGET_REPS = 10
        const val BACK_OFF_MIN_REPS = 8

        /** Back-off sets are 80% of the rounded top-set weight. */
        const val BACK_OFF_FRACTION = 0.8

        /** Isolations stay at 10-15 reps from block 3 onwards. */
        const val ISOLATION_MIN_REPS = 10
        const val ISOLATION_MAX_REPS = 15
        const val FIRST_REACTIVE_ISOLATION_BLOCK = 3

        /** Second consecutive miss reduces the working weight to 90%. */
        const val RESET_FRACTION = 0.9

        /** Deload week: weights x 0.85, sets halved (ceil), week number 9. */
        const val DELOAD_WEIGHT_FRACTION = 0.85
        const val DELOAD_WEEK = 9

        /** Suggest adding weight to pull-ups at 3 sets of 8 bodyweight reps. */
        const val PULL_UP_SUGGESTION_SETS = 3
        const val PULL_UP_SUGGESTION_REPS = 8

        fun workoutForSession(sessionNumber: Int): WorkoutLetter =
            WorkoutLetter.entries[(sessionNumber - 1) % WORKOUTS_PER_ROTATION]

        fun weekForSession(sessionNumber: Int): Int =
            (sessionNumber - 1) / WORKOUTS_PER_ROTATION + 1

        fun blockForSession(sessionNumber: Int): Int =
            (sessionNumber - 1) / SESSIONS_PER_BLOCK + 1

        /**
         * The rep count whose rep max anchors a block's ladder (15RM / 10RM / 5RM).
         * Used when a -10% reset re-derives the remaining progression.
         */
        fun referenceRepsForBlock(block: Int): Int = when (block) {
            1 -> 15
            2 -> 10
            else -> 5
        }
    }
}
