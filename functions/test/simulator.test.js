// Tests del simulador de sensores. No necesitan emuladores: planTick es una función pura y
// runSimulation se prueba con una base falsa. Desde functions/: npm test
const { describe, test } = require("node:test");
const assert = require("node:assert/strict");
const {
  RUN_BUDGET_MS, parseConfig, createRandom, planTick, runSimulation,
} = require("../simulator");

const MINUTE = 60 * 1000;

// Estacionamientos con `count` lugares libres, numerados del 01.
function lots(ids, count = 14) {
  return ids.flatMap((lotId) => Array.from({ length: count }, (_, i) => ({
    id: `${lotId}-${String(i + 1).padStart(2, "0")}`,
    lotId,
    status: "FREE",
    inUseBy: undefined,
    simulatedFault: false,
  })));
}

// Aplica los cambios a los lugares, como lo haría la base.
function apply(spots, changes) {
  for (const change of changes) {
    const spot = spots.find((s) => s.id === change.spotId);
    spot.status = change.status;
    if (change.fault === true) spot.simulatedFault = true;
    if (change.fault === false) spot.simulatedFault = false;
  }
}

function run(spots, scenario, ticks, { startMs = 0, stepMs = 10000, seed = 1 } = {}) {
  const random = createRandom(seed);
  for (let i = 0; i < ticks; i++) {
    const nowMs = startMs + i * stepMs;
    apply(spots, planTick({ scenario, spots, random, nowMs, startedAtMs: 0 }));
  }
}

const occupancy = (spots) => spots.filter((s) => s.status === "OCCUPIED").length / spots.length;

describe("parseConfig", () => {
  test("sin documento, el simulador queda en pausa con los valores por defecto", () => {
    assert.deepEqual(parseConfig(undefined), {
      scenario: "NORMAL", intervalSeconds: 10, paused: true, seed: null, startedAtMs: null,
    });
  });

  test("toma los valores válidos y corrige los inválidos", () => {
    const config = parseConfig({
      scenario: "RUSH_HOUR", intervalSeconds: 1, paused: false, seed: 7, startedAt: { toMillis: () => 123 },
    });
    assert.equal(config.scenario, "RUSH_HOUR");
    assert.equal(config.intervalSeconds, 3, "el intervalo mínimo es 3 segundos");
    assert.equal(config.paused, false);
    assert.equal(config.seed, 7);
    assert.equal(config.startedAtMs, 123);

    const bad = parseConfig({ scenario: "NOPE", intervalSeconds: "x", paused: "no", seed: 1.5 });
    assert.equal(bad.scenario, "NORMAL");
    assert.equal(bad.intervalSeconds, 10);
    assert.equal(bad.paused, true);
    assert.equal(bad.seed, null);
    assert.equal(parseConfig({ intervalSeconds: 500 }).intervalSeconds, 60);
  });
});

describe("createRandom", () => {
  test("con la misma semilla da la misma secuencia, y está entre 0 y 1", () => {
    const a = createRandom(42);
    const b = createRandom(42);
    for (let i = 0; i < 50; i++) {
      const value = a();
      assert.equal(value, b());
      assert.ok(value >= 0 && value < 1);
    }
    assert.notEqual(createRandom(1)(), createRandom(2)());
  });
});

