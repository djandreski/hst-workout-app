package com.djand.hst.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * One 8-week HST cycle (24 sessions) plus its deload week (3 sessions). The active
 * cycle is the one with [completedAtEpochMs] == null; it becomes complete once the
 * last deload session is done. A brand-new cycle is started afterwards.
 */
@Serializable
@Entity(
    tableName = "cycles",
    indices = [Index(value = ["cycleNumber"], unique = true)],
)
data class CycleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cycleNumber: Int,
    val startedAtEpochMs: Long,
    val completedAtEpochMs: Long? = null,
)
