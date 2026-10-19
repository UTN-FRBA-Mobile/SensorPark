// Reservas y lista de espera: functions invocables y el proceso que corre cada minuto.
// Las reglas están en reservationRules.js; acá se leen y escriben los documentos, siempre en
// transacciones, para que dos pedidos simultáneos no tomen el mismo lugar.
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { onDocumentUpdated } = require("firebase-functions/v2/firestore");
const logger = require("firebase-functions/logger");
const { getFirestore, FieldValue, Timestamp } = require("firebase-admin/firestore");
const rules = require("./reservationRules");
const notifications = require("./notifications");

const BOOKING_ROLES = ["DRIVER", "ADMIN"];

const MESSAGES = {
  INVALID_DURATION: "La duración tiene que ser 15 o 30 minutos, o 1, 2, 4 u 8 horas.",
  IN_THE_PAST: "La hora de llegada ya pasó.",
  TOO_FAR_AHEAD: `Se puede reservar con hasta ${rules.MAX_ADVANCE_DAYS} días de anticipación.`,
  OUTSIDE_SCHEDULE: "La franja queda fuera del horario del estacionamiento.",
  CATEGORY_REQUIRED: "Los lugares preferenciales son para quien declaró una categoría de prioridad.",
  LIMIT_REACHED: `Ya tenés ${rules.MAX_ACTIVE_RESERVATIONS} reservas activas.`,
  NO_SPOTS: "No hay lugares disponibles para esa franja.",
  CANCEL_NOT_ALLOWED: "Sólo se puede cancelar una reserva antes del check-in.",
  TOO_EARLY: `El check-in se habilita ${rules.EARLY_CHECK_IN_MINUTES} minutos antes de la llegada.`,
  EXPIRED: "La reserva ya no se puede usar.",
  TOO_FAR: "Tenés que estar en el estacionamiento para hacer check-in.",
  INVALID_STATUS: "La reserva no está en uso.",
  ALREADY_IN_WAITLIST: "Ya estás anotado en la lista de espera para esa franja.",
  SPOT_NOT_TAKEN: "Tu lugar no figura ocupado por otro auto.",
  NO_OFFER: "Esa oferta ya no está disponible.",
  OFFER_EXPIRED: `Se pasaron los ${rules.WAITLIST_OFFER_MINUTES} minutos para aceptar el lugar.`,
};

// Rechazo de negocio: la app lee el motivo en `details.reason`.
function rejection(reason) {
  const code = reason === "INVALID_DURATION" ? "invalid-argument" : "failed-precondition";
  return new HttpsError(code, MESSAGES[reason] || reason, { reason });
}

function invalid(message) {
  return new HttpsError("invalid-argument", message);
}

function requireSignedIn(request) {
  if (!request.auth) throw new HttpsError("unauthenticated", "Hay que iniciar sesión.");
  return request.auth.uid;
}

function requireBookingRole(request) {
  const uid = requireSignedIn(request);
  if (!BOOKING_ROLES.includes(request.auth.token.role)) {
    throw new HttpsError("permission-denied", "Sólo un conductor puede reservar.");
  }
  return uid;
}

function requireString(value, name) {
  if (typeof value !== "string" || value.length === 0) throw invalid(`Falta ${name}.`);
  return value;
}

function requireDate(value, name) {
  const date = new Date(requireString(value, name));
  if (Number.isNaN(date.getTime())) throw invalid(`${name} tiene que ser una fecha ISO 8601.`);
  return date;
}

function requireNumber(value, name) {
  if (typeof value !== "number" || !Number.isFinite(value)) throw invalid(`Falta ${name}.`);
  return value;
}

const toDate = (value) => (value && typeof value.toDate === "function" ? value.toDate() : value);

// Una reserva leída de Firestore, con las fechas como Date.
function reservationOf(doc) {
  const data = doc.data();
  return {
    ...data,
    id: doc.id,
    arrivesAt: toDate(data.arrivesAt),
    endsAt: toDate(data.endsAt),
    toleranceUntil: toDate(data.toleranceUntil),
    checkedInAt: toDate(data.checkedInAt),
  };
}

const spotOf = (doc) => ({ id: doc.id, ...doc.data() });

// Una entrada de la lista de espera leída de Firestore, con las fechas como Date.
function waitlistEntryOf(doc) {
  const data = doc.data();
  return {
    ...data,
    id: doc.id,
    arrivesAt: toDate(data.arrivesAt),
    endsAt: toDate(data.endsAt),
    requestedAt: toDate(data.requestedAt),
    offer: data.offer ? { ...data.offer, expiresAt: toDate(data.offer.expiresAt) } : null,
  };
}

