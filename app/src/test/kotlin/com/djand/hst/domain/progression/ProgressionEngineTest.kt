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

class ProgressionEngineTest {

    private val hack = ExerciseInput("hack", Equipment.MACHINE, 5.0, isCompound = true, weightKg = 80.0, reps = 10)
    private val latRaise = ExerciseInput("lat_raise", Equipment.DUMBBELL, 2.5, isCompound = false, weightKg = 10.0, reps = 12)
    private val pullUp = ExerciseInput("pull_up", Equipment.BODYWEIGHT, 2.5, isCompound = true, weightKg = 0.0, reps = 8)
    private val rdl = ExerciseInput("rdl", Equipment.BARBELL, 10.0, isCompound = true, weightKg = 100.0, reps = 5)
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

    @Test
    fun cycleHas24Sessions() {
        assertEquals(24, plan.sessions.size)
        assertEquals((1..24).toList(), plan.sessions.map { it.sessionNumber })
        assertEquals(1, plan.cycleNumber)
    }

    @Test
    fun workoutsRotateABC() {
        val expected = List(24) { WorkoutLetter.entries[it % 3] }
        assertEquals(expected, plan.sessions.map { it.workout })
        assertEquals(WorkoutLetter.A, ProgressionEngine.workoutForSession(1))
        assertEquals(WorkoutLetter.B, ProgressionEngine.workoutForSession(2))
        assertEquals(WorkoutLetter.C, ProgressionEngine.workoutForSession(3))
        assertEquals(WorkoutLetter.A, ProgressionEngine.workoutForSession(4))
    }

    @Test
    fun sessionsMapToPhases1Through4() {
        assertEquals(1, ProgressionEngine.phaseForSession(1))
        assertEquals(1, ProgressionEngine.phaseForSession(6))
        assertEquals(2, ProgressionEngine.phaseForSession(7))
        assertEquals(2, ProgressionEngine.phaseForSession(12))
        assertEquals(3, ProgressionEngine.phaseForSession(13))
        assertEquals(3, ProgressionEngine.phaseForSession(18))
        assertEquals(4, ProgressionEngine.phaseForSession(19))
        assertEquals(4, ProgressionEngine.phaseForSession(24))
    }

    @Test
    fun sessionsCarryCorrectPhaseNumber() {
        assertEquals(1, session(1).phase)
        assertEquals(1, session(6).phase)
        assertEquals(2, session(7).phase)
        assertEquals(3, session(13).phase)
        assertEquals(4, session(19).phase)
        assertEquals(4, session(24).phase)
    }

    @Test
    fun templateOrderAndSetCountsPreserved() {
        assertEquals(listOf("hack", "lat_raise", "pull_up"), session(1).exercises.map { it.exerciseId })
        assertEquals(2, exercise(1, "hack").sets.size)
        assertEquals(2, exercise(1, "pull_up").sets.size)
        assertEquals(3, exercise(3, "pull_up").sets.size)
        assertEquals(1, exercise(3, "leg_ext").sets.size)
    }

    @Test
    fun phase1Prescribes15Reps() {
        for (n in listOf(1, 4)) {
            exercise(n, "hack").sets.forEach {
                assertEquals(15, it.targetReps)
                assertEquals(15, it.minReps)
                assertEquals(SetKind.NORMAL, it.kind)
            }
        }
    }

    @Test
    fun phase2Prescribes10Reps() {
        for (n in listOf(7, 10)) {
            exercise(n, "hack").sets.forEach {
                assertEquals(10, it.targetReps)
                assertEquals(10, it.minReps)
                assertEquals(SetKind.NORMAL, it.kind)
            }
        }
    }

    @Test
    fun phase3Prescribes5Reps() {
        for (n in listOf(13, 16)) {
            exercise(n, "hack").sets.forEach {
                assertEquals(5, it.targetReps)
                assertEquals(5, it.minReps)
                assertEquals(SetKind.NORMAL, it.kind)
            }
        }
    }

    @Test
    fun phase4HasNormalThenNegativeWorkouts() {
        val s19 = exercise(19, "hack").sets
        s19.forEach {
            assertEquals(5, it.targetReps)
            assertEquals(SetKind.NORMAL, it.kind)
        }
        val s22 = exercise(22, "hack").sets
        s22.forEach {
            assertEquals(5, it.targetReps)
            assertEquals(SetKind.NEGATIVE, it.kind)
        }
    }

    @Test
    fun phase1HackSquatRampMatchesIncrements() {
        assertEquals(45.0, weightOf(1, "hack"), 1e-9)
        assertEquals(60.0, weightOf(4, "hack"), 1e-9)
    }

    @Test
    fun phase2HackSquatRampsTo10RM() {
        assertEquals(55.0, weightOf(7, "hack"), 1e-9)
        assertEquals(70.0, weightOf(10, "hack"), 1e-9)
    }

    @Test
    fun phase3HackSquatRampsTo5RM() {
        assertEquals(65.0, weightOf(13, "hack"), 1e-9)
        assertEquals(80.0, weightOf(16, "hack"), 1e-9)
    }

