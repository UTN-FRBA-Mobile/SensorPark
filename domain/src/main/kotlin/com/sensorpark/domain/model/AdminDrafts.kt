package com.sensorpark.domain.model

import java.time.LocalTime

// Lo que carga el administrador de estacionamiento en su panel (T22), antes de guardarlo. Las
// validaciones viven acá, sin Android, para mostrarlas en el momento y probarlas sin emulador.

/** Un campo del formulario del estacionamiento con un problema. */
enum class ParkingLotField { NAME, ADDRESS, LOCATION, SCHEDULE, TOLERANCE, GEOFENCE_RADIUS, HOURLY_RATE }

/** Los datos del estacionamiento que edita su administrador. Los contadores no: los calcula el backend. */
data class ParkingLotDraft(
    val name: String = "",
    val address: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** Sin horario, abre todo el día. */
    val opensAt: LocalTime? = null,
    val closesAt: LocalTime? = null,
    val toleranceMinutes: Int = 15,
    val geofenceRadiusMeters: Int = 300,
    val hourlyRate: Int = 0,
) {
    val location: Coordinates? get() = if (latitude == null || longitude == null) null else Coordinates(latitude, longitude)

    val schedule: Schedule? get() = if (opensAt == null || closesAt == null) null else Schedule(opensAt, closesAt)

    /** Qué campos tienen un problema; vacío si se puede guardar. */
    fun problems(): Set<ParkingLotField> = buildSet {
        if (name.isBlank()) add(ParkingLotField.NAME)
        if (address.isBlank()) add(ParkingLotField.ADDRESS)
        if (latitude == null || longitude == null || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
            add(ParkingLotField.LOCATION)
        }
        // Las dos horas o ninguna, y que abra antes de cerrar.
        if ((opensAt == null) != (closesAt == null) || (opensAt != null && closesAt != null && opensAt >= closesAt)) {
            add(ParkingLotField.SCHEDULE)
        }
        if (toleranceMinutes !in TOLERANCE_RANGE) add(ParkingLotField.TOLERANCE)
        if (geofenceRadiusMeters !in RADIUS_RANGE) add(ParkingLotField.GEOFENCE_RADIUS)
        if (hourlyRate < 0) add(ParkingLotField.HOURLY_RATE)
    }

    companion object {
        val TOLERANCE_RANGE = 0..60
        val RADIUS_RANGE = 50..2_000

        fun from(lot: ParkingLot) = ParkingLotDraft(
            name = lot.name,
            address = lot.address,
            latitude = lot.location.latitude,
            longitude = lot.location.longitude,
            opensAt = lot.schedule?.opensAt,
            closesAt = lot.schedule?.closesAt,
            toleranceMinutes = lot.toleranceMinutes,
            geofenceRadiusMeters = lot.geofenceRadiusMeters,
            hourlyRate = lot.hourlyRate,
        )
    }
}

/** Un problema al cargar un lugar. */
enum class SpotProblem { CODE_REQUIRED, CODE_TAKEN, CATEGORY_REQUIRED }

/** Un lugar que se agrega o se edita. El código es también su id, como en la semilla. */
data class SpotDraft(
    val code: String = "",
    val type: SpotType = SpotType.STANDARD,
    /** Sólo en los preferenciales: para qué categoría es. */
    val priorityCategory: Category? = null,
) {
    /** [takenCodes] son los de los otros lugares del estacionamiento. */
    fun problems(takenCodes: Set<String>): Set<SpotProblem> = buildSet {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) add(SpotProblem.CODE_REQUIRED)
        if (trimmed in takenCodes) add(SpotProblem.CODE_TAKEN)
        if (type == SpotType.PRIORITY && (priorityCategory == null || priorityCategory == Category.STANDARD)) {
            add(SpotProblem.CATEGORY_REQUIRED)
        }
    }

    companion object {
        fun from(spot: Spot) = SpotDraft(code = spot.code, type = spot.type, priorityCategory = spot.priorityCategory)

        /** El código que sigue a los que hay: "15" si el último es "14". */
        fun nextCode(codes: Collection<String>): String {
            val next = (codes.mapNotNull { it.toIntOrNull() }.maxOrNull() ?: 0) + 1
            return next.toString().padStart(2, '0')
        }
    }
}

/** Cómo fue el día de un estacionamiento: sus lugares ahora y sus reservas de hoy (T22). */
data class DaySummary(
    val free: Int,
    val occupied: Int,
    val reserved: Int,
    val outOfService: Int,
    val reservations: Int,
    val active: Int,
    val finished: Int,
    val cancelled: Int,
    val expired: Int,
) {
    companion object {
        fun of(spots: List<Spot>, today: List<Reservation>) = DaySummary(
            free = spots.count { it.status == SpotStatus.FREE },
            occupied = spots.count { it.status == SpotStatus.OCCUPIED },
            reserved = spots.count { it.status == SpotStatus.RESERVED },
            outOfService = spots.count { it.status == SpotStatus.OUT_OF_SERVICE },
            reservations = today.size,
            active = today.count { it.status.isActive },
            finished = today.count { it.status == ReservationStatus.FINISHED },
            cancelled = today.count { it.status == ReservationStatus.CANCELLED },
            expired = today.count { it.status == ReservationStatus.EXPIRED },
        )
    }
}
