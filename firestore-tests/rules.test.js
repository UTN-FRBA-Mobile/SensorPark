// Tests de firestore.rules. Corren contra el emulador, con un proyecto falso (prefijo demo-):
//   firebase emulators:exec --only firestore --project demo-sensorpark "npm --prefix firestore-tests test"
// El emulador pide Java 21+; sirve el `jbr` de Android Studio.
import { after, before, beforeEach, describe, test } from "node:test";
import { readFileSync } from "node:fs";
import { assertFails, assertSucceeds, initializeTestEnvironment } from "@firebase/rules-unit-testing";
import { deleteDoc, doc, getDoc, setDoc, updateDoc } from "firebase/firestore";

let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: "demo-sensorpark",
    firestore: { rules: readFileSync(new URL("../firestore.rules", import.meta.url), "utf8") },
  });
});

after(() => testEnv.cleanup());

// Datos de base, cargados sin pasar por las reglas (como lo haría el backend).
beforeEach(async () => {
  await testEnv.clearFirestore();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, "users/ana"), { name: "Ana", role: "DRIVER", category: "STANDARD" });
    await setDoc(doc(db, "parkingLots/e1"), {
      name: "Playa Centro", adminUids: ["encargado"], capacity: 1, freeSpots: 1,
    });
    await setDoc(doc(db, "parkingLots/e1/spots/A-01"), { code: "A-01", status: "FREE" });
    await setDoc(doc(db, "reservations/r1"), { userId: "ana", parkingLotId: "e1", spotId: "A-01" });
    await setDoc(doc(db, "waitlist/w1"), { userId: "ana", parkingLotId: "e1" });
  });
});

const anonymous = () => testEnv.unauthenticatedContext().firestore();
const as = (uid, role) => testEnv.authenticatedContext(uid, role ? { role } : {}).firestore();
const driver = (uid) => as(uid, "DRIVER");
const parkingAdmin = (uid) => as(uid, "PARKING_ADMIN");
const admin = () => as("jefa", "ADMIN");

describe("usuarios", () => {
  test("el dueño lee su documento", () =>
    assertSucceeds(getDoc(doc(driver("ana"), "users/ana"))));

  test("otro usuario no lo lee", () =>
    assertFails(getDoc(doc(driver("beto"), "users/ana"))));

  test("un ADMIN lo lee", () =>
    assertSucceeds(getDoc(doc(admin(), "users/ana"))));

  test("sin sesión no se lee", () =>
    assertFails(getDoc(doc(anonymous(), "users/ana"))));

  test("el dueño crea su documento sin rol, aunque todavía no tenga claim", () =>
    assertSucceeds(setDoc(doc(as("beto"), "users/beto"), { name: "Beto" })));

  test("el dueño no puede crearse con rol", () =>
    assertFails(setDoc(doc(as("beto"), "users/beto"), { name: "Beto", role: "ADMIN" })));

  test("al registrarse pide ser conductor o administrador de estacionamiento", async () => {
    await assertSucceeds(setDoc(doc(as("beto"), "users/beto"), { name: "Beto", requestedRole: "DRIVER" }));
    await assertSucceeds(setDoc(doc(as("caro"), "users/caro"), { name: "Caro", requestedRole: "PARKING_ADMIN" }));
  });

  test("al registrarse no puede pedir ser ADMIN", () =>
    assertFails(setDoc(doc(as("beto"), "users/beto"), { name: "Beto", requestedRole: "ADMIN" })));

  test("el dueño declara su categoría de prioridad", () =>
    assertSucceeds(setDoc(doc(as("beto"), "users/beto"), { name: "Beto", category: "PREGNANT" })));

  test("el dueño no declara una categoría inexistente", () =>
    assertFails(setDoc(doc(as("beto"), "users/beto"), { name: "Beto", category: "VIP" })));

  test("el dueño actualiza su token FCM", () =>
    assertSucceeds(updateDoc(doc(driver("ana"), "users/ana"), { fcmToken: "t" })));

  test("el dueño no puede cambiarse el rol", () =>
    assertFails(updateDoc(doc(driver("ana"), "users/ana"), { role: "ADMIN" })));

  test("el dueño no puede pedir otro rol después de registrarse", () =>
    assertFails(updateDoc(doc(driver("ana"), "users/ana"), { requestedRole: "PARKING_ADMIN" })));

  test("el dueño cambia su categoría desde el perfil", () =>
    assertSucceeds(updateDoc(doc(driver("ana"), "users/ana"), { category: "REDUCED_MOBILITY" })));

  test("el dueño no se pone una categoría inexistente", () =>
    assertFails(updateDoc(doc(driver("ana"), "users/ana"), { category: "VIP" })));

  test("el dueño guarda los tipos de aviso que apagó", () =>
    assertSucceeds(updateDoc(doc(driver("ana"), "users/ana"), { notificationsDisabled: ["RESERVATION", "WAITLIST"] })));

  test("los avisos apagados sólo aceptan tipos que existen", () =>
    assertFails(updateDoc(doc(driver("ana"), "users/ana"), { notificationsDisabled: ["MARKETING"] })));

  test("los avisos apagados son una lista", () =>
    assertFails(updateDoc(doc(driver("ana"), "users/ana"), { notificationsDisabled: "RESERVATION" })));
});

