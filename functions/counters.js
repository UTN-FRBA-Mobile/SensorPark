// Contadores de lugares de un estacionamiento: capacidad y libres, en total y por tipo de lugar.
// Se guardan en el documento del estacionamiento para que el mapa no tenga que leer cada lugar.

// Nombres de los campos que calcula el backend. firestore.rules no deja que la app los escriba.
const COUNTER_FIELDS = ["capacity", "freeSpots", "capacityByType", "freeSpotsByType"];

// Cuenta una lista de lugares ({ type, status }). Un lugar cuenta en la capacidad siempre, y como
// libre sólo si está FREE.
function countSpots(spots) {
  const counters = { capacity: 0, freeSpots: 0, capacityByType: {}, freeSpotsByType: {} };
  const add = (map, key) => { if (key) map[key] = (map[key] || 0) + 1; };

  for (const spot of spots) {
    counters.capacity++;
    add(counters.capacityByType, spot.type);
    if (spot.status === "FREE") {
      counters.freeSpots++;
      add(counters.freeSpotsByType, spot.type);
    }
  }
  return counters;
}

// Vuelve a contar todos los lugares del estacionamiento y guarda el resultado. Recontar en vez de
// sumar o restar 1 hace que el resultado no dependa de cuántas veces llegue el mismo evento ni del
// orden: los contadores nunca se desfasan. Un estacionamiento tiene decenas de lugares, así que
// leerlos todos es barato. Devuelve los contadores, o null si el estacionamiento ya no existe.
async function recountParkingLot(db, lotId) {
  const lotRef = db.doc(`parkingLots/${lotId}`);
  return db.runTransaction(async (tx) => {
    const lot = await tx.get(lotRef);
    if (!lot.exists) return null;
    const spots = await tx.get(lotRef.collection("spots"));
    const counters = countSpots(spots.docs.map((d) => d.data()));
    tx.update(lotRef, counters);
    return counters;
  });
}

// Sólo los cambios de estado o de tipo mueven un contador; también crear o borrar un lugar.
function affectsCounters(before, after) {
  if (!before || !after) return true;
  return before.status !== after.status || before.type !== after.type;
}

module.exports = { COUNTER_FIELDS, countSpots, recountParkingLot, affectsCounters };