// Las ofertas vigentes de un estacionamiento, como reservas: guardan su lugar mientras el primero
// de la fila decide. `exceptEntryId` deja afuera la propia, al aceptarla.
async function heldOffersOfLot(tx, db, lotId, now, exceptEntryId = null) {
  const docs = (await tx.get(db.collection("waitlist")
    .where("parkingLotId", "==", lotId).where("status", "==", "OFFERED"))).docs;
  return docs.filter((d) => d.id !== exceptEntryId).map(waitlistEntryOf)
    .filter((e) => !rules.offerExpired(e, now)).map(rules.holdOf);
}

function activeReservationsOfLot(db, lotId) {
  return db.collection("reservations")
    .where("parkingLotId", "==", lotId)
    .where("status", "in", rules.ACTIVE_STATUSES);
}

async function categoryOf(tx, db, uid) {
  const user = await tx.get(db.doc(`users/${uid}`));
  return (user.exists && user.get("category")) || "STANDARD";
}

// Lee la reserva y verifica que sea del usuario.
async function ownReservation(tx, db, uid, reservationId) {
  const ref = db.doc(`reservations/${requireString(reservationId, "reservationId")}`);
  const doc = await tx.get(ref);
  if (!doc.exists) throw new HttpsError("not-found", "No existe esa reserva.");
  const reservation = reservationOf(doc);
  if (reservation.userId !== uid) throw new HttpsError("permission-denied", "La reserva no es tuya.");
  return { ref, reservation };
}

// Deja el lugar de una reserva que termina: FREE, o RESERVED si otra ya está en su ventana de
// "ahora", y le saca la marca `inUseBy` del check-in. Un lugar fuera de servicio no se toca. Las lecturas tienen que ir antes que las
// escrituras de la transacción, por eso devuelve la escritura para hacerla después.
async function prepareSpotRelease(tx, db, reservation, now) {
  const spotRef = db.doc(`parkingLots/${reservation.parkingLotId}/spots/${reservation.spotId}`);
  const spot = await tx.get(spotRef);
  const others = await tx.get(activeReservationsOfLot(db, reservation.parkingLotId)
    .where("spotId", "==", reservation.spotId));
  return () => {
    if (!spot.exists) return;
    const inUse = spot.get("inUseBy") === reservation.id;
    if (spot.get("status") === "OUT_OF_SERVICE") {
      if (inUse) tx.update(spotRef, { inUseBy: FieldValue.delete() });
      return;
    }
    const status = rules.spotStatusAfterRelease(
      others.docs.filter((d) => d.id !== reservation.id).map(reservationOf), now);
    if (spot.get("status") !== status || inUse) {
      tx.update(spotRef, { status, inUseBy: FieldValue.delete(), updatedAt: FieldValue.serverTimestamp() });
    }
  };
}

