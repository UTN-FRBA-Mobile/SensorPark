// Simulador de sensores. Reemplaza a los sensores físicos: cambia el estado de los lugares en
// Firestore con el mismo formato de evento que usaría un sensor real: { spotId, status, timestamp },
// escrito en el documento del lugar (`status` y `updatedAt`).
//
// La parte que decide qué cambia (planTick) es pura: recibe los lugares y un generador de números
// al azar, y devuelve los cambios. Así se prueba sin emuladores. runSimulation es la que lee y
// escribe en Firestore, y la que llama la function programada `simulateSensors`.
const { FieldValue } = require("firebase-admin/firestore");
const logger = require("firebase-functions/logger");

// Documento con el control del simulador. Lo escribe un ADMIN y se lee en cada ejecución.
const CONFIG_PATH = "config/simulator";

const SCENARIOS = ["NORMAL", "RUSH_HOUR", "EVENT", "FAULTY_SENSOR", "INCIDENT", "DEMO"];

// Sin documento de configuración el simulador queda en pausa: cada escritura se cobra y dispara
// la function de contadores, así que sólo corre cuando alguien lo enciende.
const DEFAULT_CONFIG = { scenario: "NORMAL", intervalSeconds: 10, paused: true, seed: null, startedAtMs: null };
const MIN_INTERVAL_SECONDS = 3;
const MAX_INTERVAL_SECONDS = 60;

// Una function programada corre como mínimo una vez por minuto: cada corrida reparte varios
// cambios con esperas, y termina antes de que arranque la siguiente.
const RUN_BUDGET_MS = 55 * 1000;

const EVENT_FILL_MS = 4 * 60 * 1000; // el estacionamiento del evento se llena en 4 minutos
const EVENT_CYCLE_MS = 6 * 60 * 1000; // y se vacía de golpe: 4 minutos llenándose, 2 vacío
const DEMO_PERIOD_MS = 4 * 60 * 1000; // en la demo la ocupación sube y baja cada 4 minutos
const FAULTY_FRACTION = 0.15; // fracción de lugares que se averían en FAULTY_SENSOR
const INCIDENT_PROBABILITY = 0.2; // por tick, en INCIDENT

// Normaliza el documento de configuración: valores inválidos o faltantes vuelven al valor por defecto.
function parseConfig(data) {
  const d = data || {};
  const interval = Number(d.intervalSeconds);
  const startedAtMs = d.startedAt && typeof d.startedAt.toMillis === "function"
    ? d.startedAt.toMillis()
    : typeof d.startedAt === "number" ? d.startedAt : null;
  return {
    scenario: SCENARIOS.includes(d.scenario) ? d.scenario : DEFAULT_CONFIG.scenario,
    intervalSeconds: Number.isFinite(interval)
      ? Math.min(MAX_INTERVAL_SECONDS, Math.max(MIN_INTERVAL_SECONDS, interval))
      : DEFAULT_CONFIG.intervalSeconds,
    paused: typeof d.paused === "boolean" ? d.paused : DEFAULT_CONFIG.paused,
    seed: Number.isInteger(d.seed) ? d.seed : null,
    startedAtMs,
  };
}

