# Integración con el backend real (T23)

El paso a paso para llevar la app entera al proyecto de Firebase de verdad (`sensorpark-3f8aa`) y
probarla en un celular. Todo lo que se pudo hacer sin acceso al proyecto ya está hecho y probado
contra los emuladores; lo que queda necesita a alguien con permisos en la consola.

## Lo que ya está verificado con los emuladores

- En `RepositoriesModule` ninguna interfaz está enlazada a una versión falsa: todas usan Firebase,
  Room o Play Services. Las falsas de T04 viven sólo en `core/testing` y en los tests.
- La suite entera en verde: tests unitarios, tests de UI de cada pantalla, tests instrumentados de
  `app` y de `data`, tests de las Cloud Functions y de las reglas (ver el README).
- Recorridos probados en el emulador de Android contra los emuladores de Firebase: reservar, aviso
  de llegada, check-in, check-out, lista de espera con dos cuentas (llenar, anotarse, liberar desde
  otra cuenta, aceptar y rechazar), plano en vivo, favoritos y panel del administrador.
- El release minificado (T25) arranca y navega sin romperse.

## 1. Desplegar el backend

Con la cuenta que tiene permisos en el proyecto (plan Blaze, porque las functions lo necesitan):

```
firebase login
cd functions && npm install && cd ..
cp functions/.env.example functions/.env   # y completar ADMIN_EMAILS
firebase deploy --only firestore:rules,firestore:indexes,functions
```

Los índices tardan unos minutos en construirse: hasta que la consola los muestre como
"Habilitados", las consultas que los usan fallan con `FAILED_PRECONDITION`.

## 2. Cargar los datos

1. Crear las cuentas de prueba desde la app (o la consola de Authentication): un conductor sin
   categoría, uno con categoría de prioridad, un administrador de estacionamiento y uno de
   `ADMIN_EMAILS`, que queda como ADMIN. Las contraseñas no van al repo: se pasan por el grupo.
2. Con la cuenta ADMIN, en Cuenta > Diagnóstico de configuración, tocar "Cargar datos semilla"
   (llama a `loadSeed`; se puede repetir y deja todos los lugares libres).
3. Asignarle al administrador de estacionamiento uno de los estacionamientos (sumar su uid a
   `adminUids` desde la consola), o que cree uno propio desde su panel.

## 3. Configurar la app

- `app/google-services.json` del proyecto real (Configuración del proyecto > Tus apps).
- `MAPS_API_KEY` en `local.properties`, con la key restringida al SHA-1 de debug y al del keystore
  de release.
- Sacar `FIREBASE_EMULATOR_HOST` de `local.properties`: con esa línea el debug apunta a los
  emuladores.
- En Cuenta > Diagnóstico de configuración, confirmar que Auth, el rol, Firestore, FCM, Functions
  y Maps responden.

## 4. El recorrido en un celular real

Antes de empezar, hacer una pasada rápida para despertar las functions: la primera llamada después
de un rato tarda unos segundos.

| # | Paso | Dónde | Hecho |
| --- | --- | --- | --- |
| 1 | Registrarse como conductor (con y sin categoría) e iniciar sesión | Ingreso | [ ] |
| 2 | Encontrar estacionamientos cercanos con el GPS | Buscar | [ ] |
| 3 | Abrir el detalle y ver el plano cambiar con el simulador activo | Buscar > detalle | [ ] |
| 4 | Reservar una franja y recibir la notificación de confirmación (push) | Reservar | [ ] |
| 5 | Acercarse (o simular la ubicación) y recibir el aviso de llegada | Aviso | [ ] |
| 6 | Hacer check-in y ver el lugar ocupado | Mis reservas | [ ] |
| 7 | Hacer check-out y ver el lugar libre | Aviso "Salir" | [ ] |
| 8 | Llenar un estacionamiento, anotarse en la lista de espera, liberar desde otra cuenta y aceptar la oferta (push) | Reservar, Mis reservas | [ ] |
| 9 | Dejar vencer una reserva y recibir el aviso de "por vencer" (push) | Aviso | [ ] |
| 10 | Como administrador, marcar un lugar como preferencial y ver que un conductor sin esa categoría no lo puede reservar | Panel > Lugares | [ ] |
| 11 | Marcar un favorito, activar el modo avión y ver favoritos e historial | Buscar, Mis reservas | [ ] |

Lo que sólo se puede probar acá, porque en los emuladores no hay pushes: los pasos 4, 8 y 9 con
la app cerrada.

## 5. Casos de red

- [ ] Modo avión a mitad de una reserva: la pantalla avisa "sin conexión" y no queda colgada.
- [ ] Una function que tarda: el botón queda en "Reservando…" y a los 20 s avisa.
- [ ] Red lenta en Buscar y en el detalle: se ve lo último guardado con el aviso de sin conexión.
- [ ] Favorito marcado sin red: aparece en `users/{uid}/favorites` cuando vuelve la conexión.

## 6. Cerrar

- Cargar cada problema como Bug en Jira, asignado a quien corresponde, con pasos y prioridad.
- Cuando el recorrido salga entero sin bloqueantes, etiquetar `main`:

  ```
  git tag -a v1.0-rc -m "Versión candidata: recorrido completo contra el backend real"
  git push origin v1.0-rc
  ```

- Revisar en la consola las lecturas y escrituras de una prueba completa con el simulador activo.