// Crea una reserva. Datos: { parkingLotId, arrivesAt (ISO 8601), durationMinutes, spotType o spotId }.
exports.createReservation = onCall(async (request) => {
  const uid = requireBookingRole(request);
  const data = request.data || {};
  const lotId = requireString(data.parkingLotId, "parkingLotId");
  const arrivesAt = requireDate(data.arrivesAt, "arrivesAt");
  const durationMinutes = requireNumber(data.durationMinutes, "durationMinutes");
  const spotId = data.spotId === undefined ? null : requireString(data.spotId, "spotId");
  if (!spotId && !rules.SPOT_TYPES.includes(data.spotType)) throw invalid("Falta spotType o spotId.");

  const db = getFirestore();
  const now = new Date();
  const lotRef = db.doc(`parkingLots/${lotId}`);

  return db.runTransaction(async (tx) => {
    const lot = await tx.get(lotRef);
    if (!lot.exists) throw new HttpsError("not-found", "No existe ese estacionamiento.");
    const category = await categoryOf(tx, db, uid);
    const spots = (await tx.get(lotRef.collection("spots"))).docs.map(spotOf);
    // Los lugares guardados por una oferta de la lista de espera tampoco se pueden reservar.
    const lotReservations = [
      ...(await tx.get(activeReservationsOfLot(db, lotId))).docs.map(reservationOf),
      ...await heldOffersOfLot(tx, db, lotId, now),
    ];
    const userActive = await tx.get(db.collection("reservations")
      .where("userId", "==", uid).where("status", "in", rules.ACTIVE_STATUSES));

    let spotType = data.spotType;
    if (spotId) {
      const requested = spots.find((s) => s.id === spotId);
      if (!requested) throw new HttpsError("not-found", "No existe ese lugar.");
      spotType = requested.type;
    }

    const problem = rules.requestProblem({
      arrivesAt, durationMinutes, spotType, category,
      activeReservations: userActive.size, schedule: lot.get("schedule"),
    }, now);
    if (problem) throw rejection(problem);

    const endsAt = rules.endsAt(arrivesAt, durationMinutes);
    const spot = rules.pickSpot(spots, {
      spotType, spotId, category, arrivesAt, endsAt, reservations: lotReservations,
    }, now);
    if (!spot) throw rejection("NO_SPOTS");

    const toleranceUntil = rules.toleranceUntil(arrivesAt, lot.get("toleranceMinutes"));
    const ref = db.collection("reservations").doc();
    tx.set(ref, {
      userId: uid,
      parkingLotId: lotId,
      spotId: spot.id,
      spotCode: spot.code,
      spotType: spot.type,
      arrivesAt: Timestamp.fromDate(arrivesAt),
      durationMinutes,
      endsAt: Timestamp.fromDate(endsAt),
      toleranceUntil: Timestamp.fromDate(toleranceUntil),
      status: "CONFIRMED",
      createdAt: FieldValue.serverTimestamp(),
    });
    // Siempre se escribe el lugar elegido: si otro pedido simultáneo leyó los mismos lugares,
    // su transacción choca con ésta, se reintenta y ve la reserva nueva.
    const spotUpdate = { lastReservationAt: FieldValue.serverTimestamp() };
    if (rules.isInNowWindow(arrivesAt, now)) {
      spotUpdate.status = "RESERVED";
      spotUpdate.updatedAt = FieldValue.serverTimestamp();
    }
    tx.update(lotRef.collection("spots").doc(spot.id), spotUpdate);

    logger.info("Reserva creada", { reservationId: ref.id, uid, lotId, spotId: spot.id });
    return {
      reservationId: ref.id,
      spotId: spot.id,
      spotCode: spot.code,
      arrivesAt: arrivesAt.toISOString(),
      endsAt: endsAt.toISOString(),
      toleranceUntil: toleranceUntil.toISOString(),
    };
  });
});

// Cancela una reserva propia antes del check-in. Datos: { reservationId }.
exports.cancelReservation = onCall(async (request) => {
  const uid = requireSignedIn(request);
  const db = getFirestore();
  const now = new Date();
  return db.runTransaction(async (tx) => {
    const { ref, reservation } = await ownReservation(tx, db, uid, (request.data || {}).reservationId);
    if (!rules.canCancel(reservation)) throw rejection("CANCEL_NOT_ALLOWED");
    const release = await prepareSpotRelease(tx, db, reservation, now);
    tx.update(ref, { status: "CANCELLED", cancelledAt: FieldValue.serverTimestamp() });
    release();
    return { status: "CANCELLED" };
  });
});

