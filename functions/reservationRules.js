// Reglas de reserva, sin Firestore: reciben los datos y la hora actual (`now`) y devuelven una
// decisión. Son la versión del backend de docs/reservation-rules.md; la app tiene la misma en
// domain/.../reservation/ReservationRules.kt. Si cambia una regla, cambia en los tres lugares.
// Las fechas son objetos Date.

const ALLOWED_DURATIONS_MINUTES = [15, 30, 60, 120, 240, 480];
const MAX_ADVANCE_DAYS = 7;
const PAST_GRACE_MINUTES = 5;
const MAX_ACTIVE_RESERVATIONS = 2;
// Una reserva que empieza dentro de este margen toma el lugar en el momento.
const NOW_WINDOW_MINUTES = 15;
const EARLY_CHECK_IN_MINUTES = 15;
const WAITLIST_OFFER_MINUTES = 5;
const TIME_ZONE = "America/Argentina/Buenos_Aires";

const SPOT_TYPES = ["STANDARD", "PRIORITY", "MOTORCYCLE", "EV_CHARGING"];
const ACTIVE_STATUSES = ["CONFIRMED", "IN_USE"];
const SPECIAL_CATEGORIES = ["PREGNANT", "REDUCED_MOBILITY"];

const MINUTE = 60 * 1000;
const DAY = 24 * 60 * MINUTE;
const EARTH_RADIUS_METERS = 6371000;

const plusMinutes = (date, minutes) => new Date(date.getTime() + minutes * MINUTE);

function endsAt(arrivesAt, durationMinutes) {
  return plusMinutes(arrivesAt, durationMinutes);
}

function toleranceUntil(arrivesAt, toleranceMinutes) {
  return plusMinutes(arrivesAt, toleranceMinutes || 0);
}

// Minutos desde la medianoche, en la hora argentina.
function localMinutes(date) {
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone: TIME_ZONE, hour: "2-digit", minute: "2-digit", hourCycle: "h23",
  }).formatToParts(date);
  const value = (type) => Number(parts.find((p) => p.type === type).value);
  return value("hour") * 60 + value("minute");
}

// "HH:mm" → minutos desde la medianoche.
function parseTime(text) {
  const match = /^(\d{1,2}):(\d{2})$/.exec(text || "");
  return match ? Number(match[1]) * 60 + Number(match[2]) : null;
}

// La franja entera tiene que caer dentro del horario, en el mismo día. Sin horario, no hay límite.
function withinSchedule(schedule, arrivesAt, durationMinutes) {
  const opens = parseTime(schedule && schedule.opensAt);
  const closes = parseTime(schedule && schedule.closesAt);
  if (opens === null || closes === null) return true;
  const arrival = localMinutes(arrivesAt);
  return arrival >= opens && arrival + durationMinutes <= closes;
}

// Pasos 1 a 6 del documento, en orden. Devuelve el motivo del rechazo o null.
function requestProblem({ arrivesAt, durationMinutes, spotType, category, activeReservations, schedule }, now) {
  if (!ALLOWED_DURATIONS_MINUTES.includes(durationMinutes)) return "INVALID_DURATION";
  if (arrivesAt < plusMinutes(now, -PAST_GRACE_MINUTES)) return "IN_THE_PAST";
  if (arrivesAt.getTime() > now.getTime() + MAX_ADVANCE_DAYS * DAY) return "TOO_FAR_AHEAD";
  if (!withinSchedule(schedule, arrivesAt, durationMinutes)) return "OUTSIDE_SCHEDULE";
  if (spotType === "PRIORITY" && !SPECIAL_CATEGORIES.includes(category)) return "CATEGORY_REQUIRED";
  if (activeReservations >= MAX_ACTIVE_RESERVATIONS) return "LIMIT_REACHED";
  return null;
}

// Franjas [inicio, fin): una que termina 14:00 y otra que empieza 14:00 no se superponen.
function overlaps(startA, endA, startB, endB) {
  return startA < endB && startB < endA;
}

// Hasta cuándo toma el lugar una reserva activa. Una IN_USE que se pasó de su fin sigue ocupándolo
// por lo menos hasta el final de la ventana de "ahora": si fuera sólo hasta `now`, un pedido que
// empieza ahora no chocaría, porque las franjas que se tocan no se superponen.
function takenUntil(reservation, now) {
  if (reservation.status !== "IN_USE") return reservation.endsAt;
  const nowWindowEnd = new Date(now.getTime() + NOW_WINDOW_MINUTES * MINUTE);
  return reservation.endsAt > nowWindowEnd ? reservation.endsAt : nowWindowEnd;
}