    @Test
    fun phase4GoesBeyond5RM() {
        assertEquals(95.0, weightOf(19, "hack"), 1e-9)
        assertEquals(110.0, weightOf(22, "hack"), 1e-9)
    }

    @Test
    fun phaseBoundariesDropWeight() {
        assertTrue(weightOf(7, "hack") < weightOf(4, "hack"))
        assertTrue(weightOf(13, "hack") < weightOf(10, "hack"))
    }

    @Test
    fun weightsIncreaseWithinEachPhase() {
        assertTrue(weightOf(1, "hack") <= weightOf(4, "hack"))
        assertTrue(weightOf(7, "hack") <= weightOf(10, "hack"))
        assertTrue(weightOf(13, "hack") <= weightOf(16, "hack"))
        assertTrue(weightOf(19, "hack") <= weightOf(22, "hack"))
    }

    @Test
    fun rdlRampMatchesIncrements() {
        assertEquals(40.0, weightOf(2, "rdl"), 1e-9)
        assertEquals(70.0, weightOf(5, "rdl"), 1e-9)
        assertEquals(50.0, weightOf(8, "rdl"), 1e-9)
        assertEquals(80.0, weightOf(11, "rdl"), 1e-9)
        assertEquals(60.0, weightOf(14, "rdl"), 1e-9)
        assertEquals(90.0, weightOf(17, "rdl"), 1e-9)
    }

    @Test
    fun rdlPhase4ContinuesBeyond5RM() {
        assertEquals(120.0, weightOf(20, "rdl"), 1e-9)
        assertEquals(150.0, weightOf(23, "rdl"), 1e-9)
    }

    @Test
    fun rdlPhase4NegativesInLastThreeWorkouts() {
        exercise(20, "rdl").sets.forEach {
            assertEquals(SetKind.NORMAL, it.kind)
        }
        exercise(23, "rdl").sets.forEach {
            assertEquals(SetKind.NEGATIVE, it.kind)
        }
    }

    @Test
    fun roundingKeepsLaddersLoadable() {
        for (s in plan.sessions) {
            for (e in s.exercises) {
                for (set in e.sets) {
                    val remainder = set.weightKg % e.incrementKg
                    assertTrue("Weight ${set.weightKg} not multiple of increment ${e.incrementKg}",
                        Math.abs(remainder) < 1e-9 || Math.abs(remainder - e.incrementKg) < 1e-9)
                }
            }
        }
    }

    @Test
    fun bodyweightPullUpsStayAtZero() {
        val occurrences = plan.sessions.flatMap { s ->
            s.exercises.filter { it.exerciseId == "pull_up" }.flatMap { it.sets }
        }
        assertTrue(occurrences.isNotEmpty())
        assertTrue(occurrences.all { it.weightKg == 0.0 })
    }

    @Test
    fun pullUpsAppearInBothWorkouts() {
        val sessionsWithPullUps = plan.sessions.count { s ->
            s.exercises.any { it.exerciseId == "pull_up" }
        }
        assertEquals(16, sessionsWithPullUps)
    }

