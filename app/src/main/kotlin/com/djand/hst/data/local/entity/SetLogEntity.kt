package com.djand.hst.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.djand.hst.domain.model.SetKind
import kotlinx.serialization.Serializable

/** Outcome of one prescribed set. */
enum class SetStatus {
    /** Prescribed but not attempted yet. */
    PENDING,

    /** Attempted; [SetLogEntity.completedReps] holds the actual reps. */
    DONE,

    /** Not attempted (exercise skipped); no progression impact. */
    SKIPPED,
}

/**
 * One prescribed and/or performed set. Rows are created as PENDING when the cycle
 * is generated and double as the exercise history once the session is completed.
 *
 * [exerciseIndex] is the position of the exercise within the session (template
 * order) and [setIndex] the position of the set within the exercise, so the
 * ordered session structure can be rebuilt from these flat rows.
 *
 * Exercise-level notes are stored on the first set row ([setIndex] == 0) by
 * convention.
 */
@Serializable
@Entity(
    tableName = "set_logs",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["sessionId", "exerciseId", "setIndex"], unique = true),
        Index("exerciseId"),
    ],
)
data class SetLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: String,
    val exerciseIndex: Int,
    val setIndex: Int,
    val kind: SetKind,
    val prescribedWeightKg: Double,
    val prescribedTargetReps: Int,
    val prescribedMinReps: Int,
    val completedReps: Int? = null,
    val status: SetStatus = SetStatus.PENDING,
    val notes: String? = null,
)