describe("dispositivos", () => {
  test("el dueño registra su teléfono con el token como id", () =>
    assertSucceeds(setDoc(doc(driver("ana"), "users/ana/devices/tok-1"), { token: "tok-1" })));

  test("el token del documento tiene que ser su id", () =>
    assertFails(setDoc(doc(driver("ana"), "users/ana/devices/tok-1"), { token: "otro" })));

  test("un dispositivo no guarda otros campos", () =>
    assertFails(setDoc(doc(driver("ana"), "users/ana/devices/tok-1"), { token: "tok-1", role: "ADMIN" })));

  test("otro usuario no registra un teléfono en una cuenta ajena", () =>
    assertFails(setDoc(doc(driver("beto"), "users/ana/devices/tok-1"), { token: "tok-1" })));

  test("el dueño lo borra al cerrar sesión, y otro no", async () => {
    await testEnv.withSecurityRulesDisabled((ctx) =>
      setDoc(doc(ctx.firestore(), "users/ana/devices/tok-1"), { token: "tok-1" }));
    await assertFails(deleteDoc(doc(driver("beto"), "users/ana/devices/tok-1")));
    await assertFails(getDoc(doc(driver("beto"), "users/ana/devices/tok-1")));
    await assertSucceeds(deleteDoc(doc(driver("ana"), "users/ana/devices/tok-1")));
  });
});

describe("favoritos", () => {
  test("el dueño guarda un favorito con el id del estacionamiento", () =>
    assertSucceeds(setDoc(doc(driver("ana"), "users/ana/favorites/e1"), { parkingLotId: "e1" })));

  test("el id del estacionamiento tiene que ser el del documento", () =>
    assertFails(setDoc(doc(driver("ana"), "users/ana/favorites/e1"), { parkingLotId: "e2" })));

  test("un favorito no guarda otros campos", () =>
    assertFails(setDoc(doc(driver("ana"), "users/ana/favorites/e1"), { parkingLotId: "e1", role: "ADMIN" })));

  test("otro usuario no ve, no agrega ni borra favoritos ajenos", async () => {
    await testEnv.withSecurityRulesDisabled((ctx) =>
      setDoc(doc(ctx.firestore(), "users/ana/favorites/e1"), { parkingLotId: "e1" }));
    await assertFails(getDoc(doc(driver("beto"), "users/ana/favorites/e1")));
    await assertFails(setDoc(doc(driver("beto"), "users/ana/favorites/e2"), { parkingLotId: "e2" }));
    await assertFails(deleteDoc(doc(driver("beto"), "users/ana/favorites/e1")));
    await assertSucceeds(getDoc(doc(driver("ana"), "users/ana/favorites/e1")));
    await assertSucceeds(deleteDoc(doc(driver("ana"), "users/ana/favorites/e1")));
  });
});