// Generador al azar con semilla (mulberry32). Sin semilla usa Math.random.
function createRandom(seed) {
  if (seed === null || seed === undefined) return Math.random;
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

// Cuánto se quiere ocupado un estacionamiento (0 a 1) y cuántos lugares puede cambiar por tick.
// `elapsedMs` es el tiempo desde que se activó el escenario; `lotIndex` es la posición del
// estacionamiento ordenado por id.
function scenarioParams(scenario, lotIndex, elapsedMs) {
  switch (scenario) {
    case "RUSH_HOUR":
      return { target: 0.95, step: 4, noise: 0.2 };
    case "EVENT": {
      if (lotIndex !== 0) return { target: 0.5, step: 2, noise: 0.5 };
      return elapsedMs % EVENT_CYCLE_MS < EVENT_FILL_MS
        ? { target: 1, step: 4, noise: 0 }
        : { target: 0, step: Infinity, noise: 0 };
    }
    case "DEMO": {
      const wave = Math.sin((2 * Math.PI * elapsedMs) / DEMO_PERIOD_MS);
      return { target: 0.55 + 0.4 * wave, step: 4, noise: 0 };
    }
    default: // NORMAL, FAULTY_SENSOR, INCIDENT
      return { target: 0.5, step: 2, noise: 0.5 };
  }
}

// Mezcla una copia de la lista (Fisher-Yates).
function shuffled(list, random) {
  const out = list.slice();
  for (let i = out.length - 1; i > 0; i--) {
    const j = Math.floor(random() * (i + 1));
    [out[i], out[j]] = [out[j], out[i]];
  }
  return out;
}

// Decide qué lugares cambian en un tick. Cada lugar es { id, lotId, status, inUseBy, simulatedFault }
// y el resultado es una lista de { lotId, spotId, status, fault }, donde `fault` marca (true) o
// desmarca (false) que la avería la puso el simulador; así se puede reparar sin tocar las
// averías cargadas a mano.
//
// Nunca toca un lugar RESERVED (salvo en INCIDENT), uno ocupado por un auto con reserva en uso
// (`inUseBy`: lo libera el check-out) ni uno fuera de servicio que no averió el simulador.
function planTick({ scenario, spots, random, nowMs, startedAtMs = null }) {
  const elapsedMs = startedAtMs === null ? nowMs : Math.max(0, nowMs - startedAtMs);
  const changes = [];
  const byLot = new Map();
  for (const spot of spots) {
    if (!byLot.has(spot.lotId)) byLot.set(spot.lotId, []);
    byLot.get(spot.lotId).push(spot);
  }

  [...byLot.keys()].sort().forEach((lotId, lotIndex) => {
    const lotSpots = byLot.get(lotId);
    const movable = (status) => lotSpots.filter((s) => s.status === status && !s.inUseBy);

    // Fuera de FAULTY_SENSOR el simulador repara lo que averió él.
    if (scenario !== "FAULTY_SENSOR") {
      for (const spot of lotSpots.filter((s) => s.status === "OUT_OF_SERVICE" && s.simulatedFault)) {
        changes.push({ lotId, spotId: spot.id, status: "FREE", fault: false });
        spot.status = "FREE";
      }
    } else {
      const broken = lotSpots.filter((s) => s.status === "OUT_OF_SERVICE").length;
      const wanted = Math.round(FAULTY_FRACTION * lotSpots.length);
      // Averías de a una por tick, para que se vea cómo van apareciendo.
      if (broken < wanted) {
        const [victim] = shuffled([...movable("FREE"), ...movable("OCCUPIED")], random);
        if (victim) {
          changes.push({ lotId, spotId: victim.id, status: "OUT_OF_SERVICE", fault: true });
          victim.status = "OUT_OF_SERVICE";
        }
      }
    }

    // Ocupación: se acerca de a pasos a la ocupación objetivo del escenario.
    const params = scenarioParams(scenario, lotIndex, elapsedMs);
    const inService = lotSpots.filter((s) => s.status !== "OUT_OF_SERVICE");
    const occupied = inService.filter((s) => s.status === "OCCUPIED").length;
    let diff = Math.round(params.target * inService.length) - occupied;
    // Con la ocupación en el objetivo, un auto entra o sale al azar para que no se vea quieto.
    if (diff === 0 && random() < params.noise) diff = random() < 0.5 ? 1 : -1;

    const from = diff > 0 ? "FREE" : "OCCUPIED";
    const to = diff > 0 ? "OCCUPIED" : "FREE";
    const moved = shuffled(movable(from), random).slice(0, Math.min(Math.abs(diff), params.step));
    for (const spot of moved) {
      changes.push({ lotId, spotId: spot.id, status: to });
      spot.status = to;
    }
  });

  // Incidencia: un auto ajeno ocupa un lugar reservado (el caso alternativo de CU-06).
  if (scenario === "INCIDENT" && random() < INCIDENT_PROBABILITY) {
    const [reserved] = shuffled(spots.filter((s) => s.status === "RESERVED" && !s.inUseBy), random);
    if (reserved) {
      changes.push({ lotId: reserved.lotId, spotId: reserved.id, status: "OCCUPIED" });
      reserved.status = "OCCUPIED";
    }
  }

  return changes;
}

const defaultSleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

// Un tick: lee los lugares, decide los cambios y los escribe juntos. Devuelve cuántos cambió.
async function runTick(db, config, random, nowMs, log) {
  const snapshot = await db.collectionGroup("spots").get();
  const refs = new Map();
  const spots = snapshot.docs.map((doc) => {
    const spot = {
      id: doc.id,
      lotId: doc.ref.parent.parent.id,
      status: doc.get("status"),
      inUseBy: doc.get("inUseBy"),
      simulatedFault: doc.get("simulatedFault") === true,
    };
    refs.set(`${spot.lotId}/${spot.id}`, doc.ref);
    return spot;
  });

  const changes = planTick({
    scenario: config.scenario, spots, random, nowMs, startedAtMs: config.startedAtMs,
  });
  if (changes.length === 0) return 0;

  const batch = db.batch();
  for (const change of changes) {
    const update = { status: change.status, updatedAt: FieldValue.serverTimestamp() };
    if (change.fault === true) update.simulatedFault = true;
    if (change.fault === false) update.simulatedFault = FieldValue.delete();
    batch.update(refs.get(`${change.lotId}/${change.spotId}`), update);
    log.info("Evento de sensor", {
      lotId: change.lotId,
      spotId: change.spotId,
      status: change.status,
      timestamp: new Date(nowMs).toISOString(),
      scenario: config.scenario,
    });
  }
  await batch.commit();
  return changes.length;
}

// Una ejecución de la function programada: lee la configuración y, si no está en pausa, repite
// ticks separados por `intervalSeconds` (con algo de variación) hasta agotar el minuto.
async function runSimulation(db, { now = Date.now, sleep = defaultSleep, log = logger } = {}) {
  const snapshot = await db.doc(CONFIG_PATH).get();
  const config = parseConfig(snapshot.exists ? snapshot.data() : undefined);
  if (config.paused) {
    log.info("Simulador en pausa");
    return { ticks: 0, changes: 0 };
  }

  const startedAt = now();
  // Con semilla fija la corrida es reproducible; sumar el minuto evita repetir la misma secuencia.
  const random = createRandom(config.seed === null ? null : config.seed + Math.floor(startedAt / 60000));
  let ticks = 0;
  let changes = 0;
  for (;;) {
    changes += await runTick(db, config, random, now(), log);
    ticks++;
    const wait = Math.round(config.intervalSeconds * 1000 * (0.5 + random()));
    if (now() - startedAt + wait > RUN_BUDGET_MS) break;
    await sleep(wait);
  }
  log.info("Simulación de sensores", { scenario: config.scenario, ticks, changes });
  return { ticks, changes };
}

module.exports = {
  CONFIG_PATH, SCENARIOS, DEFAULT_CONFIG, RUN_BUDGET_MS,
  parseConfig, createRandom, scenarioParams, planTick, runSimulation,
};
