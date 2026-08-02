package com.djand.hst.data.repository

import com.djand.hst.data.local.dao.BodyweightDao
import com.djand.hst.data.local.entity.BodyweightEntryEntity
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Optional bodyweight log; at most one entry per day (re-logging overwrites). */
@Singleton
class BodyweightRepository @Inject constructor(
    private val bodyweightDao: BodyweightDao,
) {

    val entries: Flow<List<BodyweightEntryEntity>> = bodyweightDao.observeAll()

    val latest: Flow<BodyweightEntryEntity?> = bodyweightDao.observeLatest()

    suspend fun log(
        weightKg: Double,
        epochDay: Long = LocalDate.now().toEpochDay(),
        now: Long = System.currentTimeMillis(),
    ) {
        require(weightKg > 0.0) { "Bodyweight must be positive, got $weightKg" }
        bodyweightDao.upsert(BodyweightEntryEntity(epochDay = epochDay, weightKg = weightKg, loggedAtEpochMs = now))
    }

    suspend fun delete(epochDay: Long) = bodyweightDao.delete(epochDay)
}
