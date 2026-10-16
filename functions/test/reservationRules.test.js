// Tests de las reglas de reserva, sin Firestore: cubren la tabla de ejemplos de
// docs/reservation-rules.md. Corren con `npm --prefix functions test`, con o sin emuladores.
const { describe, test } = require("node:test");
const assert = require("node:assert/strict");
const rules = require("../reservationRules");

// Fechas en hora argentina.
const at = (time, day = "2026-10-20") => new Date(`${day}T${time}:00-03:00`);
const NOW = at("12:00");
const SCHEDULE = { opensAt: "06:00", closesAt: "23:00" };

// Un pedido válido, con lo que cambie cada caso.
function problem(overrides = {}, now = NOW) {
  return rules.requestProblem({
    arrivesAt: at("13:00"), durationMinutes: 60, spotType: "STANDARD", category: "STANDARD",
    activeReservations: 0, schedule: SCHEDULE, ...overrides,
  }, now);
}

const spot = (id, overrides = {}) => ({ id, code: id, type: "STANDARD", status: "FREE", ...overrides });
const reservation = (spotId, from, to, status = "CONFIRMED") =>
  ({ spotId, status, arrivesAt: at(from), endsAt: at(to) });

function pick(spots, { from, to, spotType = "STANDARD", category = "STANDARD", spotId, reservations = [] }, now = NOW) {
  return rules.pickSpot(spots, { spotType, spotId, category, arrivesAt: at(from), endsAt: at(to), reservations }, now);
}

describe("pedido", () => {
  test("un pedido válido no tiene problemas", () => assert.equal(problem(), null));

  test("duración de 45 minutos: INVALID_DURATION", () =>
    assert.equal(problem({ durationMinutes: 45 }), "INVALID_DURATION"));

  test("todas las duraciones permitidas pasan", () => {
    for (const minutes of [15, 30, 60, 120, 240, 480]) {
      assert.equal(problem({ arrivesAt: at("14:00"), durationMinutes: minutes }), null);
    }
  });

  test("llegada hace 10 minutos: IN_THE_PAST", () =>
    assert.equal(problem({ arrivesAt: at("11:50") }), "IN_THE_PAST"));

  test("llegada hace 3 minutos entra en el margen", () =>
    assert.equal(problem({ arrivesAt: at("11:57") }), null));

  test("llegada dentro de 8 días: TOO_FAR_AHEAD", () =>
    assert.equal(problem({ arrivesAt: at("13:00", "2026-10-28") }), "TOO_FAR_AHEAD"));

  test("llegada 22:30 con 1 h, termina 23:30: OUTSIDE_SCHEDULE", () =>
    assert.equal(problem({ arrivesAt: at("22:30"), durationMinutes: 60 }), "OUTSIDE_SCHEDULE"));

  test("llegada 22:00 con 1 h, termina justo al cierre: pasa", () =>
    assert.equal(problem({ arrivesAt: at("22:00"), durationMinutes: 60 }), null));

  test("llegada 05:30: OUTSIDE_SCHEDULE", () =>
    assert.equal(problem({ arrivesAt: at("05:30", "2026-10-21") }), "OUTSIDE_SCHEDULE"));

  test("sin horario cargado no hay restricción", () =>
    assert.equal(problem({ arrivesAt: at("05:30", "2026-10-21"), schedule: undefined }), null));

  test("conductor STANDARD pide PRIORITY: CATEGORY_REQUIRED", () =>
    assert.equal(problem({ spotType: "PRIORITY" }), "CATEGORY_REQUIRED"));

  test("conductor PREGNANT pide PRIORITY: pasa", () =>
    assert.equal(problem({ spotType: "PRIORITY", category: "PREGNANT" }), null));

  test("conductor con 2 reservas activas: LIMIT_REACHED", () =>
    assert.equal(problem({ activeReservations: 2 }), "LIMIT_REACHED"));

  test("se valida en orden: la duración antes que el horario", () =>
    assert.equal(problem({ durationMinutes: 45, arrivesAt: at("05:30", "2026-10-21") }), "INVALID_DURATION"));
});