describe("planTick", () => {
  test("NORMAL: la ocupación se acerca al 50 % y no toca reservados, en uso ni averías ajenas", () => {
    const spots = lots(["a", "b"]);
    spots[0].status = "RESERVED";
    spots[1].status = "OCCUPIED";
    spots[1].inUseBy = "r1";
    spots[2].status = "OUT_OF_SERVICE"; // avería cargada a mano
    run(spots, "NORMAL", 60);

    assert.equal(spots[0].status, "RESERVED");
    assert.equal(spots[1].status, "OCCUPIED");
    assert.equal(spots[2].status, "OUT_OF_SERVICE");
    const lotA = spots.filter((s) => s.lotId === "a" && s.status !== "OUT_OF_SERVICE");
    assert.ok(Math.abs(occupancy(lotA) - 0.5) <= 0.15, `ocupación ${occupancy(lotA)}`);
  });

  test("RUSH_HOUR: sube hasta casi llenarse, de a pocos lugares por tick", () => {
    const spots = lots(["a"]);
    const random = createRandom(3);
    const first = planTick({ scenario: "RUSH_HOUR", spots, random, nowMs: 0, startedAtMs: 0 });
    assert.ok(first.length > 0 && first.length <= 4);
    apply(spots, first);
    run(spots, "RUSH_HOUR", 30);
    assert.ok(occupancy(spots) >= 0.9, `ocupación ${occupancy(spots)}`);
  });

  test("EVENT: el primer estacionamiento se llena y después se vacía de golpe", () => {
    const spots = lots(["b", "a"]); // el primero por id es "a"
    run(spots, "EVENT", 30, { stepMs: 8000 }); // 4 minutos
    const lotA = spots.filter((s) => s.lotId === "a");
    assert.equal(occupancy(lotA), 1, "se llenó");

    const changes = planTick({
      scenario: "EVENT", spots, random: createRandom(5), nowMs: 4 * MINUTE + 1000, startedAtMs: 0,
    });
    apply(spots, changes);
    assert.equal(occupancy(spots.filter((s) => s.lotId === "a")), 0, "se vació en un solo tick");
  });

  test("FAULTY_SENSOR: avería lugares hasta el 15 % y marca que fue el simulador", () => {
    const spots = lots(["a"]);
    run(spots, "FAULTY_SENSOR", 40);
    const broken = spots.filter((s) => s.status === "OUT_OF_SERVICE");
    assert.equal(broken.length, 2); // round(0.15 * 14)
    assert.ok(broken.every((s) => s.simulatedFault));
  });

  test("al salir de FAULTY_SENSOR repara lo que averió él, pero no las averías manuales", () => {
    const spots = lots(["a"]);
    spots[0].status = "OUT_OF_SERVICE"; // a mano
    run(spots, "FAULTY_SENSOR", 40);
    assert.ok(spots.filter((s) => s.simulatedFault).length >= 1);

    apply(spots, planTick({ scenario: "NORMAL", spots, random: createRandom(9), nowMs: 0, startedAtMs: 0 }));
    assert.equal(spots[0].status, "OUT_OF_SERVICE");
    assert.equal(spots.filter((s) => s.simulatedFault).length, 0);
  });

  test("INCIDENT ocupa un lugar reservado; los demás escenarios no", () => {
    const reserved = () => {
      const spots = lots(["a"]);
      spots[0].status = "RESERVED";
      return spots;
    };

    const spots = reserved();
    // random() siempre 0: la probabilidad de incidencia se cumple en el primer tick.
    const changes = planTick({ scenario: "INCIDENT", spots, random: () => 0, nowMs: 0, startedAtMs: 0 });
    assert.ok(changes.some((c) => c.spotId === spots[0].id && c.status === "OCCUPIED"));

    for (const scenario of ["NORMAL", "RUSH_HOUR", "EVENT", "FAULTY_SENSOR", "DEMO"]) {
      const other = reserved();
      run(other, scenario, 40);
      assert.equal(other[0].status, "RESERVED", scenario);
    }
  });

  test("con la misma semilla produce los mismos cambios", () => {
    const plan = () => planTick({
      scenario: "NORMAL", spots: lots(["a", "b"]), random: createRandom(42), nowMs: 0, startedAtMs: 0,
    });
    assert.deepEqual(plan(), plan());
  });

  test("sin lugares no hay cambios", () => {
    assert.deepEqual(planTick({ scenario: "NORMAL", spots: [], random: Math.random, nowMs: 0 }), []);
  });
});

// Base falsa con lo justo que usa runSimulation.
function fakeDb(config, spots) {
  const db = { spotReads: 0, writes: [] };
  const docs = spots.map((spot) => ({
    id: spot.id,
    ref: { id: spot.id, parent: { parent: { id: spot.lotId } } },
    get: (field) => spot[field],
  }));
  db.doc = () => ({ get: async () => ({ exists: config !== undefined, data: () => config }) });
  db.collectionGroup = () => ({ get: async () => { db.spotReads++; return { docs }; } });
  db.batch = () => {
    const ops = [];
    return {
      update: (ref, data) => ops.push({ id: ref.id, data }),
      commit: async () => { db.writes.push(...ops); },
    };
  };
  return db;
}

const silentLog = { info: () => {} };

describe("runSimulation", () => {
  test("en pausa (o sin configuración) no lee ni escribe lugares", async () => {
    for (const config of [undefined, { paused: true, scenario: "RUSH_HOUR" }]) {
      const db = fakeDb(config, lots(["a"]));
      const result = await runSimulation(db, { log: silentLog });
      assert.deepEqual(result, { ticks: 0, changes: 0 });
      assert.equal(db.spotReads, 0);
      assert.equal(db.writes.length, 0);
    }
  });

  test("encendido, reparte varios ticks en el minuto y escribe status y updatedAt", async () => {
    const db = fakeDb({ paused: false, scenario: "RUSH_HOUR", intervalSeconds: 10, seed: 1 }, lots(["a"]));
    let clock = 0;
    const result = await runSimulation(db, {
      now: () => clock,
      sleep: async (ms) => { clock += ms; },
      log: silentLog,
    });

    assert.ok(result.ticks > 1, "más de un tick por corrida");
    assert.ok(clock <= RUN_BUDGET_MS, "termina antes de que arranque la siguiente corrida");
    assert.equal(db.spotReads, result.ticks);
    assert.equal(db.writes.length, result.changes);
    assert.ok(db.writes.length > 0);
    for (const { data } of db.writes) {
      assert.ok(["FREE", "OCCUPIED", "OUT_OF_SERVICE"].includes(data.status));
      assert.ok("updatedAt" in data);
    }
  });

  test("con intervalo largo hace un solo tick", async () => {
    const db = fakeDb({ paused: false, intervalSeconds: 60 }, lots(["a"]));
    let clock = 0;
    const result = await runSimulation(db, { now: () => clock, sleep: async (ms) => { clock += ms; }, log: silentLog });
    assert.equal(result.ticks, 1);
  });
});
