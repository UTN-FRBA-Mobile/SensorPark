# SensorPark

App Android que muestra en tiempo real qué lugares están libres en estacionamientos, permite
reservarlos y avisa con notificaciones los cambios que le importan al conductor. La ocupación de
cada cochera la informa un sensor, que en este proyecto se simula por software.

Kotlin · Jetpack Compose · Material 3 · Hilt · Navigation Compose · Firebase (Auth, Firestore,
Cloud Functions, FCM) · Google Maps.

## Cómo correrlo por primera vez

Necesitás Android Studio con soporte para AGP 9.2 y el SDK de Android 36.1 (lo ofrece instalar al
abrir el proyecto). No hace falta instalar Gradle ni un JDK aparte.

1. Cloná el repo y abrilo en Android Studio.

   ```
   git clone git@github.com:UTN-FRBA-Mobile/SensorPark.git
   ```

2. Conseguí `google-services.json`: pedí que te sumen al proyecto de Firebase, entrá a la consola,
   *Configuración del proyecto → Tus apps → SensorPark (Android)*, descargalo y copialo a
   `app/google-services.json`. Sin ese archivo el proyecto no compila.

3. Completá `local.properties` en la raíz (Android Studio lo crea solo con `sdk.dir`):

   ```properties
   sdk.dir=C\:\\Users\\<vos>\\AppData\\Local\\Android\\Sdk
   MAPS_API_KEY=<key de Maps SDK for Android>
   ```

   Sin `MAPS_API_KEY` la app compila y corre igual, pero el mapa se ve gris. Cada integrante
   crea su propia key en [Google Cloud Console](https://console.cloud.google.com): habilitar
   *Maps SDK for Android*, y en *Credenciales → Crear credenciales → Clave de API* restringirla
   a apps Android con package `com.sensorpark.app` y el SHA-1 de tu keystore de debug
   (`./gradlew signingReport`).

4. Sincronizá Gradle y corré la configuración `app` en un emulador o dispositivo con Google Play
   Services.

5. Creá una cuenta desde la app, eligiendo si sos conductor o administrás un estacionamiento.
   Después de unos segundos de "Preparando tu cuenta…" aparecen las pestañas de tu rol. En
   **Cuenta > Diagnóstico de configuración** (sólo en el build debug) se verifica contra el
   proyecto real Auth, el rol, Firestore, FCM, Functions y Maps.

`google-services.json`, `local.properties` y `functions/.env` están en el `.gitignore`: ninguna
clave se sube al repositorio.

## Módulos

| Módulo | Qué contiene |
|---|---|
| `app` | `MainActivity` (Activity única), navegación principal, configuración de Hilt, login, inicio y preferencias |
| `domain` | Modelos, interfaces de repositorios y casos de uso. Kotlin puro: sin Android ni Firebase |
| `data` | Implementación de los repositorios, Firebase, notificaciones. Acá van también Room, Retrofit, DataStore y WorkManager |
| `core/ui` | Tema y componentes comunes de Compose |
| `feature/map` | Mapa de estacionamientos |
| `feature/parking` | Detalle de un estacionamiento |
| `feature/reservations` | Reservar y mis reservas |
| `feature/checkin` | Check-in |
| `feature/admin` | Administración |

Reglas de dependencia: los `feature` dependen de `domain` y `core/ui`, nunca entre sí ni de `data`.
`data` implementa las interfaces de `domain`. `app` es el único que conoce a todos y los une.

### Cómo se agrega algo

- **Una pantalla:** el composable va en su `feature` y recibe lambdas para navegar (no conoce las
  rutas). La ruta se declara en `app/.../navigation/Route.kt` y se conecta en `AppNavigation.kt`.
- **Un repositorio:** la interfaz en `domain/repository`, la implementación en `data` y el `@Binds`
  en `data/.../di/RepositoriesModule.kt`. Los ViewModels (`@HiltViewModel`) piden la interfaz.
- **Una dependencia:** siempre por el catálogo `gradle/libs.versions.toml`, nunca con la versión
  escrita en un `build.gradle.kts`.

Deep links disponibles: `sensorpark://reservation/{id}` abre *Mis reservas* en esa reserva, y
`sensorpark://check-in/{id}` abre el check-in de esa reserva (es el que usa el aviso de llegada).
Para probarlos:

```
adb shell am start -a android.intent.action.VIEW -d "sensorpark://reservation/123" com.sensorpark.app
adb shell am start -a android.intent.action.VIEW -d "sensorpark://check-in/123" com.sensorpark.app
```

## Convenciones

- **El código va en inglés:** módulos, paquetes, clases, funciones, colecciones y campos de
  Firestore, y nombres de Cloud Functions.
- **Van en español:** los comentarios, los commits, la documentación y los textos que ve el usuario.
- **Estilos y colores:** usar siempre `MaterialTheme.colorScheme` o `SensorParkTheme.colors` (`SensorParkColors`), nunca colores fijos (hardcodeados) en una pantalla. Los textos siempre van en `strings.xml`.
- `main` es el entregable: siempre compila.
- Cada tarea se hace en una rama propia que sale de `main` y vuelve por Pull Request.

## Backend (Firebase)

El contrato entre la app y el backend (cada colección, cada function y cada aviso) está en
[docs/contract.md](docs/contract.md); las reglas de reserva, en
[docs/reservation-rules.md](docs/reservation-rules.md).

Las reglas, los índices y las functions no viajan en el APK: se despliegan aparte con la
[Firebase CLI](https://firebase.google.com/docs/cli).

```
firebase login
firebase use --add
firebase deploy --only firestore:rules,firestore:indexes,functions
```

- `firestore.rules` y `firestore.indexes.json`: reglas e índices de la base `(default)`.
- `functions/`: Cloud Functions (Node 22). Asignan el rol de cada usuario como custom claim,
  mantienen los contadores de lugares libres, mandan las pushes y simulan los sensores. Antes de desplegar, copiá `functions/.env.example` a
  `functions/.env` y poné en `ADMIN_EMAILS` los emails que entran como administradores.
- `functions/seed.js`: datos semilla (3 estacionamientos de 14 lugares, 42 en total). En el
  proyecto real los carga un usuario `ADMIN` llamando a la function `loadSeed`; se puede
  repetir sin duplicar y deja todos los lugares libres.
- **Emuladores**, para probar sin tocar la base real. Necesitan Java 21 o superior:

  ```
  firebase emulators:start
  node functions/scripts/seed-emulators.js
  ```

  El segundo comando, en otra terminal, carga la semilla y crea un usuario por rol
  (`driver@sensorpark.test`, `parking-admin@sensorpark.test` y `admin@sensorpark.test`, con
  contraseña `sensorpark`). Esas cuentas existen sólo en el emulador. La consola de los emuladores
  queda en http://localhost:4000.

  Para que la app use los emuladores, agregá `FIREBASE_EMULATOR_HOST=10.0.2.2` a
  `local.properties` y reinstalá el build debug (10.0.2.2 es tu PC vista desde el emulador de
  Android). Sacando la línea, la app vuelve a la base real.
- `firestore-tests/`: tests de las reglas contra el emulador. Necesitan Java 21 o superior (sirve
  el `jbr` de Android Studio como `JAVA_HOME`).

  ```
  cd firestore-tests && npm install && cd ..
  firebase emulators:exec --only firestore --project demo-sensorpark "npm --prefix firestore-tests test"
  ```
- `functions/test/`: tests de las functions de roles y de contadores contra los emuladores de
  Auth, Firestore y Functions. Usan `functions/.env.demo-sensorpark`, que tiene un email de prueba en `ADMIN_EMAILS`.

  ```
  cd functions && npm install && cd ..
  firebase emulators:exec --only auth,firestore,functions --project demo-sensorpark "npm --prefix functions test"
  ```

## Tests

```
./gradlew :domain:test :data:testDebugUnitTest
```

Los tests de `app/src/androidTest` corren en el emulador de Android y hablan con los emuladores de
Firebase, así que antes hay que levantarlos y cargar la semilla (ver arriba):

```
./gradlew :app:connectedDebugAndroidTest
```

Los tests de UI de las pantallas (`feature/*/src/androidTest`) y el de Room (`data`) también corren
en el emulador de Android, pero con los repositorios falsos de T04: no necesitan Firebase. Todo
junto, módulo por módulo para que no se pisen en el mismo emulador:

```
./gradlew :data:connectedDebugAndroidTest :feature:map:connectedDebugAndroidTest :feature:parking:connectedDebugAndroidTest :feature:reservations:connectedDebugAndroidTest :feature:checkin:connectedDebugAndroidTest :feature:admin:connectedDebugAndroidTest --no-parallel
```

El guion de la demo y cómo ensayarla están en [docs/guion-demo.md](docs/guion-demo.md).

## Versión firmada (T25)

El release minifica con R8 y se firma con el keystore del equipo, que **no** va al repo: se guarda
con respaldo fuera de él (si se pierde, no se puede volver a actualizar la app). Se apunta desde
`local.properties` o, en CI, con variables de entorno de los mismos nombres:

```properties
SENSORPARK_STORE_FILE=C:\ruta\al\sensorpark.jks
SENSORPARK_STORE_PASSWORD=...
SENSORPARK_KEY_ALIAS=sensorpark
SENSORPARK_KEY_PASSWORD=...
```

```
./gradlew :app:bundleRelease     # app/build/outputs/bundle/release/app-release.aab, para Play
./gradlew :app:assembleRelease   # app/build/outputs/apk/release/app-release.apk, instalable directo
```

Sin esos datos el release sale sin firmar: sirve para revisar que compile y que R8 no rompa nada.
Los cambios de cada versión están en [CHANGELOG.md](CHANGELOG.md).
