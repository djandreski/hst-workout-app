package com.djand.hst.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Optional bodyweight log: at most one entry per day ([epochDay] is the primary
 * key, so re-logging the same day overwrites).
 */
@Serializable
@Entity(tableName = "bodyweight_entries")
data class BodyweightEntryEntity(
    @PrimaryKey val epochDay: Long,
    val weightKg: Double,
    val loggedAtEpochMs: Long,
)
