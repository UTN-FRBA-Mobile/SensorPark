// Datos semilla: los mismos estacionamientos y lugares que usan los datos falsos de la app.
// Los ids son fijos, así que se puede cargar varias veces sin duplicar; volver a cargarla
// deja todos los lugares en FREE y borra los que no son de la semilla.
const { FieldValue, GeoPoint } = require("firebase-admin/firestore");
const { countSpots } = require("./counters");

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

// Cada estacionamiento tiene 14 lugares, numerados del 01 al 14: 42 lugares en total.
// STANDARD: común. PRIORITY: preferencial. MOTORCYCLE: moto. EV_CHARGING: con carga eléctrica.
const SPOT_TYPES = [
  "STANDARD", "STANDARD", "STANDARD", "STANDARD", "STANDARD", "STANDARD", "STANDARD", "STANDARD",
  "PRIORITY", "PRIORITY", "MOTORCYCLE", "MOTORCYCLE", "EV_CHARGING", "EV_CHARGING",
];

const SPOTS = SPOT_TYPES.map((type, i) => {
  const code = String(i + 1).padStart(2, "0");
  return { id: code, code, type };
});

// Carga la semilla en `db`. Conserva los administradores que ya tuviera cada estacionamiento.
async function seed(db) {
  let loadedSpots = 0;

  for (const lot of PARKING_LOTS) {
    const ref = db.doc(`parkingLots/${lot.id}`);
    const current = await ref.get();
    const existing = await ref.collection("spots").get();
    const spots = SPOTS.map((s) => ({ ...s, status: "FREE" }));
    const ids = new Set(spots.map((s) => s.id));

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
      // La function de contadores los recalcula igual al ver los lugares; se escriben acá para que
      // el estacionamiento no quede un momento sin ellos.
      ...countSpots(spots),
    });
    existing.docs.filter((d) => !ids.has(d.id)).forEach((d) => batch.delete(d.ref));
    spots.forEach((s) => {
      batch.set(ref.collection("spots").doc(s.id), {
        code: s.code,
        type: s.type,
        status: s.status,
        updatedAt: FieldValue.serverTimestamp(),
      });
    });
    await batch.commit();
    loadedSpots += spots.length;
  }

  return { parkingLots: PARKING_LOTS.length, spots: loadedSpots };
}

module.exports = { seed, PARKING_LOTS, SPOTS };
