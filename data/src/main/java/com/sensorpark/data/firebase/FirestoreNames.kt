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

    /** Rol que pide el usuario al registrarse; `assignInitialRole` lo valida y lo borra. */
    const val REQUESTED_ROLE = "requestedRole"
    const val CATEGORY = "category"
    const val FCM_TOKEN = "fcmToken"
    const val CREATED_AT = "createdAt"
}

object ParkingLotFields {
    const val NAME = "name"
    const val ADDRESS = "address"

    /** `GeoPoint`. */
    const val LOCATION = "location"

    /** Mapa con `opensAt` y `closesAt`, en formato "HH:mm". */
    const val SCHEDULE = "schedule"
    const val OPENS_AT = "opensAt"
    const val CLOSES_AT = "closesAt"
    const val TOLERANCE_MINUTES = "toleranceMinutes"
    const val GEOFENCE_RADIUS_METERS = "geofenceRadiusMeters"
    const val HOURLY_RATE = "hourlyRate"
    const val ADMIN_UIDS = "adminUids"

    // Contadores: los calcula la function updateFreeSpotCounters, la app sólo los lee.
    const val CAPACITY = "capacity"
    const val FREE_SPOTS = "freeSpots"
    const val CAPACITY_BY_TYPE = "capacityByType"
    const val FREE_SPOTS_BY_TYPE = "freeSpotsByType"
}

object SpotFields {
    const val CODE = "code"
    const val TYPE = "type"
    const val STATUS = "status"
    const val UPDATED_AT = "updatedAt"
}