function isInNowWindow(arrivesAt, now) {
  return arrivesAt.getTime() <= now.getTime() + NOW_WINDOW_MINUTES * MINUTE;
}

// ¿El lugar sirve para la franja pedida? `reservations` son las reservas activas del estacionamiento.
function spotFits(spot, { spotType, spotId, category, arrivesAt, endsAt: ends, reservations }, now) {
  if (spotId ? spot.id !== spotId : spot.type !== spotType) return false;
  if (spot.type === "PRIORITY" && spot.priorityCategory !== category) return false;
  if (spot.status === "OUT_OF_SERVICE") return false;
  if (isInNowWindow(arrivesAt, now) && spot.status !== "FREE") return false;
  return !reservations.some((r) => r.spotId === spot.id &&
    ACTIVE_STATUSES.includes(r.status) &&
    overlaps(arrivesAt, ends, r.arrivesAt, takenUntil(r, now)));
}

// El lugar disponible de código más bajo, o null si no hay.
function pickSpot(spots, request, now) {
  const fitting = spots.filter((s) => spotFits(s, request, now));
  fitting.sort((a, b) => String(a.code).localeCompare(String(b.code)));
  return fitting[0] || null;
}

function canCancel(reservation) {
  return reservation.status === "CONFIRMED";
}

function distanceMeters(from, to) {
  const rad = (deg) => deg * Math.PI / 180;
  const dLat = rad(to.latitude - from.latitude);
  const dLng = rad(to.longitude - from.longitude);
  const a = Math.sin(dLat / 2) ** 2 +
    Math.cos(rad(from.latitude)) * Math.cos(rad(to.latitude)) * Math.sin(dLng / 2) ** 2;
  return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(a));
}

// Motivo por el que no se puede hacer check-in, o null. `distance` en metros.
function checkInProblem(reservation, { distance, radiusMeters }, now) {
  // Cancelada, vencida, en uso o terminada: ya no se puede usar.
  if (reservation.status !== "CONFIRMED") return "EXPIRED";
  if (now < plusMinutes(reservation.arrivesAt, -EARLY_CHECK_IN_MINUTES)) return "TOO_EARLY";
  if (now > reservation.toleranceUntil) return "EXPIRED";
  if (distance > radiusMeters) return "TOO_FAR";
  return null;
}

// Estadía real (minutos enteros, hacia abajo) e importe informativo: tarifa por hora × minutos / 60,
// redondeado.
function checkoutSummary(checkedInAt, hourlyRate, now) {
  const stayMinutes = Math.max(0, Math.floor((now - checkedInAt) / MINUTE));
  return { stayMinutes, amount: Math.round((hourlyRate || 0) * stayMinutes / 60) };
}

function isExpired(reservation, now) {
  return reservation.status === "CONFIRMED" && now > reservation.toleranceUntil;
}

// Estado del lugar cuando lo deja una reserva: RESERVED si otra ya está en su ventana de "ahora".
function spotStatusAfterRelease(otherReservations, now) {
  const next = otherReservations.some((r) => r.status === "CONFIRMED" &&
    isInNowWindow(r.arrivesAt, now) && !isExpired(r, now));
  return next ? "RESERVED" : "FREE";
}

// Orden de la lista de espera: categorías especiales primero, después por hora de pedido.
function waitlistCompare(a, b) {
  const rank = (entry) => (SPECIAL_CATEGORIES.includes(entry.category) ? 0 : 1);
  return rank(a) - rank(b) || a.requestedAt - b.requestedAt;
}

module.exports = {
  ALLOWED_DURATIONS_MINUTES, MAX_ADVANCE_DAYS, PAST_GRACE_MINUTES, MAX_ACTIVE_RESERVATIONS,
  NOW_WINDOW_MINUTES, EARLY_CHECK_IN_MINUTES, WAITLIST_OFFER_MINUTES, TIME_ZONE,
  SPOT_TYPES, ACTIVE_STATUSES, SPECIAL_CATEGORIES,
  endsAt, toleranceUntil, localMinutes, withinSchedule, requestProblem, overlaps, takenUntil,
  isInNowWindow, spotFits, pickSpot, canCancel, distanceMeters, checkInProblem, checkoutSummary,
  isExpired, spotStatusAfterRelease, waitlistCompare,
};
