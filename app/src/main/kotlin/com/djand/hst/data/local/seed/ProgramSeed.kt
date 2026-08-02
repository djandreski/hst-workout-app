package com.djand.hst.data.local.seed

import com.djand.hst.data.local.entity.ExerciseEntity
import com.djand.hst.data.local.entity.TemplateExerciseEntity
import com.djand.hst.data.local.entity.WorkoutTemplateEntity
import com.djand.hst.domain.model.Equipment
import com.djand.hst.domain.model.WorkoutLetter

/**
 * The fixed HST program from the PRD, seeded into the database at creation.
 *
 * Three workouts rotate continuously. Pull-ups appear in both A and C, so the
 * catalogue holds 23 unique exercises, not 24.
 *
 * Compound flag drives the progression policy (block ladders + top/back-off sets
 * vs. flat 10-15 rep isolation work with reactive reps-first progression).
 * [ExerciseEntity.incrementKg] starts at the equipment default and follows the
 * per-equipment increment setting afterwards.
 */
object ProgramSeed {

    // ------------------------------------------------------------------ ids

    const val HACK_SQUAT = "hack_squat"
    const val INCLINE_BENCH_PRESS = "incline_bench_press"
    const val PULL_UP = "pull_up"
    const val CHEST_SUPPORTED_ROW = "chest_supported_row"
    const val DB_LATERAL_RAISE = "db_lateral_raise"
    const val SEATED_LEG_CURL = "seated_leg_curl"
    const val CABLE_TRICEPS_PUSHDOWN = "cable_triceps_pushdown"
    const val EZ_BAR_CURL = "ez_bar_curl"

    const val ROMANIAN_DEADLIFT = "romanian_deadlift"
    const val FLAT_DB_PRESS = "flat_db_press"
    const val LAT_PULLDOWN = "lat_pulldown"
    const val SEATED_CABLE_ROW = "seated_cable_row"
    const val DB_SHOULDER_PRESS = "db_shoulder_press"
    const val STANDING_CALF_RAISE = "standing_calf_raise"
    const val OVERHEAD_CABLE_TRICEPS_EXTENSION = "overhead_cable_triceps_extension"
    const val INCLINE_DB_CURL = "incline_db_curl"

    const val BULGARIAN_SPLIT_SQUAT = "bulgarian_split_squat"
    const val INCLINE_DB_PRESS = "incline_db_press"
    const val CHEST_SUPPORTED_TBAR_ROW = "chest_supported_tbar_row"
    const val CABLE_LATERAL_RAISE = "cable_lateral_raise"
    const val LEG_EXTENSION = "leg_extension"
    const val FACE_PULL = "face_pull"
    const val HAMMER_CURL = "hammer_curl"

    // ------------------------------------------------------------------ catalogue

    private fun exercise(id: String, name: String, equipment: Equipment, isCompound: Boolean) =
        ExerciseEntity(
            id = id,
            name = name,
            equipment = equipment,
            incrementKg = equipment.defaultIncrementKg,
            isCompound = isCompound,
        )

    val exercises: List<ExerciseEntity> = listOf(
        // Workout A
        exercise(HACK_SQUAT, "Hack Squat", Equipment.MACHINE, isCompound = true),
        exercise(INCLINE_BENCH_PRESS, "Incline Bench Press", Equipment.BARBELL, isCompound = true),
        exercise(PULL_UP, "Pull-ups", Equipment.BODYWEIGHT, isCompound = true),
        exercise(CHEST_SUPPORTED_ROW, "Chest Supported Row", Equipment.MACHINE, isCompound = true),
        exercise(DB_LATERAL_RAISE, "Dumbbell Lateral Raise", Equipment.DUMBBELL, isCompound = false),
        exercise(SEATED_LEG_CURL, "Seated Leg Curl", Equipment.MACHINE, isCompound = false),
        exercise(CABLE_TRICEPS_PUSHDOWN, "Cable Triceps Pushdown", Equipment.CABLE, isCompound = false),
        exercise(EZ_BAR_CURL, "EZ Bar Curl", Equipment.BARBELL, isCompound = false),

        // Workout B
        exercise(ROMANIAN_DEADLIFT, "Romanian Deadlift", Equipment.BARBELL, isCompound = true),
        exercise(FLAT_DB_PRESS, "Flat Dumbbell Press", Equipment.DUMBBELL, isCompound = true),
        exercise(LAT_PULLDOWN, "Lat Pulldown", Equipment.CABLE, isCompound = true),
        exercise(SEATED_CABLE_ROW, "Seated Cable Row", Equipment.CABLE, isCompound = true),
        exercise(DB_SHOULDER_PRESS, "Dumbbell Shoulder Press", Equipment.DUMBBELL, isCompound = true),
        exercise(STANDING_CALF_RAISE, "Standing Calf Raise", Equipment.MACHINE, isCompound = false),
        exercise(
            OVERHEAD_CABLE_TRICEPS_EXTENSION,
            "Overhead Cable Triceps Extension",
            Equipment.CABLE,
            isCompound = false,
        ),
        exercise(INCLINE_DB_CURL, "Incline Dumbbell Curl", Equipment.DUMBBELL, isCompound = false),

        // Workout C (pull-ups shared with workout A)
        exercise(BULGARIAN_SPLIT_SQUAT, "Bulgarian Split Squat", Equipment.DUMBBELL, isCompound = true),
        exercise(INCLINE_DB_PRESS, "Incline Dumbbell Press", Equipment.DUMBBELL, isCompound = true),
        exercise(CHEST_SUPPORTED_TBAR_ROW, "Chest Supported T-Bar Row", Equipment.MACHINE, isCompound = true),
        exercise(CABLE_LATERAL_RAISE, "Cable Lateral Raise", Equipment.CABLE, isCompound = false),
        exercise(LEG_EXTENSION, "Leg Extension", Equipment.MACHINE, isCompound = false),
        exercise(FACE_PULL, "Face Pull", Equipment.CABLE, isCompound = false),
        exercise(HAMMER_CURL, "Hammer Curl", Equipment.DUMBBELL, isCompound = false),
    )

