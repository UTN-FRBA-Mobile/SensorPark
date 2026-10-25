# Contrato de datos (T01)

Cómo se comunican la app y el backend. No hay servidor propio ni API REST: la app lee directo de
Firestore (en tiempo real) y todo lo que tiene reglas de negocio pasa por Cloud Functions
invocables. Este documento describe la forma de cada documento y la entrada y salida de cada
function, tal como están en el código.

Los nombres se repiten en tres lugares y se cambian a la vez: `firestore.rules`, `functions/` y
`data/.../firebase/FirestoreNames.kt`. Las reglas de reserva están aparte, en
[reservation-rules.md](reservation-rules.md).

## Convenciones

- **Idioma:** el código va en inglés (colecciones, campos, valores de enums, functions, claves de
  las pushes); los comentarios, la documentación y los textos que ve el usuario, en español.
- **Fechas:** en Firestore son `Timestamp`; en la entrada y la salida de las functions, texto
  ISO 8601 (`2026-10-20T14:30:00-03:00` o con `Z`). La zona de las reglas es
  `America/Argentina/Buenos_Aires`.
- **Horas del horario:** texto `HH:mm` (`"06:00"`).
- **Enums:** se guardan con su nombre en mayúsculas (`"FREE"`, `"IN_USE"`).

| En español | En el código |
| --- | --- |
| Usuario | `User`, colección `users` |
| Estacionamiento | `ParkingLot`, colección `parkingLots` |
| Lugar | `Spot`, subcolección `spots` |
| Reserva | `Reservation`, colección `reservations` |
| Lista de espera | `WaitlistEntry`, colección `waitlist` |
| Favorito | subcolección `users/{uid}/favorites` |
| Rol | `Role`, campo y custom claim `role` |
| Categoría de prioridad | `Category`, campo `category` |

### Enums

| Enum | Valores |
| --- | --- |
| `Role` | `DRIVER` (conductor), `PARKING_ADMIN` (administrador de estacionamiento), `ADMIN` (superusuario de desarrollo) |
| `Category` | `STANDARD`, `PREGNANT` (embarazo), `REDUCED_MOBILITY` (movilidad reducida) |
| `SpotType` | `STANDARD`, `PRIORITY` (preferencial), `MOTORCYCLE`, `EV_CHARGING` |
| `SpotStatus` | `FREE`, `OCCUPIED`, `RESERVED`, `OUT_OF_SERVICE` |
| `ReservationStatus` | `CONFIRMED`, `IN_USE`, `FINISHED`, `CANCELLED`, `EXPIRED` |
| `WaitlistStatus` | `WAITING`, `OFFERED`, `ACCEPTED`, `DECLINED`, `LEFT`, `EXPIRED` |

Duraciones permitidas de una reserva: 15, 30, 60, 120, 240 y 480 minutos
(`ReservationRules.ALLOWED_DURATIONS_MINUTES` y `ALLOWED_DURATIONS_MINUTES` en
`functions/reservationRules.js`).

## Colecciones de Firestore

### `users/{uid}`

| Campo | Tipo | Notas |
| --- | --- | --- |
| `name`, `email` | string | |
| `role` | string (`Role`) | Lo escribe sólo el backend; la fuente de verdad es el custom claim. |
| `requestedRole` | string | Lo que pidió al registrarse (`DRIVER` o `PARKING_ADMIN`); `assignInitialRole` lo valida y lo borra. |
| `category` | string (`Category`) | De buena fe: nadie la valida. |
| `notificationsDisabled` | array de string | Tipos de aviso apagados (`RESERVATION`, `REMINDER`, `ARRIVAL`, `WAITLIST`). |
| `createdAt` | Timestamp | |

Lee y escribe su dueño (sin tocar `role` ni `requestedRole`); el ADMIN lee todos.

```json
{ "name": "Ana", "email": "ana@mail.com", "role": "DRIVER", "category": "PREGNANT", "notificationsDisabled": ["REMINDER"] }
```

#### `users/{uid}/devices/{token}`

Un documento por teléfono con la sesión abierta; el id es el token FCM. Campos: `token` (igual al
id) y `updatedAt`. Las pushes van a todos. Si un teléfono pasa a otra cuenta, `claimDevice` lo
saca de la anterior.

#### `users/{uid}/favorites/{parkingLotId}` (T19)

