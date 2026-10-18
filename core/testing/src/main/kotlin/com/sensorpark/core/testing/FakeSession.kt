package com.sensorpark.core.testing

import com.sensorpark.domain.model.Category
import com.sensorpark.domain.model.Role
import com.sensorpark.domain.model.Session
import com.sensorpark.domain.repository.AuthRepository
import com.sensorpark.domain.repository.DeviceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sesión falsa. Arranca con un conductor con sesión iniciada, que es lo que necesitan casi todas
 * las pantallas; `signOut` y `signIn` la cambian como lo haría Firebase Auth.
 */
class FakeAuthRepository(
    session: Session? = Session(uid = "ana", email = "ana@sensorpark.test"),
    role: Role? = Role.DRIVER,
) : AuthRepository {

    private val state = MutableStateFlow(session)
    private val roleState = MutableStateFlow(role)
    val signedUp = mutableListOf<String>()

    override val session: Flow<Session?> = state.asStateFlow()

    override suspend fun signIn(email: String, password: String) {
        state.value = Session(uid = email.substringBefore("@"), email = email)
    }

    override suspend fun signUp(name: String, email: String, password: String, role: Role, category: Category) {
        signedUp += email
        // Como el backend: la cuenta existe antes de tener rol, que se asigna unos segundos después.
        roleState.value = null
        state.value = Session(uid = email.substringBefore("@"), email = email)
    }

    override fun signOut() {
        state.value = null
    }

    override fun role(uid: String): Flow<Role?> = roleState.asStateFlow()

    /** Simula que el backend ya le asignó el rol a la cuenta recién creada. */
    fun assignRole(role: Role) {
        roleState.value = role
    }
}

/** Registro de dispositivo falso: sólo anota cuántas veces le pidieron registrar el token. */
class FakeDeviceRepository : DeviceRepository {
    var registrations = 0
        private set

    override suspend fun registerCurrentToken() {
        registrations++
    }
}
