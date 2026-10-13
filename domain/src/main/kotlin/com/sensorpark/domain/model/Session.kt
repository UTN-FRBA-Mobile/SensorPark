package com.sensorpark.domain.model

/**
 * Rol del usuario. Viaja como custom claim `role` en el token de Auth y lo asigna
 * únicamente el backend (ver functions/index.js); los nombres tienen que coincidir.
 */
enum class Role {
    /** Usuario final. Es el rol de toda cuenta nueva. */
    DRIVER,

    /** Administra los estacionamientos donde figura en `adminUids`. */
    PARKING_ADMIN,

    /** Acceso total. Sólo para desarrollo y pruebas. */
    ADMIN;

    companion object {
        fun from(value: Any?): Role? = entries.firstOrNull { it.name == value }
    }
}

data class Session(val uid: String, val email: String)

sealed interface SessionState {
    data object SignedOut : SessionState

    /** @param role null mientras el backend todavía no asignó el rol de una cuenta recién creada. */
    data class SignedIn(val session: Session, val role: Role?) : SessionState
}
