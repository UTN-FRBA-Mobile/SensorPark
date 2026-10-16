package com.sensorpark.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/** La hora de verdad. Los casos de uso la reciben inyectada para que los tests usen una fija. */
@Module
@InstallIn(SingletonComponent::class)
object TimeModule {
    @Provides
    @Singleton
    fun clock(): Clock = Clock.systemUTC()
}
