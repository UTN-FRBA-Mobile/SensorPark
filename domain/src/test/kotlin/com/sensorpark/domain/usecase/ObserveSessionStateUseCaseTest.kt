package com.sensorpark.domain.usecase

import com.sensorpark.domain.model.Category
import com.sensorpark.domain.model.Role
import com.sensorpark.domain.model.Session
import com.sensorpark.domain.model.SessionState
import com.sensorpark.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveSessionStateUseCaseTest {
    private val ana = Session(uid = "ana", email = "ana@ejemplo.com")

    // Ejemplo de repositorio falso: es lo que permite probar sin Firebase.
    private class FakeAuthRepository(session: Session?, role: Role?) : AuthRepository {
        override val session = MutableStateFlow(session)
        val currentRole = MutableStateFlow(role)

        override suspend fun signIn(email: String, password: String) = Unit
        override suspend fun signUp(
            name: String,
            email: String,
            password: String,
            role: Role,
            category: Category,
        ) = Unit
        override fun signOut() { session.value = null }
        override fun role(uid: String): Flow<Role?> = currentRole
    }

    @Test
    fun `sin sesion emite SignedOut`() = runTest {
        val observe = ObserveSessionStateUseCase(FakeAuthRepository(session = null, role = null))

        assertEquals(SessionState.SignedOut, observe().first())
    }

    @Test
    fun `con sesion emite primero sin rol y despues el rol asignado`() = runTest {
        val observe = ObserveSessionStateUseCase(FakeAuthRepository(session = ana, role = Role.DRIVER))

        assertEquals(
            listOf(SessionState.SignedIn(ana, role = null), SessionState.SignedIn(ana, Role.DRIVER)),
            observe().take(2).toList(),
        )
    }
}