Campos: `parkingLotId` (igual al id) y `addedAt`. Sólo los ve y los cambia su dueño. La app los
guarda también en Room para verlos sin conexión.

### `parkingLots/{lotId}`

| Campo | Tipo | Notas |
| --- | --- | --- |
| `name`, `address` | string | |
| `location` | GeoPoint | |
| `schedule` | map `{ opensAt, closesAt }` | Opcional: sin horario abre todo el día. |
| `toleranceMinutes` | number | Minutos para hacer el check-in después de la hora de llegada. |
| `geofenceRadiusMeters` | number | Radio del aviso de llegada. |
| `hourlyRate` | number | Tarifa informativa en pesos: la app no cobra. |
| `adminUids` | array de string | Quiénes lo administran. |
| `capacity`, `freeSpots` | number | Contadores: los calcula `updateFreeSpotCounters`. |
| `capacityByType`, `freeSpotsByType` | map `SpotType → number` | Ídem, por tipo. |

Lo lee cualquiera con sesión. Lo crea un ADMIN, o un PARKING_ADMIN que queda como su único
administrador; su administrador lo edita pero no toca los contadores ni `adminUids`.

```json
{
  "name": "UTN Medrano", "address": "Av. Medrano 951, CABA", "location": [-34.5984, -58.42],
  "schedule": { "opensAt": "06:00", "closesAt": "23:00" }, "toleranceMinutes": 15,
  "geofenceRadiusMeters": 300, "hourlyRate": 2500, "adminUids": ["parking-admin"],
  "capacity": 14, "freeSpots": 9,
  "capacityByType": { "STANDARD": 8, "PRIORITY": 2, "MOTORCYCLE": 2, "EV_CHARGING": 2 },
  "freeSpotsByType": { "STANDARD": 5, "PRIORITY": 2, "MOTORCYCLE": 1, "EV_CHARGING": 1 }
}
```

#### `parkingLots/{lotId}/spots/{spotId}`

| Campo | Tipo | Notas |
| --- | --- | --- |
| `code` | string | El que se ve pintado en el piso (`"07"`); en la semilla y en el panel es también el id. |
| `type` | string (`SpotType`) | |
| `status` | string (`SpotStatus`) | Lo escribe el sensor (o el simulador) y el backend. |
| `priorityCategory` | string (`Category`) | Sólo en los `PRIORITY`: quién puede reservarlo. |
| `updatedAt` | Timestamp | |
| `inUseBy` | string | Marca del backend: el lugar lo ocupa la reserva con ese id (check-in). |
| `simulatedFault` | boolean | La avería la puso el simulador, que la puede reparar. |

Lo lee cualquiera con sesión; lo escribe su administrador. Sacar de servicio o borrar pasa por
las functions de T22, que verifican que no haya reservas activas.

### `reservations/{reservationId}`

| Campo | Tipo | Notas |
| --- | --- | --- |
| `userId`, `parkingLotId`, `spotId`, `spotCode` | string | |
| `spotType` | string (`SpotType`) | |
| `arrivesAt`, `endsAt` | Timestamp | La franja: llegada y fin (`arrivesAt` + duración). |
| `durationMinutes` | number | Una de las duraciones permitidas. |
| `toleranceUntil` | Timestamp | Hasta cuándo se puede hacer check-in. |
| `status` | string (`ReservationStatus`) | |
| `checkedInAt`, `checkedOutAt` | Timestamp | |
| `stayMinutes`, `amount` | number | Estadía real e importe informativo, al hacer check-out. |
| `fromWaitlist` | string | Si salió de aceptar una oferta: el id de la entrada. |
| `notices` | map | Qué avisos de la hora ya se mandaron (`expiring`, `ending`, `longStay`). |
| `createdAt` | Timestamp | |

Las escriben sólo las functions. Las leen su dueño, el administrador del estacionamiento y el
ADMIN.

```json
{
  "userId": "ana", "parkingLotId": "utn-medrano", "spotId": "07", "spotCode": "07", "spotType": "STANDARD",
  "arrivesAt": "2026-10-20T14:30:00-03:00", "durationMinutes": 60, "endsAt": "2026-10-20T15:30:00-03:00",
  "toleranceUntil": "2026-10-20T14:45:00-03:00", "status": "FINISHED",
  "checkedInAt": "2026-10-20T14:32:00-03:00", "checkedOutAt": "2026-10-20T15:37:00-03:00",
  "stayMinutes": 65, "amount": 2708
}
```