// Le da otro lugar del mismo tipo a una reserva cuyo lugar apareció ocupado por otro auto
// (la incidencia de T21). Datos: { reservationId }.
//
// El lugar viejo no se toca: lo está ocupando un auto de verdad, y el sensor es el que manda sobre
// su estado. Lo único que cambia es a qué lugar apunta la reserva.
exports.reassignSpot = onCall(async (request) => {
  const uid = requireSignedIn(request);
  const db = getFirestore();
  const now = new Date();

  return db.runTransaction(async (tx) => {
    const { ref, reservation } = await ownReservation(tx, db, uid, (request.data || {}).reservationId);
    const lotRef = db.doc(`parkingLots/${reservation.parkingLotId}`);
    const lot = await tx.get(lotRef);
    if (!lot.exists) throw new HttpsError("not-found", "No existe ese estacionamiento.");

    const spots = (await tx.get(lotRef.collection("spots"))).docs.map(spotOf);
    const lotReservations = [
      ...(await tx.get(activeReservationsOfLot(db, reservation.parkingLotId))).docs.map(reservationOf),
      ...await heldOffersOfLot(tx, db, reservation.parkingLotId, now),
    ];
    const category = await categoryOf(tx, db, uid);

    const current = spots.find((s) => s.id === reservation.spotId);
    const problem = rules.reassignProblem(reservation, current, now);
    if (problem) throw rejection(problem);

    // Otro lugar del mismo tipo, sin contar el que quedó ocupado ni la reserva que se está moviendo.
    const spot = rules.pickSpot(spots.filter((s) => s.id !== reservation.spotId), {
      spotType: reservation.spotType,
      spotId: null,
      category,
      arrivesAt: reservation.arrivesAt,
      endsAt: reservation.endsAt,
      reservations: lotReservations.filter((r) => r.id !== reservation.id),
    }, now);
    if (!spot) throw rejection("NO_SPOTS");

    tx.update(ref, {
      spotId: spot.id,
      spotCode: spot.code,
      reassignedAt: FieldValue.serverTimestamp(),
    });
    const spotUpdate = { lastReservationAt: FieldValue.serverTimestamp() };
    if (rules.isInNowWindow(reservation.arrivesAt, now)) {
      spotUpdate.status = "RESERVED";
      spotUpdate.updatedAt = FieldValue.serverTimestamp();
    }
    tx.update(lotRef.collection("spots").doc(spot.id), spotUpdate);

    logger.info("Lugar reasignado", {
      reservationId: reservation.id, uid, from: reservation.spotId, to: spot.id,
    });
    return { spotId: spot.id, spotCode: spot.code };
  });
});

// Confirma la llegada. Datos: { reservationId, latitude, longitude }.
exports.checkIn = onCall(async (request) => {
  const uid = requireSignedIn(request);
  const data = request.data || {};
  const position = {
    latitude: requireNumber(data.latitude, "latitude"),
    longitude: requireNumber(data.longitude, "longitude"),
  };
  const db = getFirestore();
  const now = new Date();
  return db.runTransaction(async (tx) => {
    const { ref, reservation } = await ownReservation(tx, db, uid, data.reservationId);
    const lot = await tx.get(db.doc(`parkingLots/${reservation.parkingLotId}`));
    if (!lot.exists) throw new HttpsError("not-found", "No existe ese estacionamiento.");
    const location = lot.get("location");
    const distance = rules.distanceMeters(position,
      { latitude: location.latitude, longitude: location.longitude });
    const problem = rules.checkInProblem(reservation,
      { distance, radiusMeters: lot.get("geofenceRadiusMeters") || 0 }, now);
    if (problem) throw rejection(problem);

    tx.update(ref, { status: "IN_USE", checkedInAt: Timestamp.fromDate(now) });
    // `inUseBy` marca que el auto está por una reserva: el simulador de sensores no lo libera.
    tx.update(db.doc(`parkingLots/${reservation.parkingLotId}/spots/${reservation.spotId}`),
      { status: "OCCUPIED", inUseBy: reservation.id, updatedAt: FieldValue.serverTimestamp() });
    return { status: "IN_USE" };
  });
});

// Cierra la estadía. Datos: { reservationId }.
exports.checkOut = onCall(async (request) => {
  const uid = requireSignedIn(request);
  const db = getFirestore();
  const now = new Date();
  return db.runTransaction(async (tx) => {
    const { ref, reservation } = await ownReservation(tx, db, uid, (request.data || {}).reservationId);
    if (reservation.status !== "IN_USE") throw rejection("INVALID_STATUS");
    const lot = await tx.get(db.doc(`parkingLots/${reservation.parkingLotId}`));
    const release = await prepareSpotRelease(tx, db, reservation, now);
    const summary = rules.checkoutSummary(reservation.checkedInAt, lot.get("hourlyRate"), now);
    tx.update(ref, { status: "FINISHED", checkedOutAt: Timestamp.fromDate(now), ...summary });
    release();
    return { status: "FINISHED", ...summary };
  });
});

