package com.sensorpark.data.firebase

/** Nombres de colecciones y campos de Firestore. Deben coincidir con firestore.rules y functions/. */
object Colecciones {
    const val USUARIOS = "users"
    const val ESTACIONAMIENTOS = "estacionamientos"
    const val COCHERAS = "cocheras"
    const val RESERVAS = "reservas"
}

object CamposUsuario {
    const val NOMBRE = "nombre"
    const val EMAIL = "email"
    const val ROL = "rol"
    const val FCM_TOKEN = "fcmToken"
    const val CREADO_EN = "creadoEn"
}
