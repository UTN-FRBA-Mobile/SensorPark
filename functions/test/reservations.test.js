// Tests de las functions de reservas y lista de espera, contra los emuladores (desde la raíz del repo):
//   firebase emulators:exec --only auth,firestore,functions --project demo-sensorpark "npm --prefix functions test"
// Cada test arma su propio estacionamiento, sin horario, para no depender de la hora en que corre.
const { after, before, describe, test } = require("node:test");
const assert = require("node:assert/strict");
const { initializeApp, deleteApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore, GeoPoint, Timestamp } = require("firebase-admin/firestore");
const { processReservationsAt, reconcileWaitlist } = require("../reservations");

const PROJECT = process.env.GCLOUD_PROJECT || "demo-sensorpark";
const AUTH_HOST = process.env.FIREBASE_AUTH_EMULATOR_HOST || "127.0.0.1:9099";
const FUNCTIONS_URL = `http://127.0.0.1:5001/${PROJECT}/southamerica-east1`;
const PASSWORD = "sensorpark";
const UTN = { latitude: -34.5984, longitude: -58.4200 };
const MINUTE = 60 * 1000;

let app;
let auth;
let db;
let counter = 0;

before(() => {
  app = initializeApp({ projectId: PROJECT }, "reservations");
  auth = getAuth(app);
  db = getFirestore(app);
});

after(() => deleteApp(app));

const unique = (prefix) => `${prefix}-${Date.now()}-${counter++}`;

// Un usuario con su rol ya asignado y su categoría.
async function user(role = "DRIVER", category = "STANDARD") {
  const email = `${unique("res")}@sensorpark.test`;
  const { uid } = await auth.createUser({ email, password: PASSWORD });
  await auth.setCustomUserClaims(uid, { role });
  await db.doc(`users/${uid}`).set({ name: "Prueba", email, role, category });
  const response = await fetch(
    `http://${AUTH_HOST}/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=demo`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password: PASSWORD, returnSecureToken: true }),
    });
  return { uid, token: (await response.json()).idToken };
}

