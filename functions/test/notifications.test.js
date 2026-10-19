// Tests de los avisos push. Las reglas de cuándo avisar no necesitan nada; el envío y el proceso de
// cada minuto corren contra los emuladores (desde la raíz del repo):
//   firebase emulators:exec --only auth,firestore,functions --project demo-sensorpark "npm --prefix functions test"
// FCM no tiene emulador: los tests le pasan un `messaging` falso que anota lo que se mandó.
const { after, before, describe, test } = require("node:test");
const assert = require("node:assert/strict");
const { initializeApp, deleteApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore, Timestamp } = require("firebase-admin/firestore");
const notifications = require("../notifications");
const { processReservationsAt } = require("../reservations");

const { dueNotice, messageFor, wantsNotice, localTime, sendPush } = notifications;
const MINUTE = 60 * 1000;
const at = (base, minutes) => new Date(base.getTime() + minutes * MINUTE);

describe("cuándo avisar", () => {
  const arrivesAt = new Date("2026-10-20T15:00:00Z");
  const confirmed = {
    status: "CONFIRMED", arrivesAt, toleranceUntil: at(arrivesAt, 15), endsAt: at(arrivesAt, 60),
  };
  const inUse = { ...confirmed, status: "IN_USE" };

  test("reserva por vencer: desde 5 minutos antes del fin de la tolerancia", () => {
    assert.equal(dueNotice(confirmed, at(arrivesAt, 9)), null);
    assert.equal(dueNotice(confirmed, at(arrivesAt, 10)), "reservation_expiring");
    assert.equal(dueNotice(confirmed, at(arrivesAt, 15)), "reservation_expiring");
  });

  test("ya vencida no se avisa que está por vencer", () =>
    assert.equal(dueNotice(confirmed, at(arrivesAt, 16)), null));

  test("una vez mandado, no se vuelve a mandar", () =>
    assert.equal(dueNotice({ ...confirmed, notices: { expiring: new Date() } }, at(arrivesAt, 12)), null));

  test("franja por terminar: desde 10 minutos antes del fin", () => {
    assert.equal(dueNotice(inUse, at(arrivesAt, 49)), null);
    assert.equal(dueNotice(inUse, at(arrivesAt, 50)), "reservation_ending");
    assert.equal(dueNotice({ ...inUse, notices: { ending: new Date() } }, at(arrivesAt, 55)), null);
  });

  test("estadía prolongada: pasado el fin sin check-out, una sola vez", () => {
    assert.equal(dueNotice(inUse, at(arrivesAt, 60)), "reservation_long_stay");
    assert.equal(dueNotice({ ...inUse, notices: { ending: new Date() } }, at(arrivesAt, 90)), "reservation_long_stay");
    assert.equal(dueNotice({ ...inUse, notices: { longStay: new Date() } }, at(arrivesAt, 90)), null);
  });

  test("las reservas terminadas, canceladas o vencidas no tienen avisos", () => {
    for (const status of ["FINISHED", "CANCELLED", "EXPIRED"]) {
      assert.equal(dueNotice({ ...confirmed, status }, at(arrivesAt, 12)), null);
      assert.equal(dueNotice({ ...confirmed, status }, at(arrivesAt, 70)), null);
    }
  });
});

describe("mensajes", () => {
  test("la hora va en hora argentina", () =>
    assert.equal(localTime(new Date("2026-10-20T17:05:00Z")), "14:05"));

  test("todos los datos son texto y llevan el id para abrir la reserva", () => {
    const message = messageFor("reservation_expiring", "r1", {
      spotCode: "03", toleranceUntil: new Date("2026-10-20T17:20:00Z"),
    });
    assert.deepEqual(message, {
      type: "reservation_expiring",
      id: "r1",
      title: "Tu reserva está por vencer",
      body: "Si no hacés check-in antes de las 14:20, se libera el lugar 03.",
    });
  });

  test("los avisos sobre una reserva empiezan con `reservation`, y la oferta de la lista de espera no", () => {
    for (const type of Object.keys(notifications.NOTICES)) {
      assert.equal(type.startsWith("reservation"), type !== "waitlist_offer", type);
    }
  });

  test("la oferta de la lista de espera dice dónde, la franja y cuánto tiempo hay para aceptarla", () => {
    const message = messageFor("waitlist_offer", "w1", {
      lotName: "Medrano", spotCode: "07",
      arrivesAt: new Date("2026-10-20T17:00:00Z"), endsAt: new Date("2026-10-20T18:00:00Z"),
    });
    assert.equal(message.title, "Se liberó un lugar en Medrano");
    assert.equal(message.body, "Lugar 07, de 14:00 a 15:00. Tenés 5 minutos para quedártelo.");
  });

  test("sólo se dejan de mandar los tipos que el usuario apagó en la app", () => {
    assert.equal(wantsNotice({}, "RESERVATION"), true);
    assert.equal(wantsNotice({ notificationsDisabled: ["WAITLIST"] }, "RESERVATION"), true);
    assert.equal(wantsNotice({ notificationsDisabled: ["RESERVATION"] }, "RESERVATION"), false);
  });

  test("cada aviso cae en el canal de la app que corresponde a su prefijo", () => {
    for (const [type, notice] of Object.entries(notifications.NOTICES)) {
      assert.ok(type.startsWith(notice.appType.toLowerCase()), type);
    }
  });

  test("un tipo desconocido es un error de programación", () =>
    assert.throws(() => messageFor("otra_cosa", "x", {})));
});