// Recorre la fila de un estacionamiento y la deja al día: le ofrece lugar al primero cuya franja
// entra en lo que hay libre, vence las entradas cuya franja ya pasó y recalcula las posiciones.
// Se llama cada vez que algo pudo cambiar la fila: un lugar que se libera, alguien que se anota,
// sale, rechaza o deja vencer su oferta. Es idempotente.
//
// Los avisos se mandan después de la transacción: si se reintenta, no salen dos veces.
// `messaging` reemplaza a FCM en los tests.
async function reconcileWaitlist(db, lotId, now, { messaging } = {}) {
  const offers = await db.runTransaction(async (tx) => {
    const lotRef = db.doc(`parkingLots/${lotId}`);
    const lot = await tx.get(lotRef);
    if (!lot.exists) return [];
    const spots = (await tx.get(lotRef.collection("spots"))).docs.map(spotOf);
    const active = (await tx.get(activeReservationsOfLot(db, lotId))).docs.map(reservationOf);
    const entries = (await tx.get(db.collection("waitlist")
      .where("parkingLotId", "==", lotId).where("status", "in", ["WAITING", "OFFERED"]))).docs
      .map(waitlistEntryOf);

    const holds = entries.filter((e) => e.status === "OFFERED" && !rules.offerExpired(e, now)).map(rules.holdOf);
    const waiting = entries.filter((e) => e.status === "WAITING");
    const { offers: planned, stale } = rules.planOffers(waiting, spots, [...active, ...holds], now);

    const expiresAt = new Date(now.getTime() + rules.WAITLIST_OFFER_MINUTES * 60 * 1000);
    for (const { entry, spot } of planned) {
      tx.update(db.doc(`waitlist/${entry.id}`), {
        status: "OFFERED",
        position: FieldValue.delete(),
        offer: {
          spotId: spot.id,
          spotCode: spot.code,
          offeredAt: Timestamp.fromDate(now),
          expiresAt: Timestamp.fromDate(expiresAt),
        },
      });
    }
    for (const entry of stale) {
      tx.update(db.doc(`waitlist/${entry.id}`), { status: "EXPIRED", position: FieldValue.delete() });
    }

    const gone = new Set([...planned.map((o) => o.entry.id), ...stale.map((e) => e.id)]);
    const stillWaiting = waiting.filter((e) => !gone.has(e.id));
    for (const [id, position] of rules.waitlistPositions(stillWaiting)) {
      const entry = stillWaiting.find((e) => e.id === id);
      if (entry.position !== position) tx.update(db.doc(`waitlist/${id}`), { position });
    }
    return planned.map(({ entry, spot }) => ({ entry, spot, lotName: lot.get("name") }));
  });

  for (const { entry, spot, lotName } of offers) {
    logger.info("Lugar ofrecido de la lista de espera", { entryId: entry.id, uid: entry.userId, lotId, spotId: spot.id });
    await notifications.sendNotice(db, entry.userId, "waitlist_offer", entry.id, {
      lotName, spotCode: spot.code, arrivesAt: entry.arrivesAt, endsAt: entry.endsAt,
    }, { messaging }).catch((e) => logger.warn("No se pudo mandar el aviso de la oferta", e));
  }
  return offers.length;
}

// Anota al usuario en la lista de espera. Datos: { parkingLotId, arrivesAt, durationMinutes, spotType }.
exports.joinWaitlist = onCall(async (request) => {
  const uid = requireBookingRole(request);
  const data = request.data || {};
  const lotId = requireString(data.parkingLotId, "parkingLotId");
  const arrivesAt = requireDate(data.arrivesAt, "arrivesAt");
  const durationMinutes = requireNumber(data.durationMinutes, "durationMinutes");
  if (!rules.SPOT_TYPES.includes(data.spotType)) throw invalid("Falta spotType.");

  const db = getFirestore();
  const now = new Date();
  const result = await db.runTransaction(async (tx) => {
    const lot = await tx.get(db.doc(`parkingLots/${lotId}`));
    if (!lot.exists) throw new HttpsError("not-found", "No existe ese estacionamiento.");
    const category = await categoryOf(tx, db, uid);
    // Los mismos pasos 1 a 5 que al reservar; el límite de reservas activas no aplica.
    const problem = rules.requestProblem({
      arrivesAt, durationMinutes, spotType: data.spotType, category,
      activeReservations: 0, schedule: lot.get("schedule"),
    }, now);
    if (problem) throw rejection(problem);

    const queued = (await tx.get(db.collection("waitlist")
      .where("parkingLotId", "==", lotId).where("status", "in", ["WAITING", "OFFERED"]))).docs.map(waitlistEntryOf);
    const sameSlot = (e) => e.arrivesAt.getTime() === arrivesAt.getTime() && e.durationMinutes === durationMinutes;
    // Con una oferta pendiente para esa franja tampoco: ya está en la lista.
    if (queued.some((e) => e.userId === uid && sameSlot(e))) throw rejection("ALREADY_IN_WAITLIST");
    const waiting = queued.filter((e) => e.status === "WAITING");

    const ref = db.collection("waitlist").doc();
    const requestedAt = new Date();
    const entry = {
      id: ref.id,
      userId: uid,
      parkingLotId: lotId,
      spotType: data.spotType,
      arrivesAt,
      durationMinutes,
      endsAt: rules.endsAt(arrivesAt, durationMinutes),
      category,
      requestedAt,
      status: "WAITING",
    };
    // La posición se guarda en cada entrada para que la app la muestre al día: sólo puede leer las
    // suyas, así que no la puede calcular. Con categoría de prioridad se puede quedar adelante de
    // otros, así que se recalculan todas.
    const positions = rules.waitlistPositions([...waiting, entry]);
    tx.set(ref, {
      userId: uid,
      parkingLotId: lotId,
      spotType: data.spotType,
      arrivesAt: Timestamp.fromDate(arrivesAt),
      durationMinutes,
      endsAt: Timestamp.fromDate(entry.endsAt),
      category,
      requestedAt: Timestamp.fromDate(requestedAt),
      status: "WAITING",
      position: positions.get(ref.id),
    });
    for (const other of waiting) {
      if (other.position !== positions.get(other.id)) {
        tx.update(db.doc(`waitlist/${other.id}`), { position: positions.get(other.id) });
      }
    }
    return { entryId: ref.id, position: positions.get(ref.id) };
  });

  // Si entre el "no hay lugar" y anotarse se liberó uno, la oferta sale ya.
  await reconcileWaitlist(db, lotId, now);
  return result;
});