describe("estacionamientos", () => {
  test("cualquier autenticado los ve", () =>
    assertSucceeds(getDoc(doc(driver("ana"), "parkingLots/e1"))));

  test("sin sesión no se ven", () =>
    assertFails(getDoc(doc(anonymous(), "parkingLots/e1"))));

  test("un conductor no los crea", () =>
    assertFails(setDoc(doc(driver("ana"), "parkingLots/e2"), { name: "X", adminUids: [] })));

  test("un ADMIN los crea", () =>
    assertSucceeds(setDoc(doc(admin(), "parkingLots/e2"), { name: "X", adminUids: [] })));

  test("un administrador de estacionamiento crea el suyo", () =>
    assertSucceeds(setDoc(doc(parkingAdmin("caro"), "parkingLots/e2"), { name: "X", adminUids: ["caro"] })));

  test("al crearlo no puede dejar a otro como administrador", async () => {
    await assertFails(setDoc(doc(parkingAdmin("caro"), "parkingLots/e2"), { name: "X", adminUids: ["otro"] }));
    await assertFails(setDoc(doc(parkingAdmin("caro"), "parkingLots/e2"), { name: "X", adminUids: ["caro", "otro"] }));
  });

  test("al crearlo no carga los contadores", async () => {
    await assertFails(setDoc(doc(parkingAdmin("caro"), "parkingLots/e2"), { name: "X", adminUids: ["caro"], freeSpots: 50 }));
    await assertFails(setDoc(doc(parkingAdmin("caro"), "parkingLots/e2"),
      { name: "X", adminUids: ["caro"], capacityByType: { STANDARD: 50 } }));
  });

  test("su administrador lo borra", () =>
    assertSucceeds(deleteDoc(doc(parkingAdmin("encargado"), "parkingLots/e1"))));

  test("un administrador de otro estacionamiento no lo borra", () =>
    assertFails(deleteDoc(doc(parkingAdmin("otro"), "parkingLots/e1"))));

  test("su encargado edita los datos", () =>
    assertSucceeds(updateDoc(doc(parkingAdmin("encargado"), "parkingLots/e1"), { name: "Nuevo" })));

  test("su encargado no toca los contadores", async () => {
    const lot = doc(parkingAdmin("encargado"), "parkingLots/e1");
    await assertFails(updateDoc(lot, { freeSpots: 99 }));
    await assertFails(updateDoc(lot, { capacity: 99 }));
    await assertFails(updateDoc(lot, { freeSpotsByType: { STANDARD: 99 } }));
    await assertFails(updateDoc(lot, { capacityByType: { STANDARD: 99 } }));
  });

  test("un ADMIN corrige los contadores a mano", () =>
    assertSucceeds(updateDoc(doc(admin(), "parkingLots/e1"), { freeSpots: 0 })));

  test("su encargado no se suma administradores", () =>
    assertFails(updateDoc(doc(parkingAdmin("encargado"), "parkingLots/e1"), { adminUids: ["encargado", "otro"] })));

  test("un encargado de otro estacionamiento no lo edita", () =>
    assertFails(updateDoc(doc(parkingAdmin("otro"), "parkingLots/e1"), { name: "Nuevo" })));
});

describe("lugares", () => {
  test("cualquier autenticado ve la ocupación", () =>
    assertSucceeds(getDoc(doc(driver("ana"), "parkingLots/e1/spots/A-01"))));

  test("sin sesión no se ve", () =>
    assertFails(getDoc(doc(anonymous(), "parkingLots/e1/spots/A-01"))));

  test("un conductor no cambia el estado de un lugar", () =>
    assertFails(updateDoc(doc(driver("ana"), "parkingLots/e1/spots/A-01"), { status: "OCCUPIED" })));

  test("su encargado edita un lugar", () =>
    assertSucceeds(updateDoc(
      doc(parkingAdmin("encargado"), "parkingLots/e1/spots/A-01"), { status: "OUT_OF_SERVICE" })));

  test("un encargado de otro estacionamiento no lo edita", () =>
    assertFails(updateDoc(
      doc(parkingAdmin("otro"), "parkingLots/e1/spots/A-01"), { status: "OUT_OF_SERVICE" })));

  test("ya no hay cupos por fecha: nadie los lee ni los escribe", async () => {
    await assertFails(getDoc(doc(driver("ana"), "parkingLots/e1/quotas/2026-11-02")));
    await assertFails(setDoc(doc(parkingAdmin("encargado"), "parkingLots/e1/quotas/2026-11-03"), { PREGNANT: 20 }));
  });
});

