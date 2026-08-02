package com.djand.hst.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.djand.hst.domain.model.Equipment
import kotlinx.serialization.Serializable

/**
 * One of the 23 unique exercises of the fixed HST program (pull-ups are shared by
 * workouts A and C). Seeded once at database creation; the catalogue is static.
 *
 * [id] is a stable slug (e.g. "hack_squat") so it doubles as a readable key in
 * JSON backups. [incrementKg] is the effective weight-step used to round this
 * exercise's prescriptions; it is seeded from the equipment default and updated
 * when the per-equipment increment changes in Settings.
 */
@Serializable
@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val equipment: Equipment,
    val incrementKg: Double,
    val isCompound: Boolean,
)
