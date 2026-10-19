# Reglas de reserva

Este documento es la referencia común de las reservas. Las reglas están escritas dos veces, y las
dos versiones tienen que decir lo mismo:

- en la app, en Kotlin: `domain/.../reservation/ReservationRules.kt`. La app las usa para responder
  rápido, antes de llamar al backend.
- en el backend, en JavaScript: `functions/reservationRules.js`. Es el que tiene la última palabra.

Si cambia una regla, se cambia acá primero y después en los dos lados, con sus tests. Los ejemplos
del final son casos de prueba de los dos lados.

## Valores fijos

| Constante | Valor | Para qué |
|---|---|---|
| Duraciones permitidas | 15, 30, 60, 120, 240 y 480 minutos | La franja que elige el conductor |
| Anticipación máxima | 7 días | Hasta cuándo se puede reservar a futuro |
| Margen hacia atrás | 5 minutos | Una llegada un poco en el pasado se acepta (el reloj del teléfono puede estar corrido) |
| Reservas activas por usuario | 2 | Límite por conductor |
| Ventana de "ahora" | 15 minutos | Una reserva que empieza dentro de este margen toma el lugar en el momento |
| Check-in anticipado | 15 minutos | Cuánto antes de la llegada se puede hacer check-in |
| Lista de espera: tiempo para aceptar | 5 minutos | Lo usa T18 |
| Zona horaria | `America/Argentina/Buenos_Aires` | Para comparar con el horario del estacionamiento |

## Una reserva

Una reserva tiene una hora de llegada (`arrivesAt`) y una duración (`durationMinutes`). De ahí salen:

- **Fin de la franja:** `endsAt = arrivesAt + durationMinutes`.
- **Fin de la tolerancia:** `toleranceUntil = arrivesAt + toleranceMinutes` del estacionamiento.

Estados (`status`):

| Estado | Qué significa |
|---|---|
| `CONFIRMED` | Reservada, todavía sin check-in |
| `IN_USE` | Hizo check-in: el auto está en el lugar |
| `FINISHED` | Hizo check-out |
| `CANCELLED` | La canceló el conductor |
| `EXPIRED` | No hizo check-in a tiempo |

Una reserva es **activa** si está `CONFIRMED` o `IN_USE`. Sólo las activas toman lugares y cuentan
para el límite.

## Crear una reserva

El pedido trae el estacionamiento, `arrivesAt`, `durationMinutes` y un tipo de lugar (`spotType`)
o un lugar puntual (`spotId`); con un lugar puntual, el tipo del pedido es el de ese lugar. Pueden
reservar un `DRIVER` o un `ADMIN`. Se valida en este orden; el primer problema es el motivo del
rechazo:

1. **Duración:** tiene que ser una de las permitidas. Si no, `INVALID_DURATION`.
2. **Llegada en el pasado:** si `arrivesAt` es anterior a ahora menos el margen, `IN_THE_PAST`.
3. **Demasiado a futuro:** si `arrivesAt` es posterior a ahora más la anticipación máxima,
   `TOO_FAR_AHEAD`.
4. **Horario:** la franja entera tiene que caer dentro del horario del estacionamiento, en la hora
   argentina y en el mismo día: `opensAt <= llegada` y `endsAt <= closesAt`. Si no,
   `OUTSIDE_SCHEDULE`. Un estacionamiento sin horario cargado no tiene esta restricción.
5. **Categoría:** pedir un lugar preferencial (`PRIORITY`) exige haber declarado una categoría
   especial (`PREGNANT` o `REDUCED_MOBILITY`). Si es `STANDARD`, `CATEGORY_REQUIRED`.
6. **Límite:** si el conductor ya tiene 2 reservas activas, `LIMIT_REACHED`.
7. **Lugar:** se busca un lugar disponible (ver abajo). Si no hay ninguno, `NO_SPOTS`.

Si todo pasa, la reserva queda `CONFIRMED` con el lugar elegido.

### Qué lugar está disponible para una franja

Un lugar sirve para el pedido si cumple todo esto:

