// Notificaciones push: qué avisos existen, cuándo corresponde mandar cada uno y el envío por FCM a
// todos los dispositivos del usuario, respetando sus preferencias. Ver "Notificaciones push" en
// CLAUDE.md, que tiene la tabla de tipos que usa la app.
const logger = require("firebase-functions/logger");
const { FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const { TIME_ZONE, WAITLIST_OFFER_MINUTES } = require("./reservationRules");

const MINUTE = 60 * 1000;
// Cuánto antes de que termine la tolerancia se avisa que la reserva está por vencer.
const EXPIRING_NOTICE_MINUTES = 5;
// Cuánto antes del fin de la franja se avisa a quien está estacionado.
const ENDING_NOTICE_MINUTES = 10;
// Vida del mensaje en FCM: si el teléfono no lo recibe en este tiempo, ya no sirve.
const PUSH_TTL_MS = 10 * MINUTE;

// Tipos de aviso de la app (`NotificationType` en domain): cada uno es un canal de Android y un
// interruptor en Cuenta. La app copia los que el usuario apagó a `users/{uid}.notificationsDisabled`
// (T16), y elige el canal por el prefijo del `type` de la push: `reservation...` o `waitlist...`.
const NOTIFICATION_TYPES = {
  RESERVATION: "RESERVATION",
  WAITLIST: "WAITLIST",
};

// Hora argentina en formato 24 h, por ejemplo "14:05".
function localTime(date) {
  return new Intl.DateTimeFormat("es-AR", {
    timeZone: TIME_ZONE, hour: "2-digit", minute: "2-digit", hourCycle: "h23",
  }).format(date);
}

// Cada aviso: su tipo en la app (`appType`, que decide canal e interruptor) y cómo se arma el
// texto. Los que empiezan con `reservation` abren la reserva en la app; `id` es el id de la reserva.
// `notice` es la clave con la que la reserva guarda que el aviso ya se mandó (`notices.<clave>`);
// los que no la tienen se mandan una sola vez por cómo se disparan.
const NOTICES = {
  // Mismo nombre que la clave del aviso local de la app (`reservationConfirmed{id}`): en el teléfono
  // que reservó, el push reemplaza a ese aviso en vez de sumarse. En los otros teléfonos llega solo.
  reservationConfirmed: {
    appType: NOTIFICATION_TYPES.RESERVATION,
    title: () => "Reserva confirmada",
    body: (r) => `Tu lugar ${r.spotCode} quedó reservado para las ${localTime(r.arrivesAt)}.`,
  },
  reservation_expiring: {
    appType: NOTIFICATION_TYPES.RESERVATION,
    notice: "expiring",
    title: () => "Tu reserva está por vencer",
    body: (r) => `Si no hacés check-in antes de las ${localTime(r.toleranceUntil)}, se libera el lugar ${r.spotCode}.`,
  },
  reservation_expired: {
    appType: NOTIFICATION_TYPES.RESERVATION,
    title: () => "Tu reserva venció",
    body: (r) => `No hiciste check-in a tiempo y se liberó el lugar ${r.spotCode}.`,
  },
  reservation_ending: {
    appType: NOTIFICATION_TYPES.RESERVATION,
    notice: "ending",
    title: () => "Tu franja está por terminar",
    body: (r) => `Tu estadía en el lugar ${r.spotCode} termina a las ${localTime(r.endsAt)}.`,
  },
  reservation_long_stay: {
    appType: NOTIFICATION_TYPES.RESERVATION,
    notice: "longStay",
    title: () => "Tu franja terminó",
    body: (r) => `Tu estadía en el lugar ${r.spotCode} terminaba a las ${localTime(r.endsAt)}. ` +
      "Hacé check-out cuando te vayas para liberarlo.",
  },
  // Lo dispara la lista de espera (reconcileWaitlist) cuando se libera un lugar; `id` es la entrada
  // de la lista y el toque abre la oferta. Recibe { lotName, spotCode, arrivesAt, endsAt }.
  waitlist_offer: {
    appType: NOTIFICATION_TYPES.WAITLIST,
    title: (o) => `Se liberó un lugar en ${o.lotName}`,
    body: (o) => `Lugar ${o.spotCode}, de ${localTime(o.arrivesAt)} a ${localTime(o.endsAt)}. ` +
      `Tenés ${WAITLIST_OFFER_MINUTES} minutos para quedártelo.`,
  },
};

// Datos del mensaje: sólo strings, como espera la app (SensorParkMessagingService).
function messageFor(type, id, values) {
  const notice = NOTICES[type];
  if (!notice) throw new Error(`Tipo de aviso desconocido: ${type}`);
  return { type, id: String(id), title: notice.title(values), body: notice.body(values) };
}

const noticeSent = (reservation, key) => Boolean(reservation.notices && reservation.notices[key]);

// El aviso que le corresponde ahora a una reserva, o null. A lo sumo uno por reserva: si ya se pasó
// el fin de la franja, el de "por terminar" no se manda y va directo el de estadía prolongada.
function dueNotice(reservation, now) {
  const at = now.getTime();
  if (reservation.status === "CONFIRMED") {
    const until = reservation.toleranceUntil.getTime();
    const from = until - EXPIRING_NOTICE_MINUTES * MINUTE;
    return at >= from && at <= until && !noticeSent(reservation, "expiring") ? "reservation_expiring" : null;
  }
  if (reservation.status === "IN_USE") {
    const ends = reservation.endsAt.getTime();
    if (at >= ends) return noticeSent(reservation, "longStay") ? null : "reservation_long_stay";
    const from = ends - ENDING_NOTICE_MINUTES * MINUTE;
    return at >= from && !noticeSent(reservation, "ending") ? "reservation_ending" : null;
  }
  return null;
}

// ¿El usuario quiere este tipo de aviso? Sólo se guardan los apagados: sin lista, quiere todos.
function wantsNotice(profile, appType) {
  const disabled = (profile && profile.notificationsDisabled) || [];
  return !disabled.includes(appType);
}

// Los tokens de un usuario: uno por dispositivo en `users/{uid}/devices/{token}`, más el campo
// `fcmToken` que usaban las versiones anteriores de la app.
async function devicesOf(userDoc) {
  const devices = await userDoc.ref.collection("devices").get();
  const tokens = devices.docs.map((d) => ({ token: d.get("token") || d.id, ref: d.ref }));
  const legacy = userDoc.get("fcmToken");
  if (legacy && !tokens.some((t) => t.token === legacy)) tokens.push({ token: legacy, legacy: true, ref: userDoc.ref });
  return tokens;
}

const EXPIRED_TOKEN_ERRORS = ["messaging/registration-token-not-registered", "messaging/invalid-registration-token"];

// Manda una push sólo `data` a cada usuario, a todos sus dispositivos. Si `appType` viene y el
// usuario apagó ese tipo de aviso, no se le manda. Borra los tokens que FCM informa como vencidos y deja
// cada envío en el log. Devuelve cuántos dispositivos la recibieron.
async function sendPush(db, uids, data, { appType, messaging } = {}) {
  let delivered = 0;
  for (const uid of uids) {
    const userDoc = await db.doc(`users/${uid}`).get();
    const entry = { type: data.type, id: data.id, uid };
    if (!userDoc.exists) {
      logger.info("Push no enviada: el usuario no existe", entry);
      continue;
    }
    if (appType && !wantsNotice(userDoc.data(), appType)) {
      logger.info("Push no enviada: el usuario apagó esos avisos", { ...entry, appType });
      continue;
    }
    const devices = await devicesOf(userDoc);
    if (devices.length === 0) {
      logger.info("Push no enviada: el usuario no tiene dispositivos", entry);
      continue;
    }

    const response = await (messaging || getMessaging()).sendEachForMulticast({
      tokens: devices.map((d) => d.token),
      data,
      // priority high para que Doze no la retenga; ttl corto para que no llegue horas tarde.
      android: { priority: "high", ttl: PUSH_TTL_MS },
    });
    const expired = devices.filter((d, i) =>
      !response.responses[i].success && EXPIRED_TOKEN_ERRORS.includes(response.responses[i].error.code));
    await Promise.all(expired.map((d) =>
      d.legacy ? d.ref.update({ fcmToken: FieldValue.delete() }) : d.ref.delete()));
    delivered += response.successCount;
    logger.info("Push enviada", {
      ...entry, devices: devices.length, delivered: response.successCount, expiredTokens: expired.length,
    });
  }
  return delivered;
}

// Arma el aviso de `type` y se lo manda al usuario, si no lo apagó.
function sendNotice(db, uid, type, id, values, options = {}) {
  return sendPush(db, [uid], messageFor(type, id, values), { ...options, appType: NOTICES[type].appType });
}

module.exports = {
  EXPIRING_NOTICE_MINUTES,
  ENDING_NOTICE_MINUTES,
  NOTIFICATION_TYPES,
  NOTICES,
  localTime,
  messageFor,
  dueNotice,
  wantsNotice,
  sendPush,
  sendNotice,
};
