const { setGlobalOptions } = require("firebase-functions/v2");
const { onDocumentCreated, onDocumentWritten } = require("firebase-functions/v2/firestore");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { defineString } = require("firebase-functions/params");
const logger = require("firebase-functions/logger");
const { initializeApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const { seed } = require("./seed");
const { affectsCounters, recountParkingLot } = require("./counters");

// Tiene que ser la misma región que se eligió al crear la base de Firestore.
setGlobalOptions({ region: "southamerica-east1", maxInstances: 5 });

initializeApp();
const db = getFirestore();
// Después de setGlobalOptions: si se carga antes, sus functions quedan en la región por defecto.
const reservations = require("./reservations");

// DRIVER: conductor, el usuario final. PARKING_ADMIN: crea y administra estacionamientos (los que
// lo tienen en `adminUids`). ADMIN: superusuario del sistema, sólo para desarrollo y pruebas.
const ROLES = ["DRIVER", "PARKING_ADMIN", "ADMIN"];
const INITIAL_CATEGORY = "STANDARD";
const ADMIN_EMAILS = defineString("ADMIN_EMAILS", { default: "" });

function requireAdmin(request) {
  if (!request.auth || request.auth.token.role !== "ADMIN") {
    throw new HttpsError("permission-denied", "Sólo un administrador puede hacer esto.");
  }
}

// El claim es la fuente de verdad (lo leen las reglas). `users/{uid}.role` es el aviso
// para que la app refresque el token; el dueño no puede escribir ese campo.
async function setRole(uid, role) {
  await getAuth().setCustomUserClaims(uid, { role });
  await db.doc(`users/${uid}`).set({ role }, { merge: true });
}

// Roles que un usuario puede elegir al registrarse. ADMIN no: sale sólo de ADMIN_EMAILS.
const SELF_SERVICE_ROLES = ["DRIVER", "PARKING_ADMIN"];

// Asigna el rol de una cuenta nueva: el que pidió al registrarse (`requestedRole`), DRIVER si no
// pidió ninguno, o ADMIN si su email está en ADMIN_EMAILS. Completa los datos del perfil que falten.
exports.assignInitialRole = onDocumentCreated("users/{uid}", async (event) => {
  const uid = event.params.uid;
  const profile = event.data.data();
  const user = await getAuth().getUser(uid);
  const currentRole = user.customClaims && user.customClaims.role;

  const missing = { requestedRole: FieldValue.delete() };
  if (!profile.email && user.email) missing.email = user.email;
  if (!profile.category) missing.category = INITIAL_CATEGORY;

  if (currentRole) {
    // La cuenta ya tenía rol y su documento se volvió a crear: sólo se repone el aviso.
    await event.data.ref.set({ ...missing, role: currentRole }, { merge: true });
    return;
  }

  const admins = ADMIN_EMAILS.value().split(",").map((e) => e.trim().toLowerCase()).filter(Boolean);
  const role = admins.includes((user.email || "").toLowerCase())
    ? "ADMIN"
    : SELF_SERVICE_ROLES.includes(profile.requestedRole) ? profile.requestedRole : "DRIVER";
  await event.data.ref.set(missing, { merge: true });
  await setRole(uid, role);
  logger.info("Rol inicial asignado", { uid, role });
});

// Un ADMIN cambia el rol de otro usuario. Datos: { uid, role }.
exports.assignRole = onCall(async (request) => {
  requireAdmin(request);
  const { uid, role } = request.data || {};
  if (typeof uid !== "string" || !ROLES.includes(role)) {
    throw new HttpsError("invalid-argument", "Se espera { uid, role } con un rol válido.");
  }
  await setRole(uid, role);
  return { uid, role };
});

// Un ADMIN carga (o restablece) los datos semilla en la base. Ver seed.js.
exports.loadSeed = onCall(async (request) => {
  requireAdmin(request);
  const result = await seed(db);
  logger.info("Semilla cargada", result);
  return result;
});

// Mantiene los contadores de lugares del estacionamiento (ver counters.js) cada vez que se crea,
// borra o cambia uno de sus lugares: lo dispara el simulador, la consola o su administrador.
exports.updateFreeSpotCounters = onDocumentWritten("parkingLots/{lotId}/spots/{spotId}", async (event) => {
  if (!affectsCounters(event.data.before.data(), event.data.after.data())) return;
  await recountParkingLot(db, event.params.lotId);
});

// Reservas y lista de espera (ver reservations.js y docs/reservation-rules.md).
exports.createReservation = reservations.createReservation;
exports.cancelReservation = reservations.cancelReservation;
exports.checkIn = reservations.checkIn;
exports.checkOut = reservations.checkOut;
exports.joinWaitlist = reservations.joinWaitlist;
exports.leaveWaitlist = reservations.leaveWaitlist;
exports.processReservations = reservations.processReservations;

// Envía una push sólo `data` (todos strings) a los usuarios indicados y limpia tokens vencidos.
async function sendPush(uids, data) {
  const docs = await Promise.all(uids.map((uid) => db.doc(`users/${uid}`).get()));
  const recipients = docs.filter((d) => d.exists && d.get("fcmToken"));
  if (recipients.length === 0) return 0;

  const response = await getMessaging().sendEachForMulticast({
    tokens: recipients.map((d) => d.get("fcmToken")),
    data,
    // priority high para que Doze no la retenga; ttl explícito para que no llegue horas tarde.
    android: { priority: "high", ttl: 10 * 60 * 1000 },
  });

  const expired = ["messaging/registration-token-not-registered", "messaging/invalid-registration-token"];
  await Promise.all(response.responses.map((r, i) =>
    !r.success && expired.includes(r.error.code)
      ? recipients[i].ref.update({ fcmToken: FieldValue.delete() })
      : null));
  return response.successCount;
}

// Ejemplo del circuito de avisos: confirma al conductor que su reserva quedó registrada.
exports.notifyReservationCreated = onDocumentCreated("reservations/{reservationId}", async (event) => {
  const reservation = event.data.data();
  if (!reservation.userId) return;
  await sendPush([reservation.userId], {
    type: "reservation_created",
    id: event.params.reservationId,
    title: "Reserva confirmada",
    body: "Tu lugar quedó reservado.",
  });
});

// La usa la pantalla de diagnóstico de la app: manda una push al propio usuario para
// verificar de punta a punta Functions + FCM.
exports.sendTestPush = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Hay que iniciar sesión.");
  const sent = await sendPush([request.auth.uid], {
    type: "test",
    id: String(Date.now()),
    title: "Push de prueba",
    body: "Functions y FCM están funcionando.",
  });
  if (sent === 0) {
    throw new HttpsError("failed-precondition", "No hay un token FCM válido registrado para este usuario.");
  }
  return { sent };
});

