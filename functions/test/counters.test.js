// Tests de los contadores de lugares. La parte de updateFreeSpotCounters corre contra los emuladores
// (desde la raíz del repo):
//   firebase emulators:exec --only auth,firestore,functions --project demo-sensorpark "npm --prefix functions test"
const { after, before, describe, test } = require("node:test");
const assert = require("node:assert/strict");
const { initializeApp, deleteApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { countSpots } = require("../counters");
const { seed, PARKING_LOTS } = require("../seed");

describe("countSpots", () => {
  test("cuenta capacidad y libres en total y por tipo", () => {
    const counters = countSpots([
      { type: "STANDARD", status: "FREE" },
      { type: "STANDARD", status: "OCCUPIED" },
      { type: "MOTORCYCLE", status: "FREE" },
      { type: "PRIORITY", status: "RESERVED" },
      { type: "PRIORITY", status: "OUT_OF_SERVICE" },
    ]);
    assert.deepEqual(counters, {
      capacity: 5,
      freeSpots: 2,
      capacityByType: { STANDARD: 2, MOTORCYCLE: 1, PRIORITY: 2 },
      freeSpotsByType: { STANDARD: 1, MOTORCYCLE: 1 },
    });
  });

  test("sin lugares, todo en cero", () => {
    assert.deepEqual(countSpots([]), { capacity: 0, freeSpots: 0, capacityByType: {}, freeSpotsByType: {} });
  });
});

describe("updateFreeSpotCounters", () => {
  let app;
  let db;
  const lotId = PARKING_LOTS[0].id;
  const lot = () => db.doc(`parkingLots/${lotId}`);
  const spot = (id) => lot().collection("spots").doc(id);

  before(async () => {
    app = initializeApp({ projectId: process.env.GCLOUD_PROJECT || "demo-sensorpark" }, "counters");
    db = getFirestore(app);
    await seed(db);
  });

  after(() => deleteApp(app));

  // La function corre aparte: se espera a que los contadores del documento lleguen a lo esperado.
  async function waitForCounters(expected) {
    let current;
    for (let i = 0; i < 60; i++) {
      current = (await lot().get()).data();
      if (Object.entries(expected).every(([k, v]) => JSON.stringify(current[k]) === JSON.stringify(v))) return;
      await new Promise((r) => setTimeout(r, 250));
    }
    assert.fail(`Los contadores no llegaron a ${JSON.stringify(expected)}: ${JSON.stringify(current)}`);
  }

  // Lo que deberían decir los contadores según los lugares que hay ahora.
  async function actualCounters() {
    const spots = await lot().collection("spots").get();
    return countSpots(spots.docs.map((d) => d.data()));
  }

  test("al ocupar un lugar baja el total de libres y el de su tipo", async () => {
    await spot("01").update({ status: "OCCUPIED" });
    await waitForCounters({ freeSpots: 13 });
    const data = (await lot().get()).data();
    assert.equal(data.capacity, 14);
    assert.equal(data.freeSpotsByType.STANDARD, 7);
    assert.equal(data.freeSpotsByType.PRIORITY, 2);
  });

  test("al liberarlo vuelve a subir", async () => {
    await spot("01").update({ status: "FREE" });
    await waitForCounters({ freeSpots: 14, freeSpotsByType: { STANDARD: 8, PRIORITY: 2, MOTORCYCLE: 2, EV_CHARGING: 2 } });
  });

  test("un lugar nuevo suma capacidad y uno borrado la resta", async () => {
    await spot("99").set({ code: "99", type: "MOTORCYCLE", status: "FREE" });
    await waitForCounters({ capacity: 15, freeSpots: 15 });
    await spot("99").delete();
    await waitForCounters({ capacity: 14, freeSpots: 14 });
  });

  test("no se desfasan con muchos cambios seguidos", async () => {
    const ids = ["01", "02", "03", "04", "09", "11", "13"];
    const statuses = ["OCCUPIED", "FREE", "RESERVED", "OUT_OF_SERVICE"];
    await Promise.all(Array.from({ length: 40 }, (_, i) =>
      spot(ids[i % ids.length]).update({ status: statuses[(i * 7) % statuses.length] })));

    await waitForCounters(await actualCounters());
  });

  test("borrar los lugares de un estacionamiento borrado no lo vuelve a crear", async () => {
    const deleted = db.doc(`parkingLots/${PARKING_LOTS[1].id}`);
    await deleted.delete();
    await deleted.collection("spots").doc("01").delete();
    // No hay nada que esperar: se da tiempo a que corra la function y se verifica que no escribió.
    await new Promise((r) => setTimeout(r, 3000));
    assert.equal((await deleted.get()).exists, false);
  });
});
