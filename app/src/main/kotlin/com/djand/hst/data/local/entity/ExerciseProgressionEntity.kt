package com.djand.hst.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import kotlinx.serialization.Serializable

/**
 * Per-cycle progression state of one exercise.
 *
 * [baseWeightKg] x [baseReps] is the rep-max input the cycle's plan was generated
 * from. The derived Epley rep maxes ([rm15Kg], [rm10Kg], [rm5Kg]) and the
 * [incrementKg] actually used for rounding are persisted so the persisted plan can
 * be re-evaluated (repeat / -10% reset / isolation bump) deterministically even if
 * the catalogue increments change later.
 *
 * [consecutiveMisses] is the miss counter the engine reads and rewrites after every
 * completed session. [pullUpSuggestAddingWeight] is set once bodyweight pull-ups
 * reach 3 sets of 8 reps ("Start adding weight").
 */
@Serializable
@Entity(
    tableName = "exercise_progressions",
    primaryKeys = ["cycleId", "exerciseId"],
    foreignKeys = [
        ForeignKey(
            entity = CycleEntity::class,
            parentColumns = ["id"],
            childColumns = ["cycleId"],
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
data class ExerciseProgressionEntity(
    val cycleId: Long,
    val exerciseId: String,
    val baseWeightKg: Double,
    val baseReps: Int,
    val rm15Kg: Double,
    val rm10Kg: Double,
    val rm5Kg: Double,
    val incrementKg: Double,
    val consecutiveMisses: Int = 0,
    val pullUpSuggestAddingWeight: Boolean = false,
)
