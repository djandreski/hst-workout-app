package com.djand.hst.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.djand.hst.data.local.entity.SetLogEntity
import com.djand.hst.data.local.entity.WorkoutSessionEntity

/**
 * A workout session with all of its set logs. NOTE: Room relations are not
 * ordered — consumers must sort [setLogs] by
 * [SetLogEntity.exerciseIndex] then [SetLogEntity.setIndex].
 */
data class SessionWithSetLogs(
    @Embedded val session: WorkoutSessionEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionId")
    val setLogs: List<SetLogEntity>,
)
