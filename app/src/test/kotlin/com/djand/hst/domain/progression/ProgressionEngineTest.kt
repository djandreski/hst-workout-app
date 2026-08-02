package com.djand.hst.domain.progression

import com.djand.hst.domain.model.CyclePlan
import com.djand.hst.domain.model.Equipment
import com.djand.hst.domain.model.ExerciseInput
import com.djand.hst.domain.model.ExercisePrescription
import com.djand.hst.domain.model.ExerciseResult
import com.djand.hst.domain.model.ProgressionEvent
import com.djand.hst.domain.model.SessionPrescription
import com.djand.hst.domain.model.SetKind
import com.djand.hst.domain.model.TemplateExercise
import com.djand.hst.domain.model.WorkoutLetter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixtures and expected values follow the worked example documented on
 * [ProgressionEngine]: Hack Squat 80 kg x 10 (machine, 2.5 kg) ->
 * 1RM 106.67, 15RM 71.11, 10RM 80, 5RM 91.43.
 */
class ProgressionEngineTest {

    private val hack = ExerciseInput("hack", Equipment.MACHINE, 2.5, isCompound = true, weightKg = 80.0, reps = 10)
    private val latRaise = ExerciseInput("lat_raise", Equipment.DUMBBELL, 2.0, isCompound = false, weightKg = 10.0, reps = 12)
    private val pullUp = ExerciseInput("pull_up", Equipment.BODYWEIGHT, 2.5, isCompound = true, weightKg = 0.0, reps = 8)
    private val rdl = ExerciseInput("rdl", Equipment.BARBELL, 2.5, isCompound = true, weightKg = 100.0, reps = 5)
    private val legExt = ExerciseInput("leg_ext", Equipment.MACHINE, 2.5, isCompound = false, weightKg = 40.0, reps = 10)

    private val inputs = listOf(hack, latRaise, pullUp, rdl, legExt)
    private val templates = mapOf(
        WorkoutLetter.A to listOf(
            TemplateExercise("hack", 2),
            TemplateExercise("lat_raise", 2),
            TemplateExercise("pull_up", 2),
        ),
        WorkoutLetter.B to listOf(TemplateExercise("rdl", 2)),
        WorkoutLetter.C to listOf(
            TemplateExercise("pull_up", 3),
            TemplateExercise("leg_ext", 1),
        ),
    )

    private val engine = ProgressionEngine()
    private val plan: CyclePlan = engine.generateCycle(1, inputs, templates)

    private fun session(n: Int, p: CyclePlan = plan): SessionPrescription =
        p.sessions.first { it.sessionNumber == n }

    private fun exercise(n: Int, id: String, p: CyclePlan = plan): ExercisePrescription =
        session(n, p).exercises.first { it.exerciseId == id }

    private fun weightOf(n: Int, id: String, p: CyclePlan = plan): Double =
        exercise(n, id, p).sets.first().weightKg

    // ------------------------------------------------------------ cycle structure

    @Test
    fun `cycle has 24 sessions numbered 1 to 24`() {
        assertEquals(24, plan.sessions.size)
        assertEquals((1..24).toList(), plan.sessions.map { it.sessionNumber })
    }

    @Test
    fun `workouts rotate A B C continuously`() {
        val expected = List(24) { WorkoutLetter.entries[it % 3] }
        assertEquals(expected, plan.sessions.map { it.workout })
        assertEquals(WorkoutLetter.A, ProgressionEngine.workoutForSession(1))
        assertEquals(WorkoutLetter.B, ProgressionEngine.workoutForSession(2))
        assertEquals(WorkoutLetter.C, ProgressionEngine.workoutForSession(3))
        assertEquals(WorkoutLetter.A, ProgressionEngine.workoutForSession(4))
    }

    @Test
    fun `sessions map to weeks and blocks`() {
        assertEquals(1, session(1).week)
        assertEquals(1, session(3).week)
        assertEquals(2, session(4).week)
        assertEquals(8, session(24).week)
        assertEquals(1, session(1).block)
        assertEquals(1, session(6).block)
        assertEquals(2, session(7).block)
        assertEquals(3, session(13).block)
        assertEquals(4, session(19).block)
        assertEquals(4, session(24).block)
    }

