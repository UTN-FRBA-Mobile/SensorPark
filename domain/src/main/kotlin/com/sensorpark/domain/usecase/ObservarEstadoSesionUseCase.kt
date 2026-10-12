package com.sensorpark.domain.usecase

import com.sensorpark.domain.model.EstadoSesion
import com.sensorpark.domain.model.Rol
import com.sensorpark.domain.repository.AuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

/** Combina la sesión con el rol: es lo que mira la navegación para decidir qué mostrar. */
class ObservarEstadoSesionUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<EstadoSesion> = authRepository.sesion.flatMapLatest { sesion ->
        if (sesion == null) {
            flowOf(EstadoSesion.SinSesion)
        } else {
            authRepository.rol(sesion.uid)
                .catch { emit(null) }
                .map<Rol?, EstadoSesion> { EstadoSesion.Activa(sesion, it) }
                .onStart { emit(EstadoSesion.Activa(sesion, rol = null)) }
        }
    }
}