### `waitlist/{entryId}`

| Campo | Tipo | Notas |
| --- | --- | --- |
| `userId`, `parkingLotId` | string | |
| `spotType` | string (`SpotType`) | |
| `arrivesAt`, `endsAt` | Timestamp | La franja pedida. |
| `durationMinutes` | number | |
| `category` | string (`Category`) | Las especiales pasan adelante. |
| `requestedAt` | Timestamp | |
| `status` | string (`WaitlistStatus`) | |
| `position` | number | Lugar en la fila (1 es el primero), mientras espera. |
| `offer` | map `{ spotId, spotCode, offeredAt, expiresAt }` | Mientras hay una oferta: el lugar guardado por 5 minutos. |
| `reservationId` | string | La reserva que salió de aceptar. |
| `missed` | boolean | Se le pasaron los minutos de la oferta. |

La escriben sólo las functions; la lee su dueño y el ADMIN.

```json
{
  "userId": "ana", "parkingLotId": "utn-medrano", "spotType": "MOTORCYCLE",
  "arrivesAt": "2026-10-20T22:00:00-03:00", "durationMinutes": 30, "category": "STANDARD",
  "status": "OFFERED",
  "offer": { "spotId": "11", "spotCode": "11", "offeredAt": "2026-10-20T21:40:00-03:00", "expiresAt": "2026-10-20T21:45:00-03:00" }
}
```

### `config/simulator`

Escenario del simulador de sensores (`scenario`, `intervalSeconds`, `paused`, `seed`,
`startedAtMs`). Sólo lo lee y lo escribe el ADMIN, desde las herramientas de desarrollo.

## Evento del sensor

Lo que escribe un sensor (en este proyecto, el simulador de T09) en el documento del lugar:

```json
{ "status": "OCCUPIED", "updatedAt": "2026-10-20T14:31:05-03:00" }
```

Es mínimo a propósito: un sensor sólo sabe si hay un auto o no. El tiempo real no necesita un
mensaje aparte: la app escucha el documento del lugar y recibe el cambio.

## Cloud Functions invocables

Región `southamerica-east1`. Todas piden sesión. Los errores usan los códigos de las functions
invocables, y los rechazos de negocio llevan además un `reason` en `details`:

| Código | Cuándo |
| --- | --- |
| `unauthenticated` | Sin sesión. |
| `permission-denied` | Sin el rol o sin permiso sobre ese documento. |
| `invalid-argument` | Datos con otra forma (o `INVALID_DURATION`). |
| `not-found` | No existe la reserva, la entrada o el lugar. |
| `failed-precondition` | Una regla de negocio lo impide: ver los `reason`. |

```json
{ "error": { "status": "FAILED_PRECONDITION", "message": "No hay lugares disponibles para esa franja.", "details": { "reason": "NO_SPOTS" } } }
```

Los `reason` son los de `ReservationRejection` en el dominio: `INVALID_DURATION`, `IN_THE_PAST`,
`TOO_FAR_AHEAD`, `OUTSIDE_SCHEDULE`, `CATEGORY_REQUIRED`, `LIMIT_REACHED`, `NO_SPOTS`,
`CANCEL_NOT_ALLOWED`, `TOO_EARLY`, `EXPIRED`, `TOO_FAR`, `INVALID_STATUS`, `ALREADY_IN_WAITLIST`,
`SPOT_NOT_TAKEN`, `NO_OFFER`, `OFFER_EXPIRED` y `SPOT_HAS_RESERVATION`.