// FCM falso: anota cada envío y responde OK, salvo los tokens de `failing` (token → código de error).
function fakeMessaging(failing = {}) {
  const sent = [];
  return {
    sent,
    async sendEachForMulticast(message) {
      sent.push(message);
      const responses = message.tokens.map((t) => (failing[t] ? { success: false, error: { code: failing[t] } } : { success: true }));
      return { responses, successCount: responses.filter((r) => r.success).length };
    },
  };
}

describe("envío y proceso de cada minuto", () => {
  let app;
  let auth;
  let db;
  let counter = 0;
  const unique = (prefix) => `${prefix}-${Date.now()}-${counter++}`;

  before(() => {
    app = initializeApp({ projectId: process.env.GCLOUD_PROJECT || "demo-sensorpark" }, "notifications");
    auth = getAuth(app);
    db = getFirestore(app);
  });

  after(() => deleteApp(app));

  // Un usuario con sus teléfonos. Tiene cuenta en Auth porque crear su documento dispara
  // assignInitialRole, que la busca. `disabled`: los tipos de aviso que apagó en la app.
  async function user({ tokens = [], legacyToken, disabled } = {}) {
    const uid = unique("aviso");
    await auth.createUser({ uid });
    await db.doc(`users/${uid}`).set({
      name: "Prueba",
      ...(disabled ? { notificationsDisabled: disabled } : {}),
      ...(legacyToken ? { fcmToken: legacyToken } : {}),
    });
    for (const token of tokens) await db.doc(`users/${uid}/devices/${token}`).set({ token });
    return uid;
  }

  // Una reserva escrita directo, como la dejan las functions. Lejos en el tiempo (dentro de 3 días)
  // para no cruzarse con los tests de reservas, que corren en paralelo con otras horas.
  async function reservation(uid, status, extra = {}) {
    const lotId = unique("lot-aviso");
    await db.doc(`parkingLots/${lotId}`).set({ name: "Playa", toleranceMinutes: 15 });
    await db.doc(`parkingLots/${lotId}/spots/03`).set({ code: "03", type: "STANDARD", status: "FREE" });
    const arrivesAt = new Date(Date.now() + 3 * 24 * 60 * MINUTE + counter * MINUTE);
    arrivesAt.setSeconds(0, 0);
    const ref = db.collection("reservations").doc();
    // Sin `userId` al crearla: así el trigger notifyReservationCreated del emulador la ignora y no
    // intenta mandar nada por el FCM de verdad. El dueño se agrega después.
    await ref.set({
      parkingLotId: lotId, spotId: "03", spotCode: "03", spotType: "STANDARD",
      arrivesAt: Timestamp.fromDate(arrivesAt), durationMinutes: 60,
      endsAt: Timestamp.fromDate(at(arrivesAt, 60)), toleranceUntil: Timestamp.fromDate(at(arrivesAt, 15)),
      status, ...extra,
    });
    await ref.update({ userId: uid });
    return { ref, arrivesAt };
  }

  // Lo que le llegó a ese usuario: el proceso recorre toda la base, también reservas de otros tests.
  const sentTo = (messaging, token) => messaging.sent.filter((m) => m.tokens.includes(token)).map((m) => m.data.type);

  test("manda a todos los teléfonos del usuario, también al token de la versión anterior", async () => {
    const uid = await user({ tokens: ["tel-1", "tel-2"], legacyToken: "viejo" });
    const messaging = fakeMessaging();
    const delivered = await sendPush(db, [uid], { type: "test", id: "1", title: "t", body: "b" }, { messaging });
    assert.equal(delivered, 3);
    assert.deepEqual(messaging.sent[0].tokens.sort(), ["tel-1", "tel-2", "viejo"]);
    assert.equal(messaging.sent[0].android.priority, "high");
  });

  test("no manda un tipo de aviso que el usuario apagó", async () => {
    const uid = await user({ tokens: ["tel-apagado"], disabled: ["RESERVATION"] });
    const messaging = fakeMessaging();
    const delivered = await notifications.sendNotice(db, uid, "reservation_long_stay", "r1",
      { spotCode: "03", endsAt: new Date() }, { messaging });
    assert.equal(delivered, 0);
    assert.equal(messaging.sent.length, 0);
  });

  test("sin teléfonos registrados no manda nada", async () => {
    const messaging = fakeMessaging();
    assert.equal(await sendPush(db, [await user()], { type: "test", id: "1", title: "t", body: "b" }, { messaging }), 0);
    assert.equal(messaging.sent.length, 0);
  });

  test("borra los tokens que FCM da por vencidos", async () => {
    const uid = await user({ tokens: ["vencido", "bueno"], legacyToken: "viejo-vencido" });
    const messaging = fakeMessaging({
      "vencido": "messaging/registration-token-not-registered",
      "viejo-vencido": "messaging/invalid-registration-token",
    });
    assert.equal(await sendPush(db, [uid], { type: "test", id: "1", title: "t", body: "b" }, { messaging }), 1);
    assert.equal((await db.doc(`users/${uid}/devices/vencido`).get()).exists, false);
    assert.equal((await db.doc(`users/${uid}/devices/bueno`).get()).exists, true);
    assert.equal((await db.doc(`users/${uid}`).get()).get("fcmToken"), undefined);
  });

  test("reserva sin check-in: avisa una vez que está por vencer y otra cuando vence", async () => {
    const uid = await user({ tokens: ["tel-vence"] });
    const { ref, arrivesAt } = await reservation(uid, "CONFIRMED");
    const messaging = fakeMessaging();
    const run = (minutes) => processReservationsAt(db, at(arrivesAt, minutes), { messaging });

    await run(9);
    assert.deepEqual(sentTo(messaging, "tel-vence"), []);
    await run(10);
    await run(11);
    assert.deepEqual(sentTo(messaging, "tel-vence"), ["reservation_expiring"]);
    assert.ok((await ref.get()).get("notices.expiring"));

    await run(16);
    await run(17);
    assert.deepEqual(sentTo(messaging, "tel-vence"), ["reservation_expiring", "reservation_expired"]);
    assert.equal((await ref.get()).get("status"), "EXPIRED");
    const expired = messaging.sent.find((m) => m.tokens.includes("tel-vence") && m.data.type === "reservation_expired");
    assert.equal(expired.data.id, ref.id);
  });

  test("estacionado: avisa que la franja termina y después que se pasó, una vez cada uno", async () => {
    const uid = await user({ tokens: ["tel-estadia"] });
    const { ref, arrivesAt } = await reservation(uid, "IN_USE");
    const messaging = fakeMessaging();
    const run = (minutes) => processReservationsAt(db, at(arrivesAt, minutes), { messaging });

    await run(49);
    await run(50);
    await run(55);
    assert.deepEqual(sentTo(messaging, "tel-estadia"), ["reservation_ending"]);
    await run(61);
    await run(90);
    assert.deepEqual(sentTo(messaging, "tel-estadia"), ["reservation_ending", "reservation_long_stay"]);
    assert.equal((await ref.get()).get("status"), "IN_USE");
  });

  test("con los avisos de reservas apagados no avisa, y tampoco lo hace después", async () => {
    const uid = await user({ tokens: ["tel-sin-recordatorios"], disabled: ["RESERVATION"] });
    const { ref, arrivesAt } = await reservation(uid, "IN_USE");
    const messaging = fakeMessaging();
    await processReservationsAt(db, at(arrivesAt, 61), { messaging });
    assert.deepEqual(sentTo(messaging, "tel-sin-recordatorios"), []);
    // Queda marcado igual: si los vuelve a prender, no le llega un aviso viejo.
    assert.ok((await ref.get()).get("notices.longStay"));
  });

  test("si un teléfono pasa a otra cuenta, deja de recibir los avisos de la anterior", async () => {
    const before = await user({ tokens: ["tel-compartido"] });
    const now = await user();
    await db.doc(`users/${now}/devices/tel-compartido`).set({ token: "tel-compartido" });
    // Lo saca el trigger claimDevice, que corre en el emulador de functions.
    const old = db.doc(`users/${before}/devices/tel-compartido`);
    for (let i = 0; i < 50 && (await old.get()).exists; i++) await new Promise((r) => setTimeout(r, 200));
    assert.equal((await old.get()).exists, false);
    assert.equal((await db.doc(`users/${now}/devices/tel-compartido`).get()).exists, true);
  });
});