    @Test
    fun `template order and set counts are preserved`() {
        assertEquals(listOf("hack", "lat_raise", "pull_up"), session(1).exercises.map { it.exerciseId })
        assertEquals(2, exercise(1, "hack").sets.size)
        assertEquals(2, exercise(1, "pull_up").sets.size)
        assertEquals(3, exercise(3, "pull_up").sets.size)
        assertEquals(1, exercise(3, "leg_ext").sets.size)
    }

    // ------------------------------------------------------------ block ladders

    @Test
    fun `compound block ladders match the worked example`() {
        // Hack Squat, workout A -> rungs 75%/90% of the block RM.
        assertEquals(52.5, weightOf(1, "hack"), 1e-9) // 75% x 71.11
        assertEquals(65.0, weightOf(4, "hack"), 1e-9) // 90% x 71.11
        assertEquals(60.0, weightOf(7, "hack"), 1e-9) // 75% x 80
        assertEquals(72.5, weightOf(10, "hack"), 1e-9) // 90% x 80 = 72 -> 72.5
        assertEquals(67.5, weightOf(13, "hack"), 1e-9) // 75% x 91.43
        assertEquals(82.5, weightOf(16, "hack"), 1e-9) // 90% x 91.43
    }

    @Test
    fun `ladder zig-zags down when a new block starts`() {
        assertTrue(weightOf(7, "hack") < weightOf(4, "hack")) // 60 < 65
        assertTrue(weightOf(13, "hack") < weightOf(16, "hack"))
    }

    @Test
    fun `rounding keeps ladders monotonic for light dumbbell weights`() {
        // Lateral raise: 15RM 9.33, 75% -> 7.0 and 90% -> 8.4 both round to 8 kg (2 kg increment).
        assertEquals(8.0, weightOf(1, "lat_raise"), 1e-9)
        assertEquals(8.0, weightOf(4, "lat_raise"), 1e-9)
        assertEquals(8.0, weightOf(7, "lat_raise"), 1e-9)
        assertEquals(10.0, weightOf(10, "lat_raise"), 1e-9)
    }

    @Test
    fun `rep schemes match the block`() {
        exercise(1, "hack").sets.forEach {
            assertEquals(15, it.targetReps)
            assertEquals(15, it.minReps)
            assertEquals(SetKind.NORMAL, it.kind)
        }
        exercise(7, "hack").sets.forEach {
            assertEquals(10, it.targetReps)
            assertEquals(10, it.minReps)
        }
        exercise(13, "hack").sets.forEach {
            assertEquals(8, it.targetReps)
            assertEquals(5, it.minReps)
        }
    }

    @Test
    fun `block 4 has a top set of 5 plus back-off sets at 80 percent`() {
        val sets = exercise(19, "hack").sets
        assertEquals(2, sets.size)
        assertEquals(SetKind.TOP, sets[0].kind)
        assertEquals(92.5, sets[0].weightKg, 1e-9)
        assertEquals(5, sets[0].targetReps)
        assertEquals(5, sets[0].minReps)
        assertEquals(SetKind.BACK_OFF, sets[1].kind)
        assertEquals(75.0, sets[1].weightKg, 1e-9) // 80% of 92.5 = 74 -> 75
        assertEquals(10, sets[1].targetReps)
        assertEquals(8, sets[1].minReps)
    }

    @Test
    fun `block 4 top ladder climbs past the 5RM`() {
        // Workout B's block-4 rungs are indices 1 and 4 -> 1.00 and 1.05.
        assertEquals(100.0, weightOf(20, "rdl"), 1e-9) // 1.00 x 5RM 100
        assertEquals(105.0, weightOf(23, "rdl"), 1e-9) // 1.05 x 5RM 100
    }

    @Test
    fun `three set compounds get one top set and two back-off sets`() {
        val sets = exercise(24, "pull_up").sets
        assertEquals(3, sets.size)
        assertEquals(SetKind.TOP, sets[0].kind)
        assertEquals(SetKind.BACK_OFF, sets[1].kind)
        assertEquals(SetKind.BACK_OFF, sets[2].kind)
    }

    // ------------------------------------------------------------ isolations