| Function | Datos | Respuesta | Rechazos |
| --- | --- | --- | --- |
| `createReservation` | `{ parkingLotId, arrivesAt, durationMinutes, spotType }` o con `spotId` | `{ reservationId, spotId, spotCode, arrivesAt, endsAt, toleranceUntil }` | validación del pedido, `LIMIT_REACHED`, `NO_SPOTS` |
| `cancelReservation` | `{ reservationId }` | `{ status: "CANCELLED" }` | `CANCEL_NOT_ALLOWED` |
| `reassignSpot` | `{ reservationId }` | `{ spotId, spotCode }` | `INVALID_STATUS`, `EXPIRED`, `SPOT_NOT_TAKEN`, `NO_SPOTS` |
| `checkIn` | `{ reservationId, latitude, longitude }` | `{ status: "IN_USE" }` | `TOO_EARLY`, `EXPIRED`, `TOO_FAR` |
| `checkOut` | `{ reservationId }` | `{ status: "FINISHED", stayMinutes, amount }` | `INVALID_STATUS` |
| `joinWaitlist` | `{ parkingLotId, arrivesAt, durationMinutes, spotType }` | `{ entryId, position }` | validación del pedido, `ALREADY_IN_WAITLIST` |
| `leaveWaitlist` | `{ entryId }` | `{ status: "LEFT" }` | |
| `acceptWaitlistOffer` | `{ entryId }` | como `createReservation` | `NO_OFFER`, `OFFER_EXPIRED`, `LIMIT_REACHED` |
| `declineWaitlistOffer` | `{ entryId }` | `{ status: "DECLINED" }` | `NO_OFFER` |
| `setSpotService` | `{ parkingLotId, spotId, inService }` | `{ status }` | `SPOT_HAS_RESERVATION` |
| `deleteSpot` | `{ parkingLotId, spotId }` | `{ deleted }` | `SPOT_HAS_RESERVATION` |
| `assignRole` (ADMIN) | `{ uid, role }` | `{ uid, role }` | |
| `loadSeed` (ADMIN) | `{}` | `{ parkingLots, spots }` | |
| `sendTestPush` | `{}` | `{ sent }`: a cuántos teléfonos salió | |

Ejemplo de `createReservation`:

```json
{ "parkingLotId": "utn-medrano", "arrivesAt": "2026-10-20T14:30:00-03:00", "durationMinutes": 60, "spotType": "STANDARD" }
```

```json
{ "reservationId": "r1", "spotId": "07", "spotCode": "07", "arrivesAt": "2026-10-20T17:30:00.000Z", "endsAt": "2026-10-20T18:30:00.000Z", "toleranceUntil": "2026-10-20T17:45:00.000Z" }
```

### Lo que corre solo

| Function | Disparador | Qué hace |
| --- | --- | --- |
| `assignInitialRole` | alta de `users/{uid}` | Asigna el rol pedido como custom claim. |
| `updateFreeSpotCounters` | cambio en un lugar | Recalcula los contadores del estacionamiento. |
| `processReservations` | cada minuto | Reserva el lugar de las que entran en su ventana de "ahora", vence las que no hicieron check-in, vence las ofertas sin respuesta y manda los avisos de la hora. |
| `offerReleasedSpots` | una reserva deja de estar activa | Le ofrece el lugar al primero de la lista de espera. |
| `notifyReservationCreated` | alta de una reserva | Avisa a los otros teléfonos del usuario. |
| `claimDevice` | alta de un dispositivo | Saca ese teléfono de la cuenta anterior. |
| `simulateSensors` | cada minuto | El simulador de sensores (T09). |

## Avisos

Las pushes son sólo de datos (sin bloque `notification`) y llevan `type`, `id`, `title` y `body`,
todos texto. El `type` decide el canal, el interruptor de Cuenta y a dónde lleva el toque.

| Aviso | Quién lo arma | `type` | Lleva a |
| --- | --- | --- | --- |
| Reserva confirmada | La app y el backend (a los otros teléfonos) | `reservationConfirmed` | `sensorpark://reservation/{id}` |
| Recordatorio 30 minutos antes | La app (WorkManager) | — | la reserva |
| Llegaste | La app (geocerca) | — | `sensorpark://check-in/{id}` |
| Estacionado (persistente) | La app | — | el check-in, con "Salir" |
| Reserva por vencer | El backend | `reservation_expiring` | la reserva |
| Reserva vencida | El backend | `reservation_expired` | la reserva |
| Franja por terminar | El backend | `reservation_ending` | la reserva |
| Estadía prolongada | El backend | `reservation_long_stay` | la reserva |
| Se liberó un lugar | El backend (y la app, con la app abierta) | `waitlist_offer` | `sensorpark://waitlist-offer/{id}` |

Un aviso con la misma clave (`type` + `id`) reemplaza al anterior en vez de sumarse, así el de la
app y el del backend no se ven dos veces.