describe("franjas", () => {
  test("una que termina 14:00 y otra que empieza 14:00 no se superponen", () =>
    assert.equal(rules.overlaps(at("13:00"), at("14:00"), at("14:00"), at("15:00")), false));

  test("una que empieza 13:59 se superpone con una que termina 14:00", () =>
    assert.equal(rules.overlaps(at("13:59"), at("14:59"), at("13:00"), at("14:00")), true));

  test("una reserva IN_USE vencida ocupa el lugar hasta el fin de la ventana de ahora", () => {
    const inUse = reservation("01", "10:00", "11:00", "IN_USE");
    assert.deepEqual(rules.takenUntil(inUse, NOW), at("12:15"));
  });

  test("con una IN_USE vencida, un pedido para ahora no tiene lugar", () => {
    const inUse = [reservation("01", "10:00", "11:00", "IN_USE")];
    assert.equal(pick([spot("01")], { from: "12:00", to: "13:00", reservations: inUse }), null);
  });

  test("una reserva CONFIRMED ocupa hasta su fin", () =>
    assert.deepEqual(rules.takenUntil(reservation("01", "13:00", "14:00"), NOW), at("14:00")));
});

describe("elegir lugar", () => {
  const taken = [reservation("01", "13:00", "14:00")];

  test("único lugar con reserva 13:00 a 14:00; se pide 14:00 a 15:00: se confirma en ese lugar", () =>
    assert.equal(pick([spot("01")], { from: "14:00", to: "15:00", reservations: taken }).id, "01"));

  test("el mismo lugar; se pide 13:59 a 14:59: no hay lugar", () =>
    assert.equal(pick([spot("01")], { from: "13:59", to: "14:59", reservations: taken }), null));

  test("una reserva cancelada no toma el lugar", () => {
    const cancelled = [reservation("01", "13:00", "14:00", "CANCELLED")];
    assert.equal(pick([spot("01")], { from: "13:30", to: "14:30", reservations: cancelled }).id, "01");
  });

  test("PREGNANT pide PRIORITY y sólo hay un preferencial para REDUCED_MOBILITY: no hay lugar", () => {
    const spots = [spot("09", { type: "PRIORITY", priorityCategory: "REDUCED_MOBILITY" })];
    assert.equal(pick(spots, { from: "14:00", to: "15:00", spotType: "PRIORITY", category: "PREGNANT" }), null);
  });

  test("PREGNANT pide PRIORITY y hay uno para PREGNANT: lo toma", () => {
    const spots = [
      spot("09", { type: "PRIORITY", priorityCategory: "PREGNANT" }),
      spot("10", { type: "PRIORITY", priorityCategory: "REDUCED_MOBILITY" }),
    ];
    assert.equal(pick(spots, { from: "14:00", to: "15:00", spotType: "PRIORITY", category: "PREGNANT" }).id, "09");
  });

  test("un lugar OUT_OF_SERVICE sin reservas no sirve", () =>
    assert.equal(pick([spot("01", { status: "OUT_OF_SERVICE" })], { from: "14:00", to: "15:00" }), null));

  test("reserva para dentro de 5 minutos y el lugar está OCCUPIED: no sirve", () =>
    assert.equal(pick([spot("01", { status: "OCCUPIED" })], { from: "12:05", to: "13:05" }), null));

  test("reserva para mañana y el lugar está OCCUPIED ahora: sirve", () => {
    const tomorrow = rules.pickSpot([spot("01", { status: "OCCUPIED" })], {
      spotType: "STANDARD", category: "STANDARD", arrivesAt: at("13:00", "2026-10-21"),
      endsAt: at("14:00", "2026-10-21"), reservations: [],
    }, NOW);
    assert.equal(tomorrow.id, "01");
  });

  test("entre varios elige el de código más bajo", () =>
    assert.equal(pick([spot("03"), spot("01"), spot("02")], { from: "14:00", to: "15:00" }).id, "01"));

  test("un lugar puntual: ése o ninguno", () => {
    const spots = [spot("01"), spot("02")];
    assert.equal(pick(spots, { from: "14:00", to: "15:00", spotId: "02" }).id, "02");
    assert.equal(pick(spots, { from: "13:30", to: "14:30", spotId: "01", reservations: taken }), null);
  });

  test("el tipo tiene que coincidir", () =>
    assert.equal(pick([spot("11", { type: "MOTORCYCLE" })], { from: "14:00", to: "15:00" }), null));
});

