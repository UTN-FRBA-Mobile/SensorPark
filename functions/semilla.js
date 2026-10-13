// Datos semilla: los mismos estacionamientos, niveles y lugares que usan los datos falsos de la app.
// Los ids son fijos, así que se puede cargar varias veces sin duplicar; volver a cargarla
// deja todos los lugares en LIBRE.
const { FieldValue, GeoPoint } = require("firebase-admin/firestore");

const ESTACIONAMIENTOS = [
  {
    id: "utn-medrano",
    nombre: "UTN Medrano",
    direccion: "Av. Medrano 951, CABA",
    lat: -34.5984,
    lng: -58.4200,
    tarifaHora: 2500,
  },
  {
    id: "abasto",
    nombre: "Playa Abasto",
    direccion: "Av. Corrientes 3247, CABA",
    lat: -34.6037,
    lng: -58.4108,
    tarifaHora: 3200,
  },
  {
    id: "parque-centenario",
    nombre: "Cocheras Parque Centenario",
    direccion: "Av. Díaz Vélez 4800, CABA",
    lat: -34.6065,
    lng: -58.4355,
    tarifaHora: 2800,
  },
];

// Cada estacionamiento tiene dos niveles de 7 lugares: 42 lugares en total.
const NIVELES = [
  { id: "n1", nombre: "Planta baja", letra: "A" },
  { id: "n2", nombre: "Subsuelo", letra: "B" },
];
const TIPOS_POR_NIVEL = ["COMUN", "COMUN", "COMUN", "COMUN", "PREFERENCIAL", "MOTO", "CARGA_ELECTRICA"];

function lugaresDe(nivel) {
  return TIPOS_POR_NIVEL.map((tipo, i) => {
    const codigo = `${nivel.letra}-${String(i + 1).padStart(2, "0")}`;
    return { id: codigo, codigo, nivelId: nivel.id, tipo };
  });
}

function contar(lugares) {
  const porTipo = {};
  lugares.forEach((l) => { porTipo[l.tipo] = (porTipo[l.tipo] || 0) + 1; });
  return porTipo;
}

// Carga la semilla en `db`. Conserva los administradores que ya tuviera cada estacionamiento.
async function sembrar(db) {
  let lugaresCargados = 0;

  for (const e of ESTACIONAMIENTOS) {
    const ref = db.doc(`estacionamientos/${e.id}`);
    const actual = await ref.get();
    const lugares = NIVELES.flatMap(lugaresDe);

    const niveles = {};
    NIVELES.forEach((n) => {
      niveles[n.id] = { nombre: n.nombre, capacidad: TIPOS_POR_NIVEL.length, libres: TIPOS_POR_NIVEL.length };
    });

    const lote = db.batch();
    lote.set(ref, {
      nombre: e.nombre,
      direccion: e.direccion,
      ubicacion: new GeoPoint(e.lat, e.lng),
      horario: { apertura: "06:00", cierre: "23:00" },
      toleranciaMinutos: 15,
      radioGeocercaMetros: 300,
      tarifaHora: e.tarifaHora,
      adminUids: (actual.exists && actual.get("adminUids")) || [],
      capacidad: lugares.length,
      libres: lugares.length,
      libresPorTipo: contar(lugares),
      niveles,
    });
    lugares.forEach((l) => {
      lote.set(ref.collection("lugares").doc(l.id), {
        codigo: l.codigo,
        nivelId: l.nivelId,
        tipo: l.tipo,
        estado: "LIBRE",
        actualizadoEn: FieldValue.serverTimestamp(),
      });
    });
    await lote.commit();
    lugaresCargados += lugares.length;
  }

  return { estacionamientos: ESTACIONAMIENTOS.length, lugares: lugaresCargados };
}

module.exports = { sembrar, ESTACIONAMIENTOS, NIVELES, TIPOS_POR_NIVEL };