    val templates: List<WorkoutTemplateEntity> =
        WorkoutLetter.entries.map { WorkoutTemplateEntity(it) }

    /**
     * Template lines in display order. Set counts come straight from the PRD.
     */
    val templateExercises: List<TemplateExerciseEntity> = buildList {
        fun row(letter: WorkoutLetter, order: Int, exerciseId: String, sets: Int) {
            add(
                TemplateExerciseEntity(
                    templateLetter = letter,
                    orderIndex = order,
                    exerciseId = exerciseId,
                    sets = sets,
                ),
            )
        }

        // Workout A
        row(WorkoutLetter.A, 0, HACK_SQUAT, 2)
        row(WorkoutLetter.A, 1, INCLINE_BENCH_PRESS, 2)
        row(WorkoutLetter.A, 2, PULL_UP, 2)
        row(WorkoutLetter.A, 3, CHEST_SUPPORTED_ROW, 2)
        row(WorkoutLetter.A, 4, DB_LATERAL_RAISE, 2)
        row(WorkoutLetter.A, 5, SEATED_LEG_CURL, 1)
        row(WorkoutLetter.A, 6, CABLE_TRICEPS_PUSHDOWN, 1)
        row(WorkoutLetter.A, 7, EZ_BAR_CURL, 1)

        // Workout B
        row(WorkoutLetter.B, 0, ROMANIAN_DEADLIFT, 2)
        row(WorkoutLetter.B, 1, FLAT_DB_PRESS, 2)
        row(WorkoutLetter.B, 2, LAT_PULLDOWN, 2)
        row(WorkoutLetter.B, 3, SEATED_CABLE_ROW, 2)
        row(WorkoutLetter.B, 4, DB_SHOULDER_PRESS, 2)
        row(WorkoutLetter.B, 5, STANDING_CALF_RAISE, 2)
        row(WorkoutLetter.B, 6, OVERHEAD_CABLE_TRICEPS_EXTENSION, 1)
        row(WorkoutLetter.B, 7, INCLINE_DB_CURL, 1)

        // Workout C
        row(WorkoutLetter.C, 0, BULGARIAN_SPLIT_SQUAT, 2)
        row(WorkoutLetter.C, 1, PULL_UP, 3)
        row(WorkoutLetter.C, 2, INCLINE_DB_PRESS, 2)
        row(WorkoutLetter.C, 3, CHEST_SUPPORTED_TBAR_ROW, 2)
        row(WorkoutLetter.C, 4, CABLE_LATERAL_RAISE, 3)
        row(WorkoutLetter.C, 5, LEG_EXTENSION, 1)
        row(WorkoutLetter.C, 6, FACE_PULL, 2)
        row(WorkoutLetter.C, 7, HAMMER_CURL, 1)
    }

    init {
        require(exercises.map { it.id }.distinct().size == 23) {
            "The program must have exactly 23 unique exercises, got ${exercises.size}"
        }
        val ids = exercises.map { it.id }.toSet()
        require(templateExercises.all { it.exerciseId in ids }) {
            "Every template line must reference a seeded exercise"
        }
        require(templateExercises.groupingBy { it.templateLetter }.eachCount().values.all { it == 8 }) {
            "Every workout template must have exactly 8 exercises"
        }
    }
}
