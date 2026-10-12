package com.sensorpark.domain.model

enum class MotivoErrorAuth {
    CREDENCIALES_INVALIDAS,
    EMAIL_EN_USO,
    PASSWORD_DEBIL,
    SIN_CONEXION,
    DESCONOCIDO,
}

/** Error de ingreso o registro, ya traducido para que la UI no conozca al proveedor de Auth. */
class AuthException(val motivo: MotivoErrorAuth, cause: Throwable? = null) : Exception(motivo.name, cause)
