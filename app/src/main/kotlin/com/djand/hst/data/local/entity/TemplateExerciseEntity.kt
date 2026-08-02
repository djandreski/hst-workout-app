package com.djand.hst.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.djand.hst.domain.model.WorkoutLetter
import kotlinx.serialization.Serializable

/**
 * One line of a workout template: [exerciseId] is performed as the [orderIndex]-th
 * exercise of [templateLetter] for [sets] work sets.
 */
@Serializable
@Entity(
    tableName = "template_exercises",
    primaryKeys = ["templateLetter", "orderIndex"],
    foreignKeys = [
        ForeignKey(
            entity = WorkoutTemplateEntity::class,
            parentColumns = ["letter"],
            childColumns = ["templateLetter"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("exerciseId")],
)
data class TemplateExerciseEntity(
    val templateLetter: WorkoutLetter,
    val orderIndex: Int,
    val exerciseId: String,
    val sets: Int,
)