// Lee una entrada propia de la lista de espera.
async function ownWaitlistEntry(tx, db, uid, entryId) {
  const ref = db.doc(`waitlist/${requireString(entryId, "entryId")}`);
  const doc = await tx.get(ref);
  if (!doc.exists) throw new HttpsError("not-found", "No existe esa entrada.");
  if (doc.get("userId") !== uid) throw new HttpsError("permission-denied", "La entrada no es tuya.");
  return { ref, entry: waitlistEntryOf(doc) };
}

// Saca al usuario de la lista de espera. Datos: { entryId }. Si tenía una oferta, la suelta.
exports.leaveWaitlist = onCall(async (request) => {
  const uid = requireSignedIn(request);
  const db = getFirestore();
  const now = new Date();
  const { lotId } = await db.runTransaction(async (tx) => {
    const { ref, entry } = await ownWaitlistEntry(tx, db, uid, (request.data || {}).entryId);
    if (["WAITING", "OFFERED"].includes(entry.status)) {
      tx.update(ref, { status: "LEFT", position: FieldValue.delete() });
    }
    return { lotId: entry.parkingLotId };
  });
  // Los que estaban detrás suben un lugar, y si soltó una oferta le toca al siguiente.
  await reconcileWaitlist(db, lotId, now);
  return { status: "LEFT" };
});