// Simulador de sensores mínimo: cada corrida cambia algunos lugares entre FREE y OCCUPIED.
// Los RESERVED, OUT_OF_SERVICE y los ocupados por una reserva en uso no se tocan. Los contadores
// los actualiza updateFreeSpotCounters.
// Se pausa desde Cloud Scheduler en la consola de Google Cloud.
const SPOTS_PER_RUN = 50;
const CHANGE_PROBABILITY = 0.2;

exports.simulateSensors = onSchedule("every 5 minutes", async () => {
  const spots = await db.collectionGroup("spots").limit(SPOTS_PER_RUN).get();
  const batch = db.batch();
  let changes = 0;

  spots.forEach((spot) => {
    const status = spot.get("status");
    // Un lugar con un auto que hizo check-in (`inUseBy`) lo libera el check-out, no el sensor.
    const movedBySensor = (status === "FREE" || status === "OCCUPIED") && !spot.get("inUseBy");
    if (!movedBySensor || Math.random() > CHANGE_PROBABILITY) return;
    batch.update(spot.ref, {
      status: status === "OCCUPIED" ? "FREE" : "OCCUPIED",
      updatedAt: FieldValue.serverTimestamp(),
    });
    changes++;
  });

  if (changes > 0) await batch.commit();
  logger.info("Simulación de sensores", { checked: spots.size, changes });
});
