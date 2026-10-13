package com.sensorpark.data.firebase

/** Nombres de colecciones y campos de Firestore. Deben coincidir con firestore.rules y functions/. */
object Collections {
    const val USERS = "users"
    const val PARKING_LOTS = "parkingLots"

    /** Subcolección de cada estacionamiento: sus lugares. */
    const val SPOTS = "spots"

    /** Subcolección de cada estacionamiento: cupos por categoría. El id del documento es la fecha (AAAA-MM-DD). */
    const val QUOTAS = "quotas"
    const val RESERVATIONS = "reservations"
    const val WAITLIST = "waitlist"
}

object UserFields {
    const val NAME = "name"
    const val EMAIL = "email"
    const val ROLE = "role"
    const val CATEGORY = "category"
    const val FCM_TOKEN = "fcmToken"
    const val CREATED_AT = "createdAt"
}
