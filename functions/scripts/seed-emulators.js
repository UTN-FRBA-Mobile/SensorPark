// Carga los datos semilla en los emuladores, junto con un usuario de prueba por rol.
// Desde la raíz del repo, con los emuladores levantados (firebase emulators:start):
//   node functions/scripts/seed-emulators.js
// Sólo corre contra los emuladores: en el proyecto real la semilla se carga con la
// function `loadSeed`, y las cuentas se crean a mano para no dejar contraseñas en el repo.
process.env.GCLOUD_PROJECT = process.env.GCLOUD_PROJECT || "sensorpark-3f8aa";
process.env.FIRESTORE_EMULATOR_HOST = process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8080";
process.env.FIREBASE_AUTH_EMULATOR_HOST = process.env.FIREBASE_AUTH_EMULATOR_HOST || "127.0.0.1:9099";

const { initializeApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore } = require("firebase-admin/firestore");
const { seed, PARKING_LOTS } = require("../seed");

const PASSWORD = "sensorpark";
const USERS = [
  { uid: "driver", email: "driver@sensorpark.test", name: "Conductor de prueba", role: "DRIVER" },
  { uid: "parking-admin", email: "parking-admin@sensorpark.test", name: "Encargado de prueba", role: "PARKING_ADMIN" },
  { uid: "admin", email: "admin@sensorpark.test", name: "Admin de prueba", role: "ADMIN" },
];

async function main() {
  initializeApp();
  const auth = getAuth();
  const db = getFirestore();

  for (const u of USERS) {
    await auth.deleteUser(u.uid).catch(() => {});
    await auth.createUser({ uid: u.uid, email: u.email, password: PASSWORD, displayName: u.name });
    await auth.setCustomUserClaims(u.uid, { role: u.role });
    await db.doc(`users/${u.uid}`).set({ name: u.name, email: u.email, role: u.role, category: "STANDARD" });
  }

  const result = await seed(db);
  // El encargado de prueba administra el primer estacionamiento.
  await db.doc(`parkingLots/${PARKING_LOTS[0].id}`).update({ adminUids: ["parking-admin"] });

  console.log(`Semilla cargada: ${result.parkingLots} estacionamientos, ${result.spots} lugares.`);
  console.log(`Usuarios (contraseña "${PASSWORD}"): ${USERS.map((u) => u.email).join(", ")}`);
}

main().catch((e) => { console.error(e); process.exit(1); });