describe("cancelar, check-in y check-out", () => {
  const confirmed = { status: "CONFIRMED", arrivesAt: at("13:00"), toleranceUntil: at("13:15") };

  test("cancelar una reserva IN_USE no se puede", () =>
    assert.equal(rules.canCancel({ status: "IN_USE" }), false));

  test("cancelar una CONFIRMED se puede", () => assert.equal(rules.canCancel(confirmed), true));

  test("check-in 20 minutos antes de la llegada: TOO_EARLY", () =>
    assert.equal(rules.checkInProblem(confirmed, { distance: 0, radiusMeters: 300 }, at("12:40")), "TOO_EARLY"));

  test("check-in 15 minutos antes: pasa", () =>
    assert.equal(rules.checkInProblem(confirmed, { distance: 0, radiusMeters: 300 }, at("12:45")), null));

  test("check-in pasada la tolerancia: EXPIRED", () =>
    assert.equal(rules.checkInProblem(confirmed, { distance: 0, radiusMeters: 300 }, at("13:16")), "EXPIRED"));

  test("check-in de una reserva cancelada: EXPIRED", () =>
    assert.equal(rules.checkInProblem({ ...confirmed, status: "CANCELLED" }, { distance: 0, radiusMeters: 300 }, at("13:00")), "EXPIRED"));

  test("check-in a 1 km del estacionamiento: TOO_FAR", () => {
    const utn = { latitude: -34.5984, longitude: -58.4200 };
    const oneKmAway = { latitude: -34.5894, longitude: -58.4200 };
    const distance = rules.distanceMeters(oneKmAway, utn);
    assert.ok(distance > 950 && distance < 1050);
    assert.equal(rules.checkInProblem(confirmed, { distance, radiusMeters: 300 }, at("13:00")), "TOO_FAR");
  });

  test("check-out a los 90 minutos con tarifa de $2.500 la hora: $3.750", () =>
    assert.deepEqual(rules.checkoutSummary(at("13:00"), 2500, at("14:30")), { stayMinutes: 90, amount: 3750 }));

  test("la estadía cuenta minutos enteros hacia abajo", () =>
    assert.equal(rules.checkoutSummary(at("13:00"), 2500, new Date(at("13:10").getTime() + 59 * 1000)).stayMinutes, 10));
});

describe("vencimiento y estado del lugar", () => {
  test("una CONFIRMED vence pasada la tolerancia", () => {
    const r = { status: "CONFIRMED", toleranceUntil: at("11:45") };
    assert.equal(rules.isExpired(r, NOW), true);
    assert.equal(rules.isExpired({ ...r, toleranceUntil: at("12:15") }, NOW), false);
  });

  test("al liberarse, el lugar queda FREE si no hay otra reserva en la ventana de ahora", () =>
    assert.equal(rules.spotStatusAfterRelease([reservation("01", "15:00", "16:00")], NOW), "FREE"));

  test("al liberarse, queda RESERVED si otra reserva empieza dentro de 15 minutos", () => {
    const next = { ...reservation("01", "12:10", "13:10"), toleranceUntil: at("12:25") };
    assert.equal(rules.spotStatusAfterRelease([next], NOW), "RESERVED");
  });
});

describe("lista de espera", () => {
  test("las categorías especiales van primero; a igual categoría, por hora de pedido", () => {
    const entries = [
      { id: "a", category: "STANDARD", requestedAt: at("10:00") },
      { id: "b", category: "REDUCED_MOBILITY", requestedAt: at("11:00") },
      { id: "c", category: "PREGNANT", requestedAt: at("10:30") },
      { id: "d", category: "STANDARD", requestedAt: at("09:00") },
    ];
    assert.deepEqual(entries.sort(rules.waitlistCompare).map((e) => e.id), ["c", "b", "d", "a"]);
  });
});
