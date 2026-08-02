package com.djand.hst.data.repository

import com.djand.hst.data.local.dao.TemplateDao
import com.djand.hst.data.local.relation.TemplateWithExercises
import com.djand.hst.domain.model.TemplateExercise
import com.djand.hst.domain.model.WorkoutLetter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Access to the seeded A/B/C workout templates. */
@Singleton
class TemplateRepository @Inject constructor(
    private val templateDao: TemplateDao,
) {

    /** Templates with their exercise lines, exercises sorted in display order. */
    val templates: Flow<List<TemplateWithExercises>> =
        templateDao.observeTemplatesWithExercises().map { list -> list.map { it.sorted() } }

    /** Templates mapped to the domain shape consumed by the progression engine. */
    suspend fun getDomainTemplates(): Map<WorkoutLetter, List<TemplateExercise>> =
        templateDao.getTemplatesWithExercises().associate { template ->
            template.template.letter to template.sorted().exercises.map {
                TemplateExercise(
                    exerciseId = it.exercise.id,
                    sets = it.templateExercise.sets,
                )
            }
        }

    private fun TemplateWithExercises.sorted(): TemplateWithExercises =
        copy(exercises = exercises.sortedBy { it.templateExercise.orderIndex })
}
