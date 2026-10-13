// Datos semilla: los mismos estacionamientos, niveles y lugares que usan los datos falsos de la app.
// Los ids son fijos, así que se puede cargar varias veces sin duplicar; volver a cargarla
// deja todos los lugares en FREE.
const { FieldValue, GeoPoint } = require("firebase-admin/firestore");

const PARKING_LOTS = [
  {
    id: "utn-medrano",
    name: "UTN Medrano",
    address: "Av. Medrano 951, CABA",
    lat: -34.5984,
    lng: -58.4200,
    hourlyRate: 2500,
  },
  {
    id: "abasto",
    name: "Playa Abasto",
    address: "Av. Corrientes 3247, CABA",
    lat: -34.6037,
    lng: -58.4108,
    hourlyRate: 3200,
  },
  {
    id: "parque-centenario",
    name: "Cocheras Parque Centenario",
    address: "Av. Díaz Vélez 4800, CABA",
    lat: -34.6065,
    lng: -58.4355,
    hourlyRate: 2800,
  },
];

// Cada estacionamiento tiene dos niveles de 7 lugares: 42 lugares en total.
const LEVELS = [
  { id: "level1", name: "Planta baja", letter: "A" },
  { id: "level2", name: "Subsuelo", letter: "B" },
];
// STANDARD: común. PRIORITY: preferencial. MOTORCYCLE: moto. EV_CHARGING: con carga eléctrica.
const SPOT_TYPES_PER_LEVEL = ["STANDARD", "STANDARD", "STANDARD", "STANDARD", "PRIORITY", "MOTORCYCLE", "EV_CHARGING"];

function spotsOf(level) {
  return SPOT_TYPES_PER_LEVEL.map((type, i) => {
    const code = `${level.letter}-${String(i + 1).padStart(2, "0")}`;
    return { id: code, code, levelId: level.id, type };
  });
}

function countByType(spots) {
  const byType = {};
  spots.forEach((s) => { byType[s.type] = (byType[s.type] || 0) + 1; });
  return byType;
}

// Carga la semilla en `db`. Conserva los administradores que ya tuviera cada estacionamiento.
async function seed(db) {
  let loadedSpots = 0;

  for (const lot of PARKING_LOTS) {
    const ref = db.doc(`parkingLots/${lot.id}`);
    const current = await ref.get();
    const spots = LEVELS.flatMap(spotsOf);

    const levels = {};
    LEVELS.forEach((l) => {
      levels[l.id] = { name: l.name, capacity: SPOT_TYPES_PER_LEVEL.length, freeSpots: SPOT_TYPES_PER_LEVEL.length };
    });

    const batch = db.batch();
    batch.set(ref, {
      name: lot.name,
      address: lot.address,
      location: new GeoPoint(lot.lat, lot.lng),
      schedule: { opensAt: "06:00", closesAt: "23:00" },
      toleranceMinutes: 15,
      geofenceRadiusMeters: 300,
      hourlyRate: lot.hourlyRate,
      adminUids: (current.exists && current.get("adminUids")) || [],
      capacity: spots.length,
      freeSpots: spots.length,
      freeSpotsByType: countByType(spots),
      levels,
    });
    spots.forEach((s) => {
      batch.set(ref.collection("spots").doc(s.id), {
        code: s.code,
        levelId: s.levelId,
        type: s.type,
        status: "FREE",
        updatedAt: FieldValue.serverTimestamp(),
      });
    });
    await batch.commit();
    loadedSpots += spots.length;
  }

  return { parkingLots: PARKING_LOTS.length, spots: loadedSpots };
}

module.exports = { seed, PARKING_LOTS, LEVELS, SPOT_TYPES_PER_LEVEL };
