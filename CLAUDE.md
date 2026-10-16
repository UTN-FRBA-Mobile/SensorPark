# SensorPark

App Android (UTN FRBA, Desarrollo de Apps Móviles) que muestra en tiempo real qué lugares están
libres en estacionamientos, permite reservarlos y avisa por push. Los sensores se simulan con una
Cloud Function.

Este archivo resume cómo está armado el proyecto y qué hay que saber para tocar el código. La
puesta en marcha paso a paso (clonar, `google-services.json`, key de Maps) está en el README.

El código va en inglés: módulos, paquetes, clases, funciones, colecciones y campos de Firestore, y
nombres de Cloud Functions. Los comentarios, los commits, la documentación y los textos que ve el
usuario van en español.

## Comandos

Gradle y los emuladores de Firebase necesitan Java 21 o superior. Si el `java` de tu PATH es más
viejo, alcanza con apuntar `JAVA_HOME` al JBR que trae Android Studio:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
```

App:

```powershell
.\gradlew.bat :app:assembleDebug        # compila todos los módulos
.\gradlew.bat :domain:test              # tests JVM de domain
.\gradlew.bat :data:testDebugUnitTest   # tests JVM de data (conversión de documentos)

# Tests en el emulador de Android contra los emuladores de Firebase (levantados y con la semilla)
.\gradlew.bat :app:connectedDebugAndroidTest
```

Backend (requiere la Firebase CLI, con `firebase login` hecho):

```powershell
# Tests de las reglas de Firestore. La primera vez: npm install dentro de firestore-tests
firebase emulators:exec --only firestore --project demo-sensorpark "npm --prefix firestore-tests test"

# Tests de las functions (roles y contadores). La primera vez: npm install dentro de functions
firebase emulators:exec --only auth,firestore,functions --project demo-sensorpark "npm --prefix functions test"

# Emuladores locales, para probar sin tocar la base real
firebase emulators:start
node functions/scripts/seed-emulators.js       # en otra terminal: carga datos y usuarios de prueba
# Para que la app debug use los emuladores: FIREBASE_EMULATOR_HOST=10.0.2.2 en local.properties
# (10.0.2.2 es la PC vista desde el emulador de Android). Sacar la línea para volver a la base real.

# Despliegue: reglas, índices y functions no viajan en el APK
firebase deploy --only firestore:rules,firestore:indexes
firebase deploy --only functions
```

### Archivos que no están en el repo

Los tres están en el `.gitignore`. No hay que commitearlos ni pegar su contenido en ningún lado.

| Archivo | Para qué | Si falta |
|---|---|---|
| `app/google-services.json` | Conecta la app con el proyecto de Firebase | El build falla |
| `local.properties` (`MAPS_API_KEY`) | Key de Google Maps, una por integrante | Compila, pero el mapa sale gris |
| `functions/.env` (`ADMIN_EMAILS`) | Emails que entran como `ADMIN` | Sólo hace falta para desplegar functions |

## Módulos

| Módulo | Qué contiene |
|---|---|
| `app` | Activity única, navegación (`navigation/`), ingreso y registro, la pestaña Cuenta y la pantalla de diagnóstico |
| `domain` | Kotlin puro: modelos, interfaces de repositorios y casos de uso. Sin Android ni Firebase |
| `data` | Implementaciones con Firebase, módulos de Hilt, servicio de FCM y notificaciones |
| `core/ui` | Tema y componentes comunes (por ejemplo, la pastilla de lugares libres y los textos de tipos de lugar y errores); expone Compose y Material 3 con `api` |
| `feature/{map,parking,reservations,checkin,admin}` | Una funcionalidad cada uno |

Quién puede depender de quién:

- Los `feature` usan `domain` y `core/ui`. Nunca `data` ni otro `feature`.
- `core/ui` usa `domain`, para mostrar sus modelos de la misma forma en todas las pantallas.
- `data` implementa las interfaces de `domain`.
- Sólo `app` conoce a todos y los une.

Cómo se agrega algo:

- **Una pantalla:** el composable va en su `feature` y recibe lambdas para navegar. La ruta se
  declara en `app/.../navigation/Route.kt` y se conecta en `AppNavigation.kt`, dentro del grafo
  de la pestaña a la que pertenece.
- **Un texto que ve el usuario:** en el `res/values/strings.xml` del módulo que lo usa, en
  español, nunca escrito en el código. La app es sólo en español, con formato argentino: no se
  traduce.
- **Un color con significado** (libre, ocupado, reservado, fuera de servicio): de
  `SensorParkTheme.colors` (`core/ui/theme/StatusColors.kt`), que ya tiene versión clara y oscura.
  El resto, de `MaterialTheme.colorScheme`.
- **Un repositorio:** interfaz en `domain/repository`, implementación en `data` y `@Binds` en
  `data/.../di/RepositoriesModule.kt`.
- **Una dependencia:** siempre en `gradle/libs.versions.toml`, nunca con la versión escrita en un
  `build.gradle.kts`.

## Navegación

Al iniciar sesión, la app muestra una barra inferior con las pestañas del rol (`navigation/Tab.kt`).
Cada pestaña es un grafo de navegación propio, así conserva sus pantallas al ir y volver.

| Rol | Pestañas |
|---|---|
| `DRIVER` | **Buscar** (cercanos → detalle → reservar), **Mis reservas** (de ahí se abre el check-in) y **Cuenta** |
| `PARKING_ADMIN` | **Estacionamientos** (los suyos y "Crear"; cada uno abre su panel con las solapas Lugares, Hoy y Datos) y **Cuenta** |
| `ADMIN` | Las mismas que `PARKING_ADMIN`, pero ve todos los estacionamientos |

- **Cuenta:** datos de la sesión, categoría de prioridad (sólo el conductor), avisos y cerrar
  sesión. En el build debug y para `ADMIN` suma las herramientas de desarrollo, por ahora el
  diagnóstico.
- **Pantallas sin pestaña:** el check-in y la oferta de la lista de espera se abren desde una
  reserva o una notificación.
- **Deep link:** `sensorpark://reservation/{id}` abre la pestaña Mis reservas.