    @Test
    fun `isolations sit at 10 to 15 reps on their block-2 weight in blocks 3 and 4`() {
        // Lateral raise 10RM = 10.5; last block-2 rung (90%) = 9.45 -> 10 kg.
        for (n in listOf(13, 16, 19, 22)) {
            exercise(n, "lat_raise").sets.forEach {
                assertEquals(10.0, it.weightKg, 1e-9)
                assertEquals(15, it.targetReps)
                assertEquals(10, it.minReps)
                assertEquals(SetKind.NORMAL, it.kind)
            }
        }
    }

    // ------------------------------------------------------------ pull-ups

    @Test
    fun `bodyweight pull-ups prescribe zero added weight in every session`() {
        val occurrences = plan.sessions.flatMap { s ->
            s.exercises.filter { it.exerciseId == "pull_up" }.flatMap { it.sets }
        }
        assertTrue(occurrences.isNotEmpty())
        assertTrue(occurrences.all { it.weightKg == 0.0 })
    }

    @Test
    fun `an exercise can appear in several workouts`() {
        // Pull-ups: 8 sessions of A (2 sets) + 8 sessions of C (3 sets).
        val sessionsWithPullUps = plan.sessions.count { s -> s.exercises.any { it.exerciseId == "pull_up" } }
        assertEquals(16, sessionsWithPullUps)
    }

    // ------------------------------------------------------------ evaluation: compounds

    @Test
    fun `achieved session leaves the plan untouched and resets the counter`() {
        val evaluation = engine.evaluateSession(
            plan, 1,
            results = listOf(
                ExerciseResult("hack", listOf(15, 15)),
                ExerciseResult("lat_raise", listOf(15, 15)),
                ExerciseResult("pull_up", listOf(15, 15)),
            ),
            consecutiveMisses = mapOf("hack" to 1),
        )
        assertEquals(plan, evaluation.plan)
        val outcome = evaluation.outcomes.first { it.exerciseId == "hack" }
        assertEquals(ProgressionEvent.ACHIEVED, outcome.event)
        assertEquals(0, outcome.consecutiveMisses)
    }

    @Test
    fun `first miss repeats the same weight on the next occurrence`() {
        val evaluation = engine.evaluateSession(
            plan, 13,
            results = listOf(ExerciseResult("hack", listOf(8, 4))), // second set below min 5
        )
        val outcome = evaluation.outcomes.single()
        assertEquals(ProgressionEvent.REPEAT_SCHEDULED, outcome.event)
        assertEquals(1, outcome.consecutiveMisses)
        // Next occurrence (session 16) repeats 67.5 with its own rep targets...
        val repeated = exercise(16, "hack", evaluation.plan)
        assertEquals(67.5, repeated.sets[0].weightKg, 1e-9)
        assertEquals(67.5, repeated.sets[1].weightKg, 1e-9)
        assertEquals(8, repeated.sets[0].targetReps)
        // ...and the original ladder resumes afterwards.
        assertEquals(92.5, weightOf(19, "hack", evaluation.plan), 1e-9)
        // Evaluated session itself is never modified.
        assertEquals(67.5, weightOf(13, "hack", evaluation.plan), 1e-9)
    }

    @Test
    fun `first miss across a block boundary repeats the top weight with block-4 structure`() {
        val evaluation = engine.evaluateSession(
            plan, 16,
            results = listOf(ExerciseResult("hack", listOf(8, 4))),
        )
        val repeated = exercise(19, "hack", evaluation.plan)
        assertEquals(82.5, repeated.sets[0].weightKg, 1e-9) // top set repeats the missed weight
        assertEquals(SetKind.TOP, repeated.sets[0].kind)
        assertEquals(5, repeated.sets[0].targetReps) // block-4 rep targets kept
        assertEquals(65.0, repeated.sets[1].weightKg, 1e-9) // back-off = 80% of 82.5 = 66 -> 65
        assertEquals(SetKind.BACK_OFF, repeated.sets[1].kind)
    }

