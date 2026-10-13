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
    await setDoc(doc(db, "parkingLots/e1/quotas/2026-11-02"), { PRIORITY: 10 });
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

  test("el dueño no puede crearse con categoría", () =>
    assertFails(setDoc(doc(as("beto"), "users/beto"), { name: "Beto", category: "SUBSCRIBER" })));

  test("el dueño actualiza su token FCM", () =>
    assertSucceeds(updateDoc(doc(driver("ana"), "users/ana"), { fcmToken: "t" })));

  test("el dueño no puede cambiarse el rol", () =>
    assertFails(updateDoc(doc(driver("ana"), "users/ana"), { role: "ADMIN" })));

  test("el dueño no puede cambiarse la categoría", () =>
    assertFails(updateDoc(doc(driver("ana"), "users/ana"), { category: "SUBSCRIBER" })));
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

  test("su encargado edita los datos", () =>
    assertSucceeds(updateDoc(doc(parkingAdmin("encargado"), "parkingLots/e1"), { name: "Nuevo" })));

  test("su encargado no toca los contadores", () =>
    assertFails(updateDoc(doc(parkingAdmin("encargado"), "parkingLots/e1"), { freeSpots: 99 })));

  test("su encargado no se suma administradores", () =>
    assertFails(updateDoc(doc(parkingAdmin("encargado"), "parkingLots/e1"), { adminUids: ["encargado", "otro"] })));

  test("un encargado de otro estacionamiento no lo edita", () =>
    assertFails(updateDoc(doc(parkingAdmin("otro"), "parkingLots/e1"), { name: "Nuevo" })));
});

describe("lugares y cupos", () => {
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

  test("cualquier autenticado lee los cupos", () =>
    assertSucceeds(getDoc(doc(driver("ana"), "parkingLots/e1/quotas/2026-11-02"))));

  test("su encargado define los cupos de una fecha", () =>
    assertSucceeds(setDoc(doc(parkingAdmin("encargado"), "parkingLots/e1/quotas/2026-11-03"), { SUBSCRIBER: 20 })));

  test("un conductor no define cupos", () =>
    assertFails(setDoc(doc(driver("ana"), "parkingLots/e1/quotas/2026-11-03"), { SUBSCRIBER: 20 })));
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
    await assertFails(deleteDoc(doc(driver("ana"), "reservations/r1")));
  });
});

describe("lista de espera", () => {
  test("el usuario lee su entrada", () =>
    assertSucceeds(getDoc(doc(driver("ana"), "waitlist/w1"))));

  test("otro usuario no la lee", () =>
    assertFails(getDoc(doc(driver("beto"), "waitlist/w1"))));

  test("nadie se anota escribiendo directo", () =>
    assertFails(setDoc(doc(driver("ana"), "waitlist/w2"), { userId: "ana" })));
});