    @Test
    fun achievedSessionPreservesPlan() {
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
    fun missedSetRecordsMissWithIncrementedCounter() {
        val evaluation = engine.evaluateSession(
            plan, 13,
            results = listOf(ExerciseResult("hack", listOf(5, 4))),
        )
        val outcome = evaluation.outcomes.single()
        assertEquals(ProgressionEvent.MISSED, outcome.event)
        assertEquals(1, outcome.consecutiveMisses)
        assertEquals(plan, evaluation.plan)
    }

    @Test
    fun secondConsecutiveMissIncrementsCounter() {
        val evaluation = engine.evaluateSession(
            plan, 13,
            results = listOf(ExerciseResult("hack", listOf(4, 3))),
            consecutiveMisses = mapOf("hack" to 1),
        )
        val outcome = evaluation.outcomes.single()
        assertEquals(ProgressionEvent.MISSED, outcome.event)
        assertEquals(2, outcome.consecutiveMisses)
        assertEquals(plan, evaluation.plan)
    }

    @Test
    fun missedOnLastOccurrenceStillRecords() {
        val evaluation = engine.evaluateSession(
            plan, 22,
            results = listOf(ExerciseResult("hack", listOf(3, 4))),
        )
        assertEquals(ProgressionEvent.MISSED, evaluation.outcomes.single().event)
        assertEquals(1, evaluation.outcomes.single().consecutiveMisses)
    }

    @Test
    fun skippedExerciseHasNoProgressionImpact() {
        val evaluation = engine.evaluateSession(
            plan, 13,
            results = listOf(ExerciseResult("hack", emptyList(), skipped = true)),
            consecutiveMisses = mapOf("hack" to 1),
        )
        val outcome = evaluation.outcomes.single()
        assertEquals(ProgressionEvent.SKIPPED, outcome.event)
        assertEquals(1, outcome.consecutiveMisses)
        assertEquals(plan, evaluation.plan)
    }

    @Test
    fun missingSetsCountAsZeroReps() {
        val evaluation = engine.evaluateSession(
            plan, 13,
            results = listOf(ExerciseResult("hack", listOf(5))),
        )
        assertEquals(ProgressionEvent.MISSED, evaluation.outcomes.single().event)
    }

    @Test(expected = IllegalArgumentException::class)
    fun evaluatingUnknownSessionThrows() {
        engine.evaluateSession(plan, 99, emptyList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun evaluatingUnknownExerciseThrows() {
        engine.evaluateSession(plan, 1, listOf(ExerciseResult("nope", listOf(10))))
    }

    @Test
    fun nextCycleInputsUseLastAchievedWeights() {
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
        assertEquals(110.0, next.getValue("hack").weightKg, 1e-9)
        assertEquals(5, next.getValue("hack").reps)
        assertEquals(150.0, next.getValue("rdl").weightKg, 1e-9)
        assertEquals(5, next.getValue("rdl").reps)
        assertEquals(0.0, next.getValue("pull_up").weightKg, 1e-9)
        assertEquals(5, next.getValue("pull_up").reps)
        assertEquals(hack.equipment, next.getValue("hack").equipment)
        assertEquals(hack.incrementKg, next.getValue("hack").incrementKg, 1e-9)
    }

    @Test
    fun failedExerciseKeepsPreviousInput() {
        val results = plan.sessions.associate { s ->
            s.sessionNumber to s.exercises.map { e ->
                if (e.exerciseId == "hack") {
                    ExerciseResult("hack", List(e.sets.size) { 0 })
                } else {
                    ExerciseResult(e.exerciseId, e.sets.map { it.targetReps })
                }
            }
        }
        val next = engine.nextCycleInputs(inputs, plan, results).associateBy { it.exerciseId }
        assertEquals(80.0, next.getValue("hack").weightKg, 1e-9)
        assertEquals(10, next.getValue("hack").reps)
    }

    @Test
    fun pullUpSuggestionTriggersAt3x8() {
        assertTrue(engine.shouldSuggestAddingWeight(Equipment.BODYWEIGHT, 0.0, listOf(8, 8, 8)))
        assertTrue(engine.shouldSuggestAddingWeight(Equipment.BODYWEIGHT, 0.0, listOf(9, 8, 10)))
    }

    @Test
    fun pullUpSuggestionSilentOtherwise() {
        assertFalse(engine.shouldSuggestAddingWeight(Equipment.BODYWEIGHT, 2.5, listOf(8, 8, 8)))
        assertFalse(engine.shouldSuggestAddingWeight(Equipment.BODYWEIGHT, 0.0, listOf(8, 8)))
        assertFalse(engine.shouldSuggestAddingWeight(Equipment.BODYWEIGHT, 0.0, listOf(8, 8, 7)))
        assertFalse(engine.shouldSuggestAddingWeight(Equipment.BARBELL, 0.0, listOf(8, 8, 8)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun templateExerciseWithoutInputThrows() {
        engine.generateCycle(
            1,
            inputs,
            templates + (WorkoutLetter.B to listOf(TemplateExercise("unknown", 2))),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun allThreeWorkoutsRequired() {
        engine.generateCycle(1, inputs, templates - WorkoutLetter.C)
    }

    @Test
    fun phaseNamesAreCorrect() {
        assertEquals("15 RM Phase", ProgressionEngine.phaseName(1))
        assertEquals("10 RM Phase", ProgressionEngine.phaseName(2))
        assertEquals("5 RM Phase", ProgressionEngine.phaseName(3))
        assertEquals("Post-5RM Phase", ProgressionEngine.phaseName(4))
    }

    @Test
    fun targetRepsForPhaseAreCorrect() {
        assertEquals(15, ProgressionEngine.targetRepsForPhase(1))
        assertEquals(10, ProgressionEngine.targetRepsForPhase(2))
        assertEquals(5, ProgressionEngine.targetRepsForPhase(3))
        assertEquals(5, ProgressionEngine.targetRepsForPhase(4))
        assertEquals(5, ProgressionEngine.targetRepsForPhase(99))
    }

    @Test
    fun lightIsolationRoundsToPositiveAboveZero() {
        val s1 = exercise(1, "lat_raise")
        s1.sets.forEach { assertTrue(it.weightKg >= 0.0) }
        val s7 = exercise(7, "lat_raise")
        s7.sets.forEach { assertTrue(it.weightKg >= 0.0) }
    }

    @Test
    fun legExtensionFollowsLinearRamp() {
        assertEquals(27.5, weightOf(3, "leg_ext"), 1e-9)
        assertEquals(35.0, weightOf(6, "leg_ext"), 1e-9)
        assertEquals(32.5, weightOf(9, "leg_ext"), 1e-9)
        assertEquals(40.0, weightOf(12, "leg_ext"), 1e-9)
        assertEquals(37.5, weightOf(15, "leg_ext"), 1e-9)
        assertEquals(45.0, weightOf(18, "leg_ext"), 1e-9)
        assertEquals(52.5, weightOf(21, "leg_ext"), 1e-9)
        assertEquals(60.0, weightOf(24, "leg_ext"), 1e-9)
    }
}
