# SensorPark

App Android (UTN FRBA, Desarrollo de Apps Móviles) que muestra en tiempo real qué cocheras están
libres, permite reservarlas y avisa por push. Los sensores se simulan con una Cloud Function.
El README tiene la puesta en marcha para el equipo; esto es lo que hace falta para trabajar en el código.

Código, comentarios, commits y nombres de dominio van en español.

## Comandos

En Windows el `java` del PATH es Java 8: Gradle y el emulador de Firestore necesitan el JBR de
Android Studio.

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"

.\gradlew.bat :app:assembleDebug        # compila todos los módulos
.\gradlew.bat :domain:test              # tests JVM de domain

# Tests de firestore.rules (la primera vez: npm install dentro de firestore-tests)
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
firebase emulators:exec --only firestore --project demo-sensorpark "npm --prefix firestore-tests test"

# Emuladores con datos de prueba (en otra terminal: node functions/scripts/sembrar.js)
firebase emulators:start

# Backend: nada de esto viaja en el APK
firebase deploy --only firestore:rules,firestore:indexes
firebase deploy --only functions
```

Sin `app/google-services.json` el build falla. Está gitignoreado, igual que `local.properties`
(`MAPS_API_KEY`) y `functions/.env` (`ADMIN_EMAILS`). No commitear ninguno ni imprimir sus valores.

## Módulos

- `app`: Activity única, navegación (`navegacion/Ruta.kt`, `NavegacionApp.kt`), login, inicio,
  preferencias y la pantalla de diagnóstico.
- `domain`: Kotlin puro. Modelos, interfaces de repositorios, casos de uso. Sin Android ni Firebase.
- `data`: implementaciones con Firebase, módulos de Hilt, servicio de FCM y notificaciones.
- `core/ui`: tema y componentes comunes; expone Compose y Material 3 con `api`.
- `feature/{mapa,estacionamiento,reservas,checkin,admin}`: una funcionalidad cada uno.

Dependencias permitidas: los `feature` usan `domain` y `core/ui`, nunca `data` ni otro `feature`.
`data` implementa `domain`. Sólo `app` conoce a todos. Las pantallas reciben lambdas para navegar;
las rutas viven únicamente en `app`.

Para sumar un repositorio: interfaz en `domain/repository`, implementación en `data` y `@Binds` en
`data/.../di/RepositoriosModule.kt`. Las versiones van siempre en `gradle/libs.versions.toml`.

## Toolchain

- AGP 9: Kotlin viene integrado, no se aplica `org.jetbrains.kotlin.android`. `domain` usa
  `kotlin.jvm`.
- Firebase BoM 34: no existen los artefactos `-ktx`.
- Hilt con KSP, no kapt.
- Sin flavors: se descartaron a propósito.

## Backend y tareas

El backend es sólo Firebase: la app lee Firestore directo (en tiempo real, con listeners) y lo que
tiene reglas de negocio va en Cloud Functions invocables. No hay servidor propio, API REST ni
WebSocket; Retrofit y OkHttp quedaron en el catálogo sin uso. Room se usa únicamente donde hace
falta estado propio del teléfono (favoritos, geocercas).

Las tareas están en Jira, proyecto `KAN` de `frba-team-wathitdw.atlassian.net`, y ya describen
esta arquitectura.

**Modelo de datos.** Las colecciones son `usuarios`, `estacionamientos` (con subcolecciones
`lugares` y `cupos`), `reservas` y `listaEspera`. Estados de lugar: `LIBRE`, `OCUPADO`,
`RESERVADO`, `FUERA_DE_SERVICIO`. La app no escribe reservas ni lista de espera: sólo las
functions. El contrato formal (KAN-4) todavía no se cerró; si cambia un nombre hay que tocarlo en
`firestore.rules`, `functions/` y `data/.../firebase/Colecciones.kt` a la vez.

**Semilla.** `functions/semilla.js` tiene 3 estacionamientos, 2 niveles cada uno y 42 lugares, con
ids fijos. En el proyecto real la carga la function `cargarSemilla` (sólo `ADMIN`); en los
emuladores, `node functions/scripts/sembrar.js`, que además crea un usuario por rol.

## Firebase

Proyecto `sensorpark-3f8aa`, Firestore `(default)` y functions en `southamerica-east1`. La región
está escrita en `functions/index.js` (`setGlobalOptions`) y en `data/.../di/FirebaseModule.kt`;
tienen que coincidir.

- **Roles** (`CONDUCTOR`, `ADMIN_ESTACIONAMIENTO`, `ADMIN`): custom claim `rol`, que es lo que leen
  las reglas. Lo asigna `asignarRolInicial` cuando se crea `users/{uid}`; los emails de
  `ADMIN_EMAILS` entran como `ADMIN`. `users/{uid}.rol` es sólo el aviso para que la app refresque
  el token, y el dueño no puede escribirlo.
- **Para que una cuenta sea `ADMIN`** hay que tocar `ADMIN_EMAILS` en `functions/.env` (emails
  separados por coma) y correr `firebase deploy --only functions` **antes** de registrarla. El valor
  se lee al desplegar, y el rol se asigna una sola vez: una cuenta que ya se registró no cambia
  aunque después se agregue su email. Para corregirla hay que borrarla (Authentication y su
  documento en `users`) y registrarla de nuevo, o cambiarle el rol con `asignarRol`.
- **Pendiente de decidir: elegir el rol al crear la cuenta.** Hoy el registro no pregunta nada y
  todos entran como `CONDUCTOR`. Si se quiere sumar un paso para elegir entre conductor y
  administrador, ese "administrador" es el de un garage (`ADMIN_ESTACIONAMIENTO`), no el del
  sistema (`ADMIN`). El rol lo tiene que seguir asignando el backend: la app mandaría el rol
  pedido y `asignarRolInicial` aceptaría sólo `CONDUCTOR` o `ADMIN_ESTACIONAMIENTO`. `ADMIN` nunca
  se puede elegir desde la app; sigue saliendo únicamente de `ADMIN_EMAILS`.
- **Pushes**: sólo `data`, con `tipo`, `id`, `titulo` y `cuerpo` como strings. La app arma la
  notificación; las de tipo `reserva*` abren `sensorpark://reserva/{id}`.
- **Reglas**: todo cambio en `firestore.rules` lleva su caso en `firestore-tests/reglas.test.js`.
  Colección nueva = regla nueva (Firestore deniega por defecto).
- Los nombres de colecciones y campos están en `data/.../firebase/Colecciones.kt` y se repiten en
  las reglas y en las functions.

### Cuenta de prueba

Hay una cuenta compartida del equipo con rol `ADMIN` para probar la app contra el proyecto real.
Las credenciales no están en el repo, que es público: se piden por el grupo del equipo.

Es sólo para pruebas: no guardar ahí datos reales. Para probar los otros roles, registrar una cuenta
nueva (entra como `CONDUCTOR`) y cambiarle el rol con la function `asignarRol`.

## Git

`main` es el entregable. Cada tarea va en su rama y entra por Pull Request. No hay `gh` instalado.