    @Test
    fun `second consecutive miss reduces 10 percent and regenerates the remaining ladder`() {
        val evaluation = engine.evaluateSession(
            plan, 16,
            results = listOf(ExerciseResult("hack", listOf(8, 4))),
            consecutiveMisses = mapOf("hack" to 1),
        )
        val outcome = evaluation.outcomes.single()
        assertEquals(ProgressionEvent.RESET, outcome.event)
        assertEquals(0, outcome.consecutiveMisses)

        // Reduced base: 82.5 x 0.9 = 74.25 -> 75.0, treated as the new 5RM.
        val s19 = exercise(19, "hack", evaluation.plan)
        assertEquals(75.0, s19.sets[0].weightKg, 1e-9) // 1.00 x 75
        assertEquals(60.0, s19.sets[1].weightKg, 1e-9) // 80% of 75
        assertEquals(5, s19.sets[0].targetReps)
        assertEquals(10, s19.sets[1].targetReps)

        val s22 = exercise(22, "hack", evaluation.plan)
        assertEquals(77.5, s22.sets[0].weightKg, 1e-9) // 1.025 x 75 = 76.875 -> 77.5
        assertEquals(62.5, s22.sets[1].weightKg, 1e-9) // 80% of 77.5 = 62
    }

    @Test
    fun `miss on the last occurrence only updates the counter`() {
        val evaluation = engine.evaluateSession(
            plan, 22,
            results = listOf(ExerciseResult("hack", listOf(4, 8))),
        )
        assertEquals(ProgressionEvent.REPEAT_SCHEDULED, evaluation.outcomes.single().event)
        assertEquals(1, evaluation.outcomes.single().consecutiveMisses)
        assertEquals(plan, evaluation.plan) // no future sessions to rewrite
    }

    @Test
    fun `skipped exercise has no progression impact`() {
        val evaluation = engine.evaluateSession(
            plan, 13,
            results = listOf(ExerciseResult("hack", emptyList(), skipped = true)),
            consecutiveMisses = mapOf("hack" to 1),
        )
        val outcome = evaluation.outcomes.single()
        assertEquals(ProgressionEvent.SKIPPED, outcome.event)
        assertEquals(1, outcome.consecutiveMisses) // unchanged
        assertEquals(plan, evaluation.plan)
    }

    @Test
    fun `missing sets count as zero reps`() {
        val evaluation = engine.evaluateSession(
            plan, 13,
            results = listOf(ExerciseResult("hack", listOf(8))), // only one of two sets done
        )
        assertEquals(ProgressionEvent.REPEAT_SCHEDULED, evaluation.outcomes.single().event)
    }

    // ------------------------------------------------------------ evaluation: isolations

    @Test
    fun `isolation bumps one increment after every set hits the top of the range`() {
        val evaluation = engine.evaluateSession(
            plan, 13,
            results = listOf(ExerciseResult("lat_raise", listOf(15, 15))),
        )
        assertEquals(ProgressionEvent.BUMP_SCHEDULED, evaluation.outcomes.single().event)
        assertEquals(0, evaluation.outcomes.single().consecutiveMisses)
        for (n in listOf(16, 19, 22)) {
            assertEquals(12.0, weightOf(n, "lat_raise", evaluation.plan), 1e-9)
        }
    }

    @Test
    fun `isolation below the top of the range keeps the weight`() {
        val evaluation = engine.evaluateSession(
            plan, 13,
            results = listOf(ExerciseResult("lat_raise", listOf(14, 15))),
        )
        assertEquals(ProgressionEvent.ACHIEVED, evaluation.outcomes.single().event)
        assertEquals(plan, evaluation.plan)
    }

    @Test
    fun `isolation miss repeats and double miss resets the flat weight`() {
        val firstMiss = engine.evaluateSession(
            plan, 15,
            results = listOf(ExerciseResult("leg_ext", listOf(9))), // below min 10
        )
        assertEquals(ProgressionEvent.REPEAT_SCHEDULED, firstMiss.outcomes.single().event)
        assertEquals(1, firstMiss.outcomes.single().consecutiveMisses)

        val secondMiss = engine.evaluateSession(
            firstMiss.plan, 18,
            results = listOf(ExerciseResult("leg_ext", listOf(8))),
            consecutiveMisses = mapOf("leg_ext" to 1),
        )
        assertEquals(ProgressionEvent.RESET, secondMiss.outcomes.single().event)
        // 40 x 0.9 = 36 -> 35 at a 2.5 kg increment, flat for the rest of the cycle.
        for (n in listOf(21, 24)) {
            assertEquals(35.0, weightOf(n, "leg_ext", secondMiss.plan), 1e-9)
            assertEquals(15, exercise(n, "leg_ext", secondMiss.plan).sets.first().targetReps)
        }
    }

