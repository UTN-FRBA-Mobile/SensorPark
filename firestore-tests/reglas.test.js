// Tests de firestore.rules. Corren contra el emulador, con un proyecto falso (prefijo demo-):
//   firebase emulators:exec --only firestore --project demo-sensorpark "npm --prefix firestore-tests test"
// El emulador pide Java 21+; sirve el `jbr` de Android Studio.
import { after, before, beforeEach, describe, test } from "node:test";
import { readFileSync } from "node:fs";
import { assertFails, assertSucceeds, initializeTestEnvironment } from "@firebase/rules-unit-testing";
import { deleteDoc, doc, getDoc, setDoc, updateDoc } from "firebase/firestore";

let entorno;

before(async () => {
  entorno = await initializeTestEnvironment({
    projectId: "demo-sensorpark",
    firestore: { rules: readFileSync(new URL("../firestore.rules", import.meta.url), "utf8") },
  });
});

after(() => entorno.cleanup());

// Datos de base, cargados sin pasar por las reglas (como lo haría el backend).
beforeEach(async () => {
  await entorno.clearFirestore();
  await entorno.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, "users/ana"), { nombre: "Ana", rol: "CONDUCTOR" });
    await setDoc(doc(db, "estacionamientos/e1"), { nombre: "Playa Centro", adminUids: ["encargado"] });
    await setDoc(doc(db, "estacionamientos/e1/cocheras/c1"), { numero: 1, estado: "LIBRE" });
    await setDoc(doc(db, "reservas/r1"), { conductorUid: "ana", cocheraId: "c1" });
  });
});

const anonimo = () => entorno.unauthenticatedContext().firestore();
const como = (uid, rol) => entorno.authenticatedContext(uid, rol ? { rol } : {}).firestore();

describe("users", () => {
  test("el dueño lee su documento", () =>
    assertSucceeds(getDoc(doc(como("ana", "CONDUCTOR"), "users/ana"))));

  test("otro usuario no lo lee", () =>
    assertFails(getDoc(doc(como("beto", "CONDUCTOR"), "users/ana"))));

  test("un ADMIN lo lee", () =>
    assertSucceeds(getDoc(doc(como("jefa", "ADMIN"), "users/ana"))));

  test("sin sesión no se lee", () =>
    assertFails(getDoc(doc(anonimo(), "users/ana"))));

  test("el dueño crea su documento sin rol", () =>
    assertSucceeds(setDoc(doc(como("beto"), "users/beto"), { nombre: "Beto" })));

  test("el dueño no puede crearse con rol", () =>
    assertFails(setDoc(doc(como("beto"), "users/beto"), { nombre: "Beto", rol: "ADMIN" })));

  test("el dueño actualiza su token FCM", () =>
    assertSucceeds(updateDoc(doc(como("ana", "CONDUCTOR"), "users/ana"), { fcmToken: "t" })));

  test("el dueño no puede cambiarse el rol", () =>
    assertFails(updateDoc(doc(como("ana", "CONDUCTOR"), "users/ana"), { rol: "ADMIN" })));
});

describe("estacionamientos y cocheras", () => {
  test("cualquier autenticado ve la ocupación", () =>
    assertSucceeds(getDoc(doc(como("ana", "CONDUCTOR"), "estacionamientos/e1/cocheras/c1"))));

  test("sin sesión no se ve", () =>
    assertFails(getDoc(doc(anonimo(), "estacionamientos/e1/cocheras/c1"))));

  test("un conductor no crea estacionamientos", () =>
    assertFails(setDoc(doc(como("ana", "CONDUCTOR"), "estacionamientos/e2"), { nombre: "X", adminUids: [] })));

  test("un ADMIN crea estacionamientos", () =>
    assertSucceeds(setDoc(doc(como("jefa", "ADMIN"), "estacionamientos/e2"), { nombre: "X", adminUids: [] })));

  test("el encargado edita las cocheras de su estacionamiento", () =>
    assertSucceeds(updateDoc(
      doc(como("encargado", "ADMIN_ESTACIONAMIENTO"), "estacionamientos/e1/cocheras/c1"), { numero: 2 })));

  test("un encargado de otro estacionamiento no las edita", () =>
    assertFails(updateDoc(
      doc(como("otro", "ADMIN_ESTACIONAMIENTO"), "estacionamientos/e1/cocheras/c1"), { numero: 2 })));

  test("un conductor no cambia el estado de una cochera", () =>
    assertFails(updateDoc(
      doc(como("ana", "CONDUCTOR"), "estacionamientos/e1/cocheras/c1"), { estado: "OCUPADA" })));
});

describe("reservas", () => {
  test("un conductor crea su propia reserva", () =>
    assertSucceeds(setDoc(doc(como("ana", "CONDUCTOR"), "reservas/r2"), { conductorUid: "ana" })));

  test("un conductor no reserva a nombre de otro", () =>
    assertFails(setDoc(doc(como("ana", "CONDUCTOR"), "reservas/r2"), { conductorUid: "beto" })));

  test("sin rol asignado no se reserva", () =>
    assertFails(setDoc(doc(como("beto"), "reservas/r2"), { conductorUid: "beto" })));

  test("el conductor lee y cancela su reserva", async () => {
    const db = como("ana", "CONDUCTOR");
    await assertSucceeds(getDoc(doc(db, "reservas/r1")));
    await assertSucceeds(deleteDoc(doc(db, "reservas/r1")));
  });

  test("otro conductor no la lee ni la cancela", async () => {
    const db = como("beto", "CONDUCTOR");
    await assertFails(getDoc(doc(db, "reservas/r1")));
    await assertFails(deleteDoc(doc(db, "reservas/r1")));
  });
});
