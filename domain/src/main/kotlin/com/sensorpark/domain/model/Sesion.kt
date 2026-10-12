package com.sensorpark.domain.model

/**
 * Rol del usuario. Viaja como custom claim `rol` en el token de Auth y lo asigna
 * únicamente el backend (ver functions/index.js); los nombres tienen que coincidir.
 */
enum class Rol {
    CONDUCTOR,
    ADMIN_ESTACIONAMIENTO,
    ADMIN;

    companion object {
        fun desde(valor: Any?): Rol? = entries.firstOrNull { it.name == valor }
    }
}

data class Sesion(val uid: String, val email: String)

sealed interface EstadoSesion {
    data object SinSesion : EstadoSesion

    /** @param rol null mientras el backend todavía no asignó el rol de una cuenta recién creada. */
    data class Activa(val sesion: Sesion, val rol: Rol?) : EstadoSesion
}
