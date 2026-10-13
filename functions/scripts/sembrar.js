// Carga los datos semilla en los emuladores, junto con un usuario de prueba por rol.
// Desde la raíz del repo, con los emuladores levantados (firebase emulators:start):
//   node functions/scripts/sembrar.js
// Sólo corre contra los emuladores: en el proyecto real la semilla se carga con la
// function `cargarSemilla`, y las cuentas se crean a mano para no dejar contraseñas en el repo.
process.env.GCLOUD_PROJECT = process.env.GCLOUD_PROJECT || "sensorpark-3f8aa";
process.env.FIRESTORE_EMULATOR_HOST = process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8080";
process.env.FIREBASE_AUTH_EMULATOR_HOST = process.env.FIREBASE_AUTH_EMULATOR_HOST || "127.0.0.1:9099";

const { initializeApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore } = require("firebase-admin/firestore");
const { sembrar, ESTACIONAMIENTOS } = require("../semilla");

const CLAVE = "sensorpark";
const USUARIOS = [
  { uid: "conductor", email: "conductor@sensorpark.test", nombre: "Conductor de prueba", rol: "CONDUCTOR" },
  { uid: "encargado", email: "encargado@sensorpark.test", nombre: "Encargado de prueba", rol: "ADMIN_ESTACIONAMIENTO" },
  { uid: "admin", email: "admin@sensorpark.test", nombre: "Admin de prueba", rol: "ADMIN" },
];

async function main() {
  initializeApp();
  const auth = getAuth();
  const db = getFirestore();

  for (const u of USUARIOS) {
    await auth.deleteUser(u.uid).catch(() => {});
    await auth.createUser({ uid: u.uid, email: u.email, password: CLAVE, displayName: u.nombre });
    await auth.setCustomUserClaims(u.uid, { rol: u.rol });
    await db.doc(`usuarios/${u.uid}`).set({ nombre: u.nombre, email: u.email, rol: u.rol, categoria: "COMUN" });
  }

  const resultado = await sembrar(db);
  // El encargado de prueba administra el primer estacionamiento.
  await db.doc(`estacionamientos/${ESTACIONAMIENTOS[0].id}`).update({ adminUids: ["encargado"] });

  console.log(`Semilla cargada: ${resultado.estacionamientos} estacionamientos, ${resultado.lugares} lugares.`);
  console.log(`Usuarios (contraseña "${CLAVE}"): ${USUARIOS.map((u) => u.email).join(", ")}`);
}

main().catch((e) => { console.error(e); process.exit(1); });
