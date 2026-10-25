// Tests de las functions del panel del administrador (T22), contra los emuladores (desde la raíz):
//   firebase emulators:exec --only auth,firestore,functions --project demo-sensorpark "npm --prefix functions test"
const { after, before, describe, test } = require("node:test");
const assert = require("node:assert/strict");
const { initializeApp, deleteApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore, GeoPoint } = require("firebase-admin/firestore");

const PROJECT = process.env.GCLOUD_PROJECT || "demo-sensorpark";
const AUTH_HOST = process.env.FIREBASE_AUTH_EMULATOR_HOST || "127.0.0.1:9099";
const FUNCTIONS_URL = `http://127.0.0.1:5001/${PROJECT}/southamerica-east1`;
const PASSWORD = "sensorpark";
const MINUTE = 60 * 1000;

let app;
let auth;
let db;
let counter = 0;

before(() => {
  app = initializeApp({ projectId: PROJECT }, "admin");
  auth = getAuth(app);
  db = getFirestore(app);
});

after(() => deleteApp(app));

const unique = (prefix) => `${prefix}-${Date.now()}-${counter++}`;

async function user(role) {
  const email = `${unique("adm")}@sensorpark.test`;
  const { uid } = await auth.createUser({ email, password: PASSWORD });
  await auth.setCustomUserClaims(uid, { role });
  await db.doc(`users/${uid}`).set({ name: "Prueba", email, role, category: "STANDARD" });
  const response = await fetch(
    `http://${AUTH_HOST}/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=demo`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password: PASSWORD, returnSecureToken: true }),
    });
  return { uid, token: (await response.json()).idToken };
}

// Un estacionamiento sin horario con los lugares 01 y 02, administrado por `manager`.
async function parkingLot(manager) {
  const id = unique("lot");
  const ref = db.doc(`parkingLots/${id}`);
  await ref.set({
    name: "Playa del panel",
    address: "Calle 123",
    location: new GeoPoint(-34.5984, -58.42),
    toleranceMinutes: 15,
    geofenceRadiusMeters: 300,
    hourlyRate: 2500,
    adminUids: [manager.uid],
  });
  for (const code of ["01", "02"]) {
    await ref.collection("spots").doc(code).set({ code, type: "STANDARD", status: "FREE" });
  }
  return id;
}

async function call(name, data, who) {
  const response = await fetch(`${FUNCTIONS_URL}/${name}`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${who.token}` },
    body: JSON.stringify({ data }),
  });
  return response.json();
}

function inMinutes(minutes) {
  const date = new Date(Date.now() + minutes * MINUTE);
  date.setSeconds(0, 0);
  return date.toISOString();
}

const spot = async (lotId, spotId) => (await db.doc(`parkingLots/${lotId}/spots/${spotId}`).get());

function assertRejected(response, status, reason) {
  assert.ok(response.error, `Se esperaba un error y llegó ${JSON.stringify(response)}`);
  assert.equal(response.error.status, status);
  if (reason) assert.equal(response.error.details.reason, reason);
}

describe("panel del administrador", () => {
  test("su administrador saca un lugar de servicio y lo devuelve", async () => {
    const manager = await user("PARKING_ADMIN");
    const lotId = await parkingLot(manager);

    assert.deepEqual((await call("setSpotService", { parkingLotId: lotId, spotId: "01", inService: false }, manager))
      .result, { status: "OUT_OF_SERVICE" });
    assert.equal((await spot(lotId, "01")).get("status"), "OUT_OF_SERVICE");

    assert.deepEqual((await call("setSpotService", { parkingLotId: lotId, spotId: "01", inService: true }, manager))
      .result, { status: "FREE" });
    assert.equal((await spot(lotId, "01")).get("status"), "FREE");
  });

  test("con una reserva activa no se saca de servicio ni se borra", async () => {
    const manager = await user("PARKING_ADMIN");
    const lotId = await parkingLot(manager);
    const driver = await user("DRIVER");
    const { result } = await call("createReservation",
      { parkingLotId: lotId, arrivesAt: inMinutes(120), durationMinutes: 60, spotId: "02" }, driver);
    assert.equal(result.spotId, "02");

    assertRejected(await call("setSpotService", { parkingLotId: lotId, spotId: "02", inService: false }, manager),
      "FAILED_PRECONDITION", "SPOT_HAS_RESERVATION");
    assertRejected(await call("deleteSpot", { parkingLotId: lotId, spotId: "02" }, manager),
      "FAILED_PRECONDITION", "SPOT_HAS_RESERVATION");
    assert.equal((await spot(lotId, "02")).exists, true);

    // El otro lugar, sin reservas, sí.
    assert.deepEqual((await call("deleteSpot", { parkingLotId: lotId, spotId: "01" }, manager)).result,
      { deleted: "01" });
    assert.equal((await spot(lotId, "01")).exists, false);
  });

  test("un lugar guardado por una oferta de la lista de espera tampoco", async () => {
    const manager = await user("PARKING_ADMIN");
    const lotId = await parkingLot(manager);
    await db.collection("waitlist").add({
      parkingLotId: lotId, userId: "alguien", status: "OFFERED", offer: { spotId: "01", spotCode: "01" },
    });
    assertRejected(await call("deleteSpot", { parkingLotId: lotId, spotId: "01" }, manager),
      "FAILED_PRECONDITION", "SPOT_HAS_RESERVATION");
  });

  test("otro administrador o un conductor no pueden", async () => {
    const manager = await user("PARKING_ADMIN");
    const lotId = await parkingLot(manager);
    assertRejected(await call("deleteSpot", { parkingLotId: lotId, spotId: "01" }, await user("PARKING_ADMIN")),
      "PERMISSION_DENIED");
    assertRejected(await call("setSpotService", { parkingLotId: lotId, spotId: "01", inService: false },
      await user("DRIVER")), "PERMISSION_DENIED");
  });

  test("el ADMIN puede en cualquier estacionamiento", async () => {
    const lotId = await parkingLot(await user("PARKING_ADMIN"));
    assert.deepEqual((await call("deleteSpot", { parkingLotId: lotId, spotId: "01" }, await user("ADMIN"))).result,
      { deleted: "01" });
  });

  test("datos inválidos o un lugar que no existe", async () => {
    const manager = await user("PARKING_ADMIN");
    const lotId = await parkingLot(manager);
    assertRejected(await call("setSpotService", { parkingLotId: lotId, spotId: "01" }, manager), "INVALID_ARGUMENT");
    assertRejected(await call("deleteSpot", { parkingLotId: lotId, spotId: "99" }, manager), "NOT_FOUND");
  });
});