- **Tipo:** es del tipo pedido. Si se pidió un lugar puntual, es ese lugar.
- **Preferenciales:** si es `PRIORITY`, su `priorityCategory` es la categoría del conductor. Un
  conductor con `PREGNANT` no usa un lugar para `REDUCED_MOBILITY`, y al revés. Un preferencial
  libre no se libera para otros cerca de la hora: queda para su categoría.
- **Funciona:** no está `OUT_OF_SERVICE`.
- **Sin choque:** ninguna reserva activa del lugar se superpone con la franja pedida.
- **Ahora:** si la reserva empieza dentro de la ventana de "ahora", el lugar además tiene que
  estar `FREE` en este momento (no puede haber un auto que entró sin reserva).

Entre los que sirven se elige el de código más bajo, para que el resultado sea predecible.

### Cuándo dos franjas se superponen

Dos franjas `[inicio, fin)` se superponen si `inicioA < finB` y `inicioB < finA`. Una que termina
a las 14:00 y otra que empieza a las 14:00 **no** se superponen.

Una reserva `IN_USE` ocupa el lugar hasta su `endsAt`. Si ya se pasó y todavía no hizo check-out,
el auto sigue ahí: se la considera ocupando el lugar hasta el fin de la ventana de "ahora" (ahora
+ 15 minutos), así nadie lo puede reservar para este momento. Para más adelante se asume que se va.

## Cancelar

Sólo el dueño, y sólo mientras la reserva está `CONFIRMED` (antes del check-in). No hay plazo: se
puede cancelar hasta que vence. Si no, `CANCEL_NOT_ALLOWED`. No existe "modificar": para cambiar la
hora o la duración se cancela y se reserva de nuevo.

## Pedir otro lugar

Cuando el sensor marca el lugar asignado como `OCCUPIED` antes del check-in, quiere decir que lo
tomó otro auto. El dueño puede pedir otro lugar del mismo tipo, con la reserva todavía `CONFIRMED`
(si no, `INVALID_STATUS`) y dentro de la tolerancia (si no, `EXPIRED`). Si el lugar no figura
ocupado por otro auto no hay nada que reasignar: `SPOT_NOT_TAKEN`. El lugar nuevo se elige con las
mismas reglas que al reservar, sin contar el que quedó ocupado; si no hay ninguno, `NO_SPOTS`.

La franja no cambia, y el lugar viejo tampoco se toca: lo está ocupando un auto de verdad y el
sensor es el que manda sobre su estado.

## Check-in y check-out

- **Check-in:** sólo el dueño, con la reserva `CONFIRMED` (en cualquier otro estado, `EXPIRED`:
  ya no se puede usar), desde 15 minutos antes de la llegada
  (si no, `TOO_EARLY`) hasta `toleranceUntil` (si no, `EXPIRED`), y estando a no más de
  `geofenceRadiusMeters` del estacionamiento (si no, `TOO_FAR`). La reserva pasa a `IN_USE`.
- **Check-out:** sólo el dueño, con la reserva `IN_USE` (si no, `INVALID_STATUS`). Pasa a
  `FINISHED`, guarda cuánto duró la estadía (minutos enteros) y un importe informativo: tarifa por
  hora × minutos reales / 60, redondeado.

## Vencimiento

Una reserva `CONFIRMED` sin check-in vence cuando pasa `toleranceUntil`: queda `EXPIRED` y deja el
lugar. Lo hace una function programada cada minuto.

## Estado del lugar

El estado del lugar refleja lo que pasa ahora; las reservas a futuro no lo cambian:

| Momento | Estado del lugar |
|---|---|
| Empieza la ventana de "ahora" de una reserva `CONFIRMED` | `RESERVED` |
| Check-in | `OCCUPIED` |
| Check-out, cancelación o vencimiento | `FREE`, salvo que otra reserva ya esté en su ventana de "ahora" |

Al hacer check-in, el lugar guarda la reserva que lo usa en `inUseBy`, y la liberación la borra.
El simulador de sensores no toca los lugares `RESERVED`, `OUT_OF_SERVICE` ni los que tienen
`inUseBy`: a ese auto lo saca el check-out, no el sensor.

Para que dos pedidos simultáneos no se confirmen los dos, cada reserva nueva escribe en su lugar
`lastReservationAt`. Así, dos transacciones que eligieron el mismo lugar chocan y Firestore
reintenta una, que ya ve el lugar tomado. Ese campo no cambia los contadores.