## Toolchain

- AGP 9: Kotlin viene integrado, no se aplica `org.jetbrains.kotlin.android`. `domain` usa
  `kotlin.jvm`.
- Firebase BoM 34: no existen los artefactos `-ktx`.
- Hilt con KSP, no kapt.
- Sin flavors.

## Backend

El backend es sólo Firebase; no hay servidor propio, API REST ni WebSocket.

- **Leer:** la app lee Firestore directo, con listeners, y así recibe los cambios en tiempo real.
- **Escribir con reglas de negocio** (reservar, cancelar, cambiar roles): Cloud Functions
  invocables, en `functions/index.js`.
- **Estado propio del teléfono** (favoritos, geocercas): Room.

Firestore y las functions están en `southamerica-east1`. La región está escrita en
`functions/index.js` y en `data/.../di/FirebaseModule.kt`; tienen que coincidir.

### Modelo de datos

| Colección | Qué guarda | Quién escribe |
|---|---|---|
| `users/{uid}` | Nombre, email, rol, categoría, token FCM | El dueño, salvo `role` |
| `parkingLots/{id}` | Estacionamientos: datos y contadores de lugares | Lo crea un `PARKING_ADMIN`, que queda como su administrador; los contadores, sólo el backend |
| `parkingLots/{id}/spots/{id}` | Lugares: código, tipo y estado de cada lugar | Su administrador |
| `parkingLots/{id}/quotas/{fecha}` | Cupos por categoría para una fecha | Su administrador |
| `reservations/{id}` | Reservas | Sólo las functions |
| `waitlist/{id}` | Lista de espera | Sólo las functions |

Estados de un lugar (`status`): `FREE`, `OCCUPIED`, `RESERVED`, `OUT_OF_SERVICE`. Tipos de lugar
(`type`): `STANDARD`, `PRIORITY`, `MOTORCYCLE`, `EV_CHARGING`.

Hace falta sesión para leer cualquier colección, también los estacionamientos: la app no muestra
nada antes del ingreso.

### Estacionamientos y contadores

Un documento de `parkingLots` tiene los datos que carga su administrador (`name`, `address`,
`location` como `GeoPoint`, `schedule` con `opensAt` y `closesAt` en "HH:mm", `toleranceMinutes`,
`geofenceRadiusMeters` y `hourlyRate`) y los contadores que calcula el backend. No hay niveles ni
plantas: para el tamaño del proyecto, cada estacionamiento es una sola lista de lugares.

| Campo | Qué cuenta |
|---|---|
| `capacity`, `freeSpots` | Todos los lugares, y los que están `FREE` |
| `capacityByType`, `freeSpotsByType` | Lo mismo, por tipo de lugar: `{ STANDARD: 8, ... }` |

- Los mantiene la function `updateFreeSpotCounters` (en `functions/counters.js`), que se dispara
  cuando se crea, borra o cambia un lugar. No suma ni resta: vuelve a contar todos los lugares del
  estacionamiento en una transacción. Así no se desfasan aunque el mismo evento llegue dos veces.
- Las reglas no dejan que la app los escriba. Un tipo sin lugares no tiene clave en estos campos;
  la app lo toma como 0.
- Para el mapa y las listas alcanzan los contadores. La subcolección `spots` se lee sólo al abrir
  el detalle de un estacionamiento.

En la app:

- **Modelos:** `ParkingLot` y `Spot` en `domain/model`.
- **Repositorio:** `ParkingLotRepository`. Devuelve flows de `DataResult`, que es `Success` o
  `Failure` con un `DataErrorReason` (`NOT_FOUND`, `PERMISSION_DENIED`, `NO_CONNECTION`, `UNKNOWN`);
  las excepciones de Firestore no salen de `data`.
- **Búsqueda de cercanos:** `SearchNearbyParkingLotsUseCase`. Lee todos los estacionamientos y
  calcula la distancia en `domain` con Haversine (`domain/geo/Distance.kt`). Filtra por radio
  (2 km por defecto), tipo de lugar y "sólo con libres", y ordena del más cercano al más lejano.
- **Conversión:** de documento a modelo, en `data/.../firebase/ParkingLotMapper.kt`.

Pantallas base que ya los consumen (sin mapa ni GPS todavía):

- **Estacionamientos cercanos** (`feature/map`): lista con filtros, desde un punto fijo (UTN
  Medrano) hasta que T11 use la ubicación del teléfono y sume el mapa.
- **Detalle** (`feature/parking`): datos y ocupación total y por tipo de lugar, en vivo. T12 le
  suma el plano de lugares.

La pantalla de diagnóstico también los muestra en vivo. El administrador ve los suyos en su pestaña.

Los nombres de colecciones y campos están en `data/.../firebase/FirestoreNames.kt` y se repiten en
`firestore.rules` y en `functions/`. Si cambia uno, hay que cambiarlo en los tres a la vez.

### Reglas

Firestore deniega todo lo que no tenga una regla explícita, así que una colección nueva necesita su
regla. Todo cambio en `firestore.rules` lleva su caso en `firestore-tests/rules.test.js`, y todo
cambio en las functions, en `functions/test/`.

### Roles

| Rol | Quién es |
|---|---|
| `DRIVER` | Conductor: el usuario final |
| `PARKING_ADMIN` | Administrador de estacionamiento: crea sus estacionamientos y administra los que lo tienen en `adminUids` |
| `ADMIN` | Superusuario del sistema, con acceso total. Sólo para desarrollo y pruebas; no es parte del producto |

- El rol viaja como custom claim `role` en el token del usuario; eso es lo que leen las reglas.
- El usuario elige su rol al registrarse, entre `DRIVER` y `PARKING_ADMIN`; es autogestivo y de
  buena fe. La app lo pide en `users/{uid}.requestedRole` y lo asigna el backend:
  `assignInitialRole` lo valida cuando se crea el documento y después borra `requestedRole`.
  Sin rol pedido, la cuenta entra como `DRIVER`.
- Un `ADMIN` puede cambiar el rol de cualquiera con la function `assignRole`.
- `users/{uid}.role` es una copia para que la app sepa que tiene que refrescar el token. El
  dueño no puede escribirla.
- Entran como `ADMIN` los emails de `ADMIN_EMAILS`. El valor se lee al desplegar las functions y el
  rol inicial se asigna una sola vez, así que hay que agregar el email y desplegar **antes** de
  registrar la cuenta.
  `ADMIN` nunca se elige desde la app.
- El documento del usuario lo tiene que crear el registro, no el token FCM: por eso la app
  registra el dispositivo recién cuando la cuenta ya tiene rol.

### Categoría de prioridad

`users/{uid}.category` vale `STANDARD` (sin prioridad), `PREGNANT` (embarazo) o
`REDUCED_MOBILITY` (movilidad reducida o discapacidad). Como el rol, es de buena fe: la elige el
conductor al registrarse, en un selector aparte y opcional, y la puede cambiar desde Cuenta.
Las reglas sólo validan que el valor exista. Sin categoría, `assignInitialRole` pone `STANDARD`.
Para sumar una subcategoría hay que agregarla en `domain/model/Category.kt`, en `firestore.rules` y
en `app/.../ui/profile/CategorySelector.kt`, con sus textos en `strings.xml`.

### Datos de prueba

`functions/seed.js` define 3 estacionamientos de 14 lugares cada uno, 42 en total.
Los ids son fijos, así que se puede cargar varias veces sin duplicar; cada carga deja todos los
lugares libres.

- **Emuladores:** `node functions/scripts/seed-emulators.js`.
- **Proyecto real:** la function `loadSeed`, que sólo puede llamar un `ADMIN`.

### Notificaciones push

Los mensajes son sólo `data`, con `type`, `id`, `title` y `body` como strings. La app arma la
notificación; las de tipo `reservation*` abren `sensorpark://reservation/{id}`.

## Git

`main` es el entregable y siempre tiene que compilar. Cada tarea va en su rama y entra por Pull
Request; nada se sube directo a `main`.
