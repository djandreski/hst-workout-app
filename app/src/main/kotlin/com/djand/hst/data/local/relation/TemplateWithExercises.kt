package com.djand.hst.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.djand.hst.data.local.entity.ExerciseEntity
import com.djand.hst.data.local.entity.TemplateExerciseEntity
import com.djand.hst.data.local.entity.WorkoutTemplateEntity

/** One template line joined with its exercise catalogue entry. */
data class TemplateExerciseWithExercise(
    @Embedded val templateExercise: TemplateExerciseEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity,
)

/**
 * A workout template with its full exercise list. NOTE: Room relations are not
 * ordered — consumers must sort [exercises] by
 * [TemplateExerciseEntity.orderIndex].
 */
data class TemplateWithExercises(
    @Embedded val template: WorkoutTemplateEntity,
    @Relation(entity = TemplateExerciseEntity::class, parentColumn = "letter", entityColumn = "templateLetter")
    val exercises: List<TemplateExerciseWithExercise>,
)
