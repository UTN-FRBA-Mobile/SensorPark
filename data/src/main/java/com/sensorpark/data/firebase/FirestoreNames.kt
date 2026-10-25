package com.sensorpark.data.firebase

/** Nombres de colecciones y campos de Firestore. Deben coincidir con firestore.rules y functions/. */
object Collections {
    const val USERS = "users"
    const val PARKING_LOTS = "parkingLots"

    /** Subcolección de cada estacionamiento: sus lugares. */
    const val SPOTS = "spots"

    const val RESERVATIONS = "reservations"
    const val WAITLIST = "waitlist"

    /** Subcolección de cada usuario: sus estacionamientos favoritos (T19), con el id del estacionamiento. */
    const val FAVORITES = "favorites"

    /** Subcolección de cada usuario: un documento por teléfono, con su token FCM como id. */
    const val DEVICES = "devices"
}

object UserFields {
    const val NAME = "name"
    const val EMAIL = "email"
    const val ROLE = "role"

    /** Rol que pide el usuario al registrarse; `assignInitialRole` lo valida y lo borra. */
    const val REQUESTED_ROLE = "requestedRole"
    const val CATEGORY = "category"

    /** Token de las versiones anteriores, uno solo por usuario. Ahora van en [Collections.DEVICES]. */
    const val FCM_TOKEN = "fcmToken"

    /** Tipos de aviso que el usuario apagó, por nombre de `NotificationType`. Lo lee T17. */
    const val NOTIFICATIONS_DISABLED = "notificationsDisabled"
    const val CREATED_AT = "createdAt"
}

object DeviceFields {
    const val TOKEN = "token"
    const val UPDATED_AT = "updatedAt"
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

    /** Sólo en lugares PRIORITY: la categoría que puede usarlo. */
    const val PRIORITY_CATEGORY = "priorityCategory"
    const val UPDATED_AT = "updatedAt"
}

/** Campos de `reservations/{id}`. Las escriben sólo las functions; ver docs/reservation-rules.md. */
object ReservationFields {
    const val USER_ID = "userId"
    const val PARKING_LOT_ID = "parkingLotId"
    const val SPOT_ID = "spotId"
    const val SPOT_CODE = "spotCode"
    const val SPOT_TYPE = "spotType"
    const val ARRIVES_AT = "arrivesAt"
    const val DURATION_MINUTES = "durationMinutes"
    const val ENDS_AT = "endsAt"
    const val TOLERANCE_UNTIL = "toleranceUntil"
    const val STATUS = "status"
    const val CHECKED_IN_AT = "checkedInAt"
    const val CHECKED_OUT_AT = "checkedOutAt"
    const val STAY_MINUTES = "stayMinutes"
    const val AMOUNT = "amount"
}

/** Campos de `waitlist/{id}`. Las escriben sólo las functions; ver docs/reservation-rules.md. */
object WaitlistFields {
    const val USER_ID = "userId"
    const val PARKING_LOT_ID = "parkingLotId"
    const val SPOT_TYPE = "spotType"
    const val ARRIVES_AT = "arrivesAt"
    const val DURATION_MINUTES = "durationMinutes"
    const val CATEGORY = "category"
    const val REQUESTED_AT = "requestedAt"
    const val STATUS = "status"
    const val POSITION = "position"
    const val RESERVATION_ID = "reservationId"

    /** Mapa con `spotId`, `spotCode`, `offeredAt` y `expiresAt`, mientras hay una oferta. */
    const val OFFER = "offer"
    const val OFFER_SPOT_ID = "spotId"
    const val OFFER_SPOT_CODE = "spotCode"
    const val OFFER_OFFERED_AT = "offeredAt"
    const val OFFER_EXPIRES_AT = "expiresAt"
}

/** Cloud Functions invocables de reservas, en functions/reservations.js. */
object ReservationFunctions {
    const val CREATE = "createReservation"
    const val CANCEL = "cancelReservation"
    const val REASSIGN_SPOT = "reassignSpot"
    const val CHECK_IN = "checkIn"
    const val CHECK_OUT = "checkOut"
    const val JOIN_WAITLIST = "joinWaitlist"
    const val LEAVE_WAITLIST = "leaveWaitlist"
    const val ACCEPT_WAITLIST_OFFER = "acceptWaitlistOffer"
    const val DECLINE_WAITLIST_OFFER = "declineWaitlistOffer"
}

/** Campos de `users/{uid}/favorites/{parkingLotId}`; ver firestore.rules. */
object FavoriteFields {
    const val PARKING_LOT_ID = "parkingLotId"
    const val ADDED_AT = "addedAt"
}
