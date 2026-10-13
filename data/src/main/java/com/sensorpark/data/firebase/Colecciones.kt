package com.sensorpark.data.firebase

/** Nombres de colecciones y campos de Firestore. Deben coincidir con firestore.rules y functions/. */
object Colecciones {
    const val USUARIOS = "usuarios"
    const val ESTACIONAMIENTOS = "estacionamientos"

    /** Subcolección de cada estacionamiento. */
    const val LUGARES = "lugares"

    /** Subcolección de cada estacionamiento; el id del documento es la fecha (AAAA-MM-DD). */
    const val CUPOS = "cupos"
    const val RESERVAS = "reservas"
    const val LISTA_ESPERA = "listaEspera"
}

object CamposUsuario {
    const val NOMBRE = "nombre"
    const val EMAIL = "email"
    const val ROL = "rol"
    const val CATEGORIA = "categoria"
    const val FCM_TOKEN = "fcmToken"
    const val CREADO_EN = "creadoEn"
}