// Un estacionamiento propio del test. `spots`: { "01": "STANDARD", "09": ["PRIORITY", "PREGNANT"] }.
async function parkingLot(spots = { "01": "STANDARD" }, extra = {}) {
  const id = unique("lot");
  const ref = db.doc(`parkingLots/${id}`);
  await ref.set({
    name: "Playa de prueba",
    address: "Calle 123",
    location: new GeoPoint(UTN.latitude, UTN.longitude),
    toleranceMinutes: 15,
    geofenceRadiusMeters: 300,
    hourlyRate: 2500,
    adminUids: [],
    ...extra,
  });
  for (const [code, value] of Object.entries(spots)) {
    const [type, priorityCategory] = Array.isArray(value) ? value : [value];
    await ref.collection("spots").doc(code).set({
      code, type, status: "FREE", ...(priorityCategory ? { priorityCategory } : {}),
    });
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

// La hora dentro de `minutes` minutos, redondeada al minuto, en ISO 8601.
function inMinutes(minutes) {
  const date = new Date(Date.now() + minutes * MINUTE);
  date.setSeconds(0, 0);
  return date.toISOString();
}

function reserve(who, lotId, overrides = {}) {
  return call("createReservation", {
    parkingLotId: lotId, arrivesAt: inMinutes(120), durationMinutes: 60, spotType: "STANDARD", ...overrides,
  }, who);
}

const spotStatus = async (lotId, spotId) => (await db.doc(`parkingLots/${lotId}/spots/${spotId}`).get()).get("status");
const reservationStatus = async (id) => (await db.doc(`reservations/${id}`).get()).get("status");

function assertRejected(response, status, reason) {
  assert.ok(response.error, `Se esperaba un error y llegó ${JSON.stringify(response)}`);
  assert.equal(response.error.status, status);
  if (reason) assert.equal(response.error.details.reason, reason);
}

describe("crear reserva", () => {
  test("confirma una reserva a futuro sin cambiar el estado del lugar", async () => {
    const driver = await user();
    const lotId = await parkingLot();
    const { result } = await reserve(driver, lotId);
    assert.equal(result.spotId, "01");
    assert.equal(result.spotCode, "01");
    assert.equal(new Date(result.endsAt) - new Date(result.arrivesAt), 60 * MINUTE);
    assert.equal(new Date(result.toleranceUntil) - new Date(result.arrivesAt), 15 * MINUTE);

    const saved = (await db.doc(`reservations/${result.reservationId}`).get()).data();
    assert.equal(saved.userId, driver.uid);
    assert.equal(saved.status, "CONFIRMED");
    assert.equal(saved.durationMinutes, 60);
    assert.equal(await spotStatus(lotId, "01"), "FREE");
  });

  test("una reserva que empieza ya deja el lugar RESERVED", async () => {
    const lotId = await parkingLot();
    await reserve(await user(), lotId, { arrivesAt: inMinutes(5) });
    assert.equal(await spotStatus(lotId, "01"), "RESERVED");
  });

  test("rechaza una duración no permitida", async () =>
    assertRejected(await reserve(await user(), await parkingLot(), { durationMinutes: 45 }),
      "INVALID_ARGUMENT", "INVALID_DURATION"));

  test("rechaza una llegada en el pasado", async () =>
    assertRejected(await reserve(await user(), await parkingLot(), { arrivesAt: inMinutes(-30) }),
      "FAILED_PRECONDITION", "IN_THE_PAST"));

  test("rechaza una llegada a más de 7 días", async () =>
    assertRejected(await reserve(await user(), await parkingLot(), { arrivesAt: inMinutes(8 * 24 * 60) }),
      "FAILED_PRECONDITION", "TOO_FAR_AHEAD"));

  test("rechaza una franja fuera del horario", async () => {
    // Abre un solo minuto: ninguna franja entra.
    const lotId = await parkingLot(undefined, { schedule: { opensAt: "00:00", closesAt: "00:01" } });
    assertRejected(await reserve(await user(), lotId), "FAILED_PRECONDITION", "OUTSIDE_SCHEDULE");
  });

  test("un conductor sin categoría no reserva un preferencial", async () => {
    const lotId = await parkingLot({ "09": ["PRIORITY", "PREGNANT"] });
    assertRejected(await reserve(await user(), lotId, { spotType: "PRIORITY" }),
      "FAILED_PRECONDITION", "CATEGORY_REQUIRED");
  });

  test("un conductor PREGNANT toma el preferencial de su categoría", async () => {
    const lotId = await parkingLot({ "09": ["PRIORITY", "PREGNANT"], "10": ["PRIORITY", "REDUCED_MOBILITY"] });
    const { result } = await reserve(await user("DRIVER", "PREGNANT"), lotId, { spotType: "PRIORITY" });
    assert.equal(result.spotId, "09");
  });

  test("con 2 reservas activas no puede tomar una tercera", async () => {
    const driver = await user();
    const lotId = await parkingLot({ "01": "STANDARD", "02": "STANDARD", "03": "STANDARD" });
    await reserve(driver, lotId);
    await reserve(driver, lotId);
    assertRejected(await reserve(driver, lotId), "FAILED_PRECONDITION", "LIMIT_REACHED");
  });

  test("sin lugar libre en la franja: NO_SPOTS", async () => {
    const lotId = await parkingLot();
    await reserve(await user(), lotId);
    assertRejected(await reserve(await user(), lotId), "FAILED_PRECONDITION", "NO_SPOTS");
  });

  test("una franja que empieza justo cuando termina la otra entra en el mismo lugar", async () => {
    const lotId = await parkingLot();
    await reserve(await user(), lotId, { arrivesAt: inMinutes(120), durationMinutes: 60 });
    const { result } = await reserve(await user(), lotId, { arrivesAt: inMinutes(180) });
    assert.equal(result.spotId, "01");
  });

  test("dos pedidos simultáneos por el último lugar: sólo uno se confirma", async () => {
    const lotId = await parkingLot();
    const [a, b] = await Promise.all([reserve(await user(), lotId), reserve(await user(), lotId)]);
    const confirmed = [a, b].filter((r) => r.result);
    const rejected = [a, b].filter((r) => r.error);
    assert.equal(confirmed.length, 1);
    assert.equal(rejected.length, 1);
    assert.equal(rejected[0].error.details.reason, "NO_SPOTS");
  });

  test("un administrador de estacionamiento no reserva", async () =>
    assertRejected(await reserve(await user("PARKING_ADMIN"), await parkingLot()), "PERMISSION_DENIED"));

  test("un estacionamiento inexistente: NOT_FOUND", async () =>
    assertRejected(await reserve(await user(), "no-existe"), "NOT_FOUND"));
});

describe("cancelar", () => {
  test("cancela antes del check-in y libera el lugar", async () => {
    const driver = await user();
    const lotId = await parkingLot();
    const { result } = await reserve(driver, lotId, { arrivesAt: inMinutes(5) });
    assert.equal(await spotStatus(lotId, "01"), "RESERVED");

    assert.deepEqual((await call("cancelReservation", { reservationId: result.reservationId }, driver)).result,
      { status: "CANCELLED" });
    assert.equal(await reservationStatus(result.reservationId), "CANCELLED");
    assert.equal(await spotStatus(lotId, "01"), "FREE");
  });

  test("no cancela dos veces", async () => {
    const driver = await user();
    const { result } = await reserve(driver, await parkingLot());
    await call("cancelReservation", { reservationId: result.reservationId }, driver);
    assertRejected(await call("cancelReservation", { reservationId: result.reservationId }, driver),
      "FAILED_PRECONDITION", "CANCEL_NOT_ALLOWED");
  });

  test("otro usuario no la cancela", async () => {
    const { result } = await reserve(await user(), await parkingLot());
    assertRejected(await call("cancelReservation", { reservationId: result.reservationId }, await user()),
      "PERMISSION_DENIED");
  });
});

describe("pedir otro lugar", () => {
  // El lugar reservado aparece ocupado por otro auto: lo marca el sensor, sin `inUseBy`.
  const takenByAnother = (lotId, spotId) =>
    db.doc(`parkingLots/${lotId}/spots/${spotId}`).update({ status: "OCCUPIED" });

  test("da otro lugar del mismo tipo y la reserva pasa a apuntar ahí", async () => {
    const driver = await user();
    const lotId = await parkingLot({ "01": "STANDARD", "02": "STANDARD" });
    const { result } = await reserve(driver, lotId, { arrivesAt: inMinutes(5) });
    assert.equal(result.spotCode, "01");
    await takenByAnother(lotId, "01");

    const response = await call("reassignSpot", { reservationId: result.reservationId }, driver);
    assert.deepEqual(response.result, { spotId: "02", spotCode: "02" });

    const saved = (await db.doc(`reservations/${result.reservationId}`).get()).data();
    assert.equal(saved.spotId, "02");
    assert.equal(saved.spotCode, "02");
    assert.equal(saved.status, "CONFIRMED");
    // El lugar viejo lo ocupa un auto de verdad: no se toca.
    assert.equal(await spotStatus(lotId, "01"), "OCCUPIED");
    assert.equal(await spotStatus(lotId, "02"), "RESERVED");
  });

  test("no cambia la franja de la reserva", async () => {
    const driver = await user();
    const lotId = await parkingLot({ "01": "STANDARD", "02": "STANDARD" });
    const { result } = await reserve(driver, lotId, { arrivesAt: inMinutes(5) });
    await takenByAnother(lotId, "01");
    await call("reassignSpot", { reservationId: result.reservationId }, driver);

    const saved = (await db.doc(`reservations/${result.reservationId}`).get()).data();
    assert.equal(saved.arrivesAt.toDate().toISOString(), result.arrivesAt);
    assert.equal(saved.endsAt.toDate().toISOString(), result.endsAt);
    assert.equal(saved.toleranceUntil.toDate().toISOString(), result.toleranceUntil);
  });

  test("sin otro lugar del mismo tipo: NO_SPOTS", async () => {
    const driver = await user();
    const lotId = await parkingLot({ "01": "STANDARD" });
    const { result } = await reserve(driver, lotId, { arrivesAt: inMinutes(5) });
    await takenByAnother(lotId, "01");

    assertRejected(await call("reassignSpot", { reservationId: result.reservationId }, driver),
      "FAILED_PRECONDITION", "NO_SPOTS");
  });

  test("con el lugar libre no hay nada que reasignar: SPOT_NOT_TAKEN", async () => {
    const driver = await user();
    const lotId = await parkingLot({ "01": "STANDARD", "02": "STANDARD" });
    const { result } = await reserve(driver, lotId);

    assertRejected(await call("reassignSpot", { reservationId: result.reservationId }, driver),
      "FAILED_PRECONDITION", "SPOT_NOT_TAKEN");
  });

  test("con el lugar ocupado por la propia reserva tampoco: SPOT_NOT_TAKEN", async () => {
    const driver = await user();
    const lotId = await parkingLot({ "01": "STANDARD", "02": "STANDARD" });
    const { result } = await reserve(driver, lotId, { arrivesAt: inMinutes(5) });
    await db.doc(`parkingLots/${lotId}/spots/01`)
      .update({ status: "OCCUPIED", inUseBy: result.reservationId });

    assertRejected(await call("reassignSpot", { reservationId: result.reservationId }, driver),
      "FAILED_PRECONDITION", "SPOT_NOT_TAKEN");
  });

  test("una reserva cancelada no pide otro lugar: INVALID_STATUS", async () => {
    const driver = await user();
    const lotId = await parkingLot({ "01": "STANDARD", "02": "STANDARD" });
    const { result } = await reserve(driver, lotId, { arrivesAt: inMinutes(5) });
    await call("cancelReservation", { reservationId: result.reservationId }, driver);
    await takenByAnother(lotId, "01");

    assertRejected(await call("reassignSpot", { reservationId: result.reservationId }, driver),
      "FAILED_PRECONDITION", "INVALID_STATUS");
  });

  test("otro usuario no reasigna una reserva ajena", async () => {
    const lotId = await parkingLot({ "01": "STANDARD", "02": "STANDARD" });
    const { result } = await reserve(await user(), lotId, { arrivesAt: inMinutes(5) });
    await takenByAnother(lotId, "01");

    assertRejected(await call("reassignSpot", { reservationId: result.reservationId }, await user()),
      "PERMISSION_DENIED");
  });
});

describe("check-in y check-out", () => {
  test("check-in demasiado temprano: TOO_EARLY", async () => {
    const driver = await user();
    const { result } = await reserve(driver, await parkingLot());
    assertRejected(await call("checkIn", { reservationId: result.reservationId, ...UTN }, driver),
      "FAILED_PRECONDITION", "TOO_EARLY");
  });

  test("check-in lejos del estacionamiento: TOO_FAR", async () => {
    const driver = await user();
    const { result } = await reserve(driver, await parkingLot(), { arrivesAt: inMinutes(5) });
    assertRejected(await call("checkIn", { reservationId: result.reservationId, latitude: -34.5894, longitude: -58.42 }, driver),
      "FAILED_PRECONDITION", "TOO_FAR");
  });

  test("check-in en el lugar: la reserva pasa a IN_USE y el lugar a OCCUPIED", async () => {
    const driver = await user();
    const lotId = await parkingLot();
    const { result } = await reserve(driver, lotId, { arrivesAt: inMinutes(5) });
    assert.deepEqual((await call("checkIn", { reservationId: result.reservationId, ...UTN }, driver)).result,
      { status: "IN_USE" });
    assert.equal(await reservationStatus(result.reservationId), "IN_USE");
    assert.equal(await spotStatus(lotId, "01"), "OCCUPIED");
    // La marca que hace que el simulador de sensores no libere el lugar.
    assert.equal((await db.doc(`parkingLots/${lotId}/spots/01`).get()).get("inUseBy"), result.reservationId);

    assertRejected(await call("cancelReservation", { reservationId: result.reservationId }, driver),
      "FAILED_PRECONDITION", "CANCEL_NOT_ALLOWED");
  });

  test("check-out a los 90 minutos: importe informativo y lugar libre", async () => {
    const driver = await user();
    const lotId = await parkingLot();
    const { result } = await reserve(driver, lotId, { arrivesAt: inMinutes(5), durationMinutes: 120 });
    await call("checkIn", { reservationId: result.reservationId, ...UTN }, driver);
    // Se simula que entró hace 90 minutos.
    await db.doc(`reservations/${result.reservationId}`)
      .update({ checkedInAt: Timestamp.fromMillis(Date.now() - 90 * MINUTE) });

    const { result: summary } = await call("checkOut", { reservationId: result.reservationId }, driver);
    assert.deepEqual(summary, { status: "FINISHED", stayMinutes: 90, amount: 3750 });
    assert.equal(await reservationStatus(result.reservationId), "FINISHED");
    assert.equal(await spotStatus(lotId, "01"), "FREE");
    assert.equal((await db.doc(`parkingLots/${lotId}/spots/01`).get()).get("inUseBy"), undefined);
  });

  test("check-out de una reserva sin check-in se rechaza", async () => {
    const driver = await user();
    const { result } = await reserve(driver, await parkingLot());
    assertRejected(await call("checkOut", { reservationId: result.reservationId }, driver), "FAILED_PRECONDITION");
  });
});

describe("proceso de cada minuto", () => {
  test("reserva el lugar al entrar en la ventana de ahora y vence la reserva sin check-in", async () => {
    const driver = await user();
    const lotId = await parkingLot();
    const { result } = await reserve(driver, lotId, { arrivesAt: inMinutes(120) });
    const arrivesAt = new Date(result.arrivesAt).getTime();

    await processReservationsAt(db, new Date(arrivesAt - 10 * MINUTE));
    assert.equal(await spotStatus(lotId, "01"), "RESERVED");
    assert.equal(await reservationStatus(result.reservationId), "CONFIRMED");

    // Idempotente: otra corrida en el mismo momento no cambia nada.
    await processReservationsAt(db, new Date(arrivesAt - 10 * MINUTE));
    assert.equal(await spotStatus(lotId, "01"), "RESERVED");

    await processReservationsAt(db, new Date(arrivesAt + 20 * MINUTE));
    assert.equal(await reservationStatus(result.reservationId), "EXPIRED");
    assert.equal(await spotStatus(lotId, "01"), "FREE");

    await processReservationsAt(db, new Date(arrivesAt + 21 * MINUTE));
    assert.equal(await reservationStatus(result.reservationId), "EXPIRED");
  });
});

describe("lista de espera", () => {
  test("ordena por categoría y hora de pedido, no anota dos veces y deja salir", async () => {
    const lotId = await parkingLot();
    const arrivesAt = inMinutes(120);
    const slot = { parkingLotId: lotId, arrivesAt, durationMinutes: 60, spotType: "STANDARD" };
    // El único lugar ya está reservado para esa franja: si no, anotarse le ofrece el lugar en el acto.
    await reserve(await user(), lotId, { arrivesAt });
    const standard = await user();
    const pregnant = await user("DRIVER", "PREGNANT");

    const first = await call("joinWaitlist", slot, standard);
    assert.equal(first.result.position, 1);
    const second = await call("joinWaitlist", slot, pregnant);
    assert.equal(second.result.position, 1, "la categoría especial pasa adelante");

    assertRejected(await call("joinWaitlist", slot, standard), "FAILED_PRECONDITION", "ALREADY_IN_WAITLIST");

    assert.deepEqual((await call("leaveWaitlist", { entryId: first.result.entryId }, standard)).result,
      { status: "LEFT" });
    assert.equal((await db.doc(`waitlist/${first.result.entryId}`).get()).get("status"), "LEFT");
    // Después de salir se puede volver a anotar.
    assert.ok((await call("joinWaitlist", slot, standard)).result);
  });

  test("valida el pedido como una reserva, sin el límite de reservas activas", async () => {
    const lotId = await parkingLot({ "09": ["PRIORITY", "PREGNANT"] });
    const driver = await user();
    assertRejected(await call("joinWaitlist",
      { parkingLotId: lotId, arrivesAt: inMinutes(120), durationMinutes: 45, spotType: "STANDARD" }, driver),
    "INVALID_ARGUMENT", "INVALID_DURATION");
    assertRejected(await call("joinWaitlist",
      { parkingLotId: lotId, arrivesAt: inMinutes(120), durationMinutes: 60, spotType: "PRIORITY" }, driver),
    "FAILED_PRECONDITION", "CATEGORY_REQUIRED");
  });

  test("otro usuario no saca una entrada ajena", async () => {
    const owner = await user();
    const { result } = await call("joinWaitlist",
      { parkingLotId: await parkingLot(), arrivesAt: inMinutes(120), durationMinutes: 60, spotType: "STANDARD" }, owner);
    assertRejected(await call("leaveWaitlist", { entryId: result.entryId }, await user()), "PERMISSION_DENIED");
  });
});

describe("oferta de la lista de espera", () => {
  const entryOf = async (id) => (await db.doc(`waitlist/${id}`).get()).data();

  // La oferta al liberarse un lugar la dispara un trigger: llega un instante después.
  async function waitForEntry(id, predicate, what) {
    for (let i = 0; i < 80; i++) {
      const data = await entryOf(id);
      if (predicate(data)) return data;
      await new Promise((resolve) => setTimeout(resolve, 250));
    }
    throw new Error(`La entrada ${id} nunca llegó a ${what}: quedó ${JSON.stringify(await entryOf(id))}`);
  }
  const offered = (id) => waitForEntry(id, (entry) => entry.status === "OFFERED", "OFFERED");

  // Un estacionamiento con un solo lugar, ya reservado para la franja: el que llega va a la fila.
  async function fullLot(arrivesAt = inMinutes(120)) {
    const lotId = await parkingLot();
    const holder = await user();
    const { result } = await reserve(holder, lotId, { arrivesAt });
    const slot = { parkingLotId: lotId, arrivesAt, durationMinutes: 60, spotType: "STANDARD" };
    return { lotId, holder, reservationId: result.reservationId, slot };
  }

  const join = async (slot, who) => (await call("joinWaitlist", slot, who)).result.entryId;
  const cancel = (reservationId, who) => call("cancelReservation", { reservationId }, who);

  test("al liberarse el lugar, el primero de la fila recibe la oferta por 5 minutos", async () => {
    const { holder, reservationId, slot } = await fullLot();
    const entryId = await join(slot, await user());
    assert.equal((await entryOf(entryId)).status, "WAITING");
    assert.equal((await entryOf(entryId)).position, 1);

    const before = Date.now();
    await cancel(reservationId, holder);
    const entry = await offered(entryId);

    assert.equal(entry.offer.spotCode, "01");
    const minutes = (entry.offer.expiresAt.toDate() - before) / MINUTE;
    assert.ok(minutes > 4.5 && minutes < 5.5, `la oferta dura 5 minutos, no ${minutes}`);
  });

  test("con categoría de prioridad recibe la oferta primero aunque se haya anotado después", async () => {
    const { holder, reservationId, slot } = await fullLot();
    const standard = await join(slot, await user());
    const pregnant = await join(slot, await user("DRIVER", "PREGNANT"));
    assert.equal((await entryOf(pregnant)).position, 1, "pasa adelante en la fila");
    assert.equal((await entryOf(standard)).position, 2);

    await cancel(reservationId, holder);
    await offered(pregnant);

    const waiting = await entryOf(standard);
    assert.equal(waiting.status, "WAITING");
    assert.equal(waiting.position, 1, "el que quedó pasa a ser el primero");
  });

  test("mientras dura la oferta, nadie más puede reservar ese lugar", async () => {
    const { lotId, holder, reservationId, slot } = await fullLot();
    const entryId = await join(slot, await user());
    await cancel(reservationId, holder);
    await offered(entryId);

    assertRejected(await reserve(await user(), lotId, { arrivesAt: slot.arrivesAt }),
      "FAILED_PRECONDITION", "NO_SPOTS");
  });

  test("aceptar la convierte en una reserva confirmada con esa franja", async () => {
    const { holder, reservationId, slot } = await fullLot();
    const waiter = await user();
    const entryId = await join(slot, waiter);
    await cancel(reservationId, holder);
    await offered(entryId);

    const { result } = await call("acceptWaitlistOffer", { entryId }, waiter);
    assert.equal(result.spotCode, "01");
    assert.equal(result.arrivesAt, slot.arrivesAt);

    const reservation = (await db.doc(`reservations/${result.reservationId}`).get()).data();
    assert.equal(reservation.status, "CONFIRMED");
    assert.equal(reservation.userId, waiter.uid);
    assert.equal(reservation.durationMinutes, 60);
    assert.equal(reservation.fromWaitlist, entryId);

    const entry = await entryOf(entryId);
    assert.equal(entry.status, "ACCEPTED");
    assert.equal(entry.reservationId, result.reservationId);
  });

  test("rechazar pasa el lugar al siguiente de la fila", async () => {
    const { holder, reservationId, slot } = await fullLot();
    const first = await user();
    const firstEntry = await join(slot, first);
    const secondEntry = await join(slot, await user());
    await cancel(reservationId, holder);
    await offered(firstEntry);

    assert.deepEqual((await call("declineWaitlistOffer", { entryId: firstEntry }, first)).result,
      { status: "DECLINED" });
    assert.equal((await entryOf(firstEntry)).status, "DECLINED");
    await offered(secondEntry);
  });

  test("si nadie contesta, el proceso de cada minuto se la pasa al siguiente", async () => {
    const { holder, reservationId, slot } = await fullLot();
    const firstEntry = await join(slot, await user());
    const secondEntry = await join(slot, await user());
    await cancel(reservationId, holder);
    await offered(firstEntry);

    // Seis minutos después: se pasaron los cinco de la oferta.
    await processReservationsAt(db, new Date(Date.now() + 6 * MINUTE));

    const missed = await entryOf(firstEntry);
    assert.equal(missed.status, "DECLINED");
    assert.equal(missed.missed, true);
    assert.equal((await entryOf(secondEntry)).status, "OFFERED");
  });

  test("aceptar una oferta vencida: OFFER_EXPIRED", async () => {
    const { holder, reservationId, slot } = await fullLot();
    const waiter = await user();
    const entryId = await join(slot, waiter);
    await cancel(reservationId, holder);
    await offered(entryId);
    // Como si hubieran pasado los cinco minutos sin que corriera todavía el proceso.
    await db.doc(`waitlist/${entryId}`).update({ "offer.expiresAt": Timestamp.fromDate(new Date(Date.now() - MINUTE)) });

    assertRejected(await call("acceptWaitlistOffer", { entryId }, waiter), "FAILED_PRECONDITION", "OFFER_EXPIRED");
  });

  test("aceptar o rechazar sin oferta: NO_OFFER", async () => {
    const { slot } = await fullLot();
    const waiter = await user();
    const entryId = await join(slot, waiter);
    assertRejected(await call("acceptWaitlistOffer", { entryId }, waiter), "FAILED_PRECONDITION", "NO_OFFER");
    assertRejected(await call("declineWaitlistOffer", { entryId }, waiter), "FAILED_PRECONDITION", "NO_OFFER");
  });

  test("con dos reservas activas no puede aceptar: LIMIT_REACHED", async () => {
    const { holder, reservationId, slot } = await fullLot();
    const waiter = await user();
    const entryId = await join(slot, waiter);
    const elsewhere = await parkingLot({ "01": "STANDARD", "02": "STANDARD" });
    assert.ok((await reserve(waiter, elsewhere, { arrivesAt: inMinutes(300) })).result);
    assert.ok((await reserve(waiter, elsewhere, { arrivesAt: inMinutes(420) })).result);
    await cancel(reservationId, holder);
    await offered(entryId);

    assertRejected(await call("acceptWaitlistOffer", { entryId }, waiter), "FAILED_PRECONDITION", "LIMIT_REACHED");
  });

  test("otro usuario no acepta una oferta ajena", async () => {
    const { holder, reservationId, slot } = await fullLot();
    const entryId = await join(slot, await user());
    await cancel(reservationId, holder);
    await offered(entryId);
    assertRejected(await call("acceptWaitlistOffer", { entryId }, await user()), "PERMISSION_DENIED");
  });

  test("salir de la fila con una oferta pendiente la suelta para el siguiente", async () => {
    const { holder, reservationId, slot } = await fullLot();
    const first = await user();
    const firstEntry = await join(slot, first);
    const secondEntry = await join(slot, await user());
    await cancel(reservationId, holder);
    await offered(firstEntry);

    await call("leaveWaitlist", { entryId: firstEntry }, first);

    assert.equal((await entryOf(firstEntry)).status, "LEFT");
    await offered(secondEntry);
  });

  test("las posiciones se actualizan cuando alguien sale", async () => {
    const { slot } = await fullLot();
    const a = await user();
    const aEntry = await join(slot, a);
    const bEntry = await join(slot, await user());
    const cEntry = await join(slot, await user());
    assert.deepEqual([(await entryOf(aEntry)).position, (await entryOf(bEntry)).position,
      (await entryOf(cEntry)).position], [1, 2, 3]);

    await call("leaveWaitlist", { entryId: aEntry }, a);

    assert.equal((await entryOf(bEntry)).position, 1);
    assert.equal((await entryOf(cEntry)).position, 2);
  });

  test("con una oferta pendiente no se puede anotar de nuevo para la misma franja", async () => {
    const { holder, reservationId, slot } = await fullLot();
    const waiter = await user();
    const entryId = await join(slot, waiter);
    await cancel(reservationId, holder);
    await offered(entryId);
    assertRejected(await call("joinWaitlist", slot, waiter), "FAILED_PRECONDITION", "ALREADY_IN_WAITLIST");
  });

  test("si al anotarse ya había lugar, la oferta sale en el acto", async () => {
    const lotId = await parkingLot();
    const entryId = await join(
      { parkingLotId: lotId, arrivesAt: inMinutes(120), durationMinutes: 60, spotType: "STANDARD" }, await user());
    assert.equal((await entryOf(entryId)).status, "OFFERED");
  });

  test("una entrada cuya franja ya empezó vence al repasar la fila", async () => {
    const { lotId, slot } = await fullLot();
    const entryId = await join(slot, await user());
    // Tres horas después: la franja pedida (en dos horas) ya empezó.
    await reconcileWaitlist(db, lotId, new Date(Date.now() + 3 * 60 * MINUTE));
    assert.equal((await entryOf(entryId)).status, "EXPIRED");
  });
});
