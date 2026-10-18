package com.sensorpark.domain.model

/**
 * Los tipos de aviso de la app. Cada uno tiene su canal en Android, su interruptor en la pestaña
 * Cuenta y su pantalla de destino. El backend manda el nombre en el campo `type` de la push:
 * alcanza con que empiece con [pushPrefix] (por ejemplo `reservationConfirmed`).
 */
enum class NotificationType(val pushPrefix: String) {
    /** Confirmación, cancelación y vencimiento de una reserva. */
    RESERVATION("reservation"),

    /** Aviso antes de la hora de llegada. */
    REMINDER("reminder"),

    /** Llegaste al estacionamiento: lo dispara la geocerca. */
    ARRIVAL("arrival"),

    /** Se liberó un lugar para quien está en la lista de espera. */
    WAITLIST("waitlist");

    companion object {
        fun from(value: Any?): NotificationType? = entries.firstOrNull { it.name == value }

        fun fromPushType(value: String?): NotificationType? =
            if (value == null) null else entries.firstOrNull { value.startsWith(it.pushPrefix) }
    }
}

/** Un botón de la notificación; [deepLink] es a dónde lleva. */
data class NotificationAction(val label: String, val deepLink: String)

/**
 * Un aviso listo para mostrar, lo haya pedido el backend (push) o la propia app (geocerca,
 * recordatorio). Los textos vienen resueltos: el dominio no conoce los recursos de Android.
 *
 * @param key identifica aquello sobre lo que se avisa (tipo + id): dos avisos con la misma clave
 * se reemplazan en vez de apilarse.
 * @param ongoing aviso persistente, que no se va al tocarlo (la estadía en curso).
 */
data class AppNotification(
    val type: NotificationType,
    val key: String,
    val title: String,
    val body: String,
    val deepLink: String? = null,
    val actions: List<NotificationAction> = emptyList(),
    val ongoing: Boolean = false,
)

/**
 * Qué avisos quiere recibir el usuario. Todo arranca encendido: sólo se guarda lo que apagó, así
 * un tipo nuevo no queda silenciado para quien ya tenía preferencias guardadas.
 */
data class NotificationPreferences(val disabled: Set<NotificationType> = emptySet()) {
    fun isEnabled(type: NotificationType): Boolean = type !in disabled

    fun with(type: NotificationType, enabled: Boolean): NotificationPreferences =
        copy(disabled = if (enabled) disabled - type else disabled + type)
}
