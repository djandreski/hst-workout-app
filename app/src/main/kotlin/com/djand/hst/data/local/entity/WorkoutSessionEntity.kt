package com.djand.hst.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.djand.hst.domain.model.WorkoutLetter
import kotlinx.serialization.Serializable

/** Lifecycle of a planned workout session. */
enum class SessionStatus { PLANNED, IN_PROGRESS, COMPLETED }

/**
 * One workout session of a cycle. Sessions 1-24 form the main cycle across 4
 * phases of 6 workouts each.
 *
 * The whole schedule is materialised up front as PLANNED rows (with their
 * prescribed [SetLogEntity] rows). The session number alone determines the workout
 * rotation and phase — no plan rewriting on failure.
 */
@Serializable
@Entity(
    tableName = "workout_sessions",
    foreignKeys = [
        ForeignKey(
            entity = CycleEntity::class,
            parentColumns = ["id"],
            childColumns = ["cycleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["cycleId", "sessionNumber"], unique = true)],
)
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cycleId: Long,
    val sessionNumber: Int,
    val phase: Int,
    @Deprecated("Replaced by phase; keep for backward compat")
    val week: Int = ((sessionNumber - 1) / 3 + 1),
    @Deprecated("Replaced by phase; keep for backward compat")
    val block: Int = 0,
    @Deprecated("No longer used; keep for backward compat")
    val isDeload: Boolean = false,
    val workoutLetter: WorkoutLetter,
    val status: SessionStatus = SessionStatus.PLANNED,
    val startedAtEpochMs: Long? = null,
    val completedAtEpochMs: Long? = null,
)