// Acepta la oferta de la lista de espera: la convierte en una reserva confirmada con esa franja,
// igual que si hubiera reservado. Datos: { entryId }.
exports.acceptWaitlistOffer = onCall(async (request) => {
  const uid = requireBookingRole(request);
  const db = getFirestore();
  const now = new Date();
  return db.runTransaction(async (tx) => {
    const { ref, entry } = await ownWaitlistEntry(tx, db, uid, (request.data || {}).entryId);
    const offerProblem = rules.offerProblem(entry, now);
    if (offerProblem) throw rejection(offerProblem);

    const lotRef = db.doc(`parkingLots/${entry.parkingLotId}`);
    const lot = await tx.get(lotRef);
    if (!lot.exists) throw new HttpsError("not-found", "No existe ese estacionamiento.");
    const spots = (await tx.get(lotRef.collection("spots"))).docs.map(spotOf);
    const lotReservations = [
      ...(await tx.get(activeReservationsOfLot(db, entry.parkingLotId))).docs.map(reservationOf),
      ...await heldOffersOfLot(tx, db, entry.parkingLotId, now, entry.id),
    ];
    const userActive = await tx.get(db.collection("reservations")
      .where("userId", "==", uid).where("status", "in", rules.ACTIVE_STATUSES));

    // Las mismas validaciones que al reservar: entre que se anotó y ahora pudo cambiar algo, por
    // ejemplo que ya tenga dos reservas activas.
    const problem = rules.requestProblem({
      arrivesAt: entry.arrivesAt, durationMinutes: entry.durationMinutes, spotType: entry.spotType,
      category: entry.category, activeReservations: userActive.size, schedule: lot.get("schedule"),
    }, now);
    if (problem) throw rejection(problem);

    const spot = rules.pickSpot(spots, {
      spotType: entry.spotType, spotId: entry.offer.spotId, category: entry.category,
      arrivesAt: entry.arrivesAt, endsAt: entry.endsAt, reservations: lotReservations,
    }, now);
    if (!spot) throw rejection("NO_SPOTS");

    const toleranceUntil = rules.toleranceUntil(entry.arrivesAt, lot.get("toleranceMinutes"));
    const reservationRef = db.collection("reservations").doc();
    tx.set(reservationRef, {
      userId: uid,
      parkingLotId: entry.parkingLotId,
      spotId: spot.id,
      spotCode: spot.code,
      spotType: spot.type,
      arrivesAt: Timestamp.fromDate(entry.arrivesAt),
      durationMinutes: entry.durationMinutes,
      endsAt: Timestamp.fromDate(entry.endsAt),
      toleranceUntil: Timestamp.fromDate(toleranceUntil),
      status: "CONFIRMED",
      fromWaitlist: entry.id,
      createdAt: FieldValue.serverTimestamp(),
    });
    const spotUpdate = { lastReservationAt: FieldValue.serverTimestamp() };
    if (rules.isInNowWindow(entry.arrivesAt, now)) {
      spotUpdate.status = "RESERVED";
      spotUpdate.updatedAt = FieldValue.serverTimestamp();
    }
    tx.update(lotRef.collection("spots").doc(spot.id), spotUpdate);
    tx.update(ref, { status: "ACCEPTED", reservationId: reservationRef.id });

    logger.info("Oferta de la lista de espera aceptada", { entryId: entry.id, uid, reservationId: reservationRef.id });
    return {
      reservationId: reservationRef.id,
      spotId: spot.id,
      spotCode: spot.code,
      arrivesAt: entry.arrivesAt.toISOString(),
      endsAt: entry.endsAt.toISOString(),
      toleranceUntil: toleranceUntil.toISOString(),
    };
  });
});

// Rechaza la oferta: el lugar pasa al siguiente de la fila. Datos: { entryId }.
exports.declineWaitlistOffer = onCall(async (request) => {
  const uid = requireSignedIn(request);
  const db = getFirestore();
  const now = new Date();
  const { lotId } = await db.runTransaction(async (tx) => {
    const { ref, entry } = await ownWaitlistEntry(tx, db, uid, (request.data || {}).entryId);
    if (entry.status !== "OFFERED") throw rejection("NO_OFFER");
    tx.update(ref, { status: "DECLINED" });
    return { lotId: entry.parkingLotId };
  });
  await reconcileWaitlist(db, lotId, now);
  return { status: "DECLINED" };
});

// Cuando una reserva activa deja de serlo (se cancela, termina o vence), su lugar puede servirle a
// alguien de la lista de espera. Un trigger y no una llamada desde cada function: así ningún camino
// que libera un lugar se olvida de avisar.
exports.offerReleasedSpots = onDocumentUpdated("reservations/{reservationId}", async (event) => {
  const before = event.data.before.data();
  const after = event.data.after.data();
  if (!rules.ACTIVE_STATUSES.includes(before.status) || rules.ACTIVE_STATUSES.includes(after.status)) return;
  await reconcileWaitlist(getFirestore(), after.parkingLotId, new Date());
});

