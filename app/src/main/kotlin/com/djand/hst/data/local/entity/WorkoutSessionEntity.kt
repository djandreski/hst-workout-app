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
 * One workout session of a cycle. Sessions 1-24 form the main 8-week cycle
 * ([isDeload] = false), sessions 25-27 are the deload week (week 9, block 0).
 *
 * The whole schedule is materialised up front as PLANNED rows (with their
 * prescribed [SetLogEntity] rows), which is what makes progression deterministic:
 * rewrites caused by misses or isolation bumps only touch future PENDING sets.
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
    val week: Int,
    val block: Int,
    val workoutLetter: WorkoutLetter,
    val isDeload: Boolean,
    val status: SessionStatus = SessionStatus.PLANNED,
    val startedAtEpochMs: Long? = null,
    val completedAtEpochMs: Long? = null,
)