describe("reservas", () => {
  test("el conductor lee su reserva", () =>
    assertSucceeds(getDoc(doc(driver("ana"), "reservations/r1"))));

  test("otro conductor no la lee", () =>
    assertFails(getDoc(doc(driver("beto"), "reservations/r1"))));

  test("el encargado del estacionamiento la lee", () =>
    assertSucceeds(getDoc(doc(parkingAdmin("encargado"), "reservations/r1"))));

  test("un encargado de otro estacionamiento no la lee", () =>
    assertFails(getDoc(doc(parkingAdmin("otro"), "reservations/r1"))));

  test("nadie crea una reserva desde la app: sólo las functions", async () => {
    await assertFails(setDoc(doc(driver("ana"), "reservations/r2"), { userId: "ana", parkingLotId: "e1" }));
    await assertFails(setDoc(doc(admin(), "reservations/r2"), { userId: "ana", parkingLotId: "e1" }));
  });

  test("el conductor no modifica ni borra su reserva directo", async () => {
    await assertFails(updateDoc(doc(driver("ana"), "reservations/r1"), { status: "IN_USE" }));
    await assertFails(updateDoc(doc(driver("ana"), "reservations/r1"), { status: "CANCELLED" }));
    await assertFails(deleteDoc(doc(driver("ana"), "reservations/r1")));
  });

  test("el encargado tampoco escribe reservas", async () => {
    await assertFails(updateDoc(doc(parkingAdmin("encargado"), "reservations/r1"), { status: "EXPIRED" }));
    await assertFails(setDoc(doc(parkingAdmin("encargado"), "reservations/r3"), { userId: "ana", parkingLotId: "e1" }));
  });
});

describe("lista de espera", () => {
  test("el usuario lee su entrada", () =>
    assertSucceeds(getDoc(doc(driver("ana"), "waitlist/w1"))));

  test("otro usuario no la lee", () =>
    assertFails(getDoc(doc(driver("beto"), "waitlist/w1"))));

  test("nadie se anota escribiendo directo", () =>
    assertFails(setDoc(doc(driver("ana"), "waitlist/w2"), { userId: "ana" })));

  test("el usuario no modifica ni borra su entrada directo", async () => {
    await assertFails(updateDoc(doc(driver("ana"), "waitlist/w1"), { status: "LEFT" }));
    await assertFails(deleteDoc(doc(driver("ana"), "waitlist/w1")));
  });
});

describe("configuración del simulador", () => {
  const config = { scenario: "NORMAL", intervalSeconds: 10, paused: true };

  test("un ADMIN la escribe y la lee", async () => {
    await assertSucceeds(setDoc(doc(admin(), "config/simulator"), config));
    await assertSucceeds(getDoc(doc(admin(), "config/simulator")));
  });

  test("un ADMIN la modifica", async () => {
    await assertSucceeds(setDoc(doc(admin(), "config/simulator"), config));
    await assertSucceeds(updateDoc(doc(admin(), "config/simulator"), { paused: false }));
  });

  test("ni el conductor ni el encargado la leen ni la escriben", async () => {
    await testEnv.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), "config/simulator"), config));
    for (const db of [driver("ana"), parkingAdmin("encargado")]) {
      await assertFails(getDoc(doc(db, "config/simulator")));
      await assertFails(updateDoc(doc(db, "config/simulator"), { paused: false }));
      await assertFails(setDoc(doc(db, "config/simulator"), config));
    }
  });

  test("sin sesión tampoco", () =>
    assertFails(getDoc(doc(anonymous(), "config/simulator"))));
});
