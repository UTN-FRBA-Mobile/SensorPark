// Functions del panel del administrador de estacionamiento (T22). Lo que es una edición simple
// (datos del estacionamiento, alta o cambio de tipo de un lugar) la app lo escribe directo en
// Firestore y lo validan las reglas. Acá queda lo que las reglas no pueden mirar: que un lugar no
// tenga reservas activas ni una oferta de la lista de espera antes de sacarlo de servicio o borrarlo.
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { getFirestore } = require("firebase-admin/firestore");
const rules = require("./reservationRules");

const MESSAGES = {
  SPOT_HAS_RESERVATION: "El lugar tiene una reserva activa o una oferta de la lista de espera: no se puede cambiar ahora.",
};

function rejection(reason) {
  return new HttpsError("failed-precondition", MESSAGES[reason] || reason, { reason });
}

function requireString(value, name) {
  if (typeof value !== "string" || value.length === 0) throw new HttpsError("invalid-argument", `Falta ${name}.`);
  return value;
}

// Sólo un ADMIN, o un PARKING_ADMIN que figure en adminUids del estacionamiento.
function requireManager(request, lot) {
  if (!request.auth) throw new HttpsError("unauthenticated", "Hay que iniciar sesión.");
  const { role } = request.auth.token;
  if (role === "ADMIN") return;
  if (role === "PARKING_ADMIN" && (lot.adminUids || []).includes(request.auth.uid)) return;
  throw new HttpsError("permission-denied", "Sólo el administrador de este estacionamiento puede hacerlo.");
}

// Lee el estacionamiento y el lugar, y verifica que el que llama lo administre.
async function managedSpot(tx, db, request) {
  const data = request.data || {};
  const lotId = requireString(data.parkingLotId, "parkingLotId");
  const spotId = requireString(data.spotId, "spotId");
  const lotRef = db.doc(`parkingLots/${lotId}`);
  const spotRef = lotRef.collection("spots").doc(spotId);
  const [lot, spot] = await Promise.all([tx.get(lotRef), tx.get(spotRef)]);
  if (!lot.exists) throw new HttpsError("not-found", "No existe ese estacionamiento.");
  requireManager(request, lot.data());
  if (!spot.exists) throw new HttpsError("not-found", "No existe ese lugar.");
  return { lotId, spotId, spotRef, spot: spot.data() };
}

// Un lugar está comprometido si tiene una reserva activa o se lo está guardando una oferta.
async function isCommitted(tx, db, lotId, spotId) {
  const [active, offered] = await Promise.all([
    tx.get(db.collection("reservations")
      .where("parkingLotId", "==", lotId)
      .where("spotId", "==", spotId)
      .where("status", "in", rules.ACTIVE_STATUSES)),
    tx.get(db.collection("waitlist")
      .where("parkingLotId", "==", lotId)
      .where("status", "==", "OFFERED")),
  ]);
  return !active.empty || offered.docs.some((d) => (d.get("offer") || {}).spotId === spotId);
}

// Saca un lugar de servicio o lo devuelve. Datos: { parkingLotId, spotId, inService }.
exports.setSpotService = onCall(async (request) => {
  const db = getFirestore();
  const inService = (request.data || {}).inService;
  if (typeof inService !== "boolean") throw new HttpsError("invalid-argument", "Falta inService.");
  return db.runTransaction(async (tx) => {
    const { lotId, spotId, spotRef, spot } = await managedSpot(tx, db, request);
    if (!inService && spot.status !== "OUT_OF_SERVICE" && await isCommitted(tx, db, lotId, spotId)) {
      throw rejection("SPOT_HAS_RESERVATION");
    }
    // Al devolverlo queda libre; si lo había roto el simulador, deja de figurar como avería simulada.
    const status = inService ? "FREE" : "OUT_OF_SERVICE";
    tx.update(spotRef, { status, simulatedFault: false });
    return { status };
  });
});

// Borra un lugar. Datos: { parkingLotId, spotId }. La capacidad la recalcula updateFreeSpotCounters.
exports.deleteSpot = onCall(async (request) => {
  const db = getFirestore();
  return db.runTransaction(async (tx) => {
    const { lotId, spotId, spotRef } = await managedSpot(tx, db, request);
    if (await isCommitted(tx, db, lotId, spotId)) throw rejection("SPOT_HAS_RESERVATION");
    tx.delete(spotRef);
    return { deleted: spotId };
  });
});
