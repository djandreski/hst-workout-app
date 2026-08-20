package com.djand.hst.data.local.seed

import com.djand.hst.data.local.entity.ExerciseEntity
import com.djand.hst.data.local.entity.TemplateExerciseEntity
import com.djand.hst.data.local.entity.WorkoutTemplateEntity
import com.djand.hst.domain.model.Equipment
import com.djand.hst.domain.model.WorkoutLetter

/**
 * The fixed HST program, seeded into the database at creation.
 *
 * Three workouts rotate continuously. Pull-ups appear in both A and C, so the
 * catalogue holds 23 unique exercises, not 24.
 *
 * [ExerciseEntity.incrementKg] is the per-workout ramp step (the spreadsheet E
 * column): larger for heavy compounds, smaller for isolation/DB work. These
 * increments also serve as the rounding increment.
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

    private fun exercise(id: String, name: String, equipment: Equipment, incrementKg: Double, isCompound: Boolean) =
        ExerciseEntity(
            id = id,
            name = name,
            equipment = equipment,
            incrementKg = incrementKg,
            isCompound = isCompound,
        )

    val exercises: List<ExerciseEntity> = listOf(
        // Workout A
        exercise(HACK_SQUAT, "Hack Squat", Equipment.MACHINE, 10.0, isCompound = true),
        exercise(INCLINE_BENCH_PRESS, "Incline Bench Press", Equipment.BARBELL, 5.0, isCompound = true),
        exercise(PULL_UP, "Pull-ups", Equipment.BODYWEIGHT, 2.5, isCompound = true),
        exercise(CHEST_SUPPORTED_ROW, "Chest Supported Row", Equipment.MACHINE, 5.0, isCompound = true),
        exercise(DB_LATERAL_RAISE, "Dumbbell Lateral Raise", Equipment.DUMBBELL, 2.5, isCompound = false),
        exercise(SEATED_LEG_CURL, "Seated Leg Curl", Equipment.MACHINE, 2.5, isCompound = false),
        exercise(CABLE_TRICEPS_PUSHDOWN, "Cable Triceps Pushdown", Equipment.CABLE, 2.5, isCompound = false),
        exercise(EZ_BAR_CURL, "EZ Bar Curl", Equipment.BARBELL, 2.5, isCompound = false),

        // Workout B
        exercise(ROMANIAN_DEADLIFT, "Romanian Deadlift", Equipment.BARBELL, 10.0, isCompound = true),
        exercise(FLAT_DB_PRESS, "Flat Dumbbell Press", Equipment.DUMBBELL, 5.0, isCompound = true),
        exercise(LAT_PULLDOWN, "Lat Pulldown", Equipment.CABLE, 5.0, isCompound = true),
        exercise(SEATED_CABLE_ROW, "Seated Cable Row", Equipment.CABLE, 5.0, isCompound = true),
        exercise(DB_SHOULDER_PRESS, "Dumbbell Shoulder Press", Equipment.DUMBBELL, 5.0, isCompound = true),
        exercise(STANDING_CALF_RAISE, "Standing Calf Raise", Equipment.MACHINE, 2.5, isCompound = false),
        exercise(
            OVERHEAD_CABLE_TRICEPS_EXTENSION,
            "Overhead Cable Triceps Extension",
            Equipment.CABLE,
            2.5,
            isCompound = false,
        ),
        exercise(INCLINE_DB_CURL, "Incline Dumbbell Curl", Equipment.DUMBBELL, 2.5, isCompound = false),

        // Workout C (pull-ups shared with workout A)
        exercise(BULGARIAN_SPLIT_SQUAT, "Bulgarian Split Squat", Equipment.DUMBBELL, 5.0, isCompound = true),
        exercise(INCLINE_DB_PRESS, "Incline Dumbbell Press", Equipment.DUMBBELL, 5.0, isCompound = true),
        exercise(CHEST_SUPPORTED_TBAR_ROW, "Chest Supported T-Bar Row", Equipment.MACHINE, 5.0, isCompound = true),
        exercise(CABLE_LATERAL_RAISE, "Cable Lateral Raise", Equipment.CABLE, 2.5, isCompound = false),
        exercise(LEG_EXTENSION, "Leg Extension", Equipment.MACHINE, 2.5, isCompound = false),
        exercise(FACE_PULL, "Face Pull", Equipment.CABLE, 2.5, isCompound = false),
        exercise(HAMMER_CURL, "Hammer Curl", Equipment.DUMBBELL, 2.5, isCompound = false),
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
