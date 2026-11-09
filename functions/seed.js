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

// Los dos preferenciales, uno por subcategoría: sólo los reserva quien declaró esa categoría.
const PRIORITY_CATEGORIES = { "09": "PREGNANT", "10": "REDUCED_MOBILITY" };

// Imagen PNG (320x200) de un plano de estacionamiento con dársenas arriba y abajo y calle central.
const SAMPLE_PLAN_BASE64 =
  "iVBORw0KGgoAAAANSUhEUgAAAUAAAADICAIAAAAWZq/8AAADYElEQVR42u3VoQqAQBAGYd//icwmo3arxahw5YrRqojouh9MPhj/HWzabgQQlMYnAAQM4NWAt1J/Tx5TvhlMBcyXr4ANzJevgA3Ml6+ADcxXwAbmy1fABubLV8AG5stXwA6ar4ANzJevgA3Ml6+ADcxXwALmy1fABubLV8AG5stXwA6ar4ANzJevgA3Ml6+ADcyXr4AdNF8BG5gvXwEbmC9fARuYr4ANzJevgA3Ml6+ADcyXr4AdNF8BG5gvXwEbmC9fARuYr4AFzJevgA3Ml6+ADcyXr4ANzFfABubLV8AG5stXwAbmy1fADpqvgA3Ml6+ADcyXr4ANzFfAAubLV8AG5stXwAbmy1fABuYrYAPz5StgA/PlK2AD8+UrYAfNV8AG5stXwAbmy1fABuYrYAHz5StgA/PlK2AD8+UrYAfNV8AG5stXwAbmy1fABubLV8AOmq+ADcyXr4ANzJevgA3MV8AG5stXwAbmy1fAAAQMCBiAgAE8FDCAcBwBz8sKIAQCBgQMQMAABAwIGICAAQgYgIABAQMQMAABAwIWMCBgAAIGIGBAwAAEDEDAAAQMCBiAgAEIGBCwgAEBAxAwAAEDAr5PP0yX8L73M7zvDwz4AwsYEDAAAQMQMCBgAAIGIGAAAgYEDEDAAAQMCFjAgIABCBiAgAEBAxAwAAEDEDAgYAACBiBgQMAABAxAwAAEDKQIGEA4joC3UgGEQMCAgAEIGICAz2pnm5mvgA3Ml6+ADcyXr4ANzFfAAubLV8AG5stXwAbmy1fABuYrYAPz5StgA/PlK2AD8+UrYAfNV8AG5stXwAbmy1fABuYrYAHz5StgA/PlK2AD8+UrYAPzFbCB+fIVsIH58hWwgfnyFbCD5itgA/PlK2AD8+UrYAPzFbCA+fIVsIH58hWwgfnyFbCD5itgA/PlK2AD8+UrYAPz5StgB81XwAbmy1fABubLV8AG5itgA/PlK2AD8+UrYAPz5StgB81XwAbmy1fABubLV8AG5itgAfPlK2AD8+UrYAPz5StgA/MVsIH58hWwgfnyFbCB+fIVsIPmK2AD8+UrYAPz5StgA/MVsID58hWwgfnyFbCB+fIVsIPmK2AD8+UrYAPz5StgA/PlK2AHzVfABubL91sBAwiHgAEBA3iDHX5l9gOPWdpsAAAAAElFTkSuQmCC";

// Plano de ejemplo cargado en UTN Medrano (un único plano por estacionamiento). Los demás
// estacionamientos quedan sin plano para mostrar el aviso informativo y la vista en grilla.
const SEED_LEVELS = {
  "utn-medrano": [
    { id: "main", name: "Plano", order: 1, imageBase64: SAMPLE_PLAN_BASE64, imageWidth: 320, imageHeight: 200 },
  ],
};

const SEED_SPOT_POSITIONS = {
  "utn-medrano": {
    "01": { levelId: "main", planX: 0.10, planY: 0.22 },
    "02": { levelId: "main", planX: 0.23, planY: 0.22 },
    "03": { levelId: "main", planX: 0.36, planY: 0.22 },
    "04": { levelId: "main", planX: 0.49, planY: 0.22 },
    "05": { levelId: "main", planX: 0.62, planY: 0.22 },
    "06": { levelId: "main", planX: 0.75, planY: 0.22 },
    "07": { levelId: "main", planX: 0.88, planY: 0.22 },
    "08": { levelId: "main", planX: 0.10, planY: 0.78 },
    "09": { levelId: "main", planX: 0.23, planY: 0.78 },
    "10": { levelId: "main", planX: 0.36, planY: 0.78 },
    "11": { levelId: "main", planX: 0.49, planY: 0.78 },
    "12": { levelId: "main", planX: 0.62, planY: 0.78 },
    "13": { levelId: "main", planX: 0.75, planY: 0.78 },
    "14": { levelId: "main", planX: 0.88, planY: 0.78 },
  },
};

const SPOTS = SPOT_TYPES.map((type, i) => {
  const code = String(i + 1).padStart(2, "0");
  const spot = { id: code, code, type };
  if (PRIORITY_CATEGORIES[code]) spot.priorityCategory = PRIORITY_CATEGORIES[code];
  return spot;
});

// Carga la semilla en `db`. Conserva los administradores que ya tuviera cada estacionamiento.
async function seed(db) {
  let loadedSpots = 0;

  for (const lot of PARKING_LOTS) {
    const ref = db.doc(`parkingLots/${lot.id}`);
    const current = await ref.get();
    const existing = await ref.collection("spots").get();
    const existingLevels = await ref.collection("levels").get();
    const spots = SPOTS.map((s) => ({ ...s, status: "FREE" }));
    const ids = new Set(spots.map((s) => s.id));
    const lotLevels = SEED_LEVELS[lot.id] || [];
    const levelIds = new Set(lotLevels.map((l) => l.id));
    const lotPositions = SEED_SPOT_POSITIONS[lot.id] || {};

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
    existingLevels.docs.filter((d) => !levelIds.has(d.id)).forEach((d) => batch.delete(d.ref));
    lotLevels.forEach((level) => {
      batch.set(ref.collection("levels").doc(level.id), {
        name: level.name,
        order: level.order,
        imageBase64: level.imageBase64,
        imageWidth: level.imageWidth,
        imageHeight: level.imageHeight,
      });
    });
    spots.forEach((s) => {
      const pos = lotPositions[s.id];
      batch.set(ref.collection("spots").doc(s.id), {
        code: s.code,
        type: s.type,
        ...(s.priorityCategory ? { priorityCategory: s.priorityCategory } : {}),
        ...(pos ? { levelId: pos.levelId, planX: pos.planX, planY: pos.planY } : {}),
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
