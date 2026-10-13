package com.sensorpark.domain.usecase

import com.sensorpark.domain.model.Role
import com.sensorpark.domain.model.SessionState
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
class ObserveSessionStateUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<SessionState> = authRepository.session.flatMapLatest { session ->
        if (session == null) {
            flowOf(SessionState.SignedOut)
        } else {
            authRepository.role(session.uid)
                .catch { emit(null) }
                .map<Role?, SessionState> { SessionState.SignedIn(session, it) }
                .onStart { emit(SessionState.SignedIn(session, role = null)) }
        }
    }
}
