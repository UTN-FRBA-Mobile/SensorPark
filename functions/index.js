const { setGlobalOptions } = require("firebase-functions/v2");
const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { defineString } = require("firebase-functions/params");
const logger = require("firebase-functions/logger");
const { initializeApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

// Tiene que ser la misma región que se eligió al crear la base de Firestore.
setGlobalOptions({ region: "southamerica-east1", maxInstances: 5 });

initializeApp();
const db = getFirestore();

const ROLES = ["CONDUCTOR", "ADMIN_ESTACIONAMIENTO", "ADMIN"];
const ADMIN_EMAILS = defineString("ADMIN_EMAILS", { default: "" });

// El claim es la fuente de verdad (lo leen las reglas). `users/{uid}.rol` es el aviso
// para que la app refresque el token; el dueño no puede escribir ese campo.
async function fijarRol(uid, rol) {
  await getAuth().setCustomUserClaims(uid, { rol });
  await db.doc(`users/${uid}`).set({ rol }, { merge: true });
}

// Todo usuario nuevo entra como CONDUCTOR, salvo los emails de ADMIN_EMAILS.
exports.asignarRolInicial = onDocumentCreated("users/{uid}", async (event) => {
  const uid = event.params.uid;
  const usuario = await getAuth().getUser(uid);
  if (usuario.customClaims && usuario.customClaims.rol) return;

  const admins = ADMIN_EMAILS.value().split(",").map((e) => e.trim().toLowerCase()).filter(Boolean);
  const rol = admins.includes((usuario.email || "").toLowerCase()) ? "ADMIN" : "CONDUCTOR";
  await fijarRol(uid, rol);
  logger.info("Rol inicial asignado", { uid, rol });
});

// Un ADMIN cambia el rol de otro usuario. Datos: { uid, rol }.
exports.asignarRol = onCall(async (request) => {
  if (!request.auth || request.auth.token.rol !== "ADMIN") {
    throw new HttpsError("permission-denied", "Sólo un administrador puede asignar roles.");
  }
  const { uid, rol } = request.data || {};
  if (typeof uid !== "string" || !ROLES.includes(rol)) {
    throw new HttpsError("invalid-argument", "Se espera { uid, rol } con un rol válido.");
  }
  await fijarRol(uid, rol);
  return { uid, rol };
});

// Envía una push sólo `data` (todos strings) a los usuarios indicados y limpia tokens vencidos.
async function enviarPush(uids, data) {
  const docs = await Promise.all(uids.map((uid) => db.doc(`users/${uid}`).get()));
  const destinos = docs.filter((d) => d.exists && d.get("fcmToken"));
  if (destinos.length === 0) return 0;

  const respuesta = await getMessaging().sendEachForMulticast({
    tokens: destinos.map((d) => d.get("fcmToken")),
    data,
    // priority high para que Doze no la retenga; ttl explícito para que no llegue horas tarde.
    android: { priority: "high", ttl: 10 * 60 * 1000 },
  });

  const vencidos = ["messaging/registration-token-not-registered", "messaging/invalid-registration-token"];
  await Promise.all(respuesta.responses.map((r, i) =>
    !r.success && vencidos.includes(r.error.code)
      ? destinos[i].ref.update({ fcmToken: FieldValue.delete() })
      : null));
  return respuesta.successCount;
}

// Ejemplo del circuito de avisos: confirma al conductor que su reserva quedó registrada.
exports.avisarReservaCreada = onDocumentCreated("reservas/{reservaId}", async (event) => {
  const reserva = event.data.data();
  if (!reserva.conductorUid) return;
  await enviarPush([reserva.conductorUid], {
    tipo: "reserva_creada",
    id: event.params.reservaId,
    titulo: "Reserva confirmada",
    cuerpo: "Tu lugar quedó reservado.",
  });
});

// La usa la pantalla de diagnóstico de la app: manda una push al propio usuario para
// verificar de punta a punta Functions + FCM.
exports.pushDePrueba = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Hay que iniciar sesión.");
  const enviadas = await enviarPush([request.auth.uid], {
    tipo: "prueba",
    id: String(Date.now()),
    titulo: "Push de prueba",
    cuerpo: "Functions y FCM están funcionando.",
  });
  if (enviadas === 0) {
    throw new HttpsError("failed-precondition", "No hay un token FCM válido registrado para este usuario.");
  }
  return { enviadas };
});

// Simulador de sensores: cada corrida cambia el estado de algunas cocheras entre LIBRE y OCUPADA.
// Las RESERVADA no se tocan. Se pausa desde Cloud Scheduler en la consola de Google Cloud.
const COCHERAS_POR_CORRIDA = 50;
const PROBABILIDAD_DE_CAMBIO = 0.2;

exports.simularSensores = onSchedule("every 5 minutes", async () => {
  const cocheras = await db.collectionGroup("cocheras").limit(COCHERAS_POR_CORRIDA).get();
  const lote = db.batch();
  let cambios = 0;

  cocheras.forEach((cochera) => {
    const estado = cochera.get("estado");
    if (estado === "RESERVADA" || Math.random() > PROBABILIDAD_DE_CAMBIO) return;
    lote.update(cochera.ref, {
      estado: estado === "OCUPADA" ? "LIBRE" : "OCUPADA",
      actualizadoEn: FieldValue.serverTimestamp(),
    });
    cambios++;
  });

  if (cambios > 0) await lote.commit();
  logger.info("Simulación de sensores", { revisadas: cocheras.size, cambios });
});
