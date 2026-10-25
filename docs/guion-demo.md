# Guion de la demo (T24)

Unos 10 minutos. Cada bloque dice qué se muestra, quién lo cuenta y qué tiene que estar listo.
Las cuentas del proyecto real no van acá: se pasan por el grupo. Para ensayar sin tocar la base
real están los emuladores (ver "Ensayo con los emuladores", al final).

## Antes de empezar (15 minutos antes)

- [ ] Teléfono o emulador con la app instalada, la sesión del conductor iniciada y los permisos de
      avisos y ubicación (incluida "todo el tiempo") ya dados.
- [ ] Un segundo teléfono (o emulador) con otra cuenta de conductor, para la lista de espera.
- [ ] La semilla cargada (`loadSeed`, o `seed-emulators.js` en los emuladores) y el simulador en
      pausa, en el escenario `DEMO` (Cuenta > Herramientas de desarrollo, con la cuenta ADMIN).
- [ ] Ubicación del emulador en UTN Medrano: `adb emu geo fix -58.4200 -34.5984` (longitud primero).
- [ ] Una pasada rápida por reservar y cancelar: la primera llamada a una Cloud Function después
      de un rato tarda unos segundos (arranque en frío) y no queremos que pase en vivo.
- [ ] El video de respaldo a mano (plan B), por si se cae la red o el backend.

## Recorrido

| Min | Bloque | Quién | Qué se muestra |
| --- | --- | --- | --- |
| 0:00 | El problema | P1 | Dar vueltas buscando lugar; un sensor por cochera lo resuelve. En el proyecto el sensor se simula por software. |
| 1:00 | Buscar | P3 | Pestaña Buscar: estacionamientos cerca por GPS, mapa y lista, filtros "Con lugares libres" y por tipo. |
| 2:00 | Tiempo real | P3 | Activar el simulador (escenario `DEMO`): los contadores (y el plano, si llegó T12) cambian solos, sin recargar. |
| 3:00 | Reservar | P4 | Detalle de UTN Medrano → Reservar: día, hora de llegada, duración fija y tipo de lugar. Llega el aviso "Reserva confirmada". |
| 4:00 | Mis reservas | P4 | La reserva activa con la cuenta regresiva de la tolerancia; mostrar cancelar sin confirmarlo. |
| 4:30 | Llegada | P5 | Mover la ubicación a la puerta: aviso "Llegaste a UTN Medrano" con el botón de check-in. |
| 5:00 | Check-in | P5 | Desde el aviso: check-in dentro del radio, el lugar queda ocupado y aparece el aviso persistente "Estacionado". Contar que lejos del radio lo rechaza. |
| 6:00 | Check-out | P5 | "Salir": estadía real e importe informativo; el lugar vuelve a libre. |
| 6:30 | Lista de espera | P4 + P5 | Llenar Moto con el segundo teléfono, anotarse desde el primero ("Avisarme si se libera un lugar"), cancelar en el segundo: llega la oferta con 5 minutos y se acepta. |
| 8:00 | Administrador | P2 | Entrar como encargado: Mis estacionamientos y su panel (lo que haya llegado de T22). |
| 9:00 | Arquitectura | P1 | Módulos (domain, data, features), reglas de negocio en el backend con su copia en el dominio, emuladores y la suite de tests. |
| 10:00 | Preguntas | Todos | |

## Si algo falla en vivo

- **La function tarda o falla**: esperar unos segundos y reintentar; si no responde, seguir con el
  video de respaldo desde ese bloque.
- **No llega una push**: los avisos de reserva, llegada y lista de espera también los arma el
  propio teléfono con la app abierta, así que la demo sigue igual.
- **Sin red**: los datos que ya se leyeron se siguen viendo (Firestore los guarda); mostrarlo como
  parte del manejo sin conexión y pasar al video para lo que necesita el backend.
- **El aviso de llegada no aparece**: abrir el check-in desde Mis reservas; la geocerca puede
  tardar en dispararse con la ubicación simulada.

## Ensayo con los emuladores

Sin el proyecto real se ensaya todo el recorrido en la PC. Desde la raíz del repo:

```
firebase emulators:start --only auth,firestore,functions --project demo-sensorpark
node functions/scripts/seed-emulators.js
```

Con `FIREBASE_EMULATOR_HOST=10.0.2.2` en `local.properties` la app usa los emuladores. Las
cuentas de prueba (`driver@`, `parking-admin@` y `admin@sensorpark.test`, contraseña
`sensorpark`) existen sólo ahí. Para el segundo conductor de la lista de espera, crear otra
cuenta desde la app. En los emuladores no hay pushes: los avisos los muestra el teléfono.

Antes del ensayo, la suite entera en verde:

```
./gradlew testDebugUnitTest :domain:test
./gradlew connectedDebugAndroidTest
firebase emulators:exec --only auth,firestore,functions --project demo-sensorpark "npm --prefix functions test"
firebase emulators:exec --only firestore --project demo-sensorpark "npm --prefix firestore-tests test"
```

Los tests de `app/src/androidTest` necesitan los emuladores de Firebase con la semilla cargada.
`DeviceRepositoryFirebaseTest` además necesita el `google-services.json` del proyecto real: con
uno de prueba, Firebase no entrega el token de push y el test falla.

## Pendiente del equipo

- Ensayar la demo completa con los cinco, cronometrando, y ajustar los tiempos de esta tabla.
- Grabar el video de respaldo del recorrido completo.
- Repetir el ensayo contra el proyecto real cuando esté T23 (integración).
