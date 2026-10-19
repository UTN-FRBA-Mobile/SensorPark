// Envío de pushes. Separado de index.js para poder usarlo también desde reservations.js sin que
// los dos módulos se requieran entre sí.
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

// Envía una push sólo `data` (todos strings) a los usuarios indicados y limpia tokens vencidos.
// Con `kind` (RESERVATION, REMINDER, ARRIVAL o WAITLIST) se saltea a quien apagó ese tipo de aviso
// en la app: lo guarda en `users/{uid}.notificationsDisabled`.
async function sendPush(uids, data, kind = null) {
  const db = getFirestore();
  const docs = await Promise.all(uids.map((uid) => db.doc(`users/${uid}`).get()));
  const recipients = docs.filter((d) => d.exists && d.get("fcmToken") &&
    !(kind && (d.get("notificationsDisabled") || []).includes(kind)));
  if (recipients.length === 0) return 0;

  const response = await getMessaging().sendEachForMulticast({
    tokens: recipients.map((d) => d.get("fcmToken")),
    data,
    // priority high para que Doze no la retenga; ttl explícito para que no llegue horas tarde.
    android: { priority: "high", ttl: 10 * 60 * 1000 },
  });

  const expired = ["messaging/registration-token-not-registered", "messaging/invalid-registration-token"];
  await Promise.all(response.responses.map((r, i) =>
    !r.success && expired.includes(r.error.code)
      ? recipients[i].ref.update({ fcmToken: FieldValue.delete() })
      : null));
  return response.successCount;
}

module.exports = { sendPush };