// Lo que hace el proceso de cada minuto, con la hora como parámetro para poder probarlo:
// vence las reservas sin check-in, reserva los lugares de las que entraron en su ventana de "ahora"
// y manda los avisos que correspondan (ver notifications.js). `messaging` reemplaza a FCM en los tests.
// Es idempotente: correrlo dos veces seguidas no cambia nada ni avisa de nuevo la segunda vez.
async function processReservationsAt(db, now, { messaging } = {}) {
  let expired = 0;
  let reserved = 0;

  const overdue = await db.collection("reservations")
    .where("status", "==", "CONFIRMED")
    .where("toleranceUntil", "<", Timestamp.fromDate(now)).get();
  for (const doc of overdue.docs) {
    const reservation = await db.runTransaction(async (tx) => {
      const current = reservationOf(await tx.get(doc.ref));
      if (!rules.isExpired(current, now)) return null;
      const release = await prepareSpotRelease(tx, db, current, now);
      tx.update(doc.ref, { status: "EXPIRED", expiredAt: FieldValue.serverTimestamp() });
      release();
      return current;
    });
    if (!reservation) continue;
    expired++;
    // Una sola vez: la transacción pasa la reserva a EXPIRED una sola vez.
    await notifyQuietly(db, reservation, "reservation_expired", messaging);
  }

  const windowEnd = new Date(now.getTime() + rules.NOW_WINDOW_MINUTES * 60 * 1000);
  const upcoming = await db.collection("reservations")
    .where("status", "==", "CONFIRMED")
    .where("arrivesAt", "<=", Timestamp.fromDate(windowEnd)).get();
  for (const doc of upcoming.docs) {
    const reservation = reservationOf(doc);
    if (rules.isExpired(reservation, now)) continue;
    const spotRef = db.doc(`parkingLots/${reservation.parkingLotId}/spots/${reservation.spotId}`);
    await db.runTransaction(async (tx) => {
      const spot = await tx.get(spotRef);
      if (!spot.exists || spot.get("status") !== "FREE") return;
      tx.update(spotRef, { status: "RESERVED", updatedAt: FieldValue.serverTimestamp() });
      reserved++;
    });
  }

  // Ofertas de la lista de espera que nadie contestó a tiempo: el lugar pasa al siguiente de la fila.
  const missed = await db.collection("waitlist")
    .where("status", "==", "OFFERED")
    .where("offer.expiresAt", "<", Timestamp.fromDate(now)).get();
  const lotsToReconcile = new Set();
  let offersMissed = 0;
  for (const doc of missed.docs) {
    await db.runTransaction(async (tx) => {
      const entry = waitlistEntryOf(await tx.get(doc.ref));
      if (!rules.offerExpired(entry, now)) return;
      tx.update(doc.ref, { status: "DECLINED", missed: true });
      lotsToReconcile.add(entry.parkingLotId);
      offersMissed++;
    });
  }
  for (const lotId of lotsToReconcile) await reconcileWaitlist(db, lotId, now, { messaging });


  const notified = await sendDueNotices(db, now, messaging);
  return { expired, reserved, offersMissed, notified };
}

// Avisos que dependen de la hora: reserva por vencer, franja por terminar y estadía prolongada.
// Antes de mandar cada uno se marca en la reserva (`notices.<clave>`) dentro de una transacción:
// si dos corridas se pisan, sólo una lo marca y lo manda. Si el envío falla, no se reintenta:
// es preferible perder un aviso a mandarlo repetido.
async function sendDueNotices(db, now, messaging) {
  const expiringFrom = new Date(now.getTime() + notifications.EXPIRING_NOTICE_MINUTES * 60 * 1000);
  const endingFrom = new Date(now.getTime() + notifications.ENDING_NOTICE_MINUTES * 60 * 1000);
  const [confirmed, inUse] = await Promise.all([
    db.collection("reservations").where("status", "==", "CONFIRMED")
      .where("toleranceUntil", "<=", Timestamp.fromDate(expiringFrom)).get(),
    db.collection("reservations").where("status", "==", "IN_USE")
      .where("endsAt", "<=", Timestamp.fromDate(endingFrom)).get(),
  ]);

  let notified = 0;
  for (const doc of [...confirmed.docs, ...inUse.docs]) {
    if (!notifications.dueNotice(reservationOf(doc), now)) continue;
    const claimed = await db.runTransaction(async (tx) => {
      const current = reservationOf(await tx.get(doc.ref));
      const type = notifications.dueNotice(current, now);
      if (!type) return null;
      tx.update(doc.ref, { [`notices.${notifications.NOTICES[type].notice}`]: Timestamp.fromDate(now) });
      return { reservation: current, type };
    });
    if (!claimed) continue;
    await notifyQuietly(db, claimed.reservation, claimed.type, messaging);
    notified++;
  }
  return notified;
}

// Un aviso que no se pudo mandar queda en el log, pero no frena el resto del proceso.
async function notifyQuietly(db, reservation, type, messaging) {
  try {
    await notifications.sendNotice(db, reservation.userId, type, reservation.id, reservation, { messaging });
  } catch (error) {
    logger.error("No se pudo mandar el aviso", { type, reservationId: reservation.id, error: error.message });
  }
}

exports.processReservations = onSchedule("every 1 minutes", async () => {
  const result = await processReservationsAt(getFirestore(), new Date());
  logger.info("Proceso de reservas", result);
});

exports.processReservationsAt = processReservationsAt;
exports.reconcileWaitlist = reconcileWaitlist;
