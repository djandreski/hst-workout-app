package com.djand.hst.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.djand.hst.data.backup.BackupData
import com.djand.hst.data.local.HstDatabase
import com.djand.hst.data.local.dao.BodyweightDao
import com.djand.hst.data.local.dao.CycleDao
import com.djand.hst.data.local.dao.ExerciseDao
import com.djand.hst.data.local.dao.ExerciseProgressionDao
import com.djand.hst.data.local.dao.SessionDao
import com.djand.hst.data.local.dao.SetLogDao
import com.djand.hst.data.local.dao.TemplateDao
import com.djand.hst.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Full-database JSON export/import via the Storage Access Framework: the caller
 * passes a document [Uri] obtained from `ActivityResultContracts.CreateDocument`
 * / `OpenDocument`.
 *
 * Import replaces the ENTIRE database (and restores settings) inside a single
 * transaction, inserting rows in foreign-key order with their original ids.
 */
@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: HstDatabase,
    private val exerciseDao: ExerciseDao,
    private val templateDao: TemplateDao,
    private val cycleDao: CycleDao,
    private val progressionDao: ExerciseProgressionDao,
    private val sessionDao: SessionDao,
    private val setLogDao: SetLogDao,
    private val bodyweightDao: BodyweightDao,
    private val settingsRepository: SettingsRepository,
) {

    suspend fun exportTo(uri: Uri, now: Long = System.currentTimeMillis()) {
        val backup = BackupData(
            exportedAtEpochMs = now,
            exercises = exerciseDao.getAll(),
            workoutTemplates = templateDao.getAllTemplates(),
            templateExercises = templateDao.getAllTemplateExercises(),
            cycles = cycleDao.getAll(),
            exerciseProgressions = progressionDao.getAll(),
            workoutSessions = sessionDao.getAll(),
            setLogs = setLogDao.getAll(),
            bodyweightEntries = bodyweightDao.getAll(),
            settings = settingsRepository.settings.first(),
        )
        val json = BackupData.json.encodeToString(BackupData.serializer(), backup)
        context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
            stream.write(json.toByteArray(Charsets.UTF_8))
        } ?: throw IOException("Could not open $uri for writing")
    }

    suspend fun importFrom(uri: Uri) {
        val json = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        } ?: throw IOException("Could not open $uri for reading")
        val backup = BackupData.json.decodeFromString(BackupData.serializer(), json)
        require(backup.version == BackupData.CURRENT_VERSION) {
            "Unsupported backup version ${backup.version} (expected ${BackupData.CURRENT_VERSION})"
        }

        db.withTransaction {
            // Children first so the wipe is valid even with foreign keys enforced.
            setLogDao.deleteAll()
            sessionDao.deleteAll()
            progressionDao.deleteAll()
            cycleDao.deleteAll()
            bodyweightDao.deleteAll()
            templateDao.deleteAllTemplateExercises()
            templateDao.deleteAllTemplates()
            exerciseDao.deleteAll()

            // Parents first on the way back in.
            exerciseDao.upsertAll(backup.exercises)
            templateDao.upsertTemplates(backup.workoutTemplates)
            templateDao.upsertTemplateExercises(backup.templateExercises)
            cycleDao.insertAll(backup.cycles)
            progressionDao.upsertAll(backup.exerciseProgressions)
            sessionDao.insertAll(backup.workoutSessions)
            setLogDao.insertAll(backup.setLogs)
            bodyweightDao.upsertAll(backup.bodyweightEntries)
        }
        backup.settings?.let { settingsRepository.restore(it) }
    }
}