    @Test
    fun `isolation in block 1 resets through the ladder like a compound`() {
        val evaluation = engine.evaluateSession(
            plan, 1,
            results = listOf(ExerciseResult("lat_raise", listOf(12, 15))),
            consecutiveMisses = mapOf("lat_raise" to 1),
        )
        assertEquals(ProgressionEvent.RESET, evaluation.outcomes.single().event)
        // Reduced base 8 x 0.9 = 7.2 -> 8 kg at a 2 kg increment, treated as new 15RM:
        // 1RM = 8 x 1.5 = 12, 10RM = 9. Session 4 (block 1, 90% rung): 0.9 x 8 = 7.2 -> 8.
        assertEquals(8.0, weightOf(4, "lat_raise", evaluation.plan), 1e-9)
        // Block 2, 75% rung of 10RM 9 = 6.75 -> 6 kg.
        assertEquals(6.0, weightOf(7, "lat_raise", evaluation.plan), 1e-9)
        // Blocks 3-4 flat at the regenerated last block-2 weight (90% x 9 = 8.1 -> 8).
        assertEquals(8.0, weightOf(13, "lat_raise", evaluation.plan), 1e-9)
        assertEquals(15, exercise(13, "lat_raise", evaluation.plan).sets.first().targetReps)
    }

    // ------------------------------------------------------------ evaluation: validation

