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
const { sembrar } = require("./semilla");

// Tiene que ser la misma región que se eligió al crear la base de Firestore.
setGlobalOptions({ region: "southamerica-east1", maxInstances: 5 });

initializeApp();
const db = getFirestore();

const ROLES = ["CONDUCTOR", "ADMIN_ESTACIONAMIENTO", "ADMIN"];
const CATEGORIA_INICIAL = "COMUN";
const ADMIN_EMAILS = defineString("ADMIN_EMAILS", { default: "" });

function exigirAdmin(request) {
  if (!request.auth || request.auth.token.rol !== "ADMIN") {
    throw new HttpsError("permission-denied", "Sólo un administrador puede hacer esto.");
  }
}

// El claim es la fuente de verdad (lo leen las reglas). `usuarios/{uid}.rol` es el aviso
// para que la app refresque el token; el dueño no puede escribir ese campo.
async function fijarRol(uid, rol) {
  await getAuth().setCustomUserClaims(uid, { rol });
  await db.doc(`usuarios/${uid}`).set({ rol }, { merge: true });
}

// Todo usuario nuevo entra como CONDUCTOR y con categoría COMUN, salvo los emails de
// ADMIN_EMAILS, que entran como ADMIN.
exports.asignarRolInicial = onDocumentCreated("usuarios/{uid}", async (event) => {
  const uid = event.params.uid;
  const usuario = await getAuth().getUser(uid);
  const rolActual = usuario.customClaims && usuario.customClaims.rol;

  if (rolActual) {
    // La cuenta ya tenía rol y su documento se volvió a crear: sólo se repone el aviso.
    await event.data.ref.set({ rol: rolActual, categoria: CATEGORIA_INICIAL }, { merge: true });
    return;
  }

  const admins = ADMIN_EMAILS.value().split(",").map((e) => e.trim().toLowerCase()).filter(Boolean);
  const rol = admins.includes((usuario.email || "").toLowerCase()) ? "ADMIN" : "CONDUCTOR";
  await event.data.ref.set({ categoria: CATEGORIA_INICIAL }, { merge: true });
  await fijarRol(uid, rol);
  logger.info("Rol inicial asignado", { uid, rol });
});

// Un ADMIN cambia el rol de otro usuario. Datos: { uid, rol }.
exports.asignarRol = onCall(async (request) => {
  exigirAdmin(request);
  const { uid, rol } = request.data || {};
  if (typeof uid !== "string" || !ROLES.includes(rol)) {
    throw new HttpsError("invalid-argument", "Se espera { uid, rol } con un rol válido.");
  }
  await fijarRol(uid, rol);
  return { uid, rol };
});

// Un ADMIN carga (o restablece) los datos semilla en la base. Ver semilla.js.
exports.cargarSemilla = onCall(async (request) => {
  exigirAdmin(request);
  const resultado = await sembrar(db);
  logger.info("Semilla cargada", resultado);
  return resultado;
});

// Envía una push sólo `data` (todos strings) a los usuarios indicados y limpia tokens vencidos.
async function enviarPush(uids, data) {
  const docs = await Promise.all(uids.map((uid) => db.doc(`usuarios/${uid}`).get()));
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
  if (!reserva.usuarioId) return;
  await enviarPush([reserva.usuarioId], {
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

// Simulador de sensores mínimo: cada corrida cambia algunos lugares entre LIBRE y OCUPADO.
// Los RESERVADO y FUERA_DE_SERVICIO no se tocan. Todavía no actualiza los contadores de
// libres del estacionamiento. Se pausa desde Cloud Scheduler en la consola de Google Cloud.
const LUGARES_POR_CORRIDA = 50;
const PROBABILIDAD_DE_CAMBIO = 0.2;

exports.simularSensores = onSchedule("every 5 minutes", async () => {
  const lugares = await db.collectionGroup("lugares").limit(LUGARES_POR_CORRIDA).get();
  const lote = db.batch();
  let cambios = 0;

  lugares.forEach((lugar) => {
    const estado = lugar.get("estado");
    const loMueveElSensor = estado === "LIBRE" || estado === "OCUPADO";
    if (!loMueveElSensor || Math.random() > PROBABILIDAD_DE_CAMBIO) return;
    lote.update(lugar.ref, {
      estado: estado === "OCUPADO" ? "LIBRE" : "OCUPADO",
      actualizadoEn: FieldValue.serverTimestamp(),
    });
    cambios++;
  });

  if (cambios > 0) await lote.commit();
  logger.info("Simulación de sensores", { revisados: lugares.size, cambios });
});
