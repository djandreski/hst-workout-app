package com.djand.hst.di

import com.djand.hst.domain.progression.ProgressionEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the pure-Kotlin domain services. Binding happens here instead of via
 * `@Inject` constructors so the domain layer stays dependency-free. The engine is
 * stateless (all progression state lives in the database), so one process-wide
 * instance is sufficient.
 */
@Module
@InstallIn(SingletonComponent::class)
object DomainModule {

    @Provides
    @Singleton
    fun provideProgressionEngine(): ProgressionEngine = ProgressionEngine()
}
