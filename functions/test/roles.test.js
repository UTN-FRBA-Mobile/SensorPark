// Tests de las functions de roles. Corren contra los emuladores, con el proyecto falso demo-sensorpark
// (desde la raíz del repo):
//   firebase emulators:exec --only auth,firestore,functions --project demo-sensorpark "npm --prefix functions test"
// ADMIN_EMAILS sale de functions/.env.demo-sensorpark.
const { after, before, describe, test } = require("node:test");
const assert = require("node:assert/strict");
const { initializeApp, deleteApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore } = require("firebase-admin/firestore");

const PROJECT = process.env.GCLOUD_PROJECT || "demo-sensorpark";
const AUTH_HOST = process.env.FIREBASE_AUTH_EMULATOR_HOST || "127.0.0.1:9099";
const FUNCTIONS_URL = `http://127.0.0.1:5001/${PROJECT}/southamerica-east1`;
const PASSWORD = "sensorpark";

let app;
let auth;
let db;

before(() => {
  app = initializeApp({ projectId: PROJECT });
  auth = getAuth(app);
  db = getFirestore(app);
});

after(() => deleteApp(app));

let counter = 0;

// Crea una cuenta y su documento `users/{uid}`, como lo hace la app al registrarse.
async function signUp(profile = {}, email = `user${Date.now()}-${counter++}@sensorpark.test`) {
  const { uid } = await auth.createUser({ email, password: PASSWORD });
  await db.doc(`users/${uid}`).set({ name: "Prueba", ...profile });
  return { uid, email };
}

// El rol lo asigna un trigger: hay que esperar a que corra.
async function waitForRole(uid) {
  for (let i = 0; i < 40; i++) {
    const user = await auth.getUser(uid);
    if (user.customClaims && user.customClaims.role) return user.customClaims.role;
    await new Promise((r) => setTimeout(r, 250));
  }
  throw new Error(`La cuenta ${uid} no recibió rol`);
}

async function profileOf(uid) {
  // El trigger escribe el perfil después del claim: esperar a que el aviso `role` esté.
  for (let i = 0; i < 40; i++) {
    const data = (await db.doc(`users/${uid}`).get()).data();
    if (data.role) return data;
    await new Promise((r) => setTimeout(r, 250));
  }
  throw new Error(`El perfil de ${uid} no recibió el aviso de rol`);
}

async function idTokenOf(email) {
  const response = await fetch(
    `http://${AUTH_HOST}/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=demo`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password: PASSWORD, returnSecureToken: true }),
    });
  return (await response.json()).idToken;
}

// Llama a una function invocable como lo hace el SDK de la app.
async function call(name, data, email) {
  const response = await fetch(`${FUNCTIONS_URL}/${name}`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${await idTokenOf(email)}` },
    body: JSON.stringify({ data }),
  });
  return response.json();
}

describe("rol inicial", () => {
  test("sin pedir rol entra como DRIVER, con categoría STANDARD", async () => {
    const { uid, email } = await signUp();
    assert.equal(await waitForRole(uid), "DRIVER");
    const profile = await profileOf(uid);
    assert.equal(profile.role, "DRIVER");
    assert.equal(profile.category, "STANDARD");
    assert.equal(profile.email, email);
  });

  test("el que pide ser administrador de estacionamiento entra como PARKING_ADMIN", async () => {
    const { uid } = await signUp({ requestedRole: "PARKING_ADMIN" });
    assert.equal(await waitForRole(uid), "PARKING_ADMIN");
    assert.equal((await profileOf(uid)).requestedRole, undefined);
  });

  test("pedir ADMIN no sirve: entra como DRIVER", async () => {
    const { uid } = await signUp({ requestedRole: "ADMIN" });
    assert.equal(await waitForRole(uid), "DRIVER");
  });

  test("conserva la categoría que declaró", async () => {
    const { uid } = await signUp({ category: "REDUCED_MOBILITY" });
    await waitForRole(uid);
    assert.equal((await profileOf(uid)).category, "REDUCED_MOBILITY");
  });

  test("un email de ADMIN_EMAILS entra como ADMIN", async () => {
    await auth.getUserByEmail("admin@sensorpark.test").then((u) => auth.deleteUser(u.uid), () => {});
    const { uid } = await signUp({ requestedRole: "DRIVER" }, "admin@sensorpark.test");
    assert.equal(await waitForRole(uid), "ADMIN");
  });
});

describe("cambio de rol", () => {
  test("un ADMIN le cambia el rol a otro usuario", async () => {
    const admin = await auth.getUserByEmail("admin@sensorpark.test");
    await waitForRole(admin.uid);
    const target = await signUp();
    await waitForRole(target.uid);

    const result = await call("assignRole", { uid: target.uid, role: "PARKING_ADMIN" }, admin.email);
    assert.deepEqual(result.result, { uid: target.uid, role: "PARKING_ADMIN" });
    assert.equal((await auth.getUser(target.uid)).customClaims.role, "PARKING_ADMIN");
  });

  test("quien no es ADMIN no puede cambiar roles", async () => {
    const driver = await signUp();
    await waitForRole(driver.uid);

    const result = await call("assignRole", { uid: driver.uid, role: "ADMIN" }, driver.email);
    assert.equal(result.error.status, "PERMISSION_DENIED");
    assert.equal((await auth.getUser(driver.uid)).customClaims.role, "DRIVER");
  });

  test("un rol inexistente se rechaza", async () => {
    const admin = await auth.getUserByEmail("admin@sensorpark.test");
    const result = await call("assignRole", { uid: admin.uid, role: "SUPERHEROE" }, admin.email);
    assert.equal(result.error.status, "INVALID_ARGUMENT");
  });
});
