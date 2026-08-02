package com.djand.hst.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.djand.hst.domain.model.WorkoutLetter
import kotlinx.serialization.Serializable

/**
 * One of the three workout templates (A, B or C). The exercise list and set counts
 * live in [TemplateExerciseEntity].
 */
@Serializable
@Entity(tableName = "workout_templates")
data class WorkoutTemplateEntity(
    @PrimaryKey val letter: WorkoutLetter,
)
