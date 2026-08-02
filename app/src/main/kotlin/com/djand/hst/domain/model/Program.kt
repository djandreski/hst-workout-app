package com.djand.hst.domain.model

/**
 * The three workouts of the program. They rotate continuously regardless of week or
 * block boundaries: session 1 = A, session 2 = B, session 3 = C, session 4 = A, ...
 */
enum class WorkoutLetter { A, B, C }

/**
 * One line of a workout template: which exercise is performed and how many work sets.
 * The engine receives templates as input; the concrete program (A/B/C exercise lists)
 * is seeded into the database by the data layer.
 */
data class TemplateExercise(
    val exerciseId: String,
    val sets: Int,
) {
    init {
        require(sets >= 1) { "An exercise must have at least one set, got $sets" }
    }
}