## Lista de espera

Si no hay lugar, el conductor se puede anotar con la misma franja y tipo de lugar. Se validan los
mismos pasos 1 a 5 que al reservar (duración, llegada, anticipación, horario y categoría); el
límite no, porque anotarse no toma ningún lugar. El orden es:

1. primero las categorías especiales (`PREGNANT` y `REDUCED_MOBILITY`, entre ellas por igual);
2. después `STANDARD`;
3. a igual categoría, por hora de pedido.

No se puede anotar dos veces para la misma franja del mismo estacionamiento
(`ALREADY_IN_WAITLIST`). La posición se cuenta entre quienes esperan la misma franja y el mismo tipo
de lugar, y se guarda en cada entrada (`position`): la app sólo puede leer las propias, así que no la
podría calcular. Se recalcula cada vez que cambia la fila.

### La oferta

Cuando se libera un lugar (una reserva se cancela, termina o vence), se recorre la fila del
estacionamiento en el orden de arriba y a cada uno se le busca un lugar con las mismas reglas que al
reservar. Al primero cuya franja entra se le **ofrece**: la entrada pasa a `OFFERED` con el lugar y
la hora en que vence la oferta, 5 minutos después, y le llega un aviso. Así, con dos esperando la
misma franja, el que tiene categoría de prioridad recibe la oferta aunque se haya anotado después.

Mientras dura, la oferta **guarda el lugar**: cuenta como una reserva activa más, así que ni otra
oferta ni una reserva directa se lo pueden quedar.

- **Aceptar** (dentro de los 5 minutos) la convierte en una reserva `CONFIRMED` con esa franja,
  validada igual que al reservar: por ejemplo, con dos reservas activas, `LIMIT_REACHED`. La entrada
  queda `ACCEPTED` con el id de la reserva. Pasados los 5 minutos, `OFFER_EXPIRED`.
- **Rechazar** la deja `DECLINED`, y el lugar se le ofrece al siguiente.
- **No contestar:** el proceso de cada minuto la deja `DECLINED` (con `missed`) y también pasa al
  siguiente.
- **Salir** de la lista con una oferta pendiente también la suelta.

Aceptar o rechazar algo que no es una oferta vigente: `NO_OFFER`. Si la franja de una entrada que
espera ya empezó, queda `EXPIRED`: no tiene sentido ofrecerle nada.

## Ejemplos

Estacionamiento con horario 06:00 a 23:00, tolerancia de 15 minutos. Fechas en hora argentina.

| Situación | Resultado |
|---|---|
| Duración de 45 minutos | `INVALID_DURATION` |
| Llegada hace 10 minutos | `IN_THE_PAST` |
| Llegada dentro de 8 días | `TOO_FAR_AHEAD` |
| Llegada 22:30 con 1 h (termina 23:30) | `OUTSIDE_SCHEDULE` |
| Llegada 05:30 | `OUTSIDE_SCHEDULE` |
| Conductor `STANDARD` pide `PRIORITY` | `CATEGORY_REQUIRED` |
| Conductor con 2 reservas activas | `LIMIT_REACHED` |
| Único lugar del tipo con una reserva de 13:00 a 14:00; se pide 14:00 a 15:00 | Confirmada en ese lugar |
| El mismo lugar; se pide 13:59 a 14:59 | `NO_SPOTS` |
| Conductor `PREGNANT` pide `PRIORITY` y sólo hay un preferencial para `REDUCED_MOBILITY` | `NO_SPOTS` |
| Lugar `OUT_OF_SERVICE` sin reservas | No sirve |
| Reserva para dentro de 5 minutos y el lugar está `OCCUPIED` | No sirve |
| Reserva para mañana y el lugar está `OCCUPIED` ahora | Sirve |
| Dos pedidos al mismo tiempo por el último lugar | Uno confirmado y el otro `NO_SPOTS` |
| Cancelar una reserva `IN_USE` | `CANCEL_NOT_ALLOWED` |
| Check-in 20 minutos antes de la llegada | `TOO_EARLY` |
| Check-in a 1 km del estacionamiento | `TOO_FAR` |
| Check-out a los 90 minutos con tarifa de $2.500 la hora | Importe $3.750 |