    @Test(expected = IllegalArgumentException::class)
    fun `evaluating an unknown session throws`() {
        engine.evaluateSession(plan, 99, emptyList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `evaluating an unknown exercise throws`() {
        engine.evaluateSession(plan, 1, listOf(ExerciseResult("nope", listOf(10))))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `deload sessions cannot be evaluated`() {
        val deloadPlan = CyclePlan(1, engine.generateDeload(plan))
        engine.evaluateSession(deloadPlan, 25, emptyList())
    }

    // ------------------------------------------------------------ deload

    @Test
    fun `deload has one session per workout in week 9`() {
        val deload = engine.generateDeload(plan)
        assertEquals(3, deload.size)
        assertEquals(listOf(25, 26, 27), deload.map { it.sessionNumber })
        assertEquals(listOf(WorkoutLetter.A, WorkoutLetter.B, WorkoutLetter.C), deload.map { it.workout })
        deload.forEach {
            assertEquals(9, it.week)
            assertTrue(it.isDeload)
        }
    }

    @Test
    fun `deload reduces weights by 15 percent and halves the sets`() {
        val deload = engine.generateDeload(plan)

        // Hack source (session 22): top 92.5 + back-off 75 -> 1 set at 92.5 x 0.85 = 78.6 -> 77.5.
        val deloadHack = deload[0].exercises.first { it.exerciseId == "hack" }
        assertEquals(1, deloadHack.sets.size)
        assertEquals(77.5, deloadHack.sets[0].weightKg, 1e-9)

        // Pull-ups in C have 3 sets -> ceil(3/2) = 2 sets, still 0 kg added.
        val deloadPullUp = deload[2].exercises.first { it.exerciseId == "pull_up" }
        assertEquals(2, deloadPullUp.sets.size)
        assertTrue(deloadPullUp.sets.all { it.weightKg == 0.0 })

        // Leg extension: 1 set stays 1 set; 40 x 0.85 = 34 -> 35.
        val deloadLegExt = deload[2].exercises.first { it.exerciseId == "leg_ext" }
        assertEquals(1, deloadLegExt.sets.size)
        assertEquals(35.0, deloadLegExt.sets[0].weightKg, 1e-9)
    }

    // ------------------------------------------------------------ next cycle

    @Test
    fun `next cycle inputs use the achieved top weights and reps`() {
        val results = buildMap {
            plan.sessions.forEach { s ->
                put(
                    s.sessionNumber,
                    s.exercises.map { e ->
                        ExerciseResult(e.exerciseId, e.sets.map { it.targetReps })
                    },
                )
            }
        }
        val next = engine.nextCycleInputs(inputs, plan, results).associateBy { it.exerciseId }

        // Hack: last success session 22, top set 92.5 x 5.
        assertEquals(92.5, next.getValue("hack").weightKg, 1e-9)
        assertEquals(5, next.getValue("hack").reps)
        // RDL: top set 105 x 5.
        assertEquals(105.0, next.getValue("rdl").weightKg, 1e-9)
        assertEquals(5, next.getValue("rdl").reps)
        // Lateral raise: 10 kg x 15 (min across working sets).
        assertEquals(10.0, next.getValue("lat_raise").weightKg, 1e-9)
        assertEquals(15, next.getValue("lat_raise").reps)
        // Pull-ups: last occurrence is session 24 (C), 0 kg x 8 (all sets hit target 5/10 -> reps taken
        // from the non-back-off sets = top set, target 5).
        assertEquals(0.0, next.getValue("pull_up").weightKg, 1e-9)
        assertEquals(5, next.getValue("pull_up").reps)
        // Static info is preserved.
        assertEquals(hack.equipment, next.getValue("hack").equipment)
        assertEquals(hack.incrementKg, next.getValue("hack").incrementKg, 1e-9)
        assertEquals(hack.isCompound, next.getValue("hack").isCompound)
    }

    @Test
    fun `next cycle inputs use the minimum reps across working sets`() {
        val results = plan.sessions.associate { s ->
            s.sessionNumber to s.exercises.map { e ->
                val reps = e.sets.mapIndexed { i, set ->
                    if (e.exerciseId == "lat_raise" && s.sessionNumber == 22 && i == 0) 13 else set.targetReps
                }
                ExerciseResult(e.exerciseId, reps)
            }
        }
        val next = engine.nextCycleInputs(inputs, plan, results).associateBy { it.exerciseId }
        assertEquals(10.0, next.getValue("lat_raise").weightKg, 1e-9)
        assertEquals(13, next.getValue("lat_raise").reps)
    }

    @Test
    fun `exercise without a successful session keeps its previous input`() {
        val results = plan.sessions.associate { s ->
            s.sessionNumber to s.exercises.map { e ->
                if (e.exerciseId == "hack") {
                    ExerciseResult("hack", List(e.sets.size) { 0 }) // never achieved
                } else {
                    ExerciseResult(e.exerciseId, e.sets.map { it.targetReps })
                }
            }
        }
        val next = engine.nextCycleInputs(inputs, plan, results).associateBy { it.exerciseId }
        assertEquals(80.0, next.getValue("hack").weightKg, 1e-9)
        assertEquals(10, next.getValue("hack").reps)
    }

    // ------------------------------------------------------------ pull-up suggestion

    @Test
    fun `pull-up suggestion triggers at 3 sets of 8 bodyweight reps`() {
        assertTrue(engine.shouldSuggestAddingWeight(Equipment.BODYWEIGHT, 0.0, listOf(8, 8, 8)))
        assertTrue(engine.shouldSuggestAddingWeight(Equipment.BODYWEIGHT, 0.0, listOf(9, 8, 10)))
    }

    @Test
    fun `pull-up suggestion stays silent otherwise`() {
        assertFalse(engine.shouldSuggestAddingWeight(Equipment.BODYWEIGHT, 2.5, listOf(8, 8, 8)))
        assertFalse(engine.shouldSuggestAddingWeight(Equipment.BODYWEIGHT, 0.0, listOf(8, 8)))
        assertFalse(engine.shouldSuggestAddingWeight(Equipment.BODYWEIGHT, 0.0, listOf(8, 8, 7)))
        assertFalse(engine.shouldSuggestAddingWeight(Equipment.BARBELL, 0.0, listOf(8, 8, 8)))
    }

    // ------------------------------------------------------------ generation validation

    @Test(expected = IllegalArgumentException::class)
    fun `template exercise without input throws`() {
        engine.generateCycle(
            1,
            inputs,
            templates + (WorkoutLetter.B to listOf(TemplateExercise("unknown", 2))),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `all three workouts must have templates`() {
        engine.generateCycle(1, inputs, templates - WorkoutLetter.C)
    }
}
