package com.sensorpark.data.di

import com.sensorpark.data.firebase.AuthRepositoryFirebase
import com.sensorpark.data.firebase.DeviceRepositoryFirebase
import com.sensorpark.domain.repository.AuthRepository
import com.sensorpark.domain.repository.DeviceRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Une cada interfaz de `domain` con su implementación. Los ViewModels piden la interfaz;
 * para sumar un repositorio se agrega acá su @Binds, y en un test se reemplaza por uno falso.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoriesModule {
    @Binds
    @Singleton
    abstract fun authRepository(impl: AuthRepositoryFirebase): AuthRepository

    @Binds
    @Singleton
    abstract fun deviceRepository(impl: DeviceRepositoryFirebase): DeviceRepository
}
