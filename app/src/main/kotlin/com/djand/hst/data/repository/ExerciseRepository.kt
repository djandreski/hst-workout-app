package com.djand.hst.data.repository

import com.djand.hst.data.local.dao.ExerciseDao
import com.djand.hst.data.local.entity.ExerciseEntity
import com.djand.hst.data.settings.SettingsRepository
import com.djand.hst.domain.model.Equipment
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Access to the seeded exercise catalogue. Also owns increment changes: updating a
 * per-equipment increment writes both the DataStore setting and every exercise row
 * of that equipment, so the Settings screen has a single call to make and the two
 * stores can never drift apart.
 */
@Singleton
class ExerciseRepository @Inject constructor(
    private val exerciseDao: ExerciseDao,
    private val settingsRepository: SettingsRepository,
) {

    val exercises: Flow<List<ExerciseEntity>> = exerciseDao.observeAll()

    suspend fun getAll(): List<ExerciseEntity> = exerciseDao.getAll()

    suspend fun getById(id: String): ExerciseEntity? = exerciseDao.getById(id)

    /**
     * Updates the increment for [equipment] in Settings AND on all exercises of that
     * equipment. Takes effect for the NEXT cycle; the running cycle keeps the
     * increments snapshotted in its exercise_progressions rows.
     */
    suspend fun updateEquipmentIncrement(equipment: Equipment, incrementKg: Double) {
        settingsRepository.setEquipmentIncrement(equipment, incrementKg)
        exerciseDao.updateIncrementForEquipment(equipment, incrementKg)
    }
}
