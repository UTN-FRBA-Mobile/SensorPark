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
    await setDoc(doc(db, "usuarios/ana"), { nombre: "Ana", rol: "CONDUCTOR", categoria: "COMUN" });
    await setDoc(doc(db, "estacionamientos/e1"), {
      nombre: "Playa Centro", adminUids: ["encargado"], capacidad: 1, libres: 1,
    });
    await setDoc(doc(db, "estacionamientos/e1/lugares/A-01"), { codigo: "A-01", estado: "LIBRE" });
    await setDoc(doc(db, "estacionamientos/e1/cupos/2026-11-02"), { preferencial: 10 });
    await setDoc(doc(db, "reservas/r1"), { usuarioId: "ana", estacionamientoId: "e1", lugarId: "A-01" });
    await setDoc(doc(db, "listaEspera/w1"), { usuarioId: "ana", estacionamientoId: "e1" });
  });
});

const anonimo = () => entorno.unauthenticatedContext().firestore();
const como = (uid, rol) => entorno.authenticatedContext(uid, rol ? { rol } : {}).firestore();
const conductor = (uid) => como(uid, "CONDUCTOR");
const encargado = (uid) => como(uid, "ADMIN_ESTACIONAMIENTO");
const admin = () => como("jefa", "ADMIN");

describe("usuarios", () => {
  test("el dueño lee su documento", () =>
    assertSucceeds(getDoc(doc(conductor("ana"), "usuarios/ana"))));

  test("otro usuario no lo lee", () =>
    assertFails(getDoc(doc(conductor("beto"), "usuarios/ana"))));

  test("un ADMIN lo lee", () =>
    assertSucceeds(getDoc(doc(admin(), "usuarios/ana"))));

  test("sin sesión no se lee", () =>
    assertFails(getDoc(doc(anonimo(), "usuarios/ana"))));

  test("el dueño crea su documento sin rol, aunque todavía no tenga claim", () =>
    assertSucceeds(setDoc(doc(como("beto"), "usuarios/beto"), { nombre: "Beto" })));

  test("el dueño no puede crearse con rol", () =>
    assertFails(setDoc(doc(como("beto"), "usuarios/beto"), { nombre: "Beto", rol: "ADMIN" })));

  test("el dueño no puede crearse con categoría", () =>
    assertFails(setDoc(doc(como("beto"), "usuarios/beto"), { nombre: "Beto", categoria: "ABONADO" })));

  test("el dueño actualiza su token FCM", () =>
    assertSucceeds(updateDoc(doc(conductor("ana"), "usuarios/ana"), { fcmToken: "t" })));

  test("el dueño no puede cambiarse el rol", () =>
    assertFails(updateDoc(doc(conductor("ana"), "usuarios/ana"), { rol: "ADMIN" })));

  test("el dueño no puede cambiarse la categoría", () =>
    assertFails(updateDoc(doc(conductor("ana"), "usuarios/ana"), { categoria: "ABONADO" })));
});

describe("estacionamientos", () => {
  test("cualquier autenticado los ve", () =>
    assertSucceeds(getDoc(doc(conductor("ana"), "estacionamientos/e1"))));

  test("sin sesión no se ven", () =>
    assertFails(getDoc(doc(anonimo(), "estacionamientos/e1"))));

  test("un conductor no los crea", () =>
    assertFails(setDoc(doc(conductor("ana"), "estacionamientos/e2"), { nombre: "X", adminUids: [] })));

  test("un ADMIN los crea", () =>
    assertSucceeds(setDoc(doc(admin(), "estacionamientos/e2"), { nombre: "X", adminUids: [] })));

  test("su encargado edita los datos", () =>
    assertSucceeds(updateDoc(doc(encargado("encargado"), "estacionamientos/e1"), { nombre: "Nuevo" })));

  test("su encargado no toca los contadores", () =>
    assertFails(updateDoc(doc(encargado("encargado"), "estacionamientos/e1"), { libres: 99 })));

  test("su encargado no se suma administradores", () =>
    assertFails(updateDoc(doc(encargado("encargado"), "estacionamientos/e1"), { adminUids: ["encargado", "otro"] })));

  test("un encargado de otro estacionamiento no lo edita", () =>
    assertFails(updateDoc(doc(encargado("otro"), "estacionamientos/e1"), { nombre: "Nuevo" })));
});

describe("lugares y cupos", () => {
  test("cualquier autenticado ve la ocupación", () =>
    assertSucceeds(getDoc(doc(conductor("ana"), "estacionamientos/e1/lugares/A-01"))));

  test("sin sesión no se ve", () =>
    assertFails(getDoc(doc(anonimo(), "estacionamientos/e1/lugares/A-01"))));

  test("un conductor no cambia el estado de un lugar", () =>
    assertFails(updateDoc(doc(conductor("ana"), "estacionamientos/e1/lugares/A-01"), { estado: "OCUPADO" })));

  test("su encargado edita un lugar", () =>
    assertSucceeds(updateDoc(
      doc(encargado("encargado"), "estacionamientos/e1/lugares/A-01"), { estado: "FUERA_DE_SERVICIO" })));

  test("un encargado de otro estacionamiento no lo edita", () =>
    assertFails(updateDoc(
      doc(encargado("otro"), "estacionamientos/e1/lugares/A-01"), { estado: "FUERA_DE_SERVICIO" })));

  test("cualquier autenticado lee los cupos", () =>
    assertSucceeds(getDoc(doc(conductor("ana"), "estacionamientos/e1/cupos/2026-11-02"))));

  test("su encargado define los cupos de una fecha", () =>
    assertSucceeds(setDoc(doc(encargado("encargado"), "estacionamientos/e1/cupos/2026-11-03"), { abonado: 20 })));

  test("un conductor no define cupos", () =>
    assertFails(setDoc(doc(conductor("ana"), "estacionamientos/e1/cupos/2026-11-03"), { abonado: 20 })));
});

describe("reservas", () => {
  test("el conductor lee su reserva", () =>
    assertSucceeds(getDoc(doc(conductor("ana"), "reservas/r1"))));

  test("otro conductor no la lee", () =>
    assertFails(getDoc(doc(conductor("beto"), "reservas/r1"))));

  test("el encargado del estacionamiento la lee", () =>
    assertSucceeds(getDoc(doc(encargado("encargado"), "reservas/r1"))));

  test("un encargado de otro estacionamiento no la lee", () =>
    assertFails(getDoc(doc(encargado("otro"), "reservas/r1"))));

  test("nadie crea una reserva desde la app: sólo las functions", async () => {
    await assertFails(setDoc(doc(conductor("ana"), "reservas/r2"), { usuarioId: "ana", estacionamientoId: "e1" }));
    await assertFails(setDoc(doc(admin(), "reservas/r2"), { usuarioId: "ana", estacionamientoId: "e1" }));
  });

  test("el conductor no modifica ni borra su reserva directo", async () => {
    await assertFails(updateDoc(doc(conductor("ana"), "reservas/r1"), { estado: "EN_USO" }));
    await assertFails(deleteDoc(doc(conductor("ana"), "reservas/r1")));
  });
});

describe("lista de espera", () => {
  test("el usuario lee su entrada", () =>
    assertSucceeds(getDoc(doc(conductor("ana"), "listaEspera/w1"))));

  test("otro usuario no la lee", () =>
    assertFails(getDoc(doc(conductor("beto"), "listaEspera/w1"))));

  test("nadie se anota escribiendo directo", () =>
    assertFails(setDoc(doc(conductor("ana"), "listaEspera/w2"), { usuarioId: "ana" })));
});
